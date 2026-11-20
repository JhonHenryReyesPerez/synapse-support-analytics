package com.roles.usermanagement.modules.rag.controller;
import com.roles.usermanagement.modules.rag.dto.RagContracts.*;
import com.roles.usermanagement.modules.rag.service.RagSearchService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/integration/rag")
@PreAuthorize("hasRole('INTEGRATION')")
@io.swagger.v3.oas.annotations.tags.Tag(name="Integración n8n",description="Endpoints de servicio autenticados con X-Integration-Token")
public class RagIntegrationController {
    private final RagSearchService search;
    public RagIntegrationController(RagSearchService search){this.search=search;}
    @PostMapping("/evidence") public Object evidence(@Valid @RequestBody EvidenceRequest input){return search.evidence(input);}
}
