package br.com.transferhub.transferapi.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Conta de titular. Dono do saldo. O saldo NUNCA é dinheiro em Double/float:
 * é sempre BigDecimal mapeado para NUMERIC(19,4).
 */
@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA exige construtor sem-args; protegido para o resto do código não usar
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 14)
    private String document;

    @Column(name = "holder_name", nullable = false, length = 120)
    private String holderName;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    /**
     * Optimistic locking. O Hibernate incrementa a cada UPDATE e, no commit,
     * checa se a versão em banco ainda é a que lemos. Se duas transferências
     * concorrentes debitarem a mesma conta, a segunda leva OptimisticLockException
     * em vez de sobrescrever o saldo da primeira (lost update).
     */
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public Account(String document, String holderName) {
        this.document = document;
        this.holderName = holderName;
        this.balance = BigDecimal.ZERO;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }
}
