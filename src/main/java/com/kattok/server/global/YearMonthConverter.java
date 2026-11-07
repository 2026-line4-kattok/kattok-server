package com.kattok.server.global;

import java.time.LocalDate;
import java.time.YearMonth;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** YearMonth를 DB의 date 컬럼(해당 월 1일)으로 변환한다. 엔티티마다 {@code @Convert}를 안 붙여도 자동 적용된다. */
@Converter(autoApply = true)
public class YearMonthConverter implements AttributeConverter<YearMonth, LocalDate> {

	@Override
	public LocalDate convertToDatabaseColumn(YearMonth attribute) {
		return attribute == null ? null : attribute.atDay(1);
	}

	@Override
	public YearMonth convertToEntityAttribute(LocalDate dbData) {
		return dbData == null ? null : YearMonth.from(dbData);
	}
}
