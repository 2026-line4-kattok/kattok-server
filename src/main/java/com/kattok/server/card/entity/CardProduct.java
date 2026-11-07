package com.kattok.server.card.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
@Table(name = "card_products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardProduct {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "issuer_id", nullable = false)
	private CardIssuer issuer;

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "card_type", nullable = false)
	private CardType cardType;

	/** 여신금융협회 심의필 문서번호 (예: A-20100826-9227-00149-07). 파일명·다운로드일이 아니다. */
	@Column(name = "product_version", nullable = false)
	private String productVersion;

	@Enumerated(EnumType.STRING)
	@Column(name = "data_status", nullable = false)
	private DataStatus dataStatus;

	/** 공식 상품 자료 링크 */
	@Column(name = "source_url")
	private String sourceUrl;

	/** 연회비(원). 해외겸용/국내전용처럼 연회비가 다른 변형은 별도 상품으로 등록한다. */
	@Column(name = "annual_fee", nullable = false)
	private long annualFee;

	/** 국제 브랜드(VISA/Mastercard/AMEX 등). 국내전용이면 NULL */
	private String brand;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public CardProduct(
			CardIssuer issuer,
			String name,
			CardType cardType,
			String productVersion,
			DataStatus dataStatus,
			String sourceUrl,
			long annualFee,
			String brand) {
		this.issuer = issuer;
		this.name = name;
		this.cardType = cardType;
		this.productVersion = productVersion;
		this.dataStatus = dataStatus;
		this.sourceUrl = sourceUrl;
		this.annualFee = annualFee;
		this.brand = brand;
	}
}
