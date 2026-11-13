package com.kattok.server.card.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

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

	/** 카드 데이터 소스 식별자 (예: CR001, CK012, CT04). 카드 등록 검색·동률 처리에 쓴다. */
	@Column(name = "product_key", nullable = false, unique = true)
	private String productKey;

	@Column(nullable = false)
	private String name;

	/** 예전 이름·별칭 (예: 토스뱅크 체크카드 → 토스카드). 없으면 NULL */
	@JdbcTypeCode(SqlTypes.ARRAY)
	private String[] aliases;

	@Enumerated(EnumType.STRING)
	@Column(name = "card_type", nullable = false)
	private CardType cardType;

	/** 여신금융협회 심의필 문서번호 (예: A-20100826-9227-00149-07). 파일명·다운로드일이 아니다. 아직 확인 못했으면 NULL */
	@Column(name = "product_version")
	private String productVersion;

	@Enumerated(EnumType.STRING)
	@Column(name = "data_status", nullable = false)
	private DataStatus dataStatus;

	/** 공식 상품 자료 링크 */
	@Column(name = "source_url")
	private String sourceUrl;

	/** 카드 이미지 파일명 (예: CR006_현대_알파벳카드S.png). 호스팅 위치는 별도 결정 전까지 파일명만 보관 */
	@Column(name = "image_file_name")
	private String imageFileName;

	/** 연회비(원). 해외겸용/국내전용처럼 연회비가 다른 변형은 별도 상품으로 등록한다. 아직 확인 못했으면 NULL */
	@Column(name = "annual_fee")
	private Long annualFee;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public CardProduct(
			CardIssuer issuer,
			String productKey,
			String name,
			String[] aliases,
			CardType cardType,
			String productVersion,
			DataStatus dataStatus,
			String sourceUrl,
			String imageFileName,
			Long annualFee) {
		this.issuer = issuer;
		this.productKey = productKey;
		this.name = name;
		this.aliases = aliases;
		this.cardType = cardType;
		this.productVersion = productVersion;
		this.dataStatus = dataStatus;
		this.sourceUrl = sourceUrl;
		this.imageFileName = imageFileName;
		this.annualFee = annualFee;
	}
}
