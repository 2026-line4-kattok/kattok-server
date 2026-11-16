package com.kattok.server.benefit.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.kattok.server.card.entity.CardPerformanceTier;
import com.kattok.server.card.entity.CardProduct;
import com.kattok.server.merchant.entity.Merchant;
import com.kattok.server.merchant.entity.MerchantCategory;

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
@Table(name = "benefit_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BenefitRule {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id", nullable = false)
	private CardProduct product;

	/** 대상 업종. merchant와 둘 다 NULL이면 전 업종 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "category_id")
	private MerchantCategory category;

	/** 대상 결제처 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "merchant_id")
	private Merchant merchant;

	/** 요구 실적 구간. NULL이면 실적 조건 없음 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "required_tier_id")
	private CardPerformanceTier requiredTier;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "cap_group_id", nullable = false)
	private BenefitCapGroup capGroup;

	@Enumerated(EnumType.STRING)
	@Column(name = "benefit_type", nullable = false)
	private BenefitType benefitType;

	/** 혜택률. *_RATE일 때 필수 */
	private BigDecimal rate;

	/** 혜택 금액(원). *_FIXED일 때 필수 */
	private Long amount;

	/** 포인트 원화 환산 비율. MILE_RATE/MILE_FIXED는 정책적으로 항상 NULL(원화 비교 대상에서 제외) */
	@Column(name = "point_krw_ratio")
	private BigDecimal pointKrwRatio;

	/** 선택형 그룹 식별자 (예: SELECT_1, MODE). NULL이면 항상 적용되는 고정 혜택 */
	@Column(name = "select_group")
	private String selectGroup;

	/** 그룹 내 옵션 식별자. selectGroup이 있으면 필수 */
	@Column(name = "select_option")
	private String selectOption;

	@Column(name = "min_payment_amount")
	private Long minPaymentAmount;

	/** 할인이 적용되는 결제금액 자체의 상한 (예: "승인액 5만원까지만 적용") */
	@Column(name = "max_eligible_txn_amount")
	private Long maxEligibleTxnAmount;

	/** 화면에 보여줄 혜택 문구. 계산 조건이 아니라 표시용이라 별도 컬럼으로 둔다 */
	@Column(name = "display_text")
	private String displayText;

	/** 약관으로 중복 적용을 확인한 규칙만 true. 기존 규칙과 이미 합산된 요율은 기본 false를 유지한다. */
	@Column(nullable = false)
	private boolean stackable;

	/** 제외 업종·채널·결제수단 등. 최소 키: channel, requireAppRegistration, excludedSubmerchants, timeRange, daysOfWeek, installmentExcluded */
	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> conditions;

	/** 약관 파일과 페이지 */
	@Column(name = "source_ref")
	private String sourceRef;

	@Enumerated(EnumType.STRING)
	@Column(name = "verification_status", nullable = false)
	private VerificationStatus verificationStatus;

	@Column(name = "verified_at")
	private Instant verifiedAt;

	@Column(name = "rule_version", nullable = false)
	private int ruleVersion;

	@Column(name = "valid_from", nullable = false)
	private LocalDate validFrom;

	@Column(name = "valid_to")
	private LocalDate validTo;

	public BenefitRule(
			CardProduct product,
			MerchantCategory category,
			Merchant merchant,
			CardPerformanceTier requiredTier,
			BenefitCapGroup capGroup,
			BenefitType benefitType,
			BigDecimal rate,
			Long amount,
			BigDecimal pointKrwRatio,
			String selectGroup,
			String selectOption,
			Long minPaymentAmount,
			Long maxEligibleTxnAmount,
			String displayText,
			Map<String, Object> conditions,
			String sourceRef,
			VerificationStatus verificationStatus,
			Instant verifiedAt,
			int ruleVersion,
			LocalDate validFrom,
			LocalDate validTo) {
		this.product = product;
		this.category = category;
		this.merchant = merchant;
		this.requiredTier = requiredTier;
		this.capGroup = capGroup;
		this.benefitType = benefitType;
		this.rate = rate;
		this.amount = amount;
		this.pointKrwRatio = pointKrwRatio;
		this.selectGroup = selectGroup;
		this.selectOption = selectOption;
		this.minPaymentAmount = minPaymentAmount;
		this.maxEligibleTxnAmount = maxEligibleTxnAmount;
		this.displayText = displayText;
		this.conditions = conditions;
		this.sourceRef = sourceRef;
		this.verificationStatus = verificationStatus;
		this.verifiedAt = verifiedAt;
		this.ruleVersion = ruleVersion;
		this.validFrom = validFrom;
		this.validTo = validTo;
	}
}
