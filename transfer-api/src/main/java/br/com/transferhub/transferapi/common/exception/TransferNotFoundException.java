package br.com.transferhub.transferapi.common.exception;

import java.util.UUID;

public class TransferNotFoundException extends ResourceNotFoundException {

    public TransferNotFoundException(UUID id) {
        super("Transferência não encontrada: " + id);
    }
}
