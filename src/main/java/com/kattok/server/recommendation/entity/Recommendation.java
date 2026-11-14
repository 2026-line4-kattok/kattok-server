package com.kattok.server.recommendation.entity;

import java.time.Instant;
import java.util.Map;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.kattok.server.card.entity.UserCard;
import com.kattok.server.merchant.entity.Merchant;
import com.kattok.server.merchant.entity.MerchantCategory;
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
@Table(name = "recommendations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recommendation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	/** 결제처. 업종만 골라 추천하면 {@code null} */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "merchant_id")
	private Merchant merchant;

	/** 추천 당시 결제처명 스냅샷. 업종만 골라 추천하면 {@code null} */
	@Column(name = "merchant_name_snapshot")
	private String merchantNameSnapshot;

	/** 업종. 결제처를 고르면 그 결제처의 업종 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "category_id", nullable = false)
	private MerchantCategory category;

	@Column(nullable = false)
	private long amount;

	private String paymentMethod;

	/** 추천 당시 입력 조건 스냅샷 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "input_snapshot", nullable = false)
	private Map<String, Object> inputSnapshot;

	@Enumerated(EnumType.STRING)
	@Column(name = "overall_status", nullable = false)
	private OverallStatus overallStatus;

	/** 조건부 추천일 때 가정 없이 확인된 정보만으로 계산한 1위. 추천 카드와 같으면 {@code null} */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "confirmed_only_top_user_card_id")
	private UserCard confirmedOnlyTopUserCard;

	/** 추천 확인 저장 시점. NULL = draft */
	@Column(name = "saved_at")
	private Instant savedAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public Recommendation(
			User user,
			Merchant merchant,
			MerchantCategory category,
			long amount,
			Map<String, Object> inputSnapshot,
			OverallStatus overallStatus,
			UserCard confirmedOnlyTopUserCard) {
		this.user = user;
		this.merchant = merchant;
		this.merchantNameSnapshot = merchant == null ? null : merchant.getName();
		this.category = category;
		this.amount = amount;
		this.inputSnapshot = inputSnapshot;
		this.overallStatus = overallStatus;
		this.confirmedOnlyTopUserCard = confirmedOnlyTopUserCard;
	}

	/** draft 상태의 추천을 "확인하고 홈으로" 시점에 확정한다. */
	public void confirm() {
		this.savedAt = Instant.now();
	}
}
