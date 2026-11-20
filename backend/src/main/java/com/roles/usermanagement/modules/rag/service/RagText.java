package com.roles.usermanagement.modules.rag.service;

import com.roles.usermanagement.modules.rag.dto.RagContracts.Document;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Preparación determinista y fragmentación por párrafos, sin cortar pasos por caracteres. */
public final class RagText {
    private RagText() {}
    public static String hash(String text) {
        return hash(text.getBytes(StandardCharsets.UTF_8));
    }
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static String normalized(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }
    public static List<String> terms(String question) {
        return Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}_.:/-]{1,79}").matcher(normalized(question)).results()
            .map(m->m.group()).filter(s->!Set.of("que","como","para","con","del","una","los","las","por","el","la","en","se","de","un","es","no","mi").contains(s))
            .distinct().limit(12).toList();
    }
    public static double lexical(String question, String text) {
        var terms=terms(question); if(terms.isEmpty()) return 0;
        String haystack=normalized(text);
        return terms.stream().filter(haystack::contains).count()/(double)terms.size();
    }
    public static boolean literalEvidence(String question,String text) {
        var terms=terms(question);String normalized=normalized(text);
        var identifiers=terms.stream().filter(t->t.chars().anyMatch(Character::isDigit) || t.contains(".") || t.contains("_")).toList();
        if(!identifiers.isEmpty())return identifiers.stream().allMatch(id->Pattern.compile(
            "(?<![\\p{L}\\p{N}_])"+Pattern.quote(id)+"(?![\\p{L}\\p{N}_]|[.:/-][\\p{L}\\p{N}_])").matcher(normalized).find());
        return !terms.isEmpty() && lexical(question,text)>=0.6;
    }
    public static List<Document> documents(Map<String,Object> article, Map<String,Object> profile) {
        long id=((Number)article.get("id")).longValue();
        int version=((Number)article.get("content_version")).intValue();
        String title=article.get("title").toString(), hash=article.get("content_hash").toString();
        int size=((Number)profile.get("chunk_size")).intValue();
        String representation=profile.get("representation").toString();
        var sections=new LinkedHashMap<String,String>();
        sections.put("Problema", article.get("problem").toString()); sections.put("Síntomas", article.get("symptoms").toString());
        sections.put("Causa", article.get("cause").toString()); sections.put("Solución", article.get("solution").toString());
        String full=sections.entrySet().stream().map(e->e.getKey()+":\n"+e.getValue()).collect(java.util.stream.Collectors.joining("\n\n"));
        var chunks=new ArrayList<Map.Entry<String,String>>();
        if(size==0 || full.length()<=size) chunks.add(Map.entry("Artículo",full));
        else sections.forEach((section,text)->{
            var part=new StringBuilder();
            for(String paragraph:text.split("\\n\\s*\\n")) {
                if(part.length()>0 && part.length()+paragraph.length()>size) { chunks.add(Map.entry(section,part.toString())); part.setLength(0); }
                if(part.length()>0)part.append("\n\n"); part.append(paragraph);
            }
            if(part.length()>0)chunks.add(Map.entry(section,part.toString()));
        });
        var result=new ArrayList<Document>();
        for(int i=0;i<chunks.size();i++) {
            var c=chunks.get(i);
            String prepared="FULL".equals(representation) ? title+"\n"+c.getKey()+"\n"+c.getValue()
                : title+"\nProblema: "+article.get("problem")+"\nSíntomas: "+article.get("symptoms")+"\nSección: "+c.getKey();
            String point=UUID.nameUUIDFromBytes((profile.get("id")+":"+id+":"+version+":"+i+":"+hash).getBytes(StandardCharsets.UTF_8)).toString();
            result.add(new Document(point,id,version,hash,c.getKey(),title,c.getValue(),prepared));
        }
        return result;
    }
}
