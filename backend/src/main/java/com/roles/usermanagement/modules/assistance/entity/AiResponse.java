package com.roles.usermanagement.modules.assistance.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ai_responses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "query_id", nullable = false, unique = true)
    private AiQuery query;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "steps_json", columnDefinition = "TEXT")
    private String stepsJson;

    @Column(columnDefinition = "TEXT")
    private String recommendations;

    @Column(name = "sources_count", nullable = false)
    private Integer sourcesCount;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "response", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AiSource> sources = new ArrayList<>();

    @OneToOne(mappedBy = "response", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private AiRating rating;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (sourcesCount == null) {
            sourcesCount = 0;
        }
    }
}
