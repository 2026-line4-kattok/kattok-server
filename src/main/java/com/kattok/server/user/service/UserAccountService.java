package com.kattok.server.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.card.service.CardAccountData;
import com.kattok.server.global.ApiException;
import com.kattok.server.payment.service.PaymentAccountData;
import com.kattok.server.payment.service.PaymentQueryService;
import com.kattok.server.recommendation.service.RecommendationAccountData;
import com.kattok.server.user.dto.DeletionSummaryResponse;
import com.kattok.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserAccountService {
	private static final String DEMO_EMAIL = "demo@kattok.app";
	private final CurrentUserProvider currentUser;
	private final UserMutationGuard guard;
	private final UserRepository users;
	private final CardAccountData cards;
	private final PaymentAccountData payments;
	private final RecommendationAccountData recommendations;
	private final PaymentQueryService paymentQueries;

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public DeletionSummaryResponse summary() {
		Long id = currentUser.get().getId();
		var payment = paymentQueries.accountSummary(id);
		return new DeletionSummaryResponse(cards.activeCardCount(id), payment.paymentRecordCount(), payment.savedBenefitTotal());
	}

	@Transactional
	public void delete() {
		var user = currentUser.get();
		guard.lock(user.getId());
		if (DEMO_EMAIL.equalsIgnoreCase(user.getEmail())) {
			throw ApiException.conflict("DEMO_ACCOUNT_NOT_DELETABLE", "시연용 계정은 탈퇴할 수 없습니다.");
		}
		payments.deleteForUser(user.getId());
		recommendations.deleteForUser(user.getId());
		cards.deleteForUser(user.getId());
		users.delete(user);
		users.flush();
	}
}
