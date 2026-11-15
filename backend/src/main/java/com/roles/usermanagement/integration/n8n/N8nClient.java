package com.roles.usermanagement.integration.n8n;

import com.roles.usermanagement.integration.n8n.dto.AssistRequest;
import com.roles.usermanagement.integration.n8n.dto.AssistResponse;
import com.roles.usermanagement.integration.n8n.dto.IndexRequest;
import com.roles.usermanagement.integration.n8n.dto.IndexResponse;

/**
 * Puerto hacia n8n. Las implementaciones no deben invocarse dentro de una transacción de base de
 * datos abierta. Cualquier fallo se comunica con {@link N8nException}.
 */
public interface N8nClient {

    IndexResponse index(IndexRequest request);

    AssistResponse assist(AssistRequest request);
}
