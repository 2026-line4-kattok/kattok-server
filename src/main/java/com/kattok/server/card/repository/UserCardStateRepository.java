package com.kattok.server.card.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kattok.server.card.entity.UserCardState;

public interface UserCardStateRepository extends JpaRepository<UserCardState, Long> {

	List<UserCardState> findByUserCardIdInAndPeriodMonth(Collection<Long> userCardIds, YearMonth periodMonth);

	Optional<UserCardState> findByUserCardIdAndPeriodMonth(Long userCardId, YearMonth periodMonth);

	/**
	 * 한 달의 실적 구간 확인 상태를 통째로 덮어쓴다(upsert). {@code performanceTierId}가 {@code null}이면
	 * "확인했지만 모름" 상태로 저장된다.
	 */
	@Modifying
	@Query(value = """
			insert into user_card_states (user_card_id, period_month, performance_tier_id, source, confirmed_at, created_at)
			values (:userCardId, :periodMonth, :performanceTierId, :source, :confirmedAt, now())
			on conflict (user_card_id, period_month)
			do update set
				performance_tier_id = excluded.performance_tier_id,
				source = excluded.source,
				confirmed_at = excluded.confirmed_at
			""", nativeQuery = true)
	void upsert(
			@Param("userCardId") Long userCardId,
			@Param("periodMonth") LocalDate periodMonth,
			@Param("performanceTierId") Long performanceTierId,
			@Param("source") String source,
			@Param("confirmedAt") Instant confirmedAt);
}
