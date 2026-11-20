package com.roles.usermanagement;

import static org.assertj.core.api.Assertions.*;
import com.roles.usermanagement.modules.rag.controller.RagImportController;
import com.roles.usermanagement.modules.rag.service.RagText;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class RagImportTests {
    @Test void previewExtractsTextAndHashesOriginalBytesWithoutSaving() throws Exception {
        byte[] bytes;
        try (var doc=new PDDocument(); var out=new ByteArrayOutputStream()) {
            var page=new PDPage();doc.addPage(page);
            try(var text=new PDPageContentStream(doc,page)) {
                text.beginText();text.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);
                text.newLineAtOffset(20,700);text.showText("VPN error 809");text.endText();
            }
            doc.save(out);bytes=out.toByteArray();
        }
        var result=(Map<?,?>)new RagImportController().preview(new MockMultipartFile("file","../manual.pdf","application/pdf",bytes));
        assertThat(result.get("text")).isEqualTo("VPN error 809");
        assertThat(result.get("filename")).isEqualTo("manual.pdf");
        assertThat(result.get("sha256")).isEqualTo(RagText.hash(bytes));
        assertThat(result.get("pages")).isEqualTo(1);
    }
    @Test void rejectsDisguisedOrOversizedDocuments() {
        var controller=new RagImportController();
        assertThatThrownBy(()->controller.preview(new MockMultipartFile("file","bad.pdf","application/pdf","not pdf".getBytes())))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->controller.preview(new MockMultipartFile("file","huge.pdf","application/pdf",new byte[5*1024*1024+1])))
            .isInstanceOf(ResponseStatusException.class);
    }
}
