package com.kattok.server.benefit.entity;

public enum BenefitType {
	DISCOUNT_RATE,
	DISCOUNT_FIXED,
	POINT_RATE,
	POINT_FIXED,
	/** 항공 마일리지 등 원화 환산이 불가능한 적립. pointKrwRatio는 항상 NULL로 취급한다. */
	MILE_RATE,
	MILE_FIXED
}
