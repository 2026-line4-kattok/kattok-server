package com.kattok.server.card.dto;

import jakarta.validation.constraints.NotNull;

public record RegisterCardRequest(@NotNull Long productId, Long performanceTierId) {
}
