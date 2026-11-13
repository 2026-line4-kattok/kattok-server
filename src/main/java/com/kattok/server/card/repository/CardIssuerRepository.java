package com.kattok.server.card.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kattok.server.card.entity.CardIssuer;

public interface CardIssuerRepository extends JpaRepository<CardIssuer, Long> {

	List<CardIssuer> findAllByOrderByNameAsc();
}
