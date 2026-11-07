package com.kattok.server.recommendation.entity;

/** 추천 1건 전체의 비교 결과 상태. 사용자에게 전달하는 의미이며 화면 수를 결정하지 않는다. */
public enum OverallStatus {
	FULL,
	PARTIAL,
	CONDITIONAL,
	NO_BENEFIT,
	NOT_CALCULABLE
}
