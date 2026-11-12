package com.kattok.server.card.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PerformanceStateTest {

	@Test
	@DisplayName("실적 구간을 확인한 상태에는 구간 id가 있어야 한다")
	void confirmedRequiresTierId() {
		assertThat(PerformanceState.confirmed(3L).tierId()).isEqualTo(3L);
		assertThatThrownBy(() -> new PerformanceState(PerformanceStatus.CONFIRMED, null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("실적을 못 채웠거나 모르는 상태에는 구간 id를 넣지 않는다")
	void belowAndUnknownHaveNoTierId() {
		assertThat(PerformanceState.below().tierId()).isNull();
		assertThat(PerformanceState.unknown().tierId()).isNull();
		assertThatThrownBy(() -> new PerformanceState(PerformanceStatus.UNKNOWN, 3L))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
