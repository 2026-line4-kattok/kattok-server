package com.kattok.server.card.entity;

import java.time.Instant;
import java.time.YearMonth;

import org.hibernate.annotations.CreationTimestamp;

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
@Table(name = "user_card_states")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCardState {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_card_id", nullable = false)
	private UserCard userCard;

	@Column(name = "period_month", nullable = false)
	private YearMonth periodMonth;

	/** 확인된 실적 구간. NULL = 미확인. 잔여 한도는 한도 그룹마다 다를 수 있어 {@link UserCardCapUsage}로 분리한다. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "performance_tier_id")
	private CardPerformanceTier performanceTier;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ConfirmationSource source;

	@Column(name = "confirmed_at", nullable = false)
	private Instant confirmedAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public UserCardState(
			UserCard userCard,
			YearMonth periodMonth,
			CardPerformanceTier performanceTier,
			ConfirmationSource source,
			Instant confirmedAt) {
		this.userCard = userCard;
		this.periodMonth = periodMonth;
		this.performanceTier = performanceTier;
		this.source = source;
		this.confirmedAt = confirmedAt;
	}
}
