package com.roles.usermanagement;

import com.fasterxml.jackson.databind.JsonNode;
import com.roles.usermanagement.integration.n8n.N8nException;
import com.roles.usermanagement.integration.n8n.dto.AssistRequest;
import com.roles.usermanagement.integration.n8n.dto.AssistResponse;
import com.roles.usermanagement.modules.assistance.service.AssistanceRecovery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF04, RF05 y RF08: consulta al asistente, validación de la respuesta del flujo, fuentes,
 * estados e historial. n8n se sustituye por {@link FakeN8nClient}: estas pruebas comprueban lo que
 * hace Spring Boot con cada respuesta posible del flujo, no la calidad de la recuperación real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AssistanceTests extends ApiTestSupport {

    @Autowired
    private FakeN8nClient n8n;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AssistanceRecovery recovery;

    @BeforeEach
    @AfterEach
    void resetN8n() {
        n8n.reset();
    }

    /** Prueba obligatoria 1: incidente de conectividad con soluciones relevantes mediante RAG. */
    @Test
    void connectivityIncidentGetsGroundedAnswerWithSourcesAndSimilarities() throws Exception {
        String user = registerUser();
        String userToken = token(user, PASSWORD);
        long dhcp = publishArticle("Sin acceso a internet por DHCP", "Renovar la concesión con ipconfig /renew");
        long dns = publishArticle("DNS no resuelve nombres", "Configurar los servidores DNS institucionales");
        long cable = publishArticle("Cable de red dañado", "Reemplazar el patch cord");
        long ticket = createTicket(userToken, "Conectividad", "Sin conexión a internet",
                "El equipo no navega y muestra el aviso Sin acceso a internet");
        String internalNote = "NOTA-INTERNA-" + UUID.randomUUID();
        assertThat(request("POST", "/api/tickets/" + ticket + "/comments",
                "{\"content\":\"" + internalNote + "\",\"visibility\":\"INTERNAL\"}", adminToken()).statusCode()).isEqualTo(201);
        n8n.onAssist = request -> FakeN8nClient.answered(request, "Renueve la dirección IP del equipo.",
                List.of(source(dns, 1, 0.81), source(dhcp, 1, 0.93), source(cable, 1, 0.74)));

        HttpResponse<String> response = ask(ticket, "{\"question\":\"  ¿Cómo recupero la conexión?  \"}", userToken);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        JsonNode query = json(response);
        assertThat(query.get("status").asText()).isEqualTo("ANSWERED");
        assertThat(query.get("statusLabel").asText()).isEqualTo("Respondida");
        assertThat(query.get("ticketId").asLong()).isEqualTo(ticket);
        assertThat(query.get("requesterUsername").asText()).isEqualTo(user);
        assertThat(query.get("question").asText()).isEqualTo("¿Cómo recupero la conexión?");
        assertThat(query.get("answer").asText()).isEqualTo("Renueve la dirección IP del equipo.");
        assertThat(query.get("recommendedSteps")).hasSize(2);
        assertThat(query.get("recommendedSteps").get(0).asText()).isEqualTo("Paso 1");
        assertThat(query.get("limitedEvidence").asBoolean()).isFalse();
        assertThat(query.get("errorCode").isNull()).isTrue();
        assertThat(query.get("attempts").asInt()).isEqualTo(1);
        assertThat(query.get("completedAt").isNull()).isFalse();
        // Fuentes identificables, ordenadas de mayor a menor similitud, con la evidencia utilizada.
        JsonNode sources = query.get("sources");
        assertThat(ids(sources)).containsExactly(dhcp, dns, cable);
        assertThat(sources.get(0).get("similarity").asDouble()).isEqualTo(0.93);
        assertThat(sources.get(0).get("order").asInt()).isEqualTo(1);
        assertThat(sources.get(0).get("title").asText()).isEqualTo("Sin acceso a internet por DHCP");
        assertThat(sources.get(0).get("articleVersion").asInt()).isEqualTo(1);
        assertThat(sources.get(0).get("evidence").asText()).contains("Renovar la concesión con ipconfig /renew");
        assertThat(sources.get(0).get("current").asBoolean()).isTrue();
        assertThat(sources.get(2).get("similarity").asDouble()).isEqualTo(0.74);
        // Umbral aplicado, métrica y modelos registrados.
        assertThat(query.get("retrieval").get("maxResults").asInt()).isEqualTo(5);
        assertThat(query.get("retrieval").get("minScore").asDouble()).isEqualTo(0.70);
        assertThat(query.get("retrieval").get("similarityMetric").asText()).isEqualTo("cosine");
        assertThat(query.get("retrieval").get("embeddingModel").asText()).isEqualTo(FakeN8nClient.EMBEDDING_MODEL);
        assertThat(query.get("generationModel").asText()).isEqualTo("test-generation-model");
        assertThat(query.get("workflowVersion").asText()).isEqualTo("test-workflow-1");

        // A n8n solo viajan los datos mínimos del incidente: sin usuario ni comentarios internos.
        List<AssistRequest> calls = callsFor(ticket);
        assertThat(calls).hasSize(1);
        AssistRequest sent = calls.get(0);
        assertThat(sent.schemaVersion()).isEqualTo(1);
        assertThat(sent.queryId()).isEqualTo(query.get("id").asLong());
        assertThat(sent.requestId()).isEqualTo(query.get("requestId").asText());
        assertThat(sent.attempt()).isEqualTo(1);
        assertThat(sent.question()).isEqualTo("¿Cómo recupero la conexión?");
        assertThat(sent.incidentTitle()).isEqualTo("Sin conexión a internet");
        assertThat(sent.incidentDescription()).contains("Sin acceso a internet");
        assertThat(sent.incidentCategory()).isEqualTo("Conectividad");
        assertThat(sent.maxResults()).isEqualTo(5);
        assertThat(sent.minScore()).isEqualTo(0.70);
        assertThat(mapper.writeValueAsString(sent)).doesNotContain(user).doesNotContain(internalNote);

        // La consulta, la respuesta y las fuentes quedaron persistidas.
        assertThat(json(request("GET", "/api/assistance/queries/" + query.get("id").asLong(), null, userToken))).isEqualTo(query);
        assertThat(responsesOf(query.get("id").asLong())).isEqualTo(1);
    }

    /** Prueba obligatoria 2: error de ejecución de una aplicación y artículos recuperados. */
    @Test
    void applicationErrorQueryReturnsExactlyTheRetrievedArticles() throws Exception {
        String admin = adminToken();
        String userToken = token(registerUser(), PASSWORD);
        String technician = createTechnician();
        String techToken = token(technician, PASSWORD);
        long runtime = publishArticle("Error 0xc000007b al abrir la aplicación", "Reinstalar Visual C++ Redistributable");
        long cache = publishArticle("La aplicación de nómina se cierra al iniciar", "Borrar la caché local del perfil");
        long unrelated = publishArticle("Impresora sin tóner", "Cambiar el cartucho");
        String description = "Al abrir la aplicación de nómina aparece el error 0xc000007b y se cierra";
        long ticket = createTicket(userToken, "Aplicaciones", "La aplicación de nómina no abre", description);
        assign(ticket, technician, admin);
        n8n.onAssist = request -> FakeN8nClient.answered(request, "Reinstale los componentes de Visual C++.",
                List.of(source(runtime, 1, 0.88), source(cache, 1, 0.79)));

        // El técnico asignado consulta sin escribir una pregunta: se usa la descripción del incidente.
        HttpResponse<String> response = ask(ticket, null, techToken);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        JsonNode query = json(response);
        assertThat(query.get("question").asText()).isEqualTo(description);
        assertThat(query.get("requesterUsername").asText()).isEqualTo(technician);
        assertThat(ids(query.get("sources"))).containsExactly(runtime, cache).doesNotContain(unrelated);
        // Solo dos artículos superaron el umbral: se devuelven como evidencia limitada, sin rellenar.
        assertThat(query.get("limitedEvidence").asBoolean()).isTrue();
        assertThat(query.get("message").asText()).contains("Evidencia limitada");
        assertThat(callsFor(ticket).get(0).question()).isEqualTo(description);

        // Si el artículo cambia después, la fuente conserva la versión y el texto usados y se marca como histórica.
        String changed = mapper.createObjectNode().put("title", "Error 0xc000007b al abrir la aplicación").put("problem", "p")
                .put("symptoms", "s").put("cause", "c").put("solution", "Reinstalar la aplicación completa").toString();
        assertThat(request("PUT", "/api/knowledge/articles/" + runtime, changed, admin).statusCode()).isEqualTo(200);
        JsonNode later = json(request("GET", "/api/assistance/queries/" + query.get("id").asLong(), null, techToken));
        JsonNode historical = later.get("sources").get(0);
        assertThat(historical.get("articleId").asLong()).isEqualTo(runtime);
        assertThat(historical.get("current").asBoolean()).isFalse();
        assertThat(historical.get("articleVersion").asInt()).isEqualTo(1);
        assertThat(historical.get("evidence").asText()).contains("Reinstalar Visual C++ Redistributable")
                .doesNotContain("Reinstalar la aplicación completa");
        assertThat(later.get("sources").get(1).get("current").asBoolean()).isTrue();
    }

    /** Prueba obligatoria 3: sin cobertura en la base de conocimiento no se inventan soluciones. */
    @Test
    void queryWithoutCoverageEndsInNoResultsWithoutInventedSolution() throws Exception {
        String userToken = token(registerUser(), PASSWORD);
        long ticket = createTicket(userToken, "Otros", "Dron de inventario no despega",
                "El dron del almacén no enciende los motores");
        // Aunque el flujo acompañe NO_RESULTS con texto, ese texto no tiene respaldo y no se conserva.
        n8n.onAssist = request -> new AssistResponse(1, request.requestId(), request.queryId(), "NO_RESULTS",
                "Pruebe reinstalar el sistema operativo", List.of("Formatear el equipo"), List.of(),
                FakeN8nClient.EMBEDDING_MODEL, "test-generation-model", request.minScore(), "cosine", "test-workflow-1", null);

        HttpResponse<String> response = ask(ticket, "{\"question\":\"¿Cómo reparo el dron?\"}", userToken);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        assertThat(response.body()).doesNotContain("reinstalar").doesNotContain("Formatear");
        JsonNode query = json(response);
        assertThat(query.get("status").asText()).isEqualTo("NO_RESULTS");
        assertThat(query.get("statusLabel").asText()).isEqualTo("Sin resultados");
        assertThat(query.get("answer").isNull()).isTrue();
        assertThat(query.get("recommendedSteps")).isEmpty();
        assertThat(query.get("sources")).isEmpty();
        assertThat(query.get("limitedEvidence").asBoolean()).isFalse();
        assertThat(query.get("errorCode").isNull()).isTrue();
        assertThat(query.get("message").asText()).contains("No se encontró información");
        assertThat(query.get("retrieval").get("embeddingModel").asText()).isEqualTo(FakeN8nClient.EMBEDDING_MODEL);
        assertThat(responsesOf(query.get("id").asLong())).isZero();
    }

    /** Prueba obligatoria 6: un artículo desactivado no vuelve a ser fuente, aunque su vector siga en el índice. */
    @Test
    void deactivatedArticleIsNeverUsedAsSourceEvenIfItsVectorRemains() throws Exception {
        String admin = adminToken();
        String userToken = token(registerUser(), PASSWORD);
        long valid = publishArticle("VPN: error 809", "Habilitar el paso de IPsec en el router");
        long retired = publishArticle("VPN: procedimiento antiguo", "Usar el cliente PPTP");
        assertThat(request("POST", "/api/knowledge/articles/" + retired + "/deactivate", null, admin).statusCode()).isEqualTo(200);
        long ticket = createTicket(userToken, "Conectividad", "La VPN no conecta", "Error 809 al conectar la VPN");

        // El índice todavía devuelve el artículo retirado; tras descartarlo, el reintento responde con el vigente.
        n8n.onAssist = request -> request.attempt() == 1
                ? FakeN8nClient.answered(request, "Use el cliente PPTP.", List.of(source(valid, 1, 0.90), source(retired, 1, 0.86)))
                : FakeN8nClient.answered(request, "Habilite IPsec en el router.", List.of(source(valid, 1, 0.90)));
        JsonNode retried = json(ask(ticket, null, userToken));
        assertThat(retried.get("status").asText()).isEqualTo("ANSWERED");
        assertThat(retried.get("answer").asText()).isEqualTo("Habilite IPsec en el router.");
        assertThat(ids(retried.get("sources"))).containsExactly(valid);
        assertThat(retried.get("attempts").asInt()).isEqualTo(2);
        assertThat(callsFor(ticket)).extracting(AssistRequest::attempt).containsExactly(1, 2);
        assertThat(callsFor(ticket).get(1).requestId()).isEqualTo(callsFor(ticket).get(0).requestId());

        // Si no queda evidencia vigente, la consulta termina sin resultados.
        n8n.onAssist = request -> request.attempt() == 1
                ? FakeN8nClient.answered(request, "Use el cliente PPTP.", List.of(source(retired, 1, 0.86)))
                : FakeN8nClient.noResults(request);
        JsonNode empty = json(ask(ticket, null, userToken));
        assertThat(empty.get("status").asText()).isEqualTo("NO_RESULTS");
        assertThat(empty.get("answer").isNull()).isTrue();

        // Si el flujo insiste en la fuente retirada, no se guarda ninguna respuesta: un único reintento y error.
        int before = callsFor(ticket).size();
        n8n.onAssist = request -> FakeN8nClient.answered(request, "Use el cliente PPTP.", List.of(source(retired, 1, 0.86)));
        HttpResponse<String> failed = ask(ticket, null, userToken);
        assertThat(failed.statusCode()).as(failed.body()).isEqualTo(503);
        assertThat(json(failed).get("code").asText()).isEqualTo("SOURCES_CHANGED");
        assertThat(callsFor(ticket)).hasSize(before + 2);
        long failedId = json(failed).get("queryId").asLong();
        assertThat(responsesOf(failedId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from assistance_response_sources where article_id = ?", Long.class, retired))
                .isZero();
    }

    @Test
    void sourceChangedDuringGenerationDiscardsTheResult() throws Exception {
        String admin = adminToken();
        String userToken = token(registerUser(), PASSWORD);
        long article = publishArticle("Outlook no abre", "Iniciar en modo seguro y deshabilitar complementos");
        long ticket = createTicket(userToken, "Aplicaciones", "Outlook no inicia", "Outlook se queda en Cargando perfil");
        // Mientras el flujo genera, un administrador cambia el contenido del artículo citado.
        String changed = mapper.createObjectNode().put("title", "Outlook no abre").put("problem", "p").put("symptoms", "s")
                .put("cause", "c").put("solution", "Reparar la instalación de Office").toString();
        n8n.onAssist = request -> {
            if (request.attempt() == 1) {
                quietly(() -> this.request("PUT", "/api/knowledge/articles/" + article, changed, admin));
                return FakeN8nClient.answered(request, "Inicie Outlook en modo seguro.", List.of(source(article, 1, 0.91)));
            }
            return FakeN8nClient.noResults(request);
        };

        JsonNode query = json(ask(ticket, null, userToken));

        // No basta con quitar la cita: el texto generado con la versión anterior se descarta completo.
        assertThat(query.get("status").asText()).isEqualTo("NO_RESULTS");
        assertThat(query.get("answer").isNull()).isTrue();
        assertThat(query.get("attempts").asInt()).isEqualTo(2);
        assertThat(responsesOf(query.get("id").asLong())).isZero();
    }

    /** Prueba obligatoria 7: una descripción o pregunta vacía no ejecuta el flujo inteligente. */
    @Test
    void blankDescriptionOrQuestionNeverInvokesTheFlow() throws Exception {
        String userToken = token(registerUser(), PASSWORD);
        // Un incidente sin descripción no llega a existir.
        assertThat(request("POST", "/api/tickets",
                "{\"title\":\"Sin red\",\"description\":\"   \",\"category\":\"Conectividad\",\"priority\":\"ALTA\"}", userToken)
                .statusCode()).isEqualTo(400);
        long ticket = createTicket(userToken, "Conectividad", "Sin red", "No hay conexión en la sala 2");

        assertThat(ask(ticket, "{\"question\":\"\"}", userToken).statusCode()).isEqualTo(400);
        assertThat(ask(ticket, "{\"question\":\"   \\n \"}", userToken).statusCode()).isEqualTo(400);
        assertThat(ask(ticket, "{\"question\":\"" + "a".repeat(2001) + "\"}", userToken).statusCode()).isEqualTo(400);
        assertThat(ask(ticket, "{\"question\":\"¿Qué hago?\",\"requestId\":\"no-es-un-uuid\"}", userToken).statusCode()).isEqualTo(400);

        // No se invocó n8n y no quedó ninguna consulta registrada.
        assertThat(callsFor(ticket)).isEmpty();
        assertThat(n8n.assistCalls).isEmpty();
        assertThat(history(ticket, "", userToken).get("totalElements").asLong()).isZero();
    }

    /** Prueba obligatoria 8: fallo del servicio de embeddings y otros fallos técnicos, con estado ERROR. */
    @Test
    void embeddingFailureEndsInControlledErrorWithQueryId() throws Exception {
        String userToken = token(registerUser(), PASSWORD);
        long ticket = createTicket(userToken, "Software", "Office sin licencia", "Office pide activar el producto");

        // El flujo informa que el servicio de embeddings falló.
        n8n.onAssist = request -> error(request, "EMBEDDING_FAILED");
        HttpResponse<String> response = ask(ticket, null, userToken);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(502);
        JsonNode error = json(response);
        assertThat(error.get("status").asInt()).isEqualTo(502);
        assertThat(error.get("code").asText()).isEqualTo("EMBEDDING_FAILED");
        assertThat(error.get("requestId").asText()).isNotBlank();
        JsonNode stored = json(request("GET", "/api/assistance/queries/" + error.get("queryId").asLong(), null, userToken));
        assertThat(stored.get("status").asText()).isEqualTo("ERROR");
        assertThat(stored.get("statusLabel").asText()).isEqualTo("Error");
        assertThat(stored.get("errorCode").asText()).isEqualTo("EMBEDDING_FAILED");
        assertThat(stored.get("answer").isNull()).isTrue();
        assertThat(stored.get("sources")).isEmpty();
        // Un fallo técnico no se presenta como ausencia de conocimiento.
        assertThat(stored.get("message").asText()).contains("fallo técnico").doesNotContain("No se encontró");
        assertThat(responsesOf(stored.get("id").asLong())).isZero();

        // Tiempo de espera agotado: 504 y ninguna repetición automática de la generación.
        int before = callsFor(ticket).size();
        n8n.onAssist = request -> {
            throw new N8nException(N8nException.Kind.TIMEOUT);
        };
        assertFailure(ask(ticket, null, userToken), 504, "N8N_TIMEOUT");
        assertThat(callsFor(ticket)).hasSize(before + 1);

        n8n.onAssist = request -> {
            throw new N8nException(N8nException.Kind.UNAVAILABLE);
        };
        assertFailure(ask(ticket, null, userToken), 503, "N8N_UNAVAILABLE");
        n8n.onAssist = request -> {
            throw new N8nException(N8nException.Kind.NOT_CONFIGURED);
        };
        assertFailure(ask(ticket, null, userToken), 503, "N8N_NOT_CONFIGURED");
        n8n.onAssist = request -> {
            throw new N8nException(N8nException.Kind.UPSTREAM_ERROR);
        };
        assertFailure(ask(ticket, null, userToken), 502, "N8N_UPSTREAM_ERROR");
        n8n.onAssist = request -> {
            throw new IllegalStateException("fallo no previsto");
        };
        assertFailure(ask(ticket, null, userToken), 502, "ASSIST_UNEXPECTED_ERROR");

        // El detalle del proveedor no se expone ni se guarda: solo un código con formato seguro.
        n8n.onAssist = request -> error(request, "401 Unauthorized: clave sk-secreta rechazada");
        HttpResponse<String> leaked = ask(ticket, null, userToken);
        assertFailure(leaked, 502, "ASSIST_FAILED");
        assertThat(leaked.body()).doesNotContain("sk-secreta");
        assertThat(jdbc.queryForObject("select count(*) from assistance_queries where error_code like '%sk-%'", Long.class)).isZero();
    }

    @Test
    void malformedWorkflowResponsesNeverBecomeAnswers() throws Exception {
        String userToken = token(registerUser(), PASSWORD);
        long article = publishArticle("Teclado no responde", "Reconectar el receptor USB");
        long ticket = createTicket(userToken, "Hardware", "Teclado sin respuesta", "El teclado inalámbrico no escribe");
        List<AssistResponse.Source> one = List.of(source(article, 1, 0.9));

        Map<String, Function<AssistRequest, AssistResponse>> cases = new LinkedHashMap<>();
        cases.put("consulta distinta", r -> answer(r, r.requestId(), r.queryId() + 1, 1, "ANSWERED", "Texto", one));
        cases.put("requestId distinto", r -> answer(r, UUID.randomUUID().toString(), r.queryId(), 1, "ANSWERED", "Texto", one));
        cases.put("versión de contrato distinta", r -> answer(r, r.requestId(), r.queryId(), 2, "ANSWERED", "Texto", one));
        cases.put("sin versión de contrato", r -> answer(r, r.requestId(), r.queryId(), null, "ANSWERED", "Texto", one));
        cases.put("estado desconocido", r -> answer(r, r.requestId(), r.queryId(), 1, "DONE", "Texto", one));
        cases.put("sin estado", r -> answer(r, r.requestId(), r.queryId(), 1, null, "Texto", one));
        cases.put("respuesta nula", r -> null);
        cases.put("texto vacío", r -> answered(r, "   ", one));
        cases.put("texto desmedido", r -> answered(r, "a".repeat(20_001), one));
        cases.put("respondida sin fuentes", r -> answered(r, "Texto", List.of()));
        cases.put("fuentes nulas", r -> answered(r, "Texto", null));
        cases.put("más fuentes que las pedidas", r -> answered(r, "Texto", List.of(source(1, 1, 0.9), source(2, 1, 0.9),
                source(3, 1, 0.9), source(4, 1, 0.9), source(5, 1, 0.9), source(6, 1, 0.9))));
        cases.put("artículo repetido", r -> answered(r, "Texto", List.of(source(article, 1, 0.9), source(article, 1, 0.8))));
        cases.put("similitud no numérica", r -> answered(r, "Texto", List.of(source(article, 1, Double.NaN))));
        cases.put("similitud infinita", r -> answered(r, "Texto", List.of(source(article, 1, Double.POSITIVE_INFINITY))));
        cases.put("similitud fuera de rango", r -> answered(r, "Texto", List.of(source(article, 1, 1.5))));
        cases.put("similitud bajo el umbral", r -> answered(r, "Texto", List.of(source(article, 1, 0.69))));
        cases.put("similitud nula", r -> answered(r, "Texto", List.of(new AssistResponse.Source(article, 1, null, null))));
        cases.put("versión inválida", r -> answered(r, "Texto", List.of(source(article, 0, 0.9))));
        cases.put("artículo sin id", r -> answered(r, "Texto", List.of(new AssistResponse.Source(null, 1, 0.9, null))));
        cases.put("artículo inexistente", r -> answered(r, "Texto", List.of(source(999_999_999L, 1, 0.9))));
        cases.put("sin resultados con fuentes", r -> answer(r, r.requestId(), r.queryId(), 1, "NO_RESULTS", null, one));
        cases.put("paso vacío", r -> new AssistResponse(1, r.requestId(), r.queryId(), "ANSWERED", "Texto", List.of("Paso", " "),
                one, FakeN8nClient.EMBEDDING_MODEL, "m", r.minScore(), "cosine", "w", null));
        cases.put("métrica no admitida", r -> new AssistResponse(1, r.requestId(), r.queryId(), "ANSWERED", "Texto", List.of(),
                one, FakeN8nClient.EMBEDDING_MODEL, "m", r.minScore(), "euclid", "w", null));
        cases.put("modelo desmedido", r -> new AssistResponse(1, r.requestId(), r.queryId(), "ANSWERED", "Texto", List.of(),
                one, FakeN8nClient.EMBEDDING_MODEL, "m".repeat(121), r.minScore(), "cosine", "w", null));

        for (Map.Entry<String, Function<AssistRequest, AssistResponse>> entry : cases.entrySet()) {
            n8n.onAssist = entry.getValue();
            HttpResponse<String> response = ask(ticket, null, userToken);
            assertThat(response.statusCode()).as(entry.getKey() + ": " + response.body()).isEqualTo(502);
            assertThat(json(response).get("code").asText()).as(entry.getKey()).isEqualTo("N8N_INVALID_RESPONSE");
            assertThat(responsesOf(json(response).get("queryId").asLong())).as(entry.getKey()).isZero();
        }
        // Una respuesta inválida no se reintenta: una llamada por caso.
        assertThat(callsFor(ticket)).hasSize(cases.size());

        // Consultar con un modelo de embeddings distinto del usado al indexar invalida la recuperación.
        n8n.onAssist = r -> new AssistResponse(1, r.requestId(), r.queryId(), "ANSWERED", "Texto", List.of(), one,
                "otro-modelo-de-embeddings", "m", r.minScore(), "cosine", "w", null);
        assertFailure(ask(ticket, null, userToken), 502, "EMBEDDING_MODEL_MISMATCH");

        // Ninguna de las respuestas rechazadas dejó una respuesta ni una fuente persistida.
        assertThat(jdbc.queryForObject("select count(*) from assistance_responses r join assistance_queries q on q.id = r.query_id "
                + "where q.ticket_id = ?", Long.class, ticket)).isZero();
        JsonNode list = history(ticket, "?size=100", userToken);
        assertThat(list.get("totalElements").asLong()).isEqualTo(cases.size() + 1);
        list.get("content").forEach(item -> assertThat(item.get("status").asText()).isEqualTo("ERROR"));
    }

    /** Prueba obligatoria 9: historial de asistencias de un incidente, con la autorización del ticket. */
    @Test
    void historyIsListedPerIncidentWithTicketAuthorization() throws Exception {
        String admin = adminToken();
        String userToken = token(registerUser(), PASSWORD);
        String otherUserToken = token(registerUser(), PASSWORD);
        String technician = createTechnician();
        String techToken = token(technician, PASSWORD);
        String otherTechToken = token(createTechnician(), PASSWORD);
        long article = publishArticle("Monitor sin señal", "Revisar el cable HDMI y la entrada seleccionada");
        long ticket = createTicket(userToken, "Hardware", "Monitor en negro", "El monitor enciende pero no muestra imagen");
        assign(ticket, technician, admin);

        n8n.onAssist = request -> FakeN8nClient.answered(request, "Revise el cable de video.", List.of(source(article, 1, 0.9)));
        long first = json(ask(ticket, "{\"question\":\"Primera\"}", userToken)).get("id").asLong();
        n8n.onAssist = FakeN8nClient::noResults;
        long second = json(ask(ticket, "{\"question\":\"Segunda\"}", techToken)).get("id").asLong();
        n8n.onAssist = request -> error(request, "GENERATION_FAILED");
        long third = json(ask(ticket, "{\"question\":\"Tercera\"}", userToken)).get("queryId").asLong();

        // Más recientes primero, con paginación; cada elemento trae pregunta, estado, fecha, respuesta y fuentes.
        JsonNode all = history(ticket, "", userToken);
        assertThat(all.get("totalElements").asLong()).isEqualTo(3);
        assertThat(ids(all.get("content"), "id")).containsExactly(third, second, first);
        assertThat(statuses(all.get("content"))).containsExactly("ERROR", "NO_RESULTS", "ANSWERED");
        JsonNode answered = all.get("content").get(2);
        assertThat(answered.get("question").asText()).isEqualTo("Primera");
        assertThat(answered.get("answer").asText()).isEqualTo("Revise el cable de video.");
        assertThat(ids(answered.get("sources"))).containsExactly(article);
        assertThat(answered.get("createdAt").isNull()).isFalse();
        JsonNode firstPage = history(ticket, "?page=0&size=2", userToken);
        assertThat(ids(firstPage.get("content"), "id")).containsExactly(third, second);
        assertThat(firstPage.get("totalPages").asInt()).isEqualTo(2);
        assertThat(ids(history(ticket, "?page=1&size=2", userToken).get("content"), "id")).containsExactly(first);

        // Mismo alcance que el incidente: solicitante, técnico asignado y administrador.
        assertThat(history(ticket, "", techToken).get("totalElements").asLong()).isEqualTo(3);
        assertThat(history(ticket, "", admin).get("totalElements").asLong()).isEqualTo(3);
        String path = "/api/tickets/" + ticket + "/assistance";
        assertThat(request("GET", path, null, otherUserToken).statusCode()).isEqualTo(403);
        assertThat(request("GET", path, null, otherTechToken).statusCode()).isEqualTo(403);
        assertThat(request("GET", path, null, null).statusCode()).isEqualTo(401);
        assertThat(request("GET", path + "?size=101", null, userToken).statusCode()).isEqualTo(400);
        assertThat(request("GET", "/api/tickets/999999999/assistance", null, admin).statusCode()).isEqualTo(404);
        assertThat(request("GET", "/api/assistance/queries/" + first, null, otherUserToken).statusCode()).isEqualTo(403);
        assertThat(request("GET", "/api/assistance/queries/" + first, null, otherTechToken).statusCode()).isEqualTo(403);
        assertThat(request("GET", "/api/assistance/queries/999999999", null, admin).statusCode()).isEqualTo(404);

        // Incidente cerrado: el historial sigue disponible, pero no admite consultas nuevas ni llama al flujo.
        close(ticket, techToken, userToken);
        int calls = callsFor(ticket).size();
        assertThat(ask(ticket, "{\"question\":\"Cuarta\"}", userToken).statusCode()).isEqualTo(409);
        assertThat(ask(ticket, null, admin).statusCode()).isEqualTo(409);
        assertThat(callsFor(ticket)).hasSize(calls);
        assertThat(history(ticket, "", userToken).get("totalElements").asLong()).isEqualTo(3);
    }

    @Test
    void onlyUsersWithAccessToTheIncidentCanAskAndRetriesCreateRelatedQueries() throws Exception {
        String admin = adminToken();
        String userToken = token(registerUser(), PASSWORD);
        String otherUserToken = token(registerUser(), PASSWORD);
        String otherTechToken = token(createTechnician(), PASSWORD);
        long ticket = createTicket(userToken, "Accesos", "Cuenta bloqueada", "No puedo iniciar sesión en el portal");
        long otherTicket = createTicket(otherUserToken, "Accesos", "Clave vencida", "El portal pide cambiar la clave");

        assertThat(ask(ticket, null, otherUserToken).statusCode()).isEqualTo(403);
        assertThat(ask(ticket, null, otherTechToken).statusCode()).isEqualTo(403);
        assertThat(ask(ticket, null, null).statusCode()).isEqualTo(401);
        assertThat(ask(999_999_999L, null, admin).statusCode()).isEqualTo(404);
        assertThat(callsFor(ticket)).isEmpty();

        // El administrador puede consultar cualquier incidente abierto; la consulta queda a su nombre.
        JsonNode byAdmin = json(ask(ticket, "{\"question\":\"¿Cómo desbloqueo la cuenta?\"}", admin));
        assertThat(byAdmin.get("requesterUsername").asText()).isEqualTo(ADMIN);

        // Un reintento explícito crea otra consulta enlazada y deja intacta la anterior.
        n8n.onAssist = request -> error(request, "VECTOR_SEARCH_FAILED");
        long failed = json(ask(ticket, "{\"question\":\"¿Por qué se bloqueó?\"}", userToken)).get("queryId").asLong();
        n8n.onAssist = FakeN8nClient::noResults;
        HttpResponse<String> retry = ask(ticket, "{\"retryOfQueryId\":" + failed + "}", userToken);
        assertThat(retry.statusCode()).as(retry.body()).isEqualTo(201);
        assertThat(json(retry).get("retryOfQueryId").asLong()).isEqualTo(failed);
        assertThat(json(retry).get("question").asText()).isEqualTo("¿Por qué se bloqueó?");
        assertThat(json(retry).get("id").asLong()).isNotEqualTo(failed);
        JsonNode original = json(request("GET", "/api/assistance/queries/" + failed, null, userToken));
        assertThat(original.get("status").asText()).isEqualTo("ERROR");
        assertThat(original.get("errorCode").asText()).isEqualTo("VECTOR_SEARCH_FAILED");
        // No se reintenta una consulta inexistente ni una de otro incidente.
        assertThat(ask(ticket, "{\"retryOfQueryId\":999999999}", userToken).statusCode()).isEqualTo(404);
        assertThat(ask(otherTicket, "{\"retryOfQueryId\":" + failed + "}", otherUserToken).statusCode()).isEqualTo(404);
    }

    @Test
    void doubleSubmissionDoesNotDuplicateQueriesOrResponses() throws Exception {
        String userToken = token(registerUser(), PASSWORD);
        String otherUserToken = token(registerUser(), PASSWORD);
        long article = publishArticle("Impresora en cola", "Reiniciar el servicio de cola de impresión");
        long ticket = createTicket(userToken, "Hardware", "No imprime", "Los documentos quedan en cola");
        long otherTicket = createTicket(otherUserToken, "Hardware", "No imprime", "La impresora no responde");

        // Segunda solicitud del mismo usuario mientras la primera sigue en proceso: se rechaza sin llamar al flujo.
        AtomicInteger nestedStatus = new AtomicInteger();
        n8n.onAssist = request -> {
            n8n.onAssist = FakeN8nClient::noResults;
            quietly(() -> nestedStatus.set(ask(ticket, null, userToken).statusCode()));
            return FakeN8nClient.answered(request, "Reinicie la cola de impresión.", List.of(source(article, 1, 0.9)));
        };
        HttpResponse<String> first = ask(ticket, null, userToken);
        assertThat(first.statusCode()).as(first.body()).isEqualTo(201);
        assertThat(nestedStatus.get()).isEqualTo(409);
        assertThat(callsFor(ticket)).hasSize(1);
        assertThat(history(ticket, "", userToken).get("totalElements").asLong()).isEqualTo(1);

        // Repetición con el mismo requestId: devuelve la consulta existente, sin otra llamada ni otra respuesta.
        String requestId = UUID.randomUUID().toString();
        n8n.onAssist = request -> FakeN8nClient.answered(request, "Reinicie la cola de impresión.", List.of(source(article, 1, 0.9)));
        HttpResponse<String> created = ask(ticket, "{\"requestId\":\"" + requestId + "\"}", userToken);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        assertThat(json(created).get("requestId").asText()).isEqualTo(requestId);
        HttpResponse<String> repeated = ask(ticket, "{\"requestId\":\"" + requestId + "\"}", userToken);
        assertThat(repeated.statusCode()).isEqualTo(200);
        assertThat(json(repeated)).isEqualTo(json(created));
        assertThat(callsFor(ticket)).hasSize(2);
        assertThat(history(ticket, "", userToken).get("totalElements").asLong()).isEqualTo(2);
        assertThat(responsesOf(json(created).get("id").asLong())).isEqualTo(1);

        // El requestId de otra consulta no puede reutilizarse en otro incidente.
        assertThat(ask(otherTicket, "{\"requestId\":\"" + requestId + "\"}", otherUserToken).statusCode()).isEqualTo(409);
        assertThat(callsFor(otherTicket)).isEmpty();
    }

    @Test
    void abandonedPendingQueriesAreRecoveredAndLateResultsDoNotOverwriteThem() throws Exception {
        String user = registerUser();
        String userToken = token(user, PASSWORD);
        long article = publishArticle("Escáner no detectado", "Reinstalar el controlador TWAIN");
        long ticket = createTicket(userToken, "Hardware", "Escáner sin conexión", "El equipo no detecta el escáner");
        long otherTicket = createTicket(userToken, "Hardware", "Escáner lento", "El escáner tarda varios minutos");

        // Consultas que quedaron PENDING por una interrupción: una antigua y otra reciente.
        long abandoned = insertPending(ticket, user, LocalDateTime.now().minusMinutes(30));
        long recent = insertPending(otherTicket, user, LocalDateTime.now());

        recovery.recoverAbandoned();

        JsonNode recovered = json(request("GET", "/api/assistance/queries/" + abandoned, null, userToken));
        assertThat(recovered.get("status").asText()).isEqualTo("ERROR");
        assertThat(recovered.get("errorCode").asText()).isEqualTo("ASSIST_ABANDONED");
        assertThat(recovered.get("completedAt").isNull()).isFalse();
        // La reciente puede seguir en curso: no se toca, y bloquea otra solicitud del mismo usuario.
        assertThat(json(request("GET", "/api/assistance/queries/" + recent, null, userToken)).get("status").asText())
                .isEqualTo("PENDING");
        assertThat(ask(otherTicket, null, userToken).statusCode()).isEqualTo(409);

        // Una PENDING abandonada no impide consultar de nuevo: se cierra al recibir la nueva solicitud.
        long stale = insertPending(ticket, user, LocalDateTime.now().minusMinutes(30));
        assertThat(ask(ticket, null, userToken).statusCode()).isEqualTo(201);
        assertThat(json(request("GET", "/api/assistance/queries/" + stale, null, userToken)).get("errorCode").asText())
                .isEqualTo("ASSIST_ABANDONED");

        // Resultado tardío: la consulta ya se cerró como abandonada cuando el flujo responde.
        n8n.onAssist = request -> {
            jdbc.update("update assistance_queries set created_at = ? where id = ?",
                    Timestamp.valueOf(LocalDateTime.now().minusMinutes(30)), request.queryId());
            recovery.recoverAbandoned();
            return FakeN8nClient.answered(request, "Respuesta tardía", List.of(source(article, 1, 0.9)));
        };
        HttpResponse<String> late = ask(ticket, null, userToken);
        assertFailure(late, 504, "ASSIST_ABANDONED");
        long lateId = json(late).get("queryId").asLong();
        assertThat(json(request("GET", "/api/assistance/queries/" + lateId, null, userToken)).get("answer").isNull()).isTrue();
        assertThat(responsesOf(lateId)).isZero();
    }

    private long insertPending(long ticket, String username, LocalDateTime createdAt) {
        String requestId = UUID.randomUUID().toString();
        jdbc.update("insert into assistance_queries (ticket_id, requester_username, question, status, request_id, "
                        + "max_results, min_score, attempts, created_at) values (?, ?, ?, 'PENDING', ?, 5, 0.7, 1, ?)",
                ticket, username, "Consulta interrumpida", requestId, Timestamp.valueOf(createdAt));
        return jdbc.queryForObject("select id from assistance_queries where request_id = ?", Long.class, requestId);
    }

    private void assertFailure(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        JsonNode body = json(response);
        assertThat(body.get("code").asText()).isEqualTo(code);
        assertThat(body.get("queryId").asLong()).isPositive();
        JsonNode stored = json(request("GET", "/api/assistance/queries/" + body.get("queryId").asLong(), null, adminToken()));
        assertThat(stored.get("status").asText()).isEqualTo("ERROR");
        assertThat(stored.get("errorCode").asText()).isEqualTo(code);
    }

    private long createTicket(String userToken, String category, String title, String description) throws Exception {
        String body = mapper.createObjectNode().put("title", title).put("description", description)
                .put("category", category).put("priority", "ALTA").toString();
        HttpResponse<String> response = request("POST", "/api/tickets", body, userToken);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return json(response).get("id").asLong();
    }

    private void assign(long ticket, String technician, String admin) throws Exception {
        assertThat(request("PATCH", "/api/tickets/" + ticket + "/assign",
                "{\"technicianUsername\":\"" + technician + "\"}", admin).statusCode()).isEqualTo(200);
    }

    private void close(long ticket, String techToken, String userToken) throws Exception {
        String path = "/api/tickets/" + ticket + "/transitions";
        assertThat(request("POST", path, "{\"action\":\"START_PROGRESS\"}", techToken).statusCode()).isEqualTo(200);
        assertThat(request("POST", path, "{\"action\":\"RESOLVE\",\"resolutionNote\":\"Resuelto\"}", techToken).statusCode()).isEqualTo(200);
        assertThat(request("POST", path, "{\"action\":\"CONFIRM_CLOSE\"}", userToken).statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> ask(long ticket, String body, String token) throws Exception {
        return request("POST", "/api/tickets/" + ticket + "/assistance", body, token);
    }

    private JsonNode history(long ticket, String query, String token) throws Exception {
        HttpResponse<String> response = request("GET", "/api/tickets/" + ticket + "/assistance" + query, null, token);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return json(response);
    }

    private List<AssistRequest> callsFor(long ticket) {
        return n8n.assistCalls.stream().filter(call -> call.incidentId() == ticket).toList();
    }

    private long responsesOf(long queryId) {
        return jdbc.queryForObject("select count(*) from assistance_responses where query_id = ?", Long.class, queryId);
    }

    private static AssistResponse.Source source(long articleId, int version, double score) {
        return new AssistResponse.Source(articleId, version, score, List.of("art-" + articleId + "-0"));
    }

    private static AssistResponse error(AssistRequest request, String errorCode) {
        return new AssistResponse(1, request.requestId(), request.queryId(), "ERROR", null, null, null,
                null, null, null, null, "test-workflow-1", errorCode);
    }

    private static AssistResponse answered(AssistRequest request, String answer, List<AssistResponse.Source> sources) {
        return answer(request, request.requestId(), request.queryId(), 1, "ANSWERED", answer, sources);
    }

    private static AssistResponse answer(AssistRequest request, String requestId, Long queryId, Integer schemaVersion,
                                         String status, String answer, List<AssistResponse.Source> sources) {
        return new AssistResponse(schemaVersion, requestId, queryId, status, answer, List.of("Paso 1"), sources,
                FakeN8nClient.EMBEDDING_MODEL, "test-generation-model", request.minScore(), "cosine", "test-workflow-1", null);
    }

    private static List<Long> ids(JsonNode sources) {
        return ids(sources, "articleId");
    }

    private static List<Long> ids(JsonNode items, String field) {
        List<Long> result = new ArrayList<>();
        items.forEach(item -> result.add(item.get(field).asLong()));
        return result;
    }

    private static List<String> statuses(JsonNode items) {
        List<String> result = new ArrayList<>();
        items.forEach(item -> result.add(item.get("status").asText()));
        return result;
    }

    private interface Action {
        void run() throws Exception;
    }

    private static void quietly(Action action) {
        try {
            action.run();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
