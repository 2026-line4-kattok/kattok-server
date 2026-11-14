package com.kattok.server.card.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kattok.server.card.entity.UserCard;

public interface UserCardRepository extends JpaRepository<UserCard, Long> {

	@Query("select uc.product.id from UserCard uc where uc.user.id = :userId and uc.deletedAt is null")
	Set<Long> findActiveProductIdsByUserId(@Param("userId") Long userId);

	List<UserCard> findByUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(Long userId);

	/** 마지막 카드 삭제를 막기 위한 보유 카드 수 확인(F-04-04). 온보딩에서 최소 1장을 등록해야 홈에 들어갈 수 있다. */
	long countByUserIdAndDeletedAtIsNull(Long userId);

	/** 삭제 여부와 상관없이 찾는다. 삭제 자체는 재삭제도 204로 응답해야 해서 이미 삭제된 카드도 찾아야 한다. */
	Optional<UserCard> findByIdAndUserId(Long id, Long userId);

	/** 삭제된 카드는 404로 취급해야 하는 조회·수정에 쓴다(예: 보유 카드 상태 조회·수정). */
	Optional<UserCard> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);
}
