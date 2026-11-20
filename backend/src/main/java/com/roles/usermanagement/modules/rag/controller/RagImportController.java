package com.roles.usermanagement.modules.rag.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import com.roles.usermanagement.modules.rag.service.RagText;

@RestController
@RequestMapping("/api/rag/import")
@PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN')")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
public class RagImportController {
    @PostMapping(value="/preview",consumes="multipart/form-data")
    public Object preview(@RequestParam("file") MultipartFile file) {
        if(file.isEmpty() || file.getSize()>5*1024*1024)throw bad("El PDF debe tener entre 1 byte y 5 MB");
        try {
            byte[] data=file.getBytes();
            if(data.length<5 || !new String(data,0,5,StandardCharsets.US_ASCII).equals("%PDF-"))throw bad("Solo se admite PDF con texto");
            try(var document=Loader.loadPDF(data)) {
                if(document.isEncrypted() || document.getNumberOfPages()>100)throw bad("PDF cifrado o con más de 100 páginas");
                var stripper=new PDFTextStripper() {
                    private int chars;
                    @Override protected void writeString(String text,java.util.List<org.apache.pdfbox.text.TextPosition> positions)throws IOException {
                        chars+=text.length();if(chars>50000)throw new IOException("Texto demasiado grande");super.writeString(text,positions);
                    }
                };
                String text=stripper.getText(document).trim();
                if(text.isBlank())throw bad("No hay texto extraíble; los PDF escaneados requieren OCR y revisión externa");
                String name=file.getOriginalFilename()==null?"documento.pdf":file.getOriginalFilename().replace('\\','/');
                name=name.substring(name.lastIndexOf('/')+1);if(name.length()>200)name=name.substring(name.length()-200);
                return Map.of("filename",name,"sha256",RagText.hash(data),
                    "pages",document.getNumberOfPages(),"text",text,"notice","Previsualización sin publicar ni indexar. Revisa la extracción y elimina datos sensibles antes de crear un borrador.");
            }
        }catch(ResponseStatusException e){throw e;}catch(IOException | IllegalArgumentException e){throw bad("No se pudo extraer el texto del PDF dentro de los límites admitidos");}
    }
    private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
