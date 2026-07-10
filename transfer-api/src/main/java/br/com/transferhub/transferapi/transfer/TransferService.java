package br.com.transferhub.transferapi.transfer;

import br.com.transferhub.transferapi.account.Account;
import br.com.transferhub.transferapi.account.AccountRepository;
import br.com.transferhub.transferapi.common.exception.AccountNotFoundException;
import br.com.transferhub.transferapi.common.exception.SameAccountException;
import br.com.transferhub.transferapi.common.exception.TransferNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;

    public TransferService(AccountRepository accountRepository, TransferRepository transferRepository) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
    }

    /**
     * Transferência SÍNCRONA: débito, crédito e registro numa ÚNICA transação.
     * Ou tudo acontece, ou nada — é impossível debitar a origem sem creditar o destino.
     * (Na Etapa 5 isso quebra: o crédito sai daqui e vira evento assíncrono.)
     */
    @Transactional
    public Transfer transfer(UUID sourceId, UUID targetId, BigDecimal amount) {
        if (sourceId.equals(targetId)) {
            throw new SameAccountException();
        }

        // findById carrega as contas na sessão do Hibernate; elas ficam "managed".
        Account source = accountRepository.findById(sourceId)
                .orElseThrow(() -> new AccountNotFoundException(sourceId));
        Account target = accountRepository.findById(targetId)
                .orElseThrow(() -> new AccountNotFoundException(targetId));

        // As invariantes moram no domínio: debit() lança se saldo insuficiente
        // ou valor inválido, abortando a transação inteira (rollback).
        source.debit(amount);
        target.credit(amount);

        // Não preciso chamar save() nas contas: por serem entidades managed, o
        // dirty checking do Hibernate detecta a mudança de saldo e emite o UPDATE
        // no commit — com a cláusula de @Version (optimistic lock). Se outra
        // transação concorrente tiver alterado a mesma conta, o commit falha com
        // OptimisticLockException e nada é persistido.
        Transfer transfer = new Transfer(sourceId, targetId, amount);
        transfer.complete(); // síncrono: já nasce e conclui na mesma transação
        return transferRepository.save(transfer);
    }

    @Transactional(readOnly = true)
    public Transfer findById(UUID id) {
        return transferRepository.findById(id)
                .orElseThrow(() -> new TransferNotFoundException(id));
    }
}
