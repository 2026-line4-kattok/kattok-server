package com.kattok.server.benefit.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.kattok.server.benefit.engine.BenefitDetails.AssumptionType;
import com.kattok.server.benefit.engine.BenefitEngine.CardInput;
import com.kattok.server.benefit.engine.ProductRules.Cap;
import com.kattok.server.benefit.engine.ProductRules.Tier;
import com.kattok.server.card.dto.CapState;
import com.kattok.server.card.dto.PerformanceState;
import com.kattok.server.card.dto.PerformanceStatus;
import com.kattok.server.card.dto.UserCardSnapshot;
import com.kattok.server.card.entity.ConfirmationSource;

/**
 * 조건부 추천에서 가정이 결과에 주는 영향을 본다. Spring과 JPA에 의존하지 않는다.
 *
 * <ul>
 *   <li>{@link #confirmedOnlyTop}: 모르는 값을 모두 가장 불리하게(실적 미달, 남은 한도 0) 두고 다시 계산한 1위</li>
 *   <li>{@link #affectsRank}: 카드 한 장의 가정 하나를 실제로 가능한 다른 값으로 바꿨을 때 추천 카드가 바뀌는지</li>
 * </ul>
 */
public class AssumptionAnalyzer {

	private final BenefitEngine engine;

	public AssumptionAnalyzer(BenefitEngine engine) {
		this.engine = engine;
	}

	/** 확인된 정보만으로 계산한 1위. 혜택을 확정할 수 있는 카드가 없으면 {@code null}. */
	public Long confirmedOnlyTop(List<CardInput> cards, PaymentContext payment) {
		List<CardInput> pessimistic = cards.stream()
				.map(card -> new CardInput(
						withUnknownLimitsExhausted(withUnknownPerformanceBelow(card.snapshot()), card.product()),
						card.product()))
				.toList();
		return engine.recommend(pessimistic, payment).recommendedUserCardId();
	}

	/**
	 * 카드 한 장의 가정이 추천 순위에 영향을 주는지.
	 *
	 * <p>실적 가정은 '못 받음'과 혜택이 있는 다른 실적 구간을 모두 넣어 보고, 한도 가정은 모르는 한도를 다 쓴 경우를 넣어 본다
	 * (전체 한도가 남은 경우는 이미 계산에 쓴 가정이다). 하나라도 추천 카드가 바뀌면 영향이 있다.
	 */
	public boolean affectsRank(
			List<CardInput> cards, PaymentContext payment, Long userCardId, AssumptionType type, Long currentTop) {
		CardInput target = cards.stream()
				.filter(card -> card.snapshot().userCardId().equals(userCardId))
				.findFirst()
				.orElseThrow();
		for (UserCardSnapshot alternative : alternatives(target, type)) {
			List<CardInput> replaced = new ArrayList<>(cards.size());
			for (CardInput card : cards) {
				replaced.add(card == target ? new CardInput(alternative, card.product()) : card);
			}
			Long top = engine.recommend(replaced, payment).recommendedUserCardId();
			if (!Objects.equals(top, currentTop)) {
				return true;
			}
		}
		return false;
	}

	private static List<UserCardSnapshot> alternatives(CardInput card, AssumptionType type) {
		UserCardSnapshot snapshot = card.snapshot();
		return switch (type) {
			case PERFORMANCE_TIER -> {
				List<UserCardSnapshot> options = new ArrayList<>();
				options.add(withPerformance(snapshot, PerformanceState.below()));
				for (Tier tier : card.product().tiers().values()) {
					options.add(withPerformance(snapshot, PerformanceState.confirmed(tier.id())));
				}
				yield options;
			}
			case REMAINING_LIMIT, REMAINING_COUNT -> List.of(withUnknownLimitsExhausted(snapshot, card.product()));
		};
	}

	private static UserCardSnapshot withUnknownPerformanceBelow(UserCardSnapshot snapshot) {
		if (snapshot.performance().status() != PerformanceStatus.UNKNOWN) {
			return snapshot;
		}
		return withPerformance(snapshot, PerformanceState.below());
	}

	private static UserCardSnapshot withPerformance(UserCardSnapshot snapshot, PerformanceState performance) {
		return new UserCardSnapshot(
				snapshot.userCardId(), snapshot.productId(), snapshot.registrationOrder(), performance, snapshot.caps());
	}

	/** 모르는 남은 한도·횟수를 0으로 둔다. 아는 값은 그대로 둔다. */
	private static UserCardSnapshot withUnknownLimitsExhausted(UserCardSnapshot snapshot, ProductRules product) {
		Map<Long, CapState> caps = new HashMap<>(snapshot.caps());
		for (Cap cap : product.caps().values()) {
			CapState state = caps.get(cap.id());
			Long amount = state == null ? null : state.remainingAmount();
			Integer count = state == null ? null : state.remainingCount();
			if (cap.capAmount() != null && amount == null) {
				amount = 0L;
			}
			if (cap.capCount() != null && count == null) {
				count = 0;
			}
			ConfirmationSource source = state == null || state.source() == null
					? ConfirmationSource.USER_CONFIRMED
					: state.source();
			caps.put(cap.id(), new CapState(amount, count, source));
		}
		return new UserCardSnapshot(
				snapshot.userCardId(), snapshot.productId(), snapshot.registrationOrder(), snapshot.performance(), caps);
	}
}
