package br.com.transferhub.settlementworker.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Registro de uma decisão de liquidação. É o HISTÓRICO que dá base à regra de
 * limite diário — dado próprio do worker, razão de ele ter banco separado.
 */
@Entity
@Table(name = "settlement_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** UNIQUE: idempotência do consumer (a mesma mensagem nunca é processada duas vezes). */
    @Column(name = "transfer_id", nullable = false, unique = true)
    private UUID transferId;

    @Column(name = "target_account_id", nullable = false)
    private UUID targetAccountId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SettlementOutcome outcome;

    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;

    private SettlementRecord(UUID transferId, UUID targetAccountId, BigDecimal amount,
                            SettlementOutcome outcome, String rejectionReason) {
        this.transferId = transferId;
        this.targetAccountId = targetAccountId;
        this.amount = amount;
        this.outcome = outcome;
        this.rejectionReason = rejectionReason;
    }

    public static SettlementRecord settled(UUID transferId, UUID targetAccountId, BigDecimal amount) {
        return new SettlementRecord(transferId, targetAccountId, amount, SettlementOutcome.SETTLED, null);
    }

    public static SettlementRecord rejected(UUID transferId, UUID targetAccountId, BigDecimal amount, String reason) {
        return new SettlementRecord(transferId, targetAccountId, amount, SettlementOutcome.REJECTED, reason);
    }

    @PrePersist
    void onCreate() {
        if (this.processedAt == null) {
            this.processedAt = OffsetDateTime.now();
        }
    }
}
