package com.kattok.server.recommendation.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class RecommendationAccountData {
	private final JdbcTemplate jdbc;

	public void deleteForUser(Long userId) {
		jdbc.update("delete from recommendation_items where recommendation_id in (select id from recommendations where user_id=?)", userId);
		// confirmed_only_top_user_card_id도 참조하므로 카드보다 먼저 지운다. draft도 포함한다.
		jdbc.update("delete from recommendations where user_id=?", userId);
	}
}
