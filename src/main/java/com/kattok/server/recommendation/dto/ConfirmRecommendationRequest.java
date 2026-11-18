package com.kattok.server.recommendation.dto;

/**
 * 추천 확정 요청. 본문을 아예 안 보내면 추천 1순위 카드 그대로 확정한다.
 *
 * @param userCardId 비교 결과 중 추천과 다른 카드로 하기로 했으면 그 카드 id
 */
public record ConfirmRecommendationRequest(Long userCardId) {
}
