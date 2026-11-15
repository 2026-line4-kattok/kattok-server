package com.kattok.server.payment.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class PaymentAccountData {
	private final JdbcTemplate jdbc;

	public void deleteForUser(Long userId) {
		// active=false인 취소 이력도 FK를 갖고 있으므로 모두 삭제한다.
		jdbc.update("delete from payment_cap_effects where payment_record_id in (select id from payment_records where user_id=?)", userId);
		jdbc.update("delete from payment_records where user_id=?", userId);
	}
}
