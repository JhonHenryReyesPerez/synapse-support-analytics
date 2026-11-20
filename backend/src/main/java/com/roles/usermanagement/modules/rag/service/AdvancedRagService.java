package com.roles.usermanagement.modules.rag.service;

import com.roles.usermanagement.integration.n8n.dto.AssistRequest;
import com.roles.usermanagement.modules.assistance.service.AssistOutcome;
import com.roles.usermanagement.modules.assistance.service.AssistOutcome.*;
import com.roles.usermanagement.modules.rag.dto.RagContracts.*;
import com.roles.usermanagement.modules.rag.repository.RagRepository;
import java.util.*;
import org.springframework.stereotype.Service;

/** Adaptador v2: reutiliza la persistencia, idempotencia, estados y validación final del asistente. */
@Service
public class AdvancedRagService {
    private final RagSearchService search;private final RagIndexService indexes;private final RagRepository store;
    public AdvancedRagService(RagSearchService search,RagIndexService indexes,RagRepository store){this.search=search;this.indexes=indexes;this.store=store;}
    public double minimumCosine(String id){var p=indexes.profile(id);return "TEXT".equals(p.get("strategy"))?0:((Number)p.get("min_cosine")).doubleValue();}
    public AssistOutcome answer(AssistRequest input,String actor) {
        long started=System.nanoTime();
        var profile=indexes.profile(input.retrievalProfileId());
        var found=search.search(new SearchInput(input.question(),input.retrievalProfileId(),input.maxResults(),null,null,null,null),actor,input.queryId());
        boolean textual="TEXT".equals(profile.get("strategy"));
        var metadata=new Metadata(textual?null:profile.get("model").toString(),null,textual?"text":"cosine+rank","knowledge-v2");
        if(found.hits().isEmpty())return new NoResults(metadata);
        var request=indexes.request("GENERATE",profile);request.put("question",input.question());request.put("sources",found.hits());
        try {
        Map<String,Object> reply=indexes.exchange(request);
        var telemetry=new LinkedHashMap<String,Object>();
        if(reply.get("telemetry") instanceof Map<?,?> raw)raw.forEach((key,value)->{
            if(key instanceof String label && value instanceof Number n && Double.isFinite(n.doubleValue()) && n.doubleValue()>=0)telemetry.put(label,n);
        });
        store.db().update("update rag_search_runs set telemetry_json=? where request_id=?",store.json(Map.of("retrieval",found.telemetry(),"generation",telemetry)),found.requestId());
        if(Boolean.TRUE.equals(reply.get("insufficient"))) {
            store.db().update("update rag_search_runs set status='NO_RESULTS' where request_id=?",found.requestId());
            return new NoResults(metadata);
        }
        if(!(reply.get("answer") instanceof String answer) || answer.isBlank() || answer.length()>20000
            || !(reply.get("steps") instanceof List<?> steps) || steps.isEmpty() || steps.size()>20)throw RagIndexService.invalid();
        var allowed=new HashMap<String,Hit>();found.hits().forEach(h->allowed.put(h.pointId(),h));
        var texts=new ArrayList<String>();var citations=new ArrayList<Map<String,Object>>();
        for(Object step:steps) {
            if(!(step instanceof Map<?,?> m) || !(m.get("text") instanceof String text) || text.isBlank() || text.length()>2000
                || !(m.get("citations") instanceof List<?> refs) || refs.isEmpty() || refs.size()>5)throw RagIndexService.invalid();
            for(Object ref:refs)if(!(ref instanceof String point) || !allowed.containsKey(point))throw RagIndexService.invalid();
            texts.add(text);citations.add(Map.of("stepIndex",texts.size()-1,"pointIds",refs));
        }
        // Se conservan todas las fuentes enviadas al generador: una baja concurrente descarta la respuesta completa.
        var sources=found.hits().stream().map(h->new SourceRef(h.articleId(),h.version(),h.cosine(),h.evidence())).toList();
        var details=new LinkedHashMap<String,Object>();details.put("profileId",input.retrievalProfileId());details.put("strategy",profile.get("strategy"));
        details.put("citations",citations);details.put("sources",found.hits());details.put("retrievalTelemetry",found.telemetry());
        details.put("generationTelemetry",telemetry);
        String model=reply.get("generationModel") instanceof String m && m.length()<=120?m:null;
        return new Answered(answer,List.copyOf(texts),sources,new Metadata(metadata.embeddingModel(),model,metadata.similarityMetric(),"knowledge-v2"),store.json(details));
        } catch(com.roles.usermanagement.integration.n8n.N8nException failure) {
            store.db().update("update rag_search_runs set status='ERROR',error_code=? where request_id=?",failure.code(),found.requestId());
            throw failure;
        } finally {
            store.db().update("update rag_search_runs set duration_ms=? where request_id=?",(System.nanoTime()-started)/1_000_000,found.requestId());
        }
    }
}
