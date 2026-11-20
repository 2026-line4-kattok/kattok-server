package com.kattok.server.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.kattok.server.TestcontainersConfiguration;

/** 운영 배포처럼 demoseed=true로 마이그레이션하면 시연용 테스트 계정이 발표용 카드 2장으로 맞춰진다(V37). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
		properties = {"spring.flyway.placeholders.demoseed=true", "kattok.auth.anonymous-demo-user=true"})
@AutoConfigureMockMvc
class DemoAccountSeedMigrationTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("시연용 계정에 알파벳카드S 40만원·굿데이카드 60만원 구간이 이번 달과 다음 달 모두 실적 확정 상태로 들어간다")
	void seedsPresentationCards() throws Exception {
		List<Map<String, Object>> rows = jdbc.queryForList("""
				select p.product_key, t.min_amount, s.performance_status, s.period_month
				from users u
				join user_cards c on c.user_id = u.id and c.deleted_at is null
				join card_products p on p.id = c.product_id
				join user_card_states s on s.user_card_id = c.id
				join card_performance_tiers t on t.id = s.performance_tier_id
				where u.email = 'demo@kattok.app'
				order by p.product_key, s.period_month
				""");

		assertThat(rows).hasSize(4);
		assertThat(rows).extracting(row -> row.get("product_key") + ":" + row.get("min_amount"))
				.containsExactly("CR004:600000", "CR004:600000", "CR006:400000", "CR006:400000");
		assertThat(rows).extracting(row -> row.get("performance_status")).containsOnly("CONFIRMED");

		mockMvc.perform(get("/api/v1/owned-cards"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[*].performanceStatus").value(org.hamcrest.Matchers.everyItem(
						org.hamcrest.Matchers.is("CONFIRMED"))));
	}
}
