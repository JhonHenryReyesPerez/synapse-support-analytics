package com.roles.usermanagement.modules.knowledge.dto;

/** Resultado de una reconciliación del índice vectorial. */
public record IndexSyncSummary(int processed, int indexed, int removed, int failed, int skipped) {
}
