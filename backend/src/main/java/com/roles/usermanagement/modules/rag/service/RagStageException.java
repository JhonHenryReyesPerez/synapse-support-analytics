package com.roles.usermanagement.modules.rag.service;
import com.roles.usermanagement.integration.n8n.N8nException;
/** Lista cerrada: nunca transportar mensajes arbitrarios del proveedor. */
public final class RagStageException extends N8nException {
    public enum Stage { EMBEDDING_FAILED, INDEX_UNAVAILABLE, INDEX_MODEL_MISMATCH, INDEX_CREATE_FAILED,
        INDEX_WRITE_FAILED, VECTOR_SEARCH_FAILED, ELIGIBILITY_FAILED, RERANK_FAILED, INVALID_RERANK,
        GENERATION_FAILED, INVALID_GENERATION, INVALID_CITATION, INDEX_AUDIT_FAILED, INDEX_DELETE_FAILED,
        INVALID_VECTOR, CONTEXT_TOO_LARGE }
    private final Stage stage;
    public RagStageException(Stage stage){super(Kind.UPSTREAM_ERROR);this.stage=stage;}
    @Override public String code(){return stage.name();}
}
