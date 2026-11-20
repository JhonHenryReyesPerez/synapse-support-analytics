package com.roles.usermanagement.modules.rag.controller;

import com.roles.usermanagement.modules.rag.dto.RagContracts.*;
import com.roles.usermanagement.modules.rag.repository.RagRepository;
import com.roles.usermanagement.modules.rag.service.*;
import com.roles.usermanagement.integration.n8n.N8nException;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rag")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
public class RagController {
    private final RagRepository store;private final RagIndexService indexes;private final RagSearchService search;
    private final RagEditorialService editorial;private final RagEvaluationService evaluation;
    public RagController(RagRepository store,RagIndexService indexes,RagSearchService search,RagEditorialService editorial,RagEvaluationService evaluation){
        this.store=store;this.indexes=indexes;this.search=search;this.editorial=editorial;this.evaluation=evaluation;
    }
    @GetMapping("/profiles") public Object profiles(){return store.profiles();}
    @PostMapping("/profiles") @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId="ragCreateProfile")
    public Object create(@Valid @RequestBody ProfileInput input){return indexes.create(input);}
    @PostMapping("/profiles/{id}/activate")  @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId="ragActivateProfile")
    public Object activate(@PathVariable String id){return indexes.activate(id);}
    @GetMapping("/profiles/{id}/plan") @PreAuthorize("hasRole('ADMIN')")
    public Object plan(@PathVariable String id){return indexes.plan(id);}
    @PostMapping("/profiles/{id}/index") @PreAuthorize("hasRole('ADMIN')")
    public Object index(@PathVariable String id,@RequestParam(defaultValue="5") int limit){return indexes.index(id,limit);}
    @GetMapping("/profiles/{id}/health") @PreAuthorize("hasRole('ADMIN')")
    public Object health(@PathVariable String id){return indexes.health(id,false);}
    @PostMapping("/profiles/{id}/repair") @PreAuthorize("hasRole('ADMIN')")
    public Object repair(@PathVariable String id){return indexes.health(id,true);}
    @PostMapping("/search") public Object search(@Valid @RequestBody SearchInput input,Authentication auth){return search.search(input,auth.getName(),null);}
    @PostMapping("/clarify") public Object clarify(@Valid @RequestBody FollowUp input){return editorial.clarify(input);}
    @GetMapping("/articles/{id}/metadata") public Object metadata(@PathVariable long id,Authentication auth){return editorial.metadata(id,auth);}
    @PutMapping("/articles/{id}/metadata") @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN')")
    public Object metadata(@PathVariable long id,@Valid @RequestBody TechnicalMetadata input,Authentication auth){editorial.metadata(id,input,auth);return editorial.metadata(id,auth);}
    @GetMapping("/tickets/{id}/draft") @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN')")
    public Object draft(@PathVariable long id,Authentication auth){return editorial.resolutionDraft(id,auth);}
    @GetMapping("/tickets/{id}/similar") public Object similar(@PathVariable long id,Authentication auth){return editorial.similar(id,auth);}
    @GetMapping("/gaps") @PreAuthorize("hasRole('ADMIN')")
    public Object gaps(@RequestParam(defaultValue="0") int page){return editorial.gaps(page);}
    @PutMapping("/gaps/{id}") @PreAuthorize("hasRole('ADMIN')")
    public Object review(@PathVariable long id,@Valid @RequestBody GapReview input,Authentication auth){editorial.review(id,input,auth.getName());return Map.of("saved",true);}
    @GetMapping("/evaluation/cases") @PreAuthorize("hasRole('ADMIN')") public Object cases(){return store.cases();}
    @PostMapping("/evaluation/cases") @PreAuthorize("hasRole('ADMIN')")
    public Object addCase(@Valid @RequestBody EvaluationCase input,Authentication auth){evaluation.addCase(input,auth.getName());return Map.of("saved",true);}
    @PostMapping("/evaluation/runs") @PreAuthorize("hasRole('ADMIN')")
    public Object run(@Valid @RequestBody EvaluationRequest input,Authentication auth){return evaluation.run(input,auth.getName());}
    @GetMapping("/evaluation/runs") @PreAuthorize("hasRole('ADMIN')")
    public Object runs(){return store.rows("select * from rag_evaluation_runs order by id desc limit 50");}
    @GetMapping("/operations") @PreAuthorize("hasRole('ADMIN')")
    public Object operations(){
        var rows=store.rows("select request_id,profile_id,status,duration_ms,telemetry_json,error_code,created_at from rag_search_runs order by id desc limit 1000");
        var durations=rows.stream().map(r->((Number)r.get("duration_ms")).longValue()).sorted().toList();
        var result=new LinkedHashMap<String,Object>();result.put("window","Últimas 1000 búsquedas");result.put("sampleSize",rows.size());
        result.put("p50Ms",durations.isEmpty()?null:durations.get((int)Math.floor((durations.size()-1)*.5)));
        result.put("p95Ms",durations.isEmpty()?null:durations.get((int)Math.ceil((durations.size()-1)*.95)));
        result.put("runs",rows);return result;
    }
    @ExceptionHandler(N8nException.class)
    public ResponseEntity<?> n8nFailure(N8nException e){
        int status=switch(e.kind()){case TIMEOUT->504;case NOT_CONFIGURED,UNAVAILABLE->503;default->502;};
        return ResponseEntity.status(status).body(Map.of("error","No se pudo completar la operación de conocimiento","code",e.code()));
    }
}
