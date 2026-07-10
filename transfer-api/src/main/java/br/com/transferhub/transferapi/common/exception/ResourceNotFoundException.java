package br.com.transferhub.transferapi.common.exception;

/**
 * Base para "recurso não encontrado". Um único handler mapeia toda a hierarquia
 * para 404, em vez de um handler por tipo.
 */
public abstract class ResourceNotFoundException extends RuntimeException {

    protected ResourceNotFoundException(String message) {
        super(message);
    }
}
