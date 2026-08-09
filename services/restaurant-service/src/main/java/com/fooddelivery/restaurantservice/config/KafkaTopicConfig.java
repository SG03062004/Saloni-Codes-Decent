package com.fooddelivery.restaurantservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

  @Value("${kafka.topics.order-accepted}")
  private String orderAcceptedTopic;

  @Bean
  public NewTopic orderAcceptedTopic() {
    return TopicBuilder.name(orderAcceptedTopic).partitions(3).replicas(1).build();
  }
}
