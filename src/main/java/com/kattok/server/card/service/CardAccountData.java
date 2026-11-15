package com.kattok.server.card.service;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.card.repository.UserCardRepository;
import lombok.RequiredArgsConstructor;

/** 계정 탈퇴 시 카드 도메인이 소유한 데이터만 처리한다. 공유 상품·혜택은 삭제하지 않는다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CardAccountData {
	private final UserCardRepository cards;
	private final JdbcTemplate jdbc;

	public long activeCardCount(Long userId) {
		return cards.countByUserIdAndDeletedAtIsNull(userId);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void deleteForUser(Long userId) {
		for (String table : List.of("card_cap_balances", "user_card_states", "user_card_cap_usages", "user_card_benefit_selections")) {
			jdbc.update("delete from " + table + " where user_card_id in (select id from user_cards where user_id=?)", userId);
		}
		jdbc.update("delete from user_cards where user_id=?", userId);
	}
}
