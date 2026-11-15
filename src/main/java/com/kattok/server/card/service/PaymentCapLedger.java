package com.kattok.server.card.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.benefit.entity.CapPeriod;
import com.kattok.server.card.repository.UserCardCapUsageRepository;
import com.kattok.server.global.ApiException;
import com.kattok.server.recommendation.dto.RecommendationResponse.RemainingLimitProjection;
import lombok.RequiredArgsConstructor;

/** 필드별 입력 리비전과 고정된 실제 차감량을 보관한다. 스냅샷 혜택을 재계산하지 않는다. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class PaymentCapLedger {
	private final JdbcTemplate jdbc;
	private final UserCardCapUsageRepository legacy;

	public List<Change> replace(Long paymentId, Long cardId, LocalDate date, List<RemainingLimitProjection> projections) {
		Map<Long, Balance> before = new LinkedHashMap<>();
		jdbc.query("""
			select b.* from card_cap_balances b join payment_cap_effects e on e.balance_id=b.id
			where e.payment_record_id=? and e.active order by b.id for update of b
			""", PaymentCapLedger::map, paymentId).forEach(b -> before.put(b.id(), b));
		jdbc.update("update payment_cap_effects set active=false where payment_record_id=? and active", paymentId);
		before.keySet().forEach(this::recompute);
		if (cardId != null) {
			for (var projection : projections) {
				Balance b = ensure(cardId, projection.capGroupId(), projection.capPeriod(), period(projection.capPeriod(), date),
						projection.description(), projection.capAmount() != null, projection.capCount() != null);
				before.putIfAbsent(b.id(), b);
				long requestedAmount = projection.capAmount() == null ? 0
						: projection.remainingLimitBefore() - projection.remainingLimitAmount();
				int requestedCount = projection.capCount() == null ? 0
						: projection.remainingCountBefore() - projection.remainingCount();
				long amount = Math.min(b.amount() == null ? value(projection.capAmount()) : b.amount(), requestedAmount);
				int count = Math.min(b.count() == null ? value(projection.capCount()) : b.count(), requestedCount);
				if (amount == 0 && count == 0) continue;
				jdbc.update("""
					update card_cap_balances set
					assumed_amount=case when remaining_amount is null and ? > 0 then ? else assumed_amount end,
					assumed_count=case when remaining_count is null and ? > 0 then ? else assumed_count end where id=?
					""", amount, projection.capAmount(), count, projection.capCount(), b.id());
				jdbc.update("""
					insert into payment_cap_effects(payment_record_id,balance_id,amount_delta,count_delta,amount_revision,count_revision)
					values(?,?,?,?,?,?)
					""", paymentId, b.id(), amount, count, b.amountRevision(), b.countRevision());
				recompute(b.id());
			}
		}
		List<Change> changes = new ArrayList<>();
		for (Balance old : before.values()) {
			Balance after = get(old.id());
			if (!old.state().equals(after.state())) {
				changes.add(new Change(after.cardId(), after.capId(), after.description(), after.period(), after.start(),
						old.state(), after.state(),
						after.amountLimited() && (old.amount() == null || old.amount() > 0) && Objects.equals(after.amount(), 0L),
						after.countLimited() && (old.count() == null || old.count() > 0) && Objects.equals(after.count(), 0)));
			}
		}
		return List.copyOf(changes);
	}

	public void manual(Long cardId, Long capId, CapPeriod period, LocalDate start, String description,
			boolean amountLimited, boolean countLimited, boolean amountProvided, Long amount,
			boolean countProvided, Integer count) {
		Balance b = ensure(cardId, capId, period, start, description, amountLimited, countLimited);
		if (amountProvided) jdbc.update("""
			update card_cap_balances set base_amount=?,base_amount_source=?,amount_revision=amount_revision+1,
			assumed_amount=null where id=?
			""", amount, amount == null ? "UNKNOWN" : "USER_INPUT", b.id());
		if (countProvided) jdbc.update("""
			update card_cap_balances set base_count=?,base_count_source=?,count_revision=count_revision+1,
			assumed_count=null where id=?
			""", count, count == null ? "UNKNOWN" : "USER_INPUT", b.id());
		recompute(b.id());
	}

	public List<Balance> read(Long cardId, LocalDate date) {
		return jdbc.query("""
			select * from card_cap_balances where user_card_id=? and
			((cap_period='DAILY' and period_start=?) or (cap_period='MONTHLY' and period_start=?)
			or (cap_period='YEARLY' and period_start=?))
			""", PaymentCapLedger::map, cardId, date, date.withDayOfMonth(1), date.withDayOfYear(1));
	}

	private Balance ensure(Long cardId, Long capId, CapPeriod period, LocalDate start, String description,
			boolean amountLimited, boolean countLimited) {
		var found = jdbc.query("""
			select * from card_cap_balances where user_card_id=? and cap_group_id=? and cap_period=? and period_start=? for update
			""", PaymentCapLedger::map, cardId, capId, period.name(), start);
		if (!found.isEmpty()) return found.getFirst();
		Long matches = jdbc.queryForObject("""
			select count(*) from user_cards c join benefit_cap_groups g on g.product_id=c.product_id where c.id=? and g.id=?
			""", Long.class, cardId, capId);
		if (matches == null || matches != 1) throw ApiException.invalidInput("해당 카드의 한도가 아닙니다.");
		Long amount = null; Integer count = null; String amountSource = "UNKNOWN"; String countSource = "UNKNOWN";
		if (period == CapPeriod.MONTHLY) {
			var old = legacy.findByUserCardIdAndPeriodMonth(cardId, YearMonth.from(start)).stream()
					.filter(u -> u.getCapGroup().getId().equals(capId)).findFirst().orElse(null);
			if (old != null) {
				amount = old.getRemainingLimitAmount(); count = old.getRemainingCount();
				String source = old.getSource().name().equals("USER_CONFIRMED") ? "USER_INPUT" : "ESTIMATED";
				amountSource = amount == null ? "UNKNOWN" : source;
				countSource = count == null ? "UNKNOWN" : source;
			}
		}
		if (!amountLimited) { amount = null; amountSource = "UNKNOWN"; }
		if (!countLimited) { count = null; countSource = "UNKNOWN"; }
		Long id = jdbc.queryForObject("""
			insert into card_cap_balances(user_card_id,cap_group_id,cap_period,period_start,description,amount_limited,count_limited,
			remaining_amount,remaining_count,amount_source,count_source,base_amount,base_count,base_amount_source,base_count_source)
			values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) returning id
			""", Long.class, cardId, capId, period.name(), start, description == null ? "" : description,
				amountLimited, countLimited, amount, count, amountSource, countSource, amount, count, amountSource, countSource);
		return get(id);
	}

	private void recompute(Long id) {
		Balance b = get(id);
		var totals = jdbc.queryForMap("""
			select coalesce(sum(amount_delta) filter(where amount_revision=? and amount_delta>0),0) as amount_sum,
			count(*) filter(where amount_revision=? and amount_delta>0) as amount_effects,
			coalesce(sum(count_delta) filter(where count_revision=? and count_delta>0),0) as count_sum,
			count(*) filter(where count_revision=? and count_delta>0) as count_effects
			from payment_cap_effects where balance_id=? and active
			""", b.amountRevision(), b.amountRevision(), b.countRevision(), b.countRevision(), id);
		boolean amountActive = ((Number)totals.get("amount_effects")).longValue() > 0;
		boolean countActive = ((Number)totals.get("count_effects")).longValue() > 0;
		Long amount = amountActive ? Long.valueOf((b.baseAmount() == null ? b.assumedAmount() : b.baseAmount())
				- ((Number)totals.get("amount_sum")).longValue()) : b.baseAmount();
		Integer count = countActive ? Integer.valueOf((b.baseCount() == null ? b.assumedCount() : b.baseCount())
				- ((Number)totals.get("count_sum")).intValue()) : b.baseCount();
		jdbc.update("""
			update card_cap_balances set remaining_amount=?,remaining_count=?,amount_source=?,count_source=? where id=?
			""", amount, count, amountActive ? "ESTIMATED" : b.baseAmountSource(), countActive ? "ESTIMATED" : b.baseCountSource(), id);
		if (b.period() == CapPeriod.MONTHLY) {
			String source = amountActive || countActive || b.baseAmountSource().equals("ESTIMATED") || b.baseCountSource().equals("ESTIMATED")
					? "SYSTEM_ESTIMATED" : "USER_CONFIRMED";
			legacy.upsert(b.cardId(), b.capId(), b.start(), amount, count, source, java.time.Instant.now());
		}
	}

	private Balance get(Long id) {
		return jdbc.queryForObject("select * from card_cap_balances where id=?", PaymentCapLedger::map, id);
	}

	public static LocalDate period(CapPeriod period, LocalDate date) {
		return switch (period) { case DAILY -> date; case MONTHLY -> date.withDayOfMonth(1); case YEARLY -> date.withDayOfYear(1); };
	}

	private static long value(Long value) { return value == null ? 0 : value; }
	private static int value(Integer value) { return value == null ? 0 : value; }
	private static Balance map(ResultSet r, int row) throws SQLException {
		return new Balance(r.getLong("id"),r.getLong("user_card_id"),r.getLong("cap_group_id"),
				CapPeriod.valueOf(r.getString("cap_period")),r.getDate("period_start").toLocalDate(),r.getString("description"),
				r.getBoolean("amount_limited"),r.getBoolean("count_limited"),r.getObject("remaining_amount",Long.class),
				r.getObject("remaining_count",Integer.class),r.getString("amount_source"),r.getString("count_source"),
				r.getObject("base_amount",Long.class),r.getObject("base_count",Integer.class),r.getString("base_amount_source"),
				r.getString("base_count_source"),r.getLong("amount_revision"),r.getLong("count_revision"),
				r.getObject("assumed_amount",Long.class),r.getObject("assumed_count",Integer.class));
	}

	public record State(Long remainingLimitAmount, Integer remainingCount, String amountSource, String countSource) {}
	public record Change(Long userCardId, Long capGroupId, String description, CapPeriod capPeriod, LocalDate periodStart,
			State before, State after, boolean newlyAmountExhausted, boolean newlyCountExhausted) {}
	public record Balance(Long id,Long cardId,Long capId,CapPeriod period,LocalDate start,String description,
			boolean amountLimited,boolean countLimited,Long amount,Integer count,String amountSource,String countSource,
			Long baseAmount,Integer baseCount,String baseAmountSource,String baseCountSource,long amountRevision,
			long countRevision,Long assumedAmount,Integer assumedCount) {
		public State state() { return new State(amount,count,amountLimited?amountSource:null,countLimited?countSource:null); }
	}
}
