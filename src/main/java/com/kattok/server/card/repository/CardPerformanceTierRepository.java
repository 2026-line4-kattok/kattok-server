package com.kattok.server.card.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kattok.server.card.entity.CardPerformanceTier;

public interface CardPerformanceTierRepository extends JpaRepository<CardPerformanceTier, Long> {

	List<CardPerformanceTier> findByProductIdOrderByLevelAsc(Long productId);

	Optional<CardPerformanceTier> findByIdAndProductId(Long id, Long productId);
}
