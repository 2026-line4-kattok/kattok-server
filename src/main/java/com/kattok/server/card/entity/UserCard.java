package com.kattok.server.card.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import com.kattok.server.user.entity.User;

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

/** DB에 (user_id, product_id) 부분 유니크 인덱스(deleted_at is null)가 있어, 삭제되지 않은 카드끼리만 중복 등록을 막는다. */
@Entity
@Table(name = "user_cards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCard {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id", nullable = false)
	private CardProduct product;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** soft delete. 삭제해도 과거 추천·결제 확인 기록은 유지한다. */
	@Column(name = "deleted_at")
	private Instant deletedAt;

	public UserCard(User user, CardProduct product) {
		this.user = user;
		this.product = product;
	}

	public void delete() {
		this.deletedAt = Instant.now();
	}
}
