package br.com.transferhub.transferapi.common.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Traduz exceções em respostas HTTP consistentes usando ProblemDetail (RFC 7807).
 * Centralizar aqui evita try/catch espalhado pelos controllers.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 404: qualquer recurso não encontrado (conta ou transferência) — um handler para toda a hierarquia.
    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // 409 Conflict: o pedido é válido, mas conflita com o estado atual do recurso.
    @ExceptionHandler(DuplicateDocumentException.class)
    ProblemDetail handleDuplicate(DuplicateDocumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    /*
     * 409 Conflict para conflito de concorrência (optimistic lock). O Spring traduz
     * a OptimisticLockException do JPA nesta exceção. 409 é semanticamente correto e
     * sinaliza ao cliente que a operação pode ser RE-TENTADA (o estado mudou embaixo).
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Conflito de concorrência ao atualizar a conta. Tente novamente.");
    }

    /*
     * 422 Unprocessable Entity: a requisição está bem-formada (sintaxe OK, passou na
     * validação de formato), mas viola uma regra de NEGÓCIO. Diferente de 400, que é
     * erro de forma/sintaxe. Saldo insuficiente, valor não-positivo e mesma conta caem aqui.
     */
    @ExceptionHandler({
            InsufficientBalanceException.class,
            InvalidAmountException.class,
            SameAccountException.class
    })
    ProblemDetail handleBusinessRule(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    // 400 Bad Request: header obrigatório ausente (ex.: Idempotency-Key).
    @ExceptionHandler(MissingRequestHeaderException.class)
    ProblemDetail handleMissingHeader(MissingRequestHeaderException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Header obrigatório ausente: " + ex.getHeaderName());
    }

    /*
     * 409 Conflict: violação de restrição do banco (ex.: UNIQUE da idempotency_key).
     * Rede de segurança para a corrida em que duas requisições com a mesma chave
     * passam pela checagem inicial e tentam inserir ao mesmo tempo — o UNIQUE garante
     * que só uma vinga; a outra cai aqui em vez de duplicar a transferência.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Conflito de integridade (possível chave de idempotência concorrente). Tente novamente.");
    }

    // 400 Bad Request: falha de validação de formato (Bean Validation nos DTOs).
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }
}
