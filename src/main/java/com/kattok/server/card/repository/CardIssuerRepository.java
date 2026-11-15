package com.kattok.server.card.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.kattok.server.card.entity.CardIssuer;

public interface CardIssuerRepository extends JpaRepository<CardIssuer, Long> {

	/** 등록 가능한 상품이 있는 카드사만, 이름순으로 돌려준다. */
	@Query("select i from CardIssuer i where exists (select 1 from CardProduct p where p.issuer = i) order by i.name asc")
	List<CardIssuer> findAllWithProductsOrderByNameAsc();
}
