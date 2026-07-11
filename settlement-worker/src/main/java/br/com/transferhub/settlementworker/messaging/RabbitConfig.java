package br.com.transferhub.settlementworker.messaging;

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

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "transfers.exchange";
    public static final String Q_TRANSFER_REQUESTED = "q.transfer.requested";
    public static final String RK_TRANSFER_REQUESTED = "transfer.requested";
    public static final String RK_TRANSFER_SETTLED = "transfer.settled";
    public static final String RK_TRANSFER_FAILED = "transfer.failed";

    @Bean
    TopicExchange transfersExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    Queue transferRequestedQueue() {
        // Durável. A DLX/DLQ e o retry entram na Etapa 8.
        return QueueBuilder.durable(Q_TRANSFER_REQUESTED).build();
    }

    @Bean
    Binding transferRequestedBinding(Queue transferRequestedQueue, TopicExchange transfersExchange) {
        return BindingBuilder.bind(transferRequestedQueue).to(transfersExchange).with(RK_TRANSFER_REQUESTED);
    }

    @Bean
    MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(jsonMapper);
        DefaultJacksonJavaTypeMapper typeMapper = new DefaultJacksonJavaTypeMapper();
        // INFERRED: desserializa pelo tipo do parâmetro do @RabbitListener, NÃO pelo
        // nome da classe que veio no header do remetente. Desacopla os dois serviços.
        typeMapper.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.INFERRED);
        typeMapper.setTrustedPackages("br.com.transferhub.settlementworker.messaging");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
