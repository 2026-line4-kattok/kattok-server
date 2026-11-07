package com.kattok.server.card.entity;

/** 사용자 카드 상태(실적·한도)를 누가 확인했는지 구분한다. */
public enum ConfirmationSource {
	USER_CONFIRMED,
	SYSTEM_ESTIMATED
}
