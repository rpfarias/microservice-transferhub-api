package br.com.transferhub.transferapi.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Registro de uma transferência. Note que referencia as contas por ID (UUID),
 * NÃO por @ManyToOne. Transfer e Account são agregados distintos (DDD): um
 * agregado referencia o outro por identidade, não por composição de objeto.
 * Isso mantém as fronteiras limpas e evita carregar a Account inteira à toa.
 */
@Entity
@Table(name = "transfers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_account_id", nullable = false)
    private UUID sourceAccountId;

    @Column(name = "target_account_id", nullable = false)
    private UUID targetAccountId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING) // grava o NOME ("COMPLETED"), não o ordinal (0,1,2).
    @Column(nullable = false, length = 20)
    private TransferStatus status;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Transfer(UUID sourceAccountId, UUID targetAccountId, BigDecimal amount) {
        this.sourceAccountId = sourceAccountId;
        this.targetAccountId = targetAccountId;
        this.amount = amount;
        this.status = TransferStatus.PENDING; // nasce PENDING; a transição é explícita
    }

    public void complete() {
        this.status = TransferStatus.COMPLETED;
    }

    public void fail(String reason) {
        this.status = TransferStatus.FAILED;
        this.failureReason = reason;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
