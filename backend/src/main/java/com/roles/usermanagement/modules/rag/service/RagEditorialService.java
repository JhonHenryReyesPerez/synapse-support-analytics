package com.roles.usermanagement.modules.rag.service;

import com.roles.usermanagement.modules.rag.dto.RagContracts.*;
import com.roles.usermanagement.modules.rag.repository.RagRepository;
import com.roles.usermanagement.modules.ticket.service.TicketService;
import com.roles.usermanagement.modules.ticket.service.TicketAccessPolicy;
import com.roles.usermanagement.modules.knowledge.service.KnowledgeArticleService;
import com.roles.usermanagement.modules.knowledge.dto.ArticleRequest;
import com.roles.usermanagement.modules.knowledge.dto.ArticleResponse;
import com.roles.usermanagement.modules.lifecycle.enums.TicketStatus;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RagEditorialService {
    private final RagRepository store;private final TicketService tickets;private final TicketAccessPolicy policy;private final KnowledgeArticleService articles;
    public RagEditorialService(RagRepository store,TicketService tickets,TicketAccessPolicy policy,KnowledgeArticleService articles){
        this.store=store;this.tickets=tickets;this.policy=policy;this.articles=articles;
    }
    @Transactional
    public void metadata(long id,TechnicalMetadata input,Authentication auth) {
        store.rows("select id from knowledge_articles where id=? for update",id);
        var article=articles.getById(id,auth);
        if(!policy.isAdmin(auth) && (!policy.isTechnician(auth) || !article.authorUsername().equals(auth.getName()) || article.verified()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Solo puedes etiquetar tus propios borradores");
        var previous=store.rows("select * from rag_article_metadata where article_id=?",id);
        Object sourceTicket=input.sourceTicketId()!=null?input.sourceTicketId():previous.isEmpty()?null:previous.get(0).get("source_ticket_id");
        if(input.sourceTicketId()!=null)resolutionDraft(input.sourceTicketId(),auth);
        if((input.sourceDocumentName()==null)!=(input.sourceDocumentHash()==null))throw RagIndexService.bad("Nombre y hash del documento deben enviarse juntos");
        store.db().update("delete from rag_article_metadata where article_id=?",id);
        store.db().update("insert into rag_article_metadata(article_id,platform,application,product_version,architecture,source_ticket_id,source_document_name,source_document_hash,source_document_pages) values(?,?,?,?,?,?,?,?,?)",id,
            clean(input.platform()),clean(input.application()),clean(input.productVersion()),clean(input.architecture()),sourceTicket,input.sourceDocumentName(),input.sourceDocumentHash(),input.sourceDocumentPages());
    }
    public Map<String,Object> metadata(long id,Authentication auth){
        articles.getById(id,auth);var rows=store.rows("select * from rag_article_metadata where article_id=?",id);
        if(rows.isEmpty())return Map.of("article_id",id);
        var value=new LinkedHashMap<>(rows.get(0));
        if(value.get("source_ticket_id") instanceof Number source) {
            try {tickets.getById(source.longValue(),auth);}catch(ResponseStatusException e){value.remove("source_ticket_id");}
        }
        return value;
    }
    public Map<String,Object> resolutionDraft(long id,Authentication auth) {
        if(!policy.isAdmin(auth)&&!policy.isTechnician(auth))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Solo los autores pueden preparar artículos");
        var ticket=tickets.getById(id,auth);
        if(ticket.status()!=TicketStatus.RESOLVED && ticket.status()!=TicketStatus.CLOSED)throw RagIndexService.bad("Selecciona un incidente resuelto");
        var draft=new LinkedHashMap<String,Object>();draft.put("sourceTicketId",id);draft.put("title",ticket.title());draft.put("problem",ticket.description());
        draft.put("symptoms","");draft.put("cause","");draft.put("solution",ticket.resolutionNote());
        draft.put("existingDrafts",store.rows("select a.id,a.title from rag_article_metadata m join knowledge_articles a on a.id=m.article_id where m.source_ticket_id=? and a.author_username=? and a.verified=false",id,auth.getName()));
        draft.put("notice","Completa causa y síntomas, elimina datos personales y revisa la resolución antes de guardar. No se copian comentarios.");return draft;
    }
    /** Comparación textual dentro del alcance; no envía incidentes privados al proveedor de IA. */
    public List<Map<String,Object>> similar(long id,Authentication auth) {
        var source=tickets.getById(id,auth);
        String scope="";Object[] args;
        if(policy.isAdmin(auth))args=new Object[]{id};
        else if(policy.isTechnician(auth)){scope=" and technician_username=?";args=new Object[]{id,auth.getName()};}
        else {scope=" and applicant_username=?";args=new Object[]{id,auth.getName()};}
        var rows=store.rows("select id,title,description,status from tickets where id<>?"+scope+" order by created_at desc, id desc",args);
        return rows.stream().map(row->{Map<String,Object> result=new LinkedHashMap<>();result.put("id",row.get("id"));result.put("title",row.get("title"));result.put("status",row.get("status"));
                result.put("textualSimilarity",RagText.lexical(source.title()+" "+source.description(),row.get("title")+" "+row.get("description")));return result;})
            .filter(r->((Number)r.get("textualSimilarity")).doubleValue()>0)
            .sorted(Comparator.<Map<String,Object>>comparingDouble(r->((Number)r.get("textualSimilarity")).doubleValue()).reversed())
            .limit(5).toList();
    }
    public List<Map<String,Object>> gaps(int page) {
        return store.rows("select q.id,q.ticket_id,q.question,q.status,q.created_at,r.score,g.diagnosis,g.notes,g.article_id,g.reviewer "
            + "from assistance_queries q left join assistance_responses a on a.query_id=q.id left join assistance_ratings r on r.response_id=a.id "
            + "left join rag_gap_reviews g on g.query_id=q.id where q.status='NO_RESULTS' or r.score<=2 order by q.id desc limit 50 offset ?",(long)Math.max(0,page)*50);
    }
    @Transactional
    public void review(long queryId,GapReview input,String actor) {
        var rows=store.rows("select id from assistance_queries where id=? for update",queryId);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"La consulta no existe");
        if(input.articleId()!=null && store.db().queryForObject("select count(*) from knowledge_articles where id=?",Long.class,input.articleId())==0)throw RagIndexService.bad("El artículo no existe");
        store.db().update("delete from rag_gap_reviews where query_id=?",queryId);
        store.db().update("insert into rag_gap_reviews(query_id,diagnosis,notes,article_id,reviewer) values(?,?,?,?,?)",queryId,input.diagnosis(),input.notes(),input.articleId(),actor);
    }
    public Map<String,Object> clarify(FollowUp input) {
        String question=input.question()==null?"":input.question().trim();
        var facts=input.facts()==null?List.<String>of():input.facts().stream().map(String::trim).filter(s->!s.isEmpty()).toList();
        String combined=question+(facts.isEmpty()?"":"\nHechos aportados por el usuario:\n- "+String.join("\n- ",facts));
        if(combined.length()>2000)throw RagIndexService.bad("La pregunta y los hechos no pueden superar 2000 caracteres");
        boolean vague=RagText.terms(question).size()<3 && facts.isEmpty();
        return Map.of("question",combined,"needsClarification",vague,"prompts",vague?List.of("¿Qué aplicación o conexión presenta el fallo?","¿Qué mensaje o código exacto aparece?","¿Qué sistema o versión utilizas?"):List.of());
    }
    private String clean(String v){return v==null || v.isBlank()?null:v.trim();}
}
