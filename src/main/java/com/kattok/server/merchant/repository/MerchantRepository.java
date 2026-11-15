package com.kattok.server.merchant.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kattok.server.merchant.entity.Merchant;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {

	List<Merchant> findAllByOrderByName();

	List<Merchant> findByNameContainingIgnoreCaseOrderByName(String keyword);

	/** 별칭이 여러 개 일치해도 동일 결제처는 한 번만 반환한다. pattern은 !로 LIKE 문자를 이스케이프한다. */
	@Query(value = """
			select m.* from merchants m
			where m.name ilike :pattern escape '!'
			   or exists (
			      select 1 from unnest(m.aliases) alias
			      where alias ilike :pattern escape '!'
			   )
			order by m.name, m.id
			""", nativeQuery = true)
	List<Merchant> searchByNameOrAlias(@Param("pattern") String pattern);
}
