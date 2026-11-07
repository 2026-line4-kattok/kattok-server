package com.kattok.server.benefit.entity;

import com.kattok.server.card.entity.CardProduct;

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

/**
 * 여러 BenefitRule이 하나의 한도를 나눠 쓸 때 묶는 그룹.
 * 공유 한도가 없는 혜택도 멤버 1개짜리 그룹으로 만들어서, 계산 엔진이 항상 같은 경로로 한도를 체크하게 한다.
 */
@Entity
@Table(name = "benefit_cap_groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BenefitCapGroup {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id", nullable = false)
	private CardProduct product;

	@Enumerated(EnumType.STRING)
	@Column(name = "cap_period", nullable = false)
	private CapPeriod capPeriod;

	@Column(name = "cap_amount")
	private Long capAmount;

	@Column(name = "cap_count")
	private Integer capCount;

	/** 예: "장보기쇼핑+딜리버리+대중교통 통합 한도" */
	private String description;

	public BenefitCapGroup(CardProduct product, CapPeriod capPeriod, Long capAmount, Integer capCount, String description) {
		this.product = product;
		this.capPeriod = capPeriod;
		this.capAmount = capAmount;
		this.capCount = capCount;
		this.description = description;
	}
}
