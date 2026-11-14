package com.kattok.server.card;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.kattok.server.card.dto.UserCardSnapshot;
import com.kattok.server.card.service.UserCardStateReader;

/** 테스트용 카드 상태 조회. 실제 구현이 생기기 전까지 계산 엔진 테스트에서 사용자 카드 상태를 직접 넣는다. */
public class FakeUserCardStateReader implements UserCardStateReader {

	private final Map<Long, List<UserCardSnapshot>> snapshotsByUser = new HashMap<>();

	public FakeUserCardStateReader add(Long userId, UserCardSnapshot snapshot) {
		snapshotsByUser.computeIfAbsent(userId, id -> new ArrayList<>()).add(snapshot);
		return this;
	}

	@Override
	public List<UserCardSnapshot> findForRecommendation(Long userId, YearMonth period) {
		return snapshotsByUser.getOrDefault(userId, List.of()).stream()
				.sorted((a, b) -> Integer.compare(a.registrationOrder(), b.registrationOrder()))
				.toList();
	}
}
