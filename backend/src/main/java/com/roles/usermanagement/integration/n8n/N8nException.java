package com.roles.usermanagement.integration.n8n;

/**
 * Fallo de la integración con n8n ya traducido a un código seguro: no transporta credenciales,
 * URLs internas ni detalles del proveedor de IA.
 */
public class N8nException extends RuntimeException {

    public enum Kind {
        /** N8N_BASE_URL no está configurada. */
        NOT_CONFIGURED("N8N_NOT_CONFIGURED"),
        /** No se pudo conectar. */
        UNAVAILABLE("N8N_UNAVAILABLE"),
        /** No respondió dentro del tiempo límite. */
        TIMEOUT("N8N_TIMEOUT"),
        /** Respondió con un estado HTTP de error. */
        UPSTREAM_ERROR("N8N_UPSTREAM_ERROR"),
        /** Respuesta ilegible, demasiado grande o que no cumple el contrato. */
        INVALID_RESPONSE("N8N_INVALID_RESPONSE");

        private final String code;

        Kind(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    private final Kind kind;

    public N8nException(Kind kind) {
        super(kind.code());
        this.kind = kind;
    }

    public N8nException(Kind kind, Throwable cause) {
        super(kind.code(), cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public String code() {
        return kind.code();
    }
}
