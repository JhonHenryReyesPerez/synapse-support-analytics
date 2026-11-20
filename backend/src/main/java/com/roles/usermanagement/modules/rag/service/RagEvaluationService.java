package com.roles.usermanagement.modules.rag.service;

import com.roles.usermanagement.modules.rag.dto.RagContracts.*;
import com.roles.usermanagement.modules.rag.repository.RagRepository;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class RagEvaluationService {
    private final RagRepository store;
    private final RagSearchService search;
    public RagEvaluationService(RagRepository store,RagSearchService search){this.store=store;this.search=search;}
    public void addCase(EvaluationCase input,String actor) {
        if(input.expectedArticleIds().isEmpty()!=input.expectNoResults())throw RagIndexService.bad("Define fuentes esperadas o abstención, sin combinarlas");
        var eligible=new HashSet<Long>();store.published().forEach(a->eligible.add(((Number)a.get("id")).longValue()));
        if(!eligible.containsAll(input.expectedArticleIds()))throw RagIndexService.bad("Las fuentes esperadas deben estar publicadas");
        store.db().update("insert into rag_evaluation_cases(question,expected_ids,expect_no_results,split,category,notes,author) values(?,?,?,?,?,?,?)",
            input.question().trim(),store.json(input.expectedArticleIds()),input.expectNoResults(),input.split()==null?"CALIBRATION":input.split(),input.category(),input.notes(),actor);
    }
    public Map<String,Object> run(EvaluationRequest request,String actor) {
        if(request.caseIds().size()*request.profileIds().size()>30)throw RagIndexService.bad("Máximo 30 búsquedas por ejecución");
        var cases=store.cases().stream().filter(c->request.caseIds().contains(((Number)c.get("id")).longValue())).toList();
        if(cases.size()!=new HashSet<>(request.caseIds()).size())throw RagIndexService.bad("Hay casos que no existen");
        var corpus=store.published().stream().map(a->a.get("id")+":"+a.get("content_version")+":"+a.get("content_hash")).toList();
        var results=new ArrayList<Map<String,Object>>();
        for(String profile:request.profileIds())for(var c:cases) {
            var row=new LinkedHashMap<String,Object>();row.put("caseId",c.get("id"));row.put("profileId",profile);row.put("split",c.get("split"));row.put("category",c.get("category"));
            List<?> expected=(List<?>)store.object("{\"ids\":"+c.get("expected_ids")+"}").get("ids");
            Set<Long> relevant=new HashSet<>();expected.forEach(v->relevant.add(((Number)v).longValue()));
            try {
                var response=search.search(new SearchInput(c.get("question").toString(),profile,5,null,null,null,null),actor,null);
                var retrieved=response.hits().stream().map(Hit::articleId).distinct().toList();
                int found=0;double reciprocal=0,dcg=0;
                for(int i=0;i<retrieved.size();i++)if(relevant.contains(retrieved.get(i))) {
                    found++;if(reciprocal==0)reciprocal=1.0/(i+1);dcg+=1.0/(Math.log(i+2)/Math.log(2));
                }
                double ideal=0;for(int i=0;i<Math.min(5,relevant.size());i++)ideal+=1.0/(Math.log(i+2)/Math.log(2));
                boolean abstain=Boolean.TRUE.equals(c.get("expect_no_results"));
                row.put("retrievedArticleIds",retrieved);row.put("recallAt5",relevant.isEmpty()?null:found/(double)relevant.size());
                row.put("mrrAt5",relevant.isEmpty()?null:reciprocal);row.put("ndcgAt5",ideal==0?null:dcg/ideal);
                row.put("abstentionCorrect",abstain?retrieved.isEmpty():!retrieved.isEmpty());row.put("telemetry",response.telemetry());row.put("status","COMPLETED");
            }catch(RuntimeException e){row.put("status","ERROR");row.put("errorCode","EVALUATION_SEARCH_FAILED");}
            results.add(row);
        }
        var output=new LinkedHashMap<String,Object>();output.put("corpusHash",RagText.hash(store.json(corpus)));output.put("corpus",corpus);
        output.put("profiles",request.profileIds().stream().map(store::profile).toList());output.put("results",results);
        output.put("notice","Métricas de recuperación, no de fidelidad. La revisión humana de la respuesta es independiente.");
        store.db().update("insert into rag_evaluation_runs(actor,results_json) values(?,?)",actor,store.json(output));
        return output;
    }
}
