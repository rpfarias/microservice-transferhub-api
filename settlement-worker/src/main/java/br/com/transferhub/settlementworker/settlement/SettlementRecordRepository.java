package br.com.transferhub.settlementworker.settlement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface SettlementRecordRepository extends JpaRepository<SettlementRecord, UUID> {

    boolean existsByTransferId(UUID transferId);

    /**
     * Soma dos valores JÁ LIQUIDADOS (SETTLED) para uma conta destino desde um
     * instante — base da regra de limite diário. COALESCE evita null quando não
     * há registros. Usa o índice (target_account_id, processed_at).
     */
    @Query("""
            select coalesce(sum(s.amount), 0)
            from SettlementRecord s
            where s.targetAccountId = :targetId
              and s.outcome = br.com.transferhub.settlementworker.settlement.SettlementOutcome.SETTLED
              and s.processedAt >= :since
            """)
    BigDecimal sumSettledToTargetSince(@Param("targetId") UUID targetId,
                                       @Param("since") OffsetDateTime since);
}
