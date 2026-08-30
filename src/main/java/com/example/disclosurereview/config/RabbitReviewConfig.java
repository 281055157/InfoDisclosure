package com.example.disclosurereview.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class RabbitReviewConfig {

    @Bean
    public DirectExchange reviewTaskExchange(ReviewProperties properties) {
        return new DirectExchange(properties.getRabbitmq().getExchange(), true, false);
    }

    @Bean
    public DirectExchange reviewTaskDeadLetterExchange(ReviewProperties properties) {
        return new DirectExchange(properties.getRabbitmq().getDeadLetterExchange(), true, false);
    }

    @Bean
    public Queue reviewTaskStageQueue(ReviewProperties properties) {
        return new Queue(properties.getRabbitmq().getStageQueue(), true, false, false, Map.of(
                "x-dead-letter-exchange", properties.getRabbitmq().getDeadLetterExchange(),
                "x-dead-letter-routing-key", properties.getRabbitmq().getDeadLetterQueue()));
    }

    @Bean
    public Queue reviewTaskStageDeadLetterQueue(ReviewProperties properties) {
        return new Queue(properties.getRabbitmq().getDeadLetterQueue(), true);
    }

    @Bean
    public Binding reviewTaskStageBinding(ReviewProperties properties,
                                          Queue reviewTaskStageQueue,
                                          DirectExchange reviewTaskExchange) {
        return BindingBuilder.bind(reviewTaskStageQueue)
                .to(reviewTaskExchange)
                .with(properties.getRabbitmq().getStageRoutingKey());
    }

    @Bean
    public Binding reviewTaskDeadLetterBinding(ReviewProperties properties,
                                               Queue reviewTaskStageDeadLetterQueue,
                                               DirectExchange reviewTaskDeadLetterExchange) {
        return BindingBuilder.bind(reviewTaskStageDeadLetterQueue)
                .to(reviewTaskDeadLetterExchange)
                .with(properties.getRabbitmq().getDeadLetterQueue());
    }

    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public DirectExchange feedbackGovernanceExchange(FeedbackGovernanceProperties properties) {
        return new DirectExchange(properties.getRabbitmq().getExchange(), true, false);
    }

    @Bean
    public DirectExchange feedbackGovernanceDeadLetterExchange(FeedbackGovernanceProperties properties) {
        return new DirectExchange(properties.getRabbitmq().getDeadLetterExchange(), true, false);
    }

    @Bean
    public Queue feedbackGovernanceQueue(FeedbackGovernanceProperties properties) {
        return new Queue(properties.getRabbitmq().getQueue(), true, false, false, Map.of(
                "x-dead-letter-exchange", properties.getRabbitmq().getDeadLetterExchange(),
                "x-dead-letter-routing-key", properties.getRabbitmq().getDeadLetterQueue()));
    }

    @Bean
    public Queue feedbackGovernanceDeadLetterQueue(FeedbackGovernanceProperties properties) {
        return new Queue(properties.getRabbitmq().getDeadLetterQueue(), true);
    }

    @Bean
    public Binding feedbackGovernanceBinding(FeedbackGovernanceProperties properties,
                                              Queue feedbackGovernanceQueue,
                                              DirectExchange feedbackGovernanceExchange) {
        return BindingBuilder.bind(feedbackGovernanceQueue).to(feedbackGovernanceExchange)
                .with(properties.getRabbitmq().getRoutingKey());
    }

    @Bean
    public Binding feedbackGovernanceDeadLetterBinding(FeedbackGovernanceProperties properties,
                                                        Queue feedbackGovernanceDeadLetterQueue,
                                                        DirectExchange feedbackGovernanceDeadLetterExchange) {
        return BindingBuilder.bind(feedbackGovernanceDeadLetterQueue).to(feedbackGovernanceDeadLetterExchange)
                .with(properties.getRabbitmq().getDeadLetterQueue());
    }
}
