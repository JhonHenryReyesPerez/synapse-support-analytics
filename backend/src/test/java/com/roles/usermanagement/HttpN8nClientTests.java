package com.roles.usermanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.roles.usermanagement.integration.n8n.HttpN8nClient;
import com.roles.usermanagement.integration.n8n.N8nException;
import com.roles.usermanagement.integration.n8n.N8nProperties;
import com.roles.usermanagement.integration.n8n.dto.IndexRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Transporte real de {@link HttpN8nClient} contra un servidor local: plazo total y tope de tamaño. */
@Timeout(30) // sin plazo en el cliente, la prueba del cuerpo a medias no terminaría
class HttpN8nClientTests {

    private static final int MAX_BYTES = 1024;
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void bodyThatNeverFinishesTimesOutAndClosesTheConnection() throws Exception {
        CountDownLatch connectionClosed = new CountDownLatch(1);
        HttpN8nClient client = clientFor(exchange -> {
            exchange.sendResponseHeaders(200, 0);
            OutputStream out = exchange.getResponseBody();
            out.write("{\"schemaVersion\":".getBytes(StandardCharsets.UTF_8));
            out.flush();
            // El cuerpo queda a medias: se sigue goteando hasta que el cliente corta la conexión.
            try {
                for (int i = 0; i < 100; i++) {
                    Thread.sleep(100);
                    out.write(' ');
                    out.flush();
                }
            } catch (IOException closedByClient) {
                connectionClosed.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, Duration.ofMillis(500));

        long start = System.nanoTime();
        N8nException failure = catchThrowableOfType(N8nException.class, () -> client.index(request()));
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertThat(failure).isNotNull();
        assertThat(failure.kind()).isEqualTo(N8nException.Kind.TIMEOUT);
        assertThat(millis).isLessThan(3_000);
        assertThat(connectionClosed.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void oversizedBodyIsRejectedWithoutReadingItAll() throws Exception {
        HttpN8nClient client = clientFor(exchange -> {
            exchange.sendResponseHeaders(200, 0);
            byte[] chunk = new byte[8192];
            try (OutputStream out = exchange.getResponseBody()) {
                // 80 MB si se leyera entero: el cliente debe cortar al pasar el tope.
                for (int i = 0; i < 10_000; i++) {
                    out.write(chunk);
                }
            } catch (IOException closedByClient) {
                // esperado
            }
        }, Duration.ofSeconds(10));

        N8nException failure = catchThrowableOfType(N8nException.class, () -> client.index(request()));

        assertThat(failure).isNotNull();
        assertThat(failure.kind()).isEqualTo(N8nException.Kind.INVALID_RESPONSE);
    }

    @Test
    void completeBodyWithinLimitsIsParsed() throws Exception {
        byte[] body = "{\"schemaVersion\":1,\"articleId\":7,\"contentVersion\":2,\"status\":\"INDEXED\"}"
                .getBytes(StandardCharsets.UTF_8);
        HttpN8nClient client = clientFor(exchange -> {
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }, Duration.ofSeconds(10));

        assertThat(client.index(request()).status()).isEqualTo("INDEXED");
    }

    private HttpN8nClient clientFor(HttpHandler handler, Duration readTimeout) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/index", (HttpExchange exchange) -> {
            exchange.getRequestBody().readAllBytes();
            handler.handle(exchange);
        });
        server.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
        return new HttpN8nClient(new N8nProperties(
                "http://127.0.0.1:" + server.getAddress().getPort(), "x".repeat(32), null,
                "/index", "/assist", Duration.ofSeconds(2), readTimeout, MAX_BYTES));
    }

    private static IndexRequest request() {
        return new IndexRequest(1, IndexRequest.Action.DELETE, 7L, 2, null, null, null, null, null, null, false, null);
    }
}
