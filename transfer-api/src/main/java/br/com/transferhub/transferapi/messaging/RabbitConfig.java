package br.com.transferhub.transferapi.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

/**
 * Topologia RabbitMQ do transfer-api. A partir da Etapa 7 ele é produtor
 * (TransferRequested) E consumidor (TransferSettled / TransferFailed).
 * DLX/DLQ e retry entram na Etapa 8.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "transfers.exchange";
    public static final String DLX = "transfers.dlx";
    public static final String RK_TRANSFER_REQUESTED = "transfer.requested";
    public static final String RK_TRANSFER_SETTLED = "transfer.settled";
    public static final String RK_TRANSFER_FAILED = "transfer.failed";
    public static final String Q_TRANSFER_SETTLED = "q.transfer.settled";
    public static final String Q_TRANSFER_FAILED = "q.transfer.failed";
    public static final String Q_TRANSFER_SETTLED_DLQ = "q.transfer.settled.dlq";
    public static final String Q_TRANSFER_FAILED_DLQ = "q.transfer.failed.dlq";

    @Bean
    TopicExchange transfersExchange() {
        // durable = sobrevive a restart do broker.
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    TopicExchange transfersDlx() {
        return ExchangeBuilder.topicExchange(DLX).durable(true).build();
    }

    @Bean
    Queue transferSettledQueue() {
        // Retry esgotado / erro fatal -> DLX -> DLQ (routing key original preservada).
        return QueueBuilder.durable(Q_TRANSFER_SETTLED).deadLetterExchange(DLX).build();
    }

    @Bean
    Queue transferFailedQueue() {
        return QueueBuilder.durable(Q_TRANSFER_FAILED).deadLetterExchange(DLX).build();
    }

    @Bean
    Queue transferSettledDlq() {
        return QueueBuilder.durable(Q_TRANSFER_SETTLED_DLQ).build();
    }

    @Bean
    Queue transferFailedDlq() {
        return QueueBuilder.durable(Q_TRANSFER_FAILED_DLQ).build();
    }

    @Bean
    Binding transferSettledBinding(Queue transferSettledQueue, TopicExchange transfersExchange) {
        return BindingBuilder.bind(transferSettledQueue).to(transfersExchange).with(RK_TRANSFER_SETTLED);
    }

    @Bean
    Binding transferFailedBinding(Queue transferFailedQueue, TopicExchange transfersExchange) {
        return BindingBuilder.bind(transferFailedQueue).to(transfersExchange).with(RK_TRANSFER_FAILED);
    }

    @Bean
    Binding transferSettledDlqBinding(Queue transferSettledDlq, TopicExchange transfersDlx) {
        return BindingBuilder.bind(transferSettledDlq).to(transfersDlx).with(RK_TRANSFER_SETTLED);
    }

    @Bean
    Binding transferFailedDlqBinding(Queue transferFailedDlq, TopicExchange transfersDlx) {
        return BindingBuilder.bind(transferFailedDlq).to(transfersDlx).with(RK_TRANSFER_FAILED);
    }

    @Bean
    MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(jsonMapper);
        DefaultJacksonJavaTypeMapper typeMapper = new DefaultJacksonJavaTypeMapper();
        // INFERRED: desserializa pelo tipo do parâmetro do @RabbitListener, ignorando
        // o nome da classe do remetente (que é do pacote do settlement-worker).
        typeMapper.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.INFERRED);
        typeMapper.setTrustedPackages("br.com.transferhub.transferapi.messaging");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
