package br.com.transferhub.transferapi.common.exception;

public class DuplicateDocumentException extends RuntimeException {

    public DuplicateDocumentException(String document) {
        super("Já existe uma conta com o documento: " + document);
    }
}
