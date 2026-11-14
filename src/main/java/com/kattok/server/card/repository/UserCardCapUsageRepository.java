package com.kattok.server.card.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kattok.server.card.entity.UserCardCapUsage;

public interface UserCardCapUsageRepository extends JpaRepository<UserCardCapUsage, Long> {

	List<UserCardCapUsage> findByUserCardIdAndPeriodMonth(Long userCardId, YearMonth periodMonth);

	List<UserCardCapUsage> findByUserCardIdInAndPeriodMonth(Collection<Long> userCardIds, YearMonth periodMonth);

	/**
	 * 한도 그룹 하나의 한 달 잔여 한도를 upsert한다. {@link com.kattok.server.benefit.entity.BenefitCapGroup}
	 * 엔티티 참조 없이 id만으로 쓰려고 네이티브 쿼리로 작성했다(다른 도메인 repository를 직접 호출하지 않기 위함).
	 */
	@Modifying
	@Query(value = """
			insert into user_card_cap_usages (
				user_card_id, cap_group_id, period_month, remaining_limit_amount, remaining_count, source, confirmed_at, created_at)
			values (:userCardId, :capGroupId, :periodMonth, :remainingLimitAmount, :remainingCount, :source, :confirmedAt, now())
			on conflict (user_card_id, cap_group_id, period_month)
			do update set
				remaining_limit_amount = excluded.remaining_limit_amount,
				remaining_count = excluded.remaining_count,
				source = excluded.source,
				confirmed_at = excluded.confirmed_at
			""", nativeQuery = true)
	void upsert(
			@Param("userCardId") Long userCardId,
			@Param("capGroupId") Long capGroupId,
			@Param("periodMonth") LocalDate periodMonth,
			@Param("remainingLimitAmount") Long remainingLimitAmount,
			@Param("remainingCount") Integer remainingCount,
			@Param("source") String source,
			@Param("confirmedAt") Instant confirmedAt);
}
