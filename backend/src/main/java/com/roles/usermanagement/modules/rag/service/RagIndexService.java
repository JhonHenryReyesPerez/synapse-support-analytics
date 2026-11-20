package com.roles.usermanagement.modules.rag.service;

import com.roles.usermanagement.integration.n8n.N8nClient;
import com.roles.usermanagement.integration.n8n.N8nException;
import com.roles.usermanagement.modules.rag.dto.RagContracts.*;
import com.roles.usermanagement.modules.rag.repository.RagRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RagIndexService {
    private final RagRepository store;
    private final N8nClient n8n;
    private final org.springframework.transaction.support.TransactionTemplate transactions;
    public RagIndexService(RagRepository store,N8nClient n8n,org.springframework.transaction.PlatformTransactionManager manager) {
        this.store=store;this.n8n=n8n;this.transactions=new org.springframework.transaction.support.TransactionTemplate(manager);
    }
    public Map<String,Object> profile(String id) {
        var p=store.profile(id==null || id.isBlank()?store.activeProfile():id);
        if(p==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"El perfil no existe");
        return p;
    }
    @Transactional
    public Map<String,Object> create(ProfileInput input) {
        if(store.profile(input.id())!=null)throw new ResponseStatusException(HttpStatus.CONFLICT,"El perfil ya existe; crea otro para cambiar de modelo");
        if(input.chunkSize()>0 && input.chunkSize()<300)throw bad("El fragmento debe tener al menos 300 caracteres");
        if(input.strategy()==Strategy.RERANK && (input.rerankModel()==null || input.rerankModel().isBlank()))throw bad("Selecciona un modelo de reranking");
        store.db().update("insert into rag_profiles(id,name,model,dimension,collection_name,strategy,min_cosine,chunk_size,representation,rerank_model,active) values(?,?,?,?,?,?,?,?,?,?,false)",
            input.id(),input.name().trim(),input.model().trim(),input.dimension(),"rag_synapse_"+input.id().replace('-','_'),input.strategy().name(),
            input.minCosine(),input.chunkSize(),input.representation()==null?"FULL":input.representation(),input.rerankModel());
        return profile(input.id());
    }
    public List<Document> documents(Map<String,Object> profile) {
        return store.published().stream().flatMap(a->RagText.documents(a,profile).stream()).toList();
    }
    public Map<String,Object> plan(String id) {
        var p=profile(id);var docs=documents(p);
        boolean textual="TEXT".equals(p.get("strategy"));
        var keys=new HashSet<>(store.db().queryForList("select cache_key from rag_embedding_cache",String.class));
        long cached=textual?0:docs.stream().filter(d->keys.contains(cacheKey(p,d))).count();
        var result=new LinkedHashMap<String,Object>();result.put("profile",p);result.put("chunks",docs.size());
        result.put("articles",docs.stream().map(Document::articleId).distinct().count());result.put("cachedEmbeddings",cached);
        result.put("newEmbeddings",textual?0:docs.size()-cached);
        result.put("estimatedInputTokens",textual?0:docs.stream().filter(d->!keys.contains(cacheKey(p,d))).mapToLong(d->(d.embeddingText().length()+3)/4).sum());
        result.put("estimateNotice","Estimación por caracteres; el consumo real depende del proveedor y del idioma.");
        return result;
    }
    private String cacheKey(Map<String,Object> p,Document d) {
        return RagText.hash(p.get("model")+":"+p.get("dimension")+":sections-v1:"+p.get("representation")+":"+d.embeddingText());
    }
    private String cached(Map<String,Object> p,Document d) {
        var rows=store.db().queryForList("select vector_json from rag_embedding_cache where cache_key=?",cacheKey(p,d));
        return rows.isEmpty()?null:rows.get(0).get("vector_json").toString();
    }
    /** Un lote explícito acotado; el manifiesto permite continuar sin recalcular embeddings ya guardados. */
    public Map<String,Object> index(String id,int limit) {
        var p=profile(id);
        if("TEXT".equals(p.get("strategy")))return Map.of("indexed",0,"nextOffset",0,"done",true);
        var docs=documents(p);
        Set<String> ready=new HashSet<>(store.db().queryForList("select point_id from rag_index_manifest where profile_id=?",String.class,id));
        var pending=docs.stream().filter(d->!ready.contains(d.pointId())).toList();
        int end=Math.min(Math.max(1,Math.min(10,limit)),pending.size());
        int indexed=0;
        for(Document d:pending.subList(0,end)) {
            var request=request("INDEX",p);request.put("document",d);
            String cache=cached(p,d);if(cache!=null)request.put("cachedVector",store.object("{\"vector\":"+cache+"}").get("vector"));
            var reply=exchange(request);
            Object vector=reply.get("vector");
            if(!(vector instanceof List<?> values) || values.size()!=((Number)p.get("dimension")).intValue()
                || values.stream().anyMatch(v->!(v instanceof Number) || !Double.isFinite(((Number)v).doubleValue())))throw invalid();
            String key=cacheKey(p,d);
            // Upsert portable: borrar/insertar solo la misma identidad, bajo transacción corta.
            saveIndex(p,d,key,vector);indexed++;
        }
        return Map.of("indexed",indexed,"remaining",pending.size()-end,"done",end==pending.size(),"total",docs.size());
    }
    private void saveIndex(Map<String,Object> p,Document d,String key,Object vector) {
        // Los resultados no autorizan contenido: cada búsqueda vuelve a comprobar versión y publicación.
        // shortcut: exclusión local para la caché; usar upsert PostgreSQL al desplegar varias instancias.
        synchronized(this) {
            transactions.executeWithoutResult(status -> {
            if(store.db().queryForObject("select count(*) from rag_embedding_cache where cache_key=?",Long.class,key)==0)
                store.db().update("insert into rag_embedding_cache(cache_key,model,dimension,vector_json) values(?,?,?,?)",key,p.get("model"),p.get("dimension"),store.json(vector));
            store.db().update("delete from rag_index_manifest where profile_id=? and point_id=?",p.get("id"),d.pointId());
            store.db().update("insert into rag_index_manifest(profile_id,point_id,article_id,article_version,content_hash) values(?,?,?,?,?)",p.get("id"),d.pointId(),d.articleId(),d.version(),d.hash());
            });
        }
    }
    public Map<String,Object> health(String id,boolean repair) {
        var p=profile(id);if("TEXT".equals(p.get("strategy")))return Map.of("profileId",id,"physicalIndex",false,"complete",true);
        var expected=new LinkedHashMap<String,Document>();documents(p).forEach(d->expected.put(d.pointId(),d));
        var actual=new HashSet<String>();var mismatched=new HashSet<String>();Object cursor=null;boolean complete=false;
        for(int page=0;page<100;page++) {
            var req=request("SCROLL",p);if(cursor!=null)req.put("cursor",cursor);
            var reply=exchange(req);
            if(!(reply.get("points") instanceof List<?> points) || points.size()>256)throw invalid();
            for(Object point:points) {
                if(!(point instanceof Map<?,?> m) || !(m.get("id") instanceof String key) || !key.matches("[0-9a-fA-F-]{36}"))throw invalid();
                actual.add(key);
                if(expected.containsKey(key)) {
                    var d=expected.get(key);var payload=m.get("payload") instanceof Map<?,?> data?data:Map.of();
                    if(!Objects.equals(payload.get("contentHash"),d.hash()) || !Objects.equals(payload.get("embeddingModel"),p.get("model"))
                        || !(payload.get("contentVersion") instanceof Number v) || v.intValue()!=d.version()
                        || !(payload.get("dimension") instanceof Number dim) || dim.intValue()!=((Number)p.get("dimension")).intValue())mismatched.add(key);
                }
            }
            cursor=reply.get("nextCursor");if(cursor==null){complete=true;break;}
        }
        var missing=expected.keySet().stream().filter(k->!actual.contains(k)||mismatched.contains(k)).toList();
        var orphan=actual.stream().filter(k->!expected.containsKey(k)||mismatched.contains(k)).toList();
        if(repair) {
            if(!complete)throw new ResponseStatusException(HttpStatus.CONFLICT,"Auditoría incompleta: no se reparará con una vista parcial");
            // Retirar solo puntos de esta colección aislada; los ausentes se recuperan por lotes de indexación.
            for(int i=0;i<orphan.size();i+=100){var req=request("DELETE",p);req.put("pointIds",orphan.subList(i,Math.min(i+100,orphan.size())));exchange(req);}
            for(String key:missing)store.db().update("delete from rag_index_manifest where profile_id=? and point_id=?",id,key);
        }
        return Map.of("profileId",id,"physicalIndex",true,"complete",complete,"expected",expected.size(),"actual",actual.size(),
            "missing",missing,"obsoleteOrOrphan",orphan,"removed",repair?orphan.size():0);
    }
    @Transactional
    public Map<String,Object> activate(String id) {
        var p=profile(id);
        if(!"TEXT".equals(p.get("strategy"))) {
            var expected=documents(p);
            Set<String> ready=new HashSet<>(store.db().queryForList("select point_id from rag_index_manifest where profile_id=?",String.class,id));
            if(expected.stream().anyMatch(d->!ready.contains(d.pointId())))throw new ResponseStatusException(HttpStatus.CONFLICT,"Indexa la versión vigente de todos los artículos antes de activar");
        }
        store.db().queryForList("select id from rag_profiles order by id for update");
        store.db().update("update rag_profiles set active=false where active=true");
        store.db().update("update rag_profiles set active=true where id=?",id);
        return profile(id);
    }
    public Map<String,Object> request(String operation,Map<String,Object> p) {
        var body=new LinkedHashMap<String,Object>();body.put("schemaVersion",2);body.put("operation",operation);
        body.put("requestId",UUID.randomUUID().toString());body.put("profile",p);return body;
    }
    public Map<String,Object> exchange(Map<String,Object> request) {
        var reply=n8n.knowledge(request);
        if(reply==null || !Objects.equals(request.get("requestId"),reply.get("requestId"))
            || !(reply.get("schemaVersion") instanceof Number v) || v.intValue()!=2)throw invalid();
        if(!"OK".equals(reply.get("status"))) {
            RagStageException.Stage stage;
            try {stage=RagStageException.Stage.valueOf(String.valueOf(reply.get("errorCode")));}
            catch(IllegalArgumentException e){throw new N8nException(N8nException.Kind.UPSTREAM_ERROR);}
            throw new RagStageException(stage);
        }
        return reply;
    }
    static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    static N8nException invalid(){return new N8nException(N8nException.Kind.INVALID_RESPONSE);}
}
