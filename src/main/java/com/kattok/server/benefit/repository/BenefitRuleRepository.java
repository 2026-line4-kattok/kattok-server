package com.kattok.server.benefit.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kattok.server.benefit.entity.BenefitRule;

public interface BenefitRuleRepository extends JpaRepository<BenefitRule, Long> {

	/** 상품들의 검증된 규칙 중 해당 날짜에 유효한 규칙. 실적 구간은 계산에 바로 쓰므로 함께 읽는다. */
	@Query("""
			select r from BenefitRule r
			left join fetch r.requiredTier
			where r.product.id in :productIds
				and r.verificationStatus = com.kattok.server.benefit.entity.VerificationStatus.VERIFIED
				and r.validFrom <= :date
				and (r.validTo is null or r.validTo >= :date)
			""")
	List<BenefitRule> findCalculableRules(@Param("productIds") Collection<Long> productIds, @Param("date") LocalDate date);

	/** 상품의 해당 날짜에 유효한 규칙 전체. 검증 여부와 관계없이 읽는다(카드 상세의 "추천 미반영" 표시용). */
	@Query("""
			select r from BenefitRule r
			where r.product.id = :productId
				and r.validFrom <= :date
				and (r.validTo is null or r.validTo >= :date)
			order by r.id
			""")
	List<BenefitRule> findValidRules(@Param("productId") Long productId, @Param("date") LocalDate date);

	/** 이 결제처 또는 이 업종을 대상으로 하는, 해당 날짜에 유효한 검증된 혜택 규칙이 하나라도 있는지. 결제처 지원 여부 판단에 쓴다. */
	@Query("""
			select count(r) > 0 from BenefitRule r
			where r.verificationStatus = com.kattok.server.benefit.entity.VerificationStatus.VERIFIED
				and (r.merchant.id = :merchantId or r.category.id = :categoryId)
				and r.validFrom <= :date
				and (r.validTo is null or r.validTo >= :date)
			""")
	boolean existsSupportedRule(@Param("merchantId") Long merchantId, @Param("categoryId") Long categoryId, @Param("date") LocalDate date);
}
