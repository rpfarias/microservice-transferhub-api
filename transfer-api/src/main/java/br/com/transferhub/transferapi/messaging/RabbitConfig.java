package br.com.transferhub.transferapi.messaging;

import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

/**
 * Topologia RabbitMQ do lado do PRODUTOR (transfer-api).
 * Declaramos apenas a exchange principal; as filas e a DLX ficam a cargo de quem
 * consome (settlement-worker, Etapa 6) e da configuração de resiliência (Etapa 8).
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "transfers.exchange";
    public static final String RK_TRANSFER_REQUESTED = "transfer.requested";

    @Bean
    TopicExchange transfersExchange() {
        // durable = sobrevive a restart do broker.
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    /**
     * Serializa os eventos como JSON. Reaproveita o ObjectMapper do Boot (Jackson 3),
     * que já sabe lidar com OffsetDateTime, BigDecimal e UUID corretamente.
     */
    @Bean
    MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(jsonMapper);
        // Segurança: por padrão só desserializa java.*. Confiamos no nosso pacote de eventos.
        DefaultJacksonJavaTypeMapper typeMapper = new DefaultJacksonJavaTypeMapper();
        typeMapper.setTrustedPackages("br.com.transferhub.transferapi.messaging");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
