package br.com.transferhub.transferapi.transfer;

import br.com.transferhub.transferapi.transfer.dto.CreateTransferRequest;
import br.com.transferhub.transferapi.transfer.dto.TransferResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
     * Na Etapa 2 (síncrona) a transferência já conclui aqui, então respondemos
     * 201 Created. Na Etapa 5 isto vira 202 Accepted (apenas aceita, conclui depois).
     */
    @PostMapping
    public ResponseEntity<TransferResponse> create(@Valid @RequestBody CreateTransferRequest request) {
        Transfer transfer = service.transfer(
                request.sourceAccountId(),
                request.targetAccountId(),
                request.amount()
        );

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(transfer.getId())
                .toUri();

        return ResponseEntity.created(location).body(TransferResponse.from(transfer));
    }

    @GetMapping("/{id}")
    public TransferResponse getById(@PathVariable UUID id) {
        return TransferResponse.from(service.findById(id));
    }
}
