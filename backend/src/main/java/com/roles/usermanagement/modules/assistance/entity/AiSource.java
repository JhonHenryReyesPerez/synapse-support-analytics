package com.roles.usermanagement.modules.assistance.entity;

import com.roles.usermanagement.modules.knowledge.entity.KnowledgeArticle;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ai_sources")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "response_id", nullable = false)
    private AiResponse response;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    private KnowledgeArticle article;

    @Column(name = "article_title", nullable = false, length = 200)
    private String articleTitle;

    @Column(name = "similarity_score", nullable = false)
    private Double similarityScore;

    @Column(name = "version_used", nullable = false)
    private Integer versionUsed;
}
