package com.kattok.server.recommendation.dto;

/**
 * 추천 확정 요청. 본문을 아예 안 보내면 추천 카드로 결제하겠다고 확정한다.
 *
 * @param userCardId 비교 결과 중 결제할 카드 id. 추천과 다른 카드면 결제 확인 대기 기록의 {@code chosenCard}로 남는다
 */
public record ConfirmRecommendationRequest(Long userCardId) {
}
