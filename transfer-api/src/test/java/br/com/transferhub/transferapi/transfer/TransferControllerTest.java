package br.com.transferhub.transferapi.transfer;

import br.com.transferhub.transferapi.transfer.dto.CreateTransferRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste da CAMADA WEB isolada (@WebMvcTest): só o controller + o advice de erros,
 * com o service mockado. Sem banco, sem Testcontainers — rápido. Foca no contrato
 * HTTP do header Idempotency-Key.
 */
@WebMvcTest(TransferController.class)
class TransferControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    TransferService transferService;

    private final UUID sourceId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();

    private String jsonBody() throws Exception {
        return objectMapper.writeValueAsString(
                new CreateTransferRequest(sourceId, targetId, new BigDecimal("100.00")));
    }

    private Transfer transferComId() {
        Transfer t = new Transfer(sourceId, targetId, new BigDecimal("100.00"), "k");
        t.complete();
        // id é gerado pelo JPA; num teste web injetamos via reflexão para serializar.
        ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
        return t;
    }

    @Test
    @DisplayName("sem o header Idempotency-Key retorna 400 e nem chama o service")
    void semHeader_retorna400() throws Exception {
        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody()))
                .andExpect(status().isBadRequest());

        verify(transferService, never()).transfer(any(), any(), any(), any());
    }

    @Test
    @DisplayName("chave nova retorna 202 Accepted com header Location")
    void chaveNova_retorna202() throws Exception {
        when(transferService.transfer(eq("k1"), any(), any(), any()))
                .thenReturn(new TransferResult(transferComId(), true));

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody()))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"));
    }

    @Test
    @DisplayName("chave repetida retorna 200 (transferência existente)")
    void chaveRepetida_retorna200() throws Exception {
        when(transferService.transfer(eq("k2"), any(), any(), any()))
                .thenReturn(new TransferResult(transferComId(), false));

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", "k2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody()))
                .andExpect(status().isOk());
    }
}
