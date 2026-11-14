package com.kattok.server.card.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kattok.server.card.entity.CardProduct;

public interface CardProductRepository extends JpaRepository<CardProduct, Long> {

	/**
	 * 발급사·업종(선택)·검색어(선택, 상품명 또는 별칭 부분 일치)로 카드 상품을 찾는다.
	 * 파라미터가 null이면 그 조건은 걸지 않는다.
	 */
	@Query(value = """
			select cp.* from card_products cp
			where (:issuerId is null or cp.issuer_id = :issuerId)
			  and (:cardType is null or cp.card_type = :cardType)
			  and (
			    :keyword is null
			    or cp.name ilike concat('%', :keyword, '%')
			    or exists (select 1 from unnest(cp.aliases) alias where alias ilike concat('%', :keyword, '%'))
			  )
			order by cp.name
			""", nativeQuery = true)
	List<CardProduct> search(
			@Param("issuerId") Long issuerId, @Param("keyword") String keyword, @Param("cardType") String cardType);
}
