package com.kattok.server.payment.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.kattok.server.card.entity.UserCard;
import com.kattok.server.recommendation.entity.Recommendation;
import com.kattok.server.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	/** 추천 1건당 결제 확인 기록은 1개만 허용해 중복 확인을 막는다. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recommendation_id", nullable = false, unique = true)
	private Recommendation recommendation;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentRecordStatus status;

	/** PENDING이 아니면 필수 */
	@Column(name = "confirmed_at")
	private Instant confirmedAt;

	/** 실제 사용 카드. PAID_OTHER면 필수 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "actual_user_card_id")
	private UserCard actualUserCard;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public PaymentRecord(User user, Recommendation recommendation) {
		this.user = user;
		this.recommendation = recommendation;
		this.status = PaymentRecordStatus.PENDING;
	}

	/** 추천 카드로 결제했음을 확인한다. */
	public void confirmPaidWithRecommended() {
		this.status = PaymentRecordStatus.PAID_RECOMMENDED;
		this.confirmedAt = Instant.now();
	}

	/** 추천 카드가 아닌 다른 보유 카드로 결제했음을 확인한다. */
	public void confirmPaidWithOther(UserCard actualUserCard) {
		this.status = PaymentRecordStatus.PAID_OTHER;
		this.actualUserCard = actualUserCard;
		this.confirmedAt = Instant.now();
	}

	/** 결제하지 않았음을 확인한다. */
	public void confirmNotPaid() {
		this.status = PaymentRecordStatus.NOT_PAID;
		this.confirmedAt = Instant.now();
	}
}
