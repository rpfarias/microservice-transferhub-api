package br.com.transferhub.transferapi.account;

import br.com.transferhub.transferapi.common.exception.AccountNotFoundException;
import br.com.transferhub.transferapi.common.exception.DuplicateDocumentException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository repository;

    public AccountService(AccountRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Account create(String document, String holderName) {
        if (repository.existsByDocument(document)) {
            throw new DuplicateDocumentException(document);
        }
        return repository.save(new Account(document, holderName));
    }

    @Transactional(readOnly = true)
    public Account findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }
}
