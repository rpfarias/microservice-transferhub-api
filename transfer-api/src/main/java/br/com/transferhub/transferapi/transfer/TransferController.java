package br.com.transferhub.transferapi.transfer;

import br.com.transferhub.transferapi.transfer.dto.CreateTransferRequest;
import br.com.transferhub.transferapi.transfer.dto.TransferResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService service;

    public TransferController(TransferService service) {
        this.service = service;
    }

    /**
     * O header Idempotency-Key é OBRIGATÓRIO (ausente -> 400, via @RequestHeader).
     * - Chave nova: cria a transferência -> 201 Created + Location.
     * - Chave repetida: devolve a transferência existente -> 200 OK (nunca cria duas).
     *
     * Na Etapa 2 (síncrona) a transferência já conclui aqui. Na Etapa 5 isto vira
     * 202 Accepted (apenas aceita, conclui depois de forma assíncrona).
     */
    @PostMapping
    public ResponseEntity<TransferResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateTransferRequest request) {

        TransferResult result = service.transfer(
                idempotencyKey,
                request.sourceAccountId(),
                request.targetAccountId(),
                request.amount()
        );

        TransferResponse body = TransferResponse.from(result.transfer());

        if (!result.created()) {
            // Idempotência: já existia — 200, sem semântica de "criado agora".
            return ResponseEntity.ok(body);
        }

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(result.transfer().getId())
                .toUri();

        return ResponseEntity.created(location).body(body);
    }

    @GetMapping("/{id}")
    public TransferResponse getById(@PathVariable UUID id) {
        return TransferResponse.from(service.findById(id));
    }
}
