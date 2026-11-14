package com.kattok.server.benefit.engine;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * {@code benefit_rules.conditions}(jsonb)에서 계산 엔진이 읽는 조건. 키 목록은 노션 「백엔드 역할 분담」의
 * 「개발 시작 전 결정」 표를 따르고, 목록에 없는 키(예: {@code channel})는 읽지 않는다.
 */
public record RuleConditions(
		boolean allMerchants,
		Set<Long> excludedCategoryIds,
		Set<Long> excludedMerchantIds,
		Set<DayOfWeek> daysOfWeek,
		TimeRange timeRange,
		Long maxPaymentAmount) {

	public static final RuleConditions NONE = new RuleConditions(false, Set.of(), Set.of(), Set.of(), null, null);

	public RuleConditions {
		excludedCategoryIds = Set.copyOf(excludedCategoryIds);
		excludedMerchantIds = Set.copyOf(excludedMerchantIds);
		daysOfWeek = daysOfWeek.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(daysOfWeek));
	}

	public static RuleConditions from(Map<String, Object> raw) {
		if (raw == null || raw.isEmpty()) {
			return NONE;
		}
		return new RuleConditions(
				Boolean.TRUE.equals(raw.get("allMerchants")),
				toIds(raw.get("excludedCategoryIds")),
				toIds(raw.get("excludedMerchantIds")),
				toDays(raw.get("daysOfWeek")),
				toTimeRange(raw.get("timeRange")),
				raw.get("maxPaymentAmount") instanceof Number n ? n.longValue() : null);
	}

	/** 결제 금액 상한, 요일, 시간대 조건을 모두 만족하는지 본다. */
	boolean matches(PaymentContext payment) {
		if (maxPaymentAmount != null && payment.amount() > maxPaymentAmount) {
			return false;
		}
		LocalDateTime paidAt = payment.paidAt();
		if (!daysOfWeek.isEmpty() && !daysOfWeek.contains(paidAt.getDayOfWeek())) {
			return false;
		}
		return timeRange == null || timeRange.contains(paidAt.toLocalTime());
	}

	boolean excludes(PaymentContext payment) {
		return excludedCategoryIds.contains(payment.categoryId())
				|| (payment.merchantId() != null && excludedMerchantIds.contains(payment.merchantId()));
	}

	private static Set<Long> toIds(Object value) {
		if (!(value instanceof Collection<?> values)) {
			return Set.of();
		}
		Set<Long> ids = new HashSet<>();
		for (Object id : values) {
			ids.add(((Number) id).longValue());
		}
		return ids;
	}

	private static Set<DayOfWeek> toDays(Object value) {
		if (!(value instanceof Collection<?> values)) {
			return Set.of();
		}
		Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
		for (Object day : values) {
			days.add(parseDay(day.toString()));
		}
		return days;
	}

	private static DayOfWeek parseDay(String day) {
		return switch (day) {
			case "MON" -> DayOfWeek.MONDAY;
			case "TUE" -> DayOfWeek.TUESDAY;
			case "WED" -> DayOfWeek.WEDNESDAY;
			case "THU" -> DayOfWeek.THURSDAY;
			case "FRI" -> DayOfWeek.FRIDAY;
			case "SAT" -> DayOfWeek.SATURDAY;
			case "SUN" -> DayOfWeek.SUNDAY;
			default -> throw new IllegalArgumentException("알 수 없는 요일 값: " + day);
		};
	}

	private static TimeRange toTimeRange(Object value) {
		if (!(value instanceof Map<?, ?> range)) {
			return null;
		}
		return new TimeRange(LocalTime.parse(range.get("from").toString()), LocalTime.parse(range.get("to").toString()));
	}

	/**
	 * 적용 시간대(한국 시간). 시작 시각은 포함하고 끝 시각은 제외한다.
	 * {@code to}가 {@code from}보다 이르면 자정을 넘는 구간이다. 예: 21:00 ~ 09:00
	 */
	public record TimeRange(LocalTime from, LocalTime to) {

		boolean contains(LocalTime time) {
			if (from.isBefore(to)) {
				return !time.isBefore(from) && time.isBefore(to);
			}
			return !time.isBefore(from) || time.isBefore(to);
		}
	}
}
