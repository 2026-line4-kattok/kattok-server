package com.kattok.server.card.dto;

import com.kattok.server.card.entity.ConfirmationSource;

/**
 * 한도 그룹 하나의 이번 달 남은 한도.
 *
 * <p>상위 한도(parent)가 있는 한도 그룹은 하위와 상위가 각각 별도의 {@code CapState}로 들어간다.
 * 계산 엔진은 혜택 금액을 하위 한도와 그 위의 모든 상위 한도 중 가장 작은 남은 한도까지만 적용한다.
 *
 * @param remainingAmount 남은 한도(원). 모르면 {@code null}
 * @param remainingCount 남은 횟수. 횟수 제한이 없거나 모르면 {@code null}
 * @param source 값을 사용자가 입력했는지, 시스템이 추정했는지. 둘 다 모르면 {@code null}
 */
public record CapState(Long remainingAmount, Integer remainingCount, ConfirmationSource source) {
}
