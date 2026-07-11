package br.com.transferhub.transferapi.transfer;

import br.com.transferhub.transferapi.account.Account;
import br.com.transferhub.transferapi.account.AccountRepository;
import br.com.transferhub.transferapi.common.exception.AccountNotFoundException;
import br.com.transferhub.transferapi.common.exception.SameAccountException;
import br.com.transferhub.transferapi.common.exception.TransferNotFoundException;
import br.com.transferhub.transferapi.messaging.TransferRequested;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TransferService(AccountRepository accountRepository,
                           TransferRepository transferRepository,
                           ApplicationEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Transferência ASSÍNCRONA e IDEMPOTENTE. Nesta transação:
     *   1. debita a origem (o dinheiro sai agora e fica "reservado");
     *   2. grava a Transfer como PENDING.
     * O crédito no destino NÃO acontece aqui — depende da liquidação aprovar
     * (settlement-worker) e do transfer-api consumir TransferSettled (Etapa 7).
     *
     * O evento TransferRequested é publicado APÓS o commit (ver TransferEventPublisher).
     */
    @Transactional
    public TransferResult transfer(String idempotencyKey, UUID sourceId, UUID targetId, BigDecimal amount) {
        // IDEMPOTÊNCIA (primeira coisa): se a chave já foi processada, devolve o
        // resultado anterior SEM refazer nada. É o que torna o retry do cliente seguro.
        Optional<Transfer> existing = transferRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return new TransferResult(existing.get(), false);
        }

        if (sourceId.equals(targetId)) {
            throw new SameAccountException();
        }

        // Origem: carregada (managed) para debitar. Destino: só validamos que
        // existe — não o creditamos aqui (isso é assíncrono, Etapa 7).
        Account source = accountRepository.findById(sourceId)
                .orElseThrow(() -> new AccountNotFoundException(sourceId));
        if (!accountRepository.existsById(targetId)) {
            throw new AccountNotFoundException(targetId);
        }

        // Invariante no domínio: debit() lança se saldo insuficiente/valor inválido,
        // abortando a transação (rollback). O dirty checking emite o UPDATE com o
        // @Version (optimistic lock) no commit.
        source.debit(amount);

        Transfer transfer = new Transfer(sourceId, targetId, amount, idempotencyKey); // fica PENDING
        Transfer saved = transferRepository.save(transfer);

        // Evento de APLICAÇÃO (in-process). O envio real ao RabbitMQ ocorre só
        // depois do commit desta transação (@TransactionalEventListener AFTER_COMMIT).
        eventPublisher.publishEvent(new TransferRequested(
                saved.getId(), sourceId, targetId, amount, OffsetDateTime.now()));

        return new TransferResult(saved, true);
    }

    @Transactional(readOnly = true)
    public Transfer findById(UUID id) {
        return transferRepository.findById(id)
                .orElseThrow(() -> new TransferNotFoundException(id));
    }

    /**
     * Consome TransferSettled: credita o destino e conclui a transferência.
     *
     * IDEMPOTENTE: se status != PENDING, a mensagem é duplicata (at-least-once)
     * — ignora sem creditar de novo. O status é máquina de estados de mão única:
     * PENDING -> COMPLETED | FAILED, nunca volta.
     */
    @Transactional
    public void settle(UUID transferId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));
        if (transfer.getStatus() != TransferStatus.PENDING) {
            return;
        }

        Account target = accountRepository.findById(transfer.getTargetAccountId())
                .orElseThrow(() -> new AccountNotFoundException(transfer.getTargetAccountId()));
        target.credit(transfer.getAmount());
        transfer.complete();
    }

    /**
     * Consome TransferFailed: TRANSAÇÃO COMPENSATÓRIA. O débito da origem já
     * commitou lá atrás (Etapa 5) e não pode ser "desfeito" — não existe
     * transação distribuída aqui. Compensamos com a operação inversa: creditar
     * de volta a origem (estorno). É o mecanismo básico de uma Saga coreografada.
     *
     * IDEMPOTENTE pelo mesmo critério: status != PENDING -> ignora.
     */
    @Transactional
    public void fail(UUID transferId, String reason) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));
        if (transfer.getStatus() != TransferStatus.PENDING) {
            return;
        }

        Account source = accountRepository.findById(transfer.getSourceAccountId())
                .orElseThrow(() -> new AccountNotFoundException(transfer.getSourceAccountId()));
        source.credit(transfer.getAmount()); // estorno do débito original
        transfer.fail(reason);
    }
}
