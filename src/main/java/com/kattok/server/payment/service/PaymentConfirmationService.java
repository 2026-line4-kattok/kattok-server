package com.kattok.server.payment.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.card.entity.UserCard;
import com.kattok.server.card.service.PaymentCapLedger;
import com.kattok.server.card.service.PaymentCardReader;
import com.kattok.server.global.ApiException;
import com.kattok.server.payment.dto.ConfirmPaymentRequest;
import com.kattok.server.payment.dto.PaymentResponses.Card;
import com.kattok.server.payment.entity.PaymentRecord;
import com.kattok.server.payment.entity.PaymentRecordStatus;
import com.kattok.server.payment.repository.PaymentRecordRepository;
import com.kattok.server.recommendation.dto.RecommendationResponse;
import com.kattok.server.recommendation.dto.RecommendationResponse.RemainingLimitProjection;
import com.kattok.server.recommendation.service.PaymentRecommendationReader;
import com.kattok.server.user.service.CurrentUserProvider;
import com.kattok.server.user.service.UserMutationGuard;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentConfirmationService {
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private final CurrentUserProvider currentUser;
	private final UserMutationGuard guard;
	private final PaymentRecommendationReader recommendations;
	private final PaymentRecordRepository payments;
	private final PaymentCardReader cards;
	private final PaymentCapLedger ledger;
	private final PaymentQueryService queries;
	private final EntityManager entityManager;

	public Response confirm(Long recommendationId, ConfirmPaymentRequest request) {
		Long userId = currentUser.get().getId();
		guard.lock(userId);
		RecommendationResponse snapshot = recommendations.lockForPayment(userId, recommendationId);
		PaymentRecord payment = payments.findForUpdate(recommendationId, userId)
				.orElseThrow(() -> ApiException.conflict("PAYMENT_NOT_REQUESTED", "결제 확인 대기 기록이 없습니다."));
		validateRequest(request, snapshot.recommendedUserCardId());
		LocalDate date = snapshot.createdAt().atZone(KST).toLocalDate();
		String month = YearMonth.from(date).toString();
		long before = queries.history(month, null).savedBenefit().amount();
		if (sameAnswer(payment, request)) {
			return response(payment, month, before, before, List.of());
		}
		Long actualId = switch (request.result()) {
			case PAID_RECOMMENDED -> snapshot.recommendedUserCardId();
			case PAID_OTHER -> request.userCardId();
			default -> null;
		};
		Long benefit = 0L; String name = null;
		List<RemainingLimitProjection> projections = List.of();
		if (actualId != null) {
			var card = cards.owned(userId, actualId);
			if (card.deleted()) throw ApiException.notFound("OWNED_CARD_NOT_FOUND", "존재하지 않는 카드입니다.");
			var item = snapshot.items().stream().filter(i -> i.userCardId().equals(actualId)).findFirst().orElse(null);
			benefit = item == null ? null : Long.valueOf(item.expectedBenefit());
			name = item == null ? snapshot.excludedCards().stream().filter(i -> i.userCardId().equals(actualId))
					.map(i -> i.productName()).findFirst().orElse(card.productName()) : item.productName();
			if (item != null && item.expectedBenefit() > 0) {
				if (!recommendations.accountingComplete(recommendationId, actualId)) throw incomplete();
				projections = validateProjections(item, recommendations.uncappedCapGroupIds(recommendationId, actualId));
			}
		}
		var changes = ledger.replace(payment.getId(), actualId, date, projections);
		payment.applyConfirmation(request.result(), actualId == null ? null : entityManager.getReference(UserCard.class, actualId),
				name, benefit, Instant.now().truncatedTo(ChronoUnit.MICROS));
		payments.flush();
		long after = queries.history(month, null).savedBenefit().amount();
		return response(payment, month, before, after, changes);
	}

	private static void validateRequest(ConfirmPaymentRequest request, Long recommendedId) {
		if (request.result() == null || request.result() == PaymentRecordStatus.PENDING
				|| request.result() == PaymentRecordStatus.PAID_OTHER && (request.userCardId() == null || Objects.equals(request.userCardId(), recommendedId))
				|| request.result() != PaymentRecordStatus.PAID_OTHER && request.userCardId() != null) {
			throw ApiException.invalidInput("결제 결과와 사용 카드를 확인해 주세요.");
		}
	}

	private static boolean sameAnswer(PaymentRecord payment, ConfirmPaymentRequest request) {
		return payment.getStatus() == request.result() && (request.result() != PaymentRecordStatus.PAID_OTHER
				|| payment.getActualUserCard() != null && payment.getActualUserCard().getId().equals(request.userCardId()));
	}

	private static List<RemainingLimitProjection> validateProjections(RecommendationResponse.Item item, Set<Long> uncapped) {
		Set<Long> connected = new HashSet<>();
		item.appliedRules().forEach(rule -> connected.addAll(rule.capGroupIds()));
		Set<Long> seen = new HashSet<>();
		for (var p : item.remainingLimitsAfter()) {
			if (p.capGroupId() == null || p.capPeriod() == null || !seen.add(p.capGroupId()) || !connected.contains(p.capGroupId())
					|| !valid(p.capAmount(), p.remainingLimitBefore(), p.remainingLimitAmount())
					|| !valid(p.capCount() == null ? null : p.capCount().longValue(),
							p.remainingCountBefore() == null ? null : p.remainingCountBefore().longValue(),
							p.remainingCount() == null ? null : p.remainingCount().longValue())) throw incomplete();
		}
		if (!java.util.Collections.disjoint(seen, uncapped)) throw incomplete();
		seen.addAll(uncapped);
		if (!seen.equals(connected)) throw incomplete();
		return item.remainingLimitsAfter().stream().sorted(java.util.Comparator.comparing(RemainingLimitProjection::capGroupId)).toList();
	}

	private static boolean valid(Long cap, Long before, Long after) {
		if (cap == null) return before == null && after == null;
		return cap >= 0 && before != null && after != null && before >= after && after >= 0 && before <= cap;
	}

	private static ApiException incomplete() {
		return ApiException.conflict("PAYMENT_SNAPSHOT_INCOMPLETE", "추천 당시 차감 정보가 없어 결제를 확인할 수 없습니다. 새 추천을 이용해 주세요.");
	}

	private Response response(PaymentRecord p, String month, long before, long after, List<PaymentCapLedger.Change> changes) {
		var record = queries.options(p.getRecommendation().getId());
		Card actual = record.userCard();
		return new Response(p.getId(), p.getRecommendation().getId(), p.getStatus().name(), actual, record.savedBenefit(),
				changes, new Monthly(month,before,after), p.getConfirmedAt());
	}

	public record Monthly(String month, long before, long after) {}
	public record Response(Long id, Long recommendationId, String result, Card userCard, Long savedBenefit,
			List<PaymentCapLedger.Change> capChanges, Monthly monthlySavedBenefit, Instant confirmedAt) {}
}
