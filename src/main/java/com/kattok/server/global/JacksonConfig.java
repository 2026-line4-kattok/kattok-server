package com.kattok.server.global;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * docs/conventions.md: "특정 시점은... API로 내보낼 때만 한국 시간(+09:00)으로 변환합니다." 기본
 * {@link Instant} 직렬화는 UTC("Z")라서, 한국 시간 오프셋으로 바꿔 내보내도록 전역으로 재정의한다.
 */
@Configuration
public class JacksonConfig {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	@Bean
	public JsonMapperBuilderCustomizer kstInstantSerialization() {
		SimpleModule module = new SimpleModule();
		module.addSerializer(Instant.class, new ValueSerializer<Instant>() {
			@Override
			public void serialize(Instant value, tools.jackson.core.JsonGenerator gen, tools.jackson.databind.SerializationContext ctxt) {
				gen.writeString(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(value.atZone(KST)));
			}
		});
		return builder -> builder.addModule(module);
	}
}
