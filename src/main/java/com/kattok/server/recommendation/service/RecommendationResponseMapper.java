package com.kattok.server.recommendation.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.kattok.server.recommendation.dto.RecommendationResponse;
import com.kattok.server.recommendation.dto.RecommendationResponse.AppliedRule;
import com.kattok.server.recommendation.dto.RecommendationResponse.Assumption;
import com.kattok.server.recommendation.dto.RecommendationResponse.Basis;
import com.kattok.server.recommendation.dto.RecommendationResponse.Calculation;
import com.kattok.server.recommendation.dto.RecommendationResponse.ExcludedCard;
import com.kattok.server.recommendation.dto.RecommendationResponse.IdName;
import com.kattok.server.recommendation.dto.RecommendationResponse.Item;
import com.kattok.server.recommendation.dto.RecommendationResponse.Reason;
import com.kattok.server.recommendation.dto.RecommendationResponse.RemainingLimit;
import com.kattok.server.recommendation.entity.Recommendation;
import com.kattok.server.recommendation.entity.RecommendationItem;
import com.kattok.server.recommendation.entity.RecommendationItemStatus;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 저장한 추천과 카드별 결과로 응답을 만든다. 추천 생성과 조회가 같은 응답을 쓰도록, 생성할 때도 저장한 값만으로 만든다.
 */
@Component
@RequiredArgsConstructor
class RecommendationResponseMapper {

	private final JsonMapper jsonMapper;

	RecommendationResponse toResponse(Recommendation recommendation, List<RecommendationItem> items) {
		List<RecommendationItem> ranked = items.stream()
				.filter(item -> item.getStatus() != RecommendationItemStatus.NOT_CALCULABLE)
				.sorted(Comparator.comparing(RecommendationItem::getRankNo))
				.toList();
		RecommendationItem recommended = ranked.stream().filter(RecommendationItem::isRecommended).findFirst()
				.orElse(null);

		List<Long> tied = recommended == null
				? List.of()
				: ranked.stream()
						.filter(item -> item != recommended)
						.filter(item -> item.getExpectedBenefitMax().equals(recommended.getExpectedBenefitMax()))
						.map(item -> item.getUserCard().getId())
						.toList();
		Long differenceFromSecond = recommended == null || ranked.size() < 2
				? null
				: ranked.get(0).getExpectedBenefitMax() - ranked.get(1).getExpectedBenefitMax();

		return new RecommendationResponse(
				recommendation.getId(),
				recommendation.getMerchant() == null ? Basis.CATEGORY : Basis.MERCHANT,
				recommendation.getMerchant() == null
						? null
						: new IdName(recommendation.getMerchant().getId(), recommendation.getMerchantNameSnapshot()),
				new IdName(recommendation.getCategory().getId(), recommendation.getCategory().getName()),
				recommendation.getAmount(),
				recommendation.getOverallStatus().name(),
				recommended == null ? null : recommended.getUserCard().getId(),
				tied,
				differenceFromSecond,
				recommendation.getConfirmedOnlyTopUserCard() == null
						? null
						: recommendation.getConfirmedOnlyTopUserCard().getId(),
				ranked.stream().map(this::toItem).toList(),
				items.stream()
						.filter(item -> item.getStatus() == RecommendationItemStatus.NOT_CALCULABLE)
						.map(this::toExcluded)
						.toList(),
				recommendation.getCreatedAt());
	}

	private Item toItem(RecommendationItem item) {
		Map<String, Object> applied = item.getAppliedRules();
		return new Item(
				item.getUserCard().getId(),
				item.getProductNameSnapshot(),
				item.getIssuerNameSnapshot(),
				item.getStatus().name(),
				item.getRankNo(),
				item.getExpectedBenefitMax(),
				read(applied.get("calculation"), new TypeReference<Calculation>() {
				}),
				orEmpty(read(applied.get("rules"), new TypeReference<List<AppliedRule>>() {
				})),
				orEmpty(read(item.getUnknownConditions().get("assumptions"), new TypeReference<List<Assumption>>() {
				})),
				read(applied.get("remainingLimitAfter"), new TypeReference<RemainingLimit>() {
				}),
				(String) applied.get("noBenefitReason"));
	}

	private ExcludedCard toExcluded(RecommendationItem item) {
		return new ExcludedCard(
				item.getUserCard().getId(),
				item.getProductNameSnapshot(),
				jsonMapper.convertValue(item.getExcludedReasons(), Reason.class));
	}

	private <T> T read(Object value, TypeReference<T> type) {
		return value == null ? null : jsonMapper.convertValue(value, type);
	}

	private static <T> List<T> orEmpty(List<T> values) {
		return values == null ? List.of() : values;
	}
}
