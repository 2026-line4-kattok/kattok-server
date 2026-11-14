package com.kattok.server.card.repository;

import java.time.YearMonth;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kattok.server.card.entity.UserCardState;

public interface UserCardStateRepository extends JpaRepository<UserCardState, Long> {

	List<UserCardState> findByUserCardIdInAndPeriodMonth(Collection<Long> userCardIds, YearMonth periodMonth);
}
