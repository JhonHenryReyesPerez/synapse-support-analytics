package com.roles.usermanagement.modules.rag.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RagRepository {
    private final JdbcTemplate db;
    private final ObjectMapper json = new ObjectMapper();
    public RagRepository(JdbcTemplate db) { this.db = db; }
    public JdbcTemplate db() { return db; }
    public List<Map<String,Object>> rows(String sql,Object... args) {
        return db.queryForList(sql,args).stream().map(row->{Map<String,Object> clean=new LinkedHashMap<>();row.forEach((k,v)->clean.put(k.toLowerCase(Locale.ROOT),v));return clean;}).toList();
    }
    public String json(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException("No se pudo serializar el contrato RAG", e); }
    }
    public Map<String,Object> object(String value) {
        try { return json.readValue(value, new TypeReference<Map<String,Object>>() {}); }
        catch (Exception e) { throw new IllegalStateException("Registro RAG inválido", e); }
    }
    public List<Map<String,Object>> profiles() { return rows("select * from rag_profiles order by name, id"); }
    public Map<String,Object> profile(String id) {
        var rows = rows("select * from rag_profiles where id = ?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }
    public String activeProfile() {
        var rows = rows("select id from rag_profiles where active = true order by id");
        return rows.isEmpty() ? "text" : rows.get(0).get("id").toString();
    }
    public List<Map<String,Object>> published() {
        return rows("select a.id, a.title, a.problem, a.symptoms, a.cause, a.solution, a.content_version, a.content_hash, "
            + "m.platform, m.application, m.product_version, m.architecture from knowledge_articles a "
            + "left join rag_article_metadata m on m.article_id=a.id where a.active=true and a.verified=true order by a.id");
    }
    public void recordRun(String id, String actor, Long queryId, String profile, String question, String status,
                          long millis, Object hits, Object telemetry, String error) {
        db.update("insert into rag_search_runs (request_id, actor, query_id, profile_id, question, status, duration_ms, hits_json, telemetry_json, error_code, created_at) "
            + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)", id, actor, queryId, profile, question, status,
            millis, json(hits), json(telemetry), error);
    }
    public List<Map<String,Object>> cases() { return rows("select * from rag_evaluation_cases order by id"); }
}
