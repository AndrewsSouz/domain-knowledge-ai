package com.domainknowledge.infrastructure.kafka.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration(proxyBeanMethods = false)
class KafkaAdminClientConfiguration {

    /**
     * Um cliente de administração do Kafka para a aplicação toda, reaproveitado
     * a cada checagem de health em vez de abrir conexão nova toda vez. Mesmo
     * endereço do {@link KafkaAdmin} do Boot; timeouts curtos porque é usado
     * pra perguntar "está de pé?".
     */
    @Bean(destroyMethod = "close")
    AdminClient kafkaAdminClient(KafkaAdmin kafkaAdmin) {
        Map<String, Object> config = new HashMap<>(kafkaAdmin.getConfigurationProperties());
        config.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 3_000);
        config.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 3_000);
        return AdminClient.create(config);
    }
}
