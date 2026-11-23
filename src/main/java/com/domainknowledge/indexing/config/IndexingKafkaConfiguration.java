package com.domainknowledge.indexing.config;

import com.domainknowledge.document.exception.DocumentNotFoundException;
import com.domainknowledge.document.service.DocumentLifecycle;
import com.domainknowledge.indexing.exception.ChecksumMismatchException;
import com.domainknowledge.indexing.messaging.MarkFailedRecoverer;
import com.domainknowledge.infrastructure.storage.exception.StorageObjectNotFoundException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration(proxyBeanMethods = false)
class IndexingKafkaConfiguration {

    /**
     * Tratamento de erro do consumidor (RF7, plan §7). O Boot liga este bean
     * no container do {@code @KafkaListener}.
     *
     * <ul>
     *   <li>Erro inesperado (storage fora do ar, banco lento): 3 novas
     *       tentativas, esperando 1 s, 2 s e 4 s. Depois, {@code FAILED}.</li>
     *   <li>Erro que não muda tentando de novo (arquivo sumiu, hash diferente,
     *       documento não existe): {@code FAILED} na hora.</li>
     * </ul>
     *
     * A espera acontece na thread do consumidor daquela partição: as mensagens
     * de trás esperam, e a ordem por documento se mantém.
     */
    @Bean
    DefaultErrorHandler indexingErrorHandler(DocumentLifecycle lifecycle) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1_000);
        backOff.setMultiplier(2.0);
        DefaultErrorHandler handler = new DefaultErrorHandler(new MarkFailedRecoverer(lifecycle), backOff);
        handler.addNotRetryableExceptions(
                StorageObjectNotFoundException.class,
                ChecksumMismatchException.class,
                DocumentNotFoundException.class);
        // Confirma o offset da mensagem recuperada na hora, sem esperar a próxima.
        handler.setCommitRecovered(true);
        return handler;
    }
}
