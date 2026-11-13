package com.roles.usermanagement.modules.assistance.service;

import com.roles.usermanagement.integration.n8n.N8nException;
import org.springframework.http.HttpStatus;

/**
 * La consulta quedó persistida en ERROR por un fallo técnico. Transporta el identificador de la
 * consulta para que el cliente pueda consultarla o reintentarla, y un código seguro del fallo.
 */
public class AssistanceFailedException extends RuntimeException {

    private final Long queryId;
    private final String requestId;
    private final String errorCode;

    public AssistanceFailedException(Long queryId, String requestId, String errorCode) {
        super(errorCode);
        this.queryId = queryId;
        this.requestId = requestId;
        this.errorCode = errorCode;
    }

    public Long queryId() {
        return queryId;
    }

    public String requestId() {
        return requestId;
    }

    public String errorCode() {
        return errorCode;
    }

    /** 503 si el servicio no está disponible, 504 si no respondió a tiempo, 502 si respondió mal. */
    public HttpStatus httpStatus() {
        if (N8nException.Kind.NOT_CONFIGURED.code().equals(errorCode)
                || N8nException.Kind.UNAVAILABLE.code().equals(errorCode)
                || AssistanceService.SOURCES_CHANGED.equals(errorCode)) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (N8nException.Kind.TIMEOUT.code().equals(errorCode) || AssistanceService.ABANDONED.equals(errorCode)) {
            return HttpStatus.GATEWAY_TIMEOUT;
        }
        return HttpStatus.BAD_GATEWAY;
    }
}
