package com.kattok.server.merchant.entity;

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
@Table(name = "merchant_aliases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MerchantAlias {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "merchant_id", nullable = false)
	private Merchant merchant;

	/** 검색용 별칭 */
	@Column(nullable = false)
	private String alias;

	public MerchantAlias(Merchant merchant, String alias) {
		this.merchant = merchant;
		this.alias = alias;
	}
}
