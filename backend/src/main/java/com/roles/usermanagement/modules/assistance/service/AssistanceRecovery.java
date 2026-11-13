package com.roles.usermanagement.modules.assistance.service;

import com.roles.usermanagement.modules.assistance.config.AssistanceProperties;
import com.roles.usermanagement.modules.assistance.repository.AssistanceQueryRepository;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Recupera las consultas que quedaron PENDING porque el proceso se interrumpió antes de registrar
 * su resultado (caída o reinicio). Pasado el tiempo límite se cierran como ERROR con el código
 * ASSIST_ABANDONED. Se ejecuta al arrancar y después de forma periódica.
 */
@Component
public class AssistanceRecovery {

    private static final Logger log = LoggerFactory.getLogger(AssistanceRecovery.class);

    private final AssistanceQueryRepository queries;
    private final AssistanceProperties properties;
    private final TransactionTemplate transactions;

    public AssistanceRecovery(AssistanceQueryRepository queries, AssistanceProperties properties,
                              PlatformTransactionManager transactionManager) {
        this.queries = queries;
        this.properties = properties;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${app.assistance.recovery-interval:60s}")
    void run() {
        int recovered = recoverAbandoned();
        if (recovered > 0) {
            log.warn("{} consultas al asistente quedaron sin resultado y se cerraron como ERROR", recovered);
        }
    }

    /** Devuelve cuántas consultas abandonadas se cerraron. Una consulta ya terminada no se modifica. */
    public int recoverAbandoned() {
        LocalDateTime now = LocalDateTime.now();
        Integer recovered = transactions.execute(status ->
                queries.expirePending(now.minus(properties.pendingTimeout()), AssistanceService.ABANDONED, now));
        return recovered == null ? 0 : recovered;
    }
}
