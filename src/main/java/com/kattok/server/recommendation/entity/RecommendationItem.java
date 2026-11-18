package com.kattok.server.recommendation.entity;

import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.kattok.server.card.entity.UserCard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "recommendation_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recommendation_id", nullable = false)
	private Recommendation recommendation;

	/** 비교 대상 보유 카드 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_card_id", nullable = false)
	private UserCard userCard;

	/** 추천 당시 상품명 스냅샷 */
	@Column(name = "product_name_snapshot", nullable = false)
	private String productNameSnapshot;

	/** 추천 당시 카드사명 스냅샷 */
	@Column(name = "issuer_name_snapshot", nullable = false)
	private String issuerNameSnapshot;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RecommendationItemStatus status;

	/** 대표 추천 카드 여부 */
	@Column(name = "is_recommended", nullable = false)
	private boolean recommended;

	/** 사용자가 결제 확정 시 다른 카드를 골라, 이 항목이 새 대표가 되거나 대표에서 빠질 때 쓴다. */
	public void setRecommended(boolean recommended) {
		this.recommended = recommended;
	}

	/** 순위. 동률이면 같은 값 */
	@Column(name = "rank_no")
	private Integer rankNo;

	@Column(name = "expected_benefit_min")
	private Long expectedBenefitMin;

	@Column(name = "expected_benefit_max")
	private Long expectedBenefitMax;

	/** ruleId, ruleVersion, 계산 근거 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "applied_rules", nullable = false)
	private Map<String, Object> appliedRules;

	/** 결과에 영향을 주는 미확인 조건 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "unknown_conditions", nullable = false)
	private Map<String, Object> unknownConditions;

	/** 제외된 혜택과 사유 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "excluded_reasons", nullable = false)
	private Map<String, Object> excludedReasons;

	public RecommendationItem(
			Recommendation recommendation,
			UserCard userCard,
			String productNameSnapshot,
			String issuerNameSnapshot,
			RecommendationItemStatus status,
			boolean recommended,
			Integer rankNo,
			Long expectedBenefitMin,
			Long expectedBenefitMax,
			Map<String, Object> appliedRules,
			Map<String, Object> unknownConditions,
			Map<String, Object> excludedReasons) {
		this.recommendation = recommendation;
		this.userCard = userCard;
		this.productNameSnapshot = productNameSnapshot;
		this.issuerNameSnapshot = issuerNameSnapshot;
		this.status = status;
		this.recommended = recommended;
		this.rankNo = rankNo;
		this.expectedBenefitMin = expectedBenefitMin;
		this.expectedBenefitMax = expectedBenefitMax;
		this.appliedRules = appliedRules;
		this.unknownConditions = unknownConditions;
		this.excludedReasons = excludedReasons;
	}
}
