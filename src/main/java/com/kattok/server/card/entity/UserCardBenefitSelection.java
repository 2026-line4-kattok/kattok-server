package com.kattok.server.card.entity;

import java.time.Instant;
import java.time.YearMonth;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * 사용자가 선택형 혜택(SELECT_1, MODE 등)에서 고른 옵션. P1 범위.
 * 선택 기록이 없으면 해당 select_group의 BenefitRule은 계산에서 제외되고 "추가 조건 미확인"으로 처리된다.
 */
@Entity
@Table(name = "user_card_benefit_selections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCardBenefitSelection {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_card_id", nullable = false)
	private UserCard userCard;

	@Column(name = "select_group", nullable = false)
	private String selectGroup;

	@Column(name = "selected_option", nullable = false)
	private String selectedOption;

	/** 적용 시작월. 대부분 카드가 "신청 다음달 1일 반영" */
	@Column(name = "effective_from", nullable = false)
	private YearMonth effectiveFrom;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public UserCardBenefitSelection(UserCard userCard, String selectGroup, String selectedOption, YearMonth effectiveFrom) {
		this.userCard = userCard;
		this.selectGroup = selectGroup;
		this.selectedOption = selectedOption;
		this.effectiveFrom = effectiveFrom;
	}
}
