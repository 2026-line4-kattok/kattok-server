package com.kattok.server.card.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kattok.server.card.entity.CardProduct;

public interface CardProductRepository extends JpaRepository<CardProduct, Long> {
}
