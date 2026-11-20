package com.roles.usermanagement.modules.rag.dto;

import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;

/** Contratos de las herramientas de conocimiento; scores de ranking y coseno permanecen separados. */
public final class RagContracts {
    private RagContracts() {}
    public enum Strategy { TEXT, DENSE, HYBRID, RERANK }
    public record ProfileInput(@NotBlank @Pattern(regexp="[a-z0-9_-]{1,40}") String id,
        @NotBlank @Size(max=100) String name, @NotBlank @Size(max=120) String model,
        @Min(1) @Max(8192) int dimension, @NotNull Strategy strategy,
        @NotNull @DecimalMin("0") @DecimalMax("1") Double minCosine,
        @Min(0) @Max(8000) int chunkSize, @Pattern(regexp="FULL|SYMPTOMS") String representation,
        @Size(max=120) String rerankModel) {}
    public record SearchInput(@NotBlank @Size(max=2000) String question, @Size(max=40) String profileId,
        @Min(1) @Max(20) Integer limit, @Size(max=80) String platform, @Size(max=120) String application,
        @Size(max=80) String productVersion, @Size(max=40) String architecture) {}
    public record TechnicalMetadata(@Size(max=80) String platform, @Size(max=120) String application,
        @Size(max=80) String productVersion, @Size(max=40) String architecture, @Positive Long sourceTicketId,
        @Size(max=200) String sourceDocumentName, @Pattern(regexp="[0-9a-f]{64}") String sourceDocumentHash,
        @Min(1) @Max(100) Integer sourceDocumentPages) {}
    public record Document(String pointId, long articleId, int version, String hash, String section,
        String title, String text, String embeddingText) {}
    public record Hit(String pointId, long articleId, int version, String title, String section,
        String evidence, Double cosine, double rankingScore, String metric, int rank) {}
    public record SearchResult(String requestId, String profileId, String strategy, List<Hit> hits,
        Map<String,Object> telemetry) {}
    public record EvaluationCase(@NotBlank @Size(max=2000) String question,
        @NotNull @Size(max=20) List<@NotNull @Positive Long> expectedArticleIds, boolean expectNoResults,
        @Pattern(regexp="CALIBRATION|HOLDOUT") String split, @Size(max=100) String category,
        @Size(max=4000) String notes) {}
    public record EvaluationRequest(@NotEmpty @Size(max=3) List<@NotBlank @Size(max=40) String> profileIds,
        @NotEmpty @Size(max=30) List<@NotNull @Positive Long> caseIds) {}
    public record GapReview(@NotBlank @Pattern(regexp="MISSING_CONTENT|INCOMPLETE|RETRIEVAL|UNFAITHFUL|OUT_OF_SCOPE|RESOLVED") String diagnosis,
        @Size(max=4000) String notes, @Positive Long articleId) {}
    public record FollowUp(@Size(max=2000) String question, @Size(max=4) List<@NotNull @Size(max=500) String> facts) {}
    public record EvidenceRequest(@NotBlank @Size(max=40) String profileId, @NotNull @Size(max=200) List<@NotBlank @Pattern(regexp="[0-9a-fA-F-]{36}") String> pointIds) {}
}
