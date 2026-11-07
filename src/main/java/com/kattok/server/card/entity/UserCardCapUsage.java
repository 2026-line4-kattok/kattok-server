package com.kattok.server.card.entity;

import java.time.Instant;
import java.time.YearMonth;

import org.hibernate.annotations.CreationTimestamp;

import com.kattok.server.benefit.entity.BenefitCapGroup;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 보유 카드가 특정 한도 그룹({@link BenefitCapGroup})을 월별로 얼마나 썼는지(잔여 한도·횟수).
 * 실적 구간은 카드 단위라 {@link UserCardState}에 두고, 한도는 혜택 그룹마다 따로일 수 있어 분리했다.
 */
@Entity
@Table(
		name = "user_card_cap_usages",
		uniqueConstraints = @UniqueConstraint(columnNames = {"user_card_id", "cap_group_id", "period_month"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCardCapUsage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_card_id", nullable = false)
	private UserCard userCard;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "cap_group_id", nullable = false)
	private BenefitCapGroup capGroup;

	@Column(name = "period_month", nullable = false)
	private YearMonth periodMonth;

	/** 잔여 한도(원). NULL = 미확인 (0과 구분) */
	@Column(name = "remaining_limit_amount")
	private Long remainingLimitAmount;

	/** 잔여 횟수. NULL = 미확인 (0과 구분) */
	@Column(name = "remaining_count")
	private Integer remainingCount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ConfirmationSource source;

	@Column(name = "confirmed_at", nullable = false)
	private Instant confirmedAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public UserCardCapUsage(
			UserCard userCard,
			BenefitCapGroup capGroup,
			YearMonth periodMonth,
			Long remainingLimitAmount,
			Integer remainingCount,
			ConfirmationSource source,
			Instant confirmedAt) {
		this.userCard = userCard;
		this.capGroup = capGroup;
		this.periodMonth = periodMonth;
		this.remainingLimitAmount = remainingLimitAmount;
		this.remainingCount = remainingCount;
		this.source = source;
		this.confirmedAt = confirmedAt;
	}
}
