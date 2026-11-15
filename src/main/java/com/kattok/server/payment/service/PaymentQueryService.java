package com.kattok.server.payment.service;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.card.service.PaymentCardReader;
import com.kattok.server.global.ApiException;
import com.kattok.server.payment.dto.PaymentResponses;
import com.kattok.server.payment.dto.PaymentResponses.ByCard;
import com.kattok.server.payment.dto.PaymentResponses.Card;
import com.kattok.server.payment.dto.PaymentResponses.CardOption;
import com.kattok.server.payment.dto.PaymentResponses.History;
import com.kattok.server.payment.dto.PaymentResponses.Options;
import com.kattok.server.payment.dto.PaymentResponses.RecommendedCard;
import com.kattok.server.payment.dto.PaymentResponses.SavedBenefit;
import com.kattok.server.payment.entity.PaymentRecord;
import com.kattok.server.payment.entity.PaymentRecordStatus;
import com.kattok.server.payment.repository.PaymentRecordRepository;
import com.kattok.server.recommendation.dto.RecommendationResponse;
import com.kattok.server.recommendation.dto.RecommendationResponse.Item;
import com.kattok.server.recommendation.service.PaymentRecommendationReader;
import com.kattok.server.user.service.CurrentUserProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentQueryService {
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final Comparator<PaymentResponses.Record> NEWEST = Comparator
			.comparing(PaymentResponses.Record::recommendedAt).reversed()
			.thenComparing(PaymentResponses.Record::recommendationId, Comparator.reverseOrder());
	private final PaymentRecordRepository payments;
	private final PaymentRecommendationReader recommendations;
	private final PaymentCardReader cards;
	private final CurrentUserProvider currentUser;

	public List<PaymentResponses.Record> pending() {
		Long userId = currentUser.get().getId();
		return records(userId, payments.findByUserIdAndStatus(userId, PaymentRecordStatus.PENDING));
	}

	public Options options(Long recommendationId) {
		Long userId = currentUser.get().getId();
		RecommendationResponse snapshot = recommendations.read(userId, List.of(recommendationId)).get(recommendationId);
		if (snapshot == null) throw ApiException.notFound("RECOMMENDATION_NOT_FOUND", "존재하지 않는 추천입니다.");
		PaymentRecord payment = payments.findByRecommendationId(recommendationId)
				.orElseThrow(() -> ApiException.conflict("PAYMENT_NOT_REQUESTED", "결제 확인 대기 기록이 없습니다."));
		var record = record(userId, payment, snapshot);
		var options = cards.active(userId).stream()
				.filter(card -> !Objects.equals(card.userCardId(), snapshot.recommendedUserCardId()))
				.map(card -> new CardOption(card.userCardId(), card.productName(), card.issuerName(),
						benefit(snapshot, card.userCardId()))).toList();
		return new Options(record.recommendationId(), record.result(), record.merchant(), record.category(),
				record.amount(), record.recommendedCard(), record.userCard(), record.savedBenefit(),
				record.recommendedAt(), record.confirmedAt(), options);
	}

	public History history(String monthText, Long userCardId) {
		YearMonth month = parseMonth(monthText);
		Long userId = currentUser.get().getId();
		if (userCardId != null) cards.owned(userId, userCardId);
		var confirmed = payments.findConfirmedInMonth(userId, PaymentRecordStatus.PENDING,
				month.atDay(1).atStartOfDay(KST).toInstant(),
				month.plusMonths(1).atDay(1).atStartOfDay(KST).toInstant());
		var records = records(userId, confirmed).stream()
				.filter(record -> userCardId == null || record.userCard() != null
						&& record.userCard().userCardId().equals(userCardId)).toList();
		var counted = records.stream().filter(record -> record.userCard() != null && record.savedBenefit() != null)
				.filter(record -> record.result().equals("PAID_RECOMMENDED") || record.result().equals("PAID_OTHER"))
				.toList();
		Map<Long, List<PaymentResponses.Record>> byCard = new LinkedHashMap<>();
		counted.forEach(record -> byCard.computeIfAbsent(record.userCard().userCardId(), key -> new ArrayList<>()).add(record));
		var groups = byCard.entrySet().stream().map(entry -> new ByCard(entry.getKey(),
				entry.getValue().getFirst().userCard().productName(),
				entry.getValue().stream().mapToLong(record -> record.savedBenefit()).sum(), entry.getValue().size()))
				.sorted(Comparator.comparingLong(ByCard::amount).reversed().thenComparing(ByCard::userCardId)).toList();
		return new History(month.toString(), new SavedBenefit(counted.stream().mapToLong(record -> record.savedBenefit()).sum(),
				counted.size()), groups, payments.countByUserIdAndStatus(userId, PaymentRecordStatus.PENDING), records);
	}

	private List<PaymentResponses.Record> records(Long userId, List<PaymentRecord> records) {
		if (records.isEmpty()) return List.of();
		var snapshots = recommendations.read(userId, records.stream().map(p -> p.getRecommendation().getId()).toList());
		return records.stream().map(payment -> record(userId, payment, snapshots.get(payment.getRecommendation().getId())))
				.sorted(NEWEST).toList();
	}

	private PaymentResponses.Record record(Long userId, PaymentRecord payment, RecommendationResponse snapshot) {
		Item top = snapshot.items().stream().filter(item -> item.userCardId().equals(snapshot.recommendedUserCardId()))
				.findFirst().orElseThrow();
		var recommended = new RecommendedCard(top.userCardId(), top.productName(), top.expectedBenefit(),
				cards.owned(userId, top.userCardId()).deleted());
		Long actualId = switch (payment.getStatus()) {
			case PAID_RECOMMENDED -> top.userCardId();
			case PAID_OTHER -> payment.getActualUserCard().getId();
			default -> null;
		};
		Card actual = actualId == null ? null : new Card(actualId, payment.getActualCardNameSnapshot() != null
				? payment.getActualCardNameSnapshot() : snapshot.items().stream()
				.filter(item -> item.userCardId().equals(actualId)).map(Item::productName).findFirst()
				.orElseGet(() -> snapshot.excludedCards().stream().filter(item -> item.userCardId().equals(actualId))
						.map(item -> item.productName()).findFirst().orElseGet(() -> cards.owned(userId, actualId).productName())));
		return new PaymentResponses.Record(snapshot.id(), payment.getStatus().name(), snapshot.merchant(), snapshot.category(),
				snapshot.amount(), recommended, actual, payment.getConfirmationVersion() > 0 ? payment.getSavedBenefit()
				: actualId == null ? Long.valueOf(0) : benefit(snapshot, actualId),
				snapshot.createdAt(), payment.getConfirmedAt());
	}

	private static Long benefit(RecommendationResponse snapshot, Long cardId) {
		return snapshot.items().stream().filter(item -> item.userCardId().equals(cardId)).map(Item::expectedBenefit)
				.findFirst().orElse(null);
	}

	private static YearMonth parseMonth(String input) {
		if (input == null) return YearMonth.now(KST);
		if (!input.matches("[0-9]{4}-(0[1-9]|1[0-2])")) throw ApiException.invalidInput("month는 YYYY-MM 형식이어야 합니다.");
		return YearMonth.parse(input);
	}
}
