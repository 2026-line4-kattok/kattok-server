package com.kattok.server.user.dto;

public record DeletionSummaryResponse(long ownedCardCount, long paymentRecordCount, long savedBenefitTotal) {}
