package com.kattok.server.card.repository;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kattok.server.card.entity.UserCard;

public interface UserCardRepository extends JpaRepository<UserCard, Long> {

	@Query("select uc.product.id from UserCard uc where uc.user.id = :userId and uc.deletedAt is null")
	Set<Long> findActiveProductIdsByUserId(@Param("userId") Long userId);
}
