-- V7: Tablas para consultas y respuestas de IA (RAG) y calificaciones de utilidad
-- Satisface RF04, RF05 y RF08

CREATE TABLE IF NOT EXISTS ai_queries (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    requested_by VARCHAR(50) NOT NULL,
    query_text TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_ai_query_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_query_user FOREIGN KEY (requested_by) REFERENCES "user"(username) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ai_query_ticket ON ai_queries(ticket_id);
CREATE INDEX IF NOT EXISTS idx_ai_query_status ON ai_queries(status);
CREATE INDEX IF NOT EXISTS idx_ai_query_requested_by ON ai_queries(requested_by);

CREATE TABLE IF NOT EXISTS ai_responses (
    id BIGSERIAL PRIMARY KEY,
    query_id BIGINT NOT NULL UNIQUE,
    summary TEXT,
    steps_json TEXT,
    recommendations TEXT,
    sources_count INT NOT NULL DEFAULT 0,
    model_name VARCHAR(100),
    response_time_ms BIGINT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_ai_response_query FOREIGN KEY (query_id) REFERENCES ai_queries(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ai_response_query ON ai_responses(query_id);

CREATE TABLE IF NOT EXISTS ai_sources (
    id BIGSERIAL PRIMARY KEY,
    response_id BIGINT NOT NULL,
    article_id BIGINT NOT NULL,
    article_title VARCHAR(200) NOT NULL,
    similarity_score DOUBLE PRECISION NOT NULL,
    version_used INT NOT NULL,
    CONSTRAINT fk_ai_source_response FOREIGN KEY (response_id) REFERENCES ai_responses(id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_source_article FOREIGN KEY (article_id) REFERENCES knowledge_articles(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ai_source_response ON ai_sources(response_id);
CREATE INDEX IF NOT EXISTS idx_ai_source_article ON ai_sources(article_id);

CREATE TABLE IF NOT EXISTS ai_ratings (
    id BIGSERIAL PRIMARY KEY,
    response_id BIGINT NOT NULL UNIQUE,
    rated_by VARCHAR(50) NOT NULL,
    stars INT NOT NULL CHECK (stars >= 1 AND stars <= 5),
    comment TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_ai_rating_response FOREIGN KEY (response_id) REFERENCES ai_responses(id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_rating_user FOREIGN KEY (rated_by) REFERENCES "user"(username) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ai_rating_response ON ai_ratings(response_id);
