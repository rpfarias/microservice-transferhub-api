package br.com.transferhub.transferapi.common.exception;

import java.util.UUID;

public class AccountNotFoundException extends ResourceNotFoundException {

    public AccountNotFoundException(UUID id) {
        super("Conta não encontrada: " + id);
    }
}
