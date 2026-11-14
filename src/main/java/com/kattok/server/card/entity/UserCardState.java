package com.kattok.server.card.entity;

import java.time.Instant;
import java.time.YearMonth;

import org.hibernate.annotations.CreationTimestamp;

import com.kattok.server.card.dto.PerformanceStatus;

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

	/** 확인된 실적 구간. {@link PerformanceStatus#CONFIRMED}일 때만 값이 있다. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "performance_tier_id")
	private CardPerformanceTier performanceTier;

	@Enumerated(EnumType.STRING)
	@Column(name = "performance_status", nullable = false)
	private PerformanceStatus performanceStatus;

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
			PerformanceStatus performanceStatus,
			ConfirmationSource source,
			Instant confirmedAt) {
		this.userCard = userCard;
		this.periodMonth = periodMonth;
		this.performanceTier = performanceTier;
		this.performanceStatus = performanceStatus;
		this.source = source;
		this.confirmedAt = confirmedAt;
	}
}
