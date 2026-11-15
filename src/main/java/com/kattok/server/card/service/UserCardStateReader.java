package com.kattok.server.card.service;

import java.time.YearMonth;
import java.time.LocalDate;
import java.util.List;

import com.kattok.server.card.dto.UserCardSnapshot;

/**
 * 계산 엔진이 추천할 때 읽는 사용자 카드 상태(실적 구간, 남은 한도) 조회 창구.
 *
 * <p>카드 상품 정보(혜택 규칙, 연회비, 실적 구간 정의, 한도 그룹의 상위 관계, 데이터 상태)는 여기서 주지 않는다.
 * 사용자와 상관없는 데이터라서 계산 엔진이 따로 읽는다.
 *
 * <p>모르는 값은 0이 아니라 {@code null}로 준다. 계산 엔진은 실적을 모르면 혜택을 받는 가장 낮은 구간,
 * 남은 한도를 모르면 해당 한도 그룹의 월 전체 한도로 가정하고 결과를 조건부로 표시한다.
 */
public interface UserCardStateReader {

	/**
	 * 사용자가 보유한(삭제하지 않은) 카드와 해당 월의 상태를 등록 순서대로 돌려준다.
	 *
	 * @param userId 사용자 id
	 * @param period 상태 기준 월(한국 시간 기준). 매월 1일 0시에 새 달로 넘어간다.
	 * @return 보유 카드가 없으면 빈 목록
	 */
	List<UserCardSnapshot> findForRecommendation(Long userId, YearMonth period);

	default List<UserCardSnapshot> findForRecommendation(Long userId, LocalDate date) {
		return findForRecommendation(userId, YearMonth.from(date));
	}
}
