package com.roles.usermanagement.integration.n8n;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.roles.usermanagement.integration.n8n.dto.AssistRequest;
import com.roles.usermanagement.integration.n8n.dto.AssistResponse;
import com.roles.usermanagement.integration.n8n.dto.IndexRequest;
import com.roles.usermanagement.integration.n8n.dto.IndexResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cliente HTTP de n8n: destino fijo por configuración, secreto de servicio en cabecera, tiempos
 * límite de conexión y respuesta, y tope de tamaño. No reintenta: repetir una generación tras un
 * timeout es decisión del caso de uso, no del transporte.
 */
@Component
@EnableConfigurationProperties(N8nProperties.class)
public class HttpN8nClient implements N8nClient {

    private static final Logger log = LoggerFactory.getLogger(HttpN8nClient.class);
    static final String TOKEN_HEADER = "X-Service-Token";

    private final N8nProperties properties;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public HttpN8nClient(N8nProperties properties) {
        this.properties = properties;
        this.http = HttpClient.newBuilder()
                // HTTP/1.1 fijo: por defecto el cliente pide actualizar a HTTP/2 (h2c) y n8n deja
                // esa petición sin responder hasta agotar el tiempo límite.
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public IndexResponse index(IndexRequest request) {
        return post(properties.indexPath(), request, IndexResponse.class, "article=" + request.articleId());
    }

    @Override
    public AssistResponse assist(AssistRequest request) {
        return post(properties.assistPath(), request, AssistResponse.class,
                "requestId=" + request.requestId() + " queryId=" + request.queryId());
    }

    private <T> T post(String path, Object payload, Class<T> type, String correlation) {
        if (!properties.isConfigured()) {
            throw new N8nException(N8nException.Kind.NOT_CONFIGURED);
        }
        long start = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.baseUrl().replaceAll("/+$", "") + path))
                    .timeout(properties.readTimeout())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header(TOKEN_HEADER, properties.serviceToken())
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            byte[] body;
            try (InputStream stream = response.body()) {
                body = stream.readNBytes(properties.maxResponseBytes() + 1);
            }
            long millis = (System.nanoTime() - start) / 1_000_000;
            // Se registran correlación, estado y duración; nunca el contenido ni el secreto.
            log.info("n8n {} {} status={} ms={}", path, correlation, response.statusCode(), millis);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new N8nException(N8nException.Kind.UPSTREAM_ERROR);
            }
            if (body.length > properties.maxResponseBytes()) {
                throw new N8nException(N8nException.Kind.INVALID_RESPONSE);
            }
            T parsed = mapper.readValue(body, type);
            if (parsed == null) {
                throw new N8nException(N8nException.Kind.INVALID_RESPONSE);
            }
            return parsed;
        } catch (N8nException e) {
            throw e;
        } catch (HttpTimeoutException e) {
            log.warn("n8n {} {} timeout", path, correlation);
            throw new N8nException(N8nException.Kind.TIMEOUT, e);
        } catch (ConnectException e) {
            log.warn("n8n {} {} sin conexión", path, correlation);
            throw new N8nException(N8nException.Kind.UNAVAILABLE, e);
        } catch (com.fasterxml.jackson.core.JacksonException e) {
            log.warn("n8n {} {} respuesta ilegible", path, correlation);
            throw new N8nException(N8nException.Kind.INVALID_RESPONSE, e);
        } catch (IOException e) {
            log.warn("n8n {} {} fallo de E/S: {}", path, correlation, e.getClass().getSimpleName());
            throw new N8nException(N8nException.Kind.UNAVAILABLE, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new N8nException(N8nException.Kind.UNAVAILABLE, e);
        }
    }
}
