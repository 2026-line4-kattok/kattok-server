package com.kattok.server.benefit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.kattok.server.TestcontainersConfiguration;

/** Flyway로 적재한 혜택 규칙 데이터가 화면·계산에서 기대하는 기본 조건을 지키는지 확인한다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BenefitRuleSeedDataTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	@DisplayName("모든 혜택 규칙에 화면에 보여줄 문구가 있다 (V32)")
	void everyRuleHasDisplayText() {
		List<String> missing = jdbc.queryForList("""
				select p.name || ' / rule ' || r.id
				from benefit_rules r join card_products p on p.id = r.product_id
				where r.display_text is null or btrim(r.display_text) = ''
				order by r.id
				""", String.class);

		assertThat(missing).isEmpty();
	}
}
