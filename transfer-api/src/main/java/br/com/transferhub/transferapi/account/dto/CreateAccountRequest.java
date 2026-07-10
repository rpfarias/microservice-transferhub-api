package br.com.transferhub.transferapi.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload de criação de conta. A validação roda ANTES de chegar no controller
 * (via @Valid); se falhar, o cliente recebe 400 com os campos inválidos.
 */
public record CreateAccountRequest(

        @NotBlank(message = "document é obrigatório")
        @Pattern(regexp = "\\d{11}|\\d{14}", message = "document deve ter 11 (CPF) ou 14 (CNPJ) dígitos")
        String document,

        @NotBlank(message = "holderName é obrigatório")
        @Size(max = 120, message = "holderName deve ter no máximo 120 caracteres")
        String holderName
) {
}
