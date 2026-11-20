package com.roles.usermanagement;

import static org.assertj.core.api.Assertions.assertThat;
import com.roles.usermanagement.modules.rag.service.*;
import com.roles.usermanagement.modules.rag.repository.RagRepository;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.core.io.ClassPathResource;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RagToolsTests extends ApiTestSupport {
    @Autowired RagRepository store;
    @Autowired RagIndexService indexes;
    @Autowired FakeN8nClient n8n;
    @DynamicPropertySource static void isolatedDatabase(DynamicPropertyRegistry registry) {
        String db=System.getenv("TEST_DB_URL");
        if(db==null || !db.startsWith("jdbc:postgresql:"))registry.add("spring.datasource.url",()->"jdbc:h2:mem:ragtools;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    }
    @BeforeEach void schema() throws Exception {
        boolean exists;
        try{store.db().queryForObject("select count(*) from rag_profiles",Long.class);exists=true;}catch(org.springframework.jdbc.BadSqlGrammarException e){exists=false;}
        if(!exists) {
            String sql=new String(new ClassPathResource("db/migration/V10__rag_knowledge_tools.sql").getInputStream().readAllBytes(),StandardCharsets.UTF_8);
            sql=sql.replaceAll("(?m)^--.*$","").replaceAll("(?m)^ALTER TABLE.*$","").replaceAll("(?m)^CREATE UNIQUE INDEX.*WHERE.*$","");
            for(String statement:sql.split(";"))if(!statement.isBlank())store.db().execute(statement);
        }
        n8n.onKnowledge=r->{throw new com.roles.usermanagement.integration.n8n.N8nException(com.roles.usermanagement.integration.n8n.N8nException.Kind.NOT_CONFIGURED);};
    }
    private String profile(String strategy) throws Exception {
        String id="p"+UUID.randomUUID().toString().substring(0,8);
        var body=mapper.createObjectNode().put("id",id).put("name",id).put("model","openai/text-embedding-3-small").put("dimension",1536)
            .put("strategy",strategy).put("minCosine",0.55).put("chunkSize",700).put("representation","FULL").put("rerankModel","cohere/rerank-v3.5");
        var r=request("POST","/api/rag/profiles",body.toString(),adminToken());assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return id;
    }
    @Test void semanticToolsPreservePermissionsAndTextSearchNeedsNoProvider() throws Exception {
        String user=registerUser(), t=token(user,PASSWORD);String word="probe"+UUID.randomUUID().toString().replace("-","");
        long article=publishArticle(word,"Procedimiento del manual");
        var search=request("POST","/api/rag/search",mapper.createObjectNode().put("question",word).toString(),t);
        assertThat(search.statusCode()).as(search.body()).isEqualTo(200);
        assertThat(json(search).get("hits").get(0).get("articleId").asLong()).isEqualTo(article);
        assertThat(json(search).get("hits").get(0).get("cosine").isNull()).isTrue();
        assertThat(request("GET","/api/rag/operations",null,t).statusCode()).isEqualTo(403);
        assertThat(request("POST","/api/rag/profiles","{}",t).statusCode()).isIn(400,403);
        assertThat(request("POST","/api/integration/rag/evidence","{\"profileId\":\"text\",\"pointIds\":[]}",t).statusCode()).isEqualTo(401);
    }
    @Test void metadataFiltersAndWithdrawalApplyBeforeResultsAreReturned() throws Exception {
        String word="filter"+UUID.randomUUID().toString().replace("-","");long id=publishArticle(word,"paso");String admin=adminToken();
        assertThat(request("PUT","/api/rag/articles/"+id+"/metadata","{\"platform\":\"Windows 11\"}",admin).statusCode()).isEqualTo(200);
        String query=mapper.createObjectNode().put("question",word).put("platform","Linux").toString();
        assertThat(json(request("POST","/api/rag/search",query,admin)).get("hits")).isEmpty();
        query=mapper.createObjectNode().put("question",word).put("platform","Windows 11").toString();
        assertThat(json(request("POST","/api/rag/search",query,admin)).get("hits")).hasSize(1);
        request("POST","/api/knowledge/articles/"+id+"/deactivate",null,admin);
        assertThat(json(request("POST","/api/rag/search",query,admin)).get("hits")).isEmpty();
    }
    @Test void hybridAcceptsAnExactCodeWithoutPretendingRrfIsCosine() throws Exception {
        long id=publishArticle("Error probe0x8871","Corregir probe0x8871");String profile=profile("HYBRID");
        var doc=indexes.documents(indexes.profile(profile)).stream().filter(d->d.articleId()==id).findFirst().orElseThrow();
        n8n.onKnowledge=r->Map.of("schemaVersion",2,"requestId",r.get("requestId"),"status","OK","hits",List.of(Map.of("pointId",doc.pointId(),"cosine",0.1,"rankingScore",0.02)));
        var res=request("POST","/api/rag/search",mapper.createObjectNode().put("question","probe0x8871").put("profileId",profile).toString(),adminToken());
        assertThat(res.statusCode()).as(res.body()).isEqualTo(200);
        var h=json(res).get("hits").get(0);assertThat(h.get("articleId").asLong()).isEqualTo(id);assertThat(h.get("cosine").asDouble()).isEqualTo(0.1);assertThat(h.get("metric").asText()).isEqualTo("rrf");
    }
    @Test void vectorProviderCannotInjectAnUnknownPoint() throws Exception {
        String profile=profile("DENSE");
        n8n.onKnowledge=r->Map.of("schemaVersion",2,"requestId",r.get("requestId"),"status","OK","hits",List.of(Map.of("pointId",UUID.randomUUID().toString(),"cosine",0.99,"rankingScore",0.99)));
        var res=request("POST","/api/rag/search",mapper.createObjectNode().put("question","unrelated").put("profileId",profile).toString(),adminToken());
        assertThat(res.statusCode()).as(res.body()).isEqualTo(200);assertThat(json(res).get("hits")).isEmpty();
    }
    @Test void profilesCannotActivateBeforeIndexingAndPhysicalHealthDetectsMissingPoints() throws Exception {
        publishArticle("health"+UUID.randomUUID(),"paso");String profile=profile("DENSE");String admin=adminToken();
        assertThat(request("POST","/api/rag/profiles/"+profile+"/activate",null,admin).statusCode()).isEqualTo(409);
        n8n.onKnowledge=r->Map.of("schemaVersion",2,"requestId",r.get("requestId"),"status","OK","points",List.of());
        var result=request("GET","/api/rag/profiles/"+profile+"/health",null,admin);
        assertThat(result.statusCode()).as(result.body()).isEqualTo(200);assertThat(json(result).get("missing")).isNotEmpty();
    }
    @Test void chunkingPreservesParagraphsAndLiteralCodesAreNotInterchangeable() {
        Map<String,Object> a=Map.of("id",1L,"content_version",2,"content_hash","a".repeat(64),"title","Manual","problem","p","symptoms","s","cause","c","solution","Un paso indivisible "+"x".repeat(800));
        var profile=Map.<String,Object>of("id","test","chunk_size",300,"representation","FULL");
        var docs=RagText.documents(a,profile);
        assertThat(docs.stream().filter(d->d.section().equals("Solución")).findFirst().orElseThrow().text()).contains("x".repeat(800));
        assertThat(RagText.documents(a,profile)).isEqualTo(docs);
        assertThat(RagText.literalEvidence("error 0xc000007b","error 0xc0000005")).isFalse();
        assertThat(RagText.literalEvidence("error 809","error 8090")).isFalse();
        assertThat(RagText.literalEvidence("versión 1.2","versión 1.2.3")).isFalse();
        assertThat(RagText.literalEvidence("error 809","Código de error 809.")).isTrue();
    }
    @Test void similarTicketsNeverExposeAnotherApplicantsIncident() throws Exception {
        String owner=registerUser(),other=registerUser(),ownToken=token(owner,PASSWORD);
        long first=createTicket(ownToken),second=createTicket(ownToken),foreign=createTicket(token(other,PASSWORD));
        var result=request("GET","/api/rag/tickets/"+first+"/similar",null,ownToken);
        assertThat(result.statusCode()).as(result.body()).isEqualTo(200);
        var ids=new ArrayList<Long>();json(result).forEach(v->ids.add(v.get("id").asLong()));
        assertThat(ids).contains(second).doesNotContain(foreign);
        assertThat(request("GET","/api/rag/tickets/"+foreign+"/similar",null,ownToken).statusCode()).isEqualTo(403);
    }
    @Test void clarificationKeepsExplicitFactsAndNeverSendsThemToAProvider() throws Exception {
        var result=request("POST","/api/rag/clarify","{\"question\":\"no conecta\",\"facts\":[\"VPN error 809\"]}",adminToken());
        assertThat(result.statusCode()).as(result.body()).isEqualTo(200);assertThat(json(result).get("question").asText()).contains("VPN error 809");
        assertThat(json(result).get("needsClarification").asBoolean()).isFalse();
    }
    @Test void citedAssistancePersistsExactEvidenceAndDeduplicatesRequest() throws Exception {
        String word="cited"+UUID.randomUUID().toString().replace("-","");long article=publishArticle(word,"Paso verificable");
        var doc=indexes.documents(indexes.profile("text")).stream().filter(d->d.articleId()==article).findFirst().orElseThrow();
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        n8n.onKnowledge=r->{calls.incrementAndGet();return Map.of("schemaVersion",2,"requestId",r.get("requestId"),"status","OK",
            "answer","Paso verificable","steps",List.of(Map.of("text","Paso verificable","citations",List.of(doc.pointId()))),"telemetry",Map.of("inputTokens",12));};
        String token=token(registerUser(),PASSWORD);long ticket=createTicket(token);String requestId=UUID.randomUUID().toString();
        String body=mapper.createObjectNode().put("question",word).put("requestId",requestId).put("retrievalProfileId","text").toString();
        var response=request("POST","/api/tickets/"+ticket+"/assistance",body,token);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        var query=json(response);assertThat(query.get("status").asText()).isEqualTo("ANSWERED");
        assertThat(query.get("retrieval").get("embeddingModel").isNull()).isTrue();
        assertThat(query.get("sources").get(0).get("similarity").isNull()).isTrue();
        assertThat(query.get("sources").get(0).get("evidence").asText()).isEqualTo(doc.text());
        assertThat(mapper.readTree(query.get("advancedJson").asText()).get("citations").get(0).get("pointIds").get(0).asText()).isEqualTo(doc.pointId());
        assertThat(request("POST","/api/tickets/"+ticket+"/assistance",body,token).statusCode()).isEqualTo(200);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(json(request("GET","/api/assistance/queries/"+query.get("id").asLong(),null,token))).isEqualTo(query);
    }
    @Test void forgedStepCitationIsRejectedAndRecordedAsError() throws Exception {
        String word="forged"+UUID.randomUUID().toString().replace("-","");publishArticle(word,"Paso");
        n8n.onKnowledge=r->Map.of("schemaVersion",2,"requestId",r.get("requestId"),"status","OK","answer","Texto",
            "steps",List.of(Map.of("text","Paso","citations",List.of(UUID.randomUUID().toString()))));
        String token=token(registerUser(),PASSWORD);long ticket=createTicket(token);
        var response=request("POST","/api/tickets/"+ticket+"/assistance",mapper.createObjectNode().put("question",word).put("retrievalProfileId","text").toString(),token);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(502);
        assertThat(store.db().queryForObject("select count(*) from rag_search_runs where question=? and status='ERROR'",Long.class,word)).isEqualTo(1);
    }
    @Test void evaluationKeepsHoldoutAndUsesAllExpectedDocumentsInRecall() throws Exception {
        String word="benchmark"+UUID.randomUUID().toString().replace("-","");long first=publishArticle(word,"Paso"),second=publishArticle("Distinto "+UUID.randomUUID(),"Otro");
        String admin=adminToken();String body=mapper.createObjectNode().put("question",word).put("split","HOLDOUT").put("expectNoResults",false).set("expectedArticleIds",mapper.valueToTree(List.of(first,second))).toString();
        var created=request("POST","/api/rag/evaluation/cases",body,admin);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(200);
        long id=store.db().queryForObject("select max(id) from rag_evaluation_cases",Long.class);
        body=mapper.createObjectNode().set("profileIds",mapper.valueToTree(List.of("text"))).toString();
        var input=mapper.readTree(body);((com.fasterxml.jackson.databind.node.ObjectNode)input).set("caseIds",mapper.valueToTree(List.of(id)));
        var response=request("POST","/api/rag/evaluation/runs",input.toString(),admin);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);var row=json(response).get("results").get(0);
        assertThat(row.get("recallAt5").asDouble()).isEqualTo(.5);assertThat(row.get("mrrAt5").asDouble()).isEqualTo(1);
        assertThat(row.get("split").asText()).isEqualTo("HOLDOUT");assertThat(json(response).get("corpusHash").asText()).hasSize(64);
    }
    @Test void missingPhysicalPointCanBeRepairedWithExactCachedVector() throws Exception {
        String id=profile("DENSE");publishArticle("cache"+UUID.randomUUID(),"Paso");
        var generated=new java.util.concurrent.atomic.AtomicInteger();var reused=new java.util.concurrent.atomic.AtomicInteger();
        List<Double> vector=java.util.Collections.nCopies(1536,.125);
        n8n.onKnowledge=r->{
            if(r.get("operation").equals("SCROLL"))return Map.of("schemaVersion",2,"requestId",r.get("requestId"),"status","OK","points",List.of());
            if(r.containsKey("cachedVector"))reused.incrementAndGet();else generated.incrementAndGet();
            return Map.of("schemaVersion",2,"requestId",r.get("requestId"),"status","OK","vector",vector);
        };
        for(int i=0;i<50;i++)if(Boolean.TRUE.equals(indexes.index(id,10).get("done")))break;
        assertThat(generated.get()).isPositive();
        assertThat(indexes.activate(id).get("active")).isEqualTo(true);
        indexes.activate("text");assertThat(store.activeProfile()).isEqualTo("text");
        var health=indexes.health(id,true);assertThat((List<?>)health.get("missing")).isNotEmpty();
        int before=generated.get();indexes.index(id,1);assertThat(generated.get()).isEqualTo(before);assertThat(reused.get()).isPositive();
    }
    @Test void sourceWithdrawalDuringGenerationDiscardsWholeAnswer() throws Exception {
        String word="withdraw"+UUID.randomUUID().toString().replace("-","");long id=publishArticle(word,"Paso");
        var doc=indexes.documents(indexes.profile("text")).stream().filter(d->d.articleId()==id).findFirst().orElseThrow();
        n8n.onKnowledge=r->{
            store.db().update("update knowledge_articles set active=false where id=?",id);
            return Map.of("schemaVersion",2,"requestId",r.get("requestId"),"status","OK","answer","No debe persistirse",
                "steps",List.of(Map.of("text","Paso","citations",List.of(doc.pointId()))));
        };
        String token=token(registerUser(),PASSWORD);long ticket=createTicket(token);
        var response=request("POST","/api/tickets/"+ticket+"/assistance",mapper.createObjectNode().put("question",word).put("retrievalProfileId","text").toString(),token);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);assertThat(json(response).get("status").asText()).isEqualTo("NO_RESULTS");
        assertThat(json(response).get("answer").isNull()).isTrue();
    }

}
