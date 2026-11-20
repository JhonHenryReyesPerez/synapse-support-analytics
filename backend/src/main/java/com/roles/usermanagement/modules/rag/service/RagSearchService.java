package com.roles.usermanagement.modules.rag.service;

import com.roles.usermanagement.modules.rag.dto.RagContracts.*;
import com.roles.usermanagement.modules.rag.repository.RagRepository;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class RagSearchService {
    private final RagRepository store;
    private final RagIndexService indexes;
    public RagSearchService(RagRepository store,RagIndexService indexes){this.store=store;this.indexes=indexes;}
    private boolean match(Object actual,String filter){return filter==null || filter.isBlank() || (actual!=null && actual.toString().equalsIgnoreCase(filter.trim()));}
    public List<Document> eligible(Map<String,Object> profile,SearchInput input) {
        return store.published().stream().filter(a->match(a.get("platform"),input.platform()) && match(a.get("application"),input.application())
            && match(a.get("product_version"),input.productVersion()) && match(a.get("architecture"),input.architecture()))
            .flatMap(a->RagText.documents(a,profile).stream()).toList();
    }
    public SearchResult search(SearchInput input,String actor,Long queryId) {
        var p=indexes.profile(input.profileId());String id=UUID.randomUUID().toString();long start=System.nanoTime();
        var telemetry=new LinkedHashMap<String,Object>();List<Hit> result;
        try {
            List<Document> documents=eligible(p,input);
            Map<String,Document> allowed=new HashMap<>();documents.forEach(d->allowed.put(d.pointId(),d));
            var lexical=documents.stream().filter(d->RagText.literalEvidence(input.question(),d.title()+"\n"+d.text()))
                .sorted(Comparator.<Document>comparingDouble(d->RagText.lexical(input.question(),d.title()+"\n"+d.text())).reversed().thenComparing(Document::pointId))
                .limit(200).toList();
            telemetry.put("eligibleChunks",documents.size());telemetry.put("lexicalCandidates",lexical.size());
            int limit=input.limit()==null?5:input.limit();
            if(documents.isEmpty()) {result=List.of();} else if("TEXT".equals(p.get("strategy"))) {
                Set<Long> seen=new HashSet<>();var hits=new ArrayList<Hit>();
                for(Document d:lexical)if(seen.add(d.articleId())) {
                    hits.add(hit(d,null,RagText.lexical(input.question(),d.title()+"\n"+d.text()),"term_coverage",hits.size()+1));
                    if(hits.size()==limit)break;
                }
                result=List.copyOf(hits);
            } else {
                var request=indexes.request("SEARCH",p);request.put("question",input.question());request.put("limit",limit);
                request.put("lexicalCandidates",lexical);request.put("allowedPointIds",documents.stream().map(Document::pointId).toList());
                var reply=indexes.exchange(request);
                if(!(reply.get("hits") instanceof List<?> hits) || hits.size()>200)throw RagIndexService.invalid();
                var valid=new ArrayList<Hit>();Set<String> seen=new HashSet<>();Set<Long> articles=new HashSet<>();
                // Revalidación posterior al transporte: nunca confiar en el contenido del payload vectorial.
                var current=new HashMap<String,Document>();eligible(p,input).forEach(d->current.put(d.pointId(),d));
                for(Object item:hits) {
                    if(!(item instanceof Map<?,?> h) || !(h.get("pointId") instanceof String point)
                        || !seen.add(point) || !(h.get("rankingScore") instanceof Number rank) || !Double.isFinite(rank.doubleValue()))throw RagIndexService.invalid();
                    Document d=current.get(point);if(d==null || articles.contains(d.articleId()))continue;
                    Double cosine=h.get("cosine") instanceof Number n?n.doubleValue():null;
                    if(cosine!=null && (!Double.isFinite(cosine) || cosine < -1 || cosine>1))throw RagIndexService.invalid();
                    boolean literal=lexical.stream().anyMatch(l->l.pointId().equals(point));
                    double threshold=((Number)p.get("min_cosine")).doubleValue();
                    // La aceptación híbrida admite evidencia léxica; el umbral nunca se aplica al score RRF.
                    if("DENSE".equals(p.get("strategy")) ? cosine==null || cosine<threshold : !literal && (cosine==null || cosine<threshold))continue;
                    articles.add(d.articleId());
                    valid.add(hit(d,cosine,rank.doubleValue(),"RERANK".equals(p.get("strategy"))?"rerank":"DENSE".equals(p.get("strategy"))?"cosine":"rrf",valid.size()+1));
                    if(valid.size()==limit)break;
                }
                result=List.copyOf(valid);
                if(reply.get("telemetry") instanceof Map<?,?> m)for(var entry:m.entrySet()) {
                    if(entry.getKey() instanceof String key && entry.getValue() instanceof Number n && Double.isFinite(n.doubleValue()) && n.doubleValue()>=0)telemetry.put(key,n);
                }
            }
            telemetry.put("totalMs",(System.nanoTime()-start)/1_000_000);
            store.recordRun(id,actor,queryId,p.get("id").toString(),input.question(),result.isEmpty()?"NO_RESULTS":"FOUND",
                (System.nanoTime()-start)/1_000_000,result,telemetry,null);
            return new SearchResult(id,p.get("id").toString(),p.get("strategy").toString(),result,telemetry);
        }catch(RuntimeException e){
            store.recordRun(id,actor,queryId,p.get("id").toString(),input.question(),"ERROR",(System.nanoTime()-start)/1_000_000,List.of(),telemetry,e instanceof com.roles.usermanagement.integration.n8n.N8nException failure?failure.code():"RETRIEVAL_FAILED");throw e;
        }
    }
    private Hit hit(Document d,Double cosine,double ranking,String metric,int rank) {
        return new Hit(d.pointId(),d.articleId(),d.version(),d.title(),d.section(),d.text(),cosine,ranking,metric,rank);
    }
    public List<Document> evidence(EvidenceRequest request) {
        var p=indexes.profile(request.profileId());Set<String> ids=new HashSet<>(request.pointIds());
        return indexes.documents(p).stream().filter(d->ids.contains(d.pointId())).toList();
    }
}
