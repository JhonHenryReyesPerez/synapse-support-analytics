package com.roles.usermanagement.modules.assistance.enums;

/**
 * Estados de una consulta al asistente (RF08). Solo se permite PENDING → un estado terminal;
 * una consulta terminal no vuelve a cambiar.
 */
public enum AssistanceStatus {
    /** Consulta aceptada, aún sin resultado terminal. */
    PENDING("Pendiente"),
    /** Respuesta persistida con al menos una fuente válida. */
    ANSWERED("Respondida"),
    /** Consulta válida sin evidencia relevante: no se generó orientación técnica. */
    NO_RESULTS("Sin resultados"),
    /** Fallo técnico de procesamiento o integración; no significa ausencia de conocimiento. */
    ERROR("Error");

    private final String label;

    AssistanceStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isTerminal() {
        return this != PENDING;
    }
}
