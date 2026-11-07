package com.kattok.server.card.entity;

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

@Entity
@Table(name = "card_performance_tiers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardPerformanceTier {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id", nullable = false)
	private CardProduct product;

	/** 구간 단계. 클수록 상위 구간 */
	@Column(nullable = false)
	private int level;

	/** 전월 실적 하한 (원) */
	@Column(name = "min_amount", nullable = false)
	private long minAmount;

	/** 표시명 */
	private String label;

	public CardPerformanceTier(CardProduct product, int level, long minAmount, String label) {
		this.product = product;
		this.level = level;
		this.minAmount = minAmount;
		this.label = label;
	}
}
