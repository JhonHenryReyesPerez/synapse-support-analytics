package com.roles.usermanagement;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

/** B07, B12 y §5: datos del usuario, política efectiva de permisos y protección de cuentas. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UserAccountPolicyTests extends ApiTestSupport {

    @Test
    void selfRegistrationStoresNameAndAreaAndCreatesOnlyUser() throws Exception {
        String user = "u_" + UUID.randomUUID().toString().substring(0, 10);
        String body = String.format("{\"username\":\"%s\",\"email\":\"%s@test.local\",\"password\":\"%s\","
                + "\"fullName\":\" Ana Gómez \",\"area\":\"Contabilidad\","
                + "\"role\":\"ADMIN\",\"roles\":[{\"role\":\"ADMIN\"}],\"additionalPermissions\":[\"USER_DELETE\"],"
                + "\"locked\":true,\"mustChangePassword\":true}", user, user, PASSWORD);

        assertThat(request("POST", "/api/auth/register", body, null).statusCode()).isEqualTo(201);

        JsonNode stored = account(user);
        assertThat(stored.get("fullName").asText()).isEqualTo("Ana Gómez");
        assertThat(stored.get("area").asText()).isEqualTo("Contabilidad");
        assertThat(stored.get("role").asText()).isEqualTo("USER");
        assertThat(stored.get("additionalPermissions")).isEmpty();
        assertThat(stored.get("locked").asBoolean()).isFalse();
        assertThat(stored.has("password")).isFalse();
        // La cuenta opera de inmediato: no quedó bloqueada ni con cambio pendiente.
        assertThat(request("GET", "/api/tickets", null, token(user, PASSWORD)).statusCode()).isEqualTo(200);
    }

    @Test
    void individualPermissionsDoNotEscalatePrivilegesNorBypassOwnership() throws Exception {
        String admin = adminToken();
        String holder = registerUser();
        String other = registerUser();
        long foreignTicket = createTicket(token(other, PASSWORD));
        for (String permission : new String[]{"USER_UPDATE", "USER_CREATE", "USER_DELETE", "ROLE_ASSIGN",
                "PERMISSION_ASSIGN", "ROLE_MANAGE", "PERMISSION_MANAGE", "TICKET_READ_ALL", "TICKET_ASSIGN"}) {
            assertThat(request("POST", "/api/user/assignPermission",
                    "{\"username\":\"" + holder + "\",\"permission\":\"" + permission + "\"}", admin).statusCode())
                    .as(permission).isEqualTo(200);
        }
        String holderToken = token(holder, PASSWORD);

        // Ascenso de privilegios: todo rechazado aunque tenga los permisos individuales.
        assertThat(request("POST", "/api/user/assignRole", "{\"username\":\"" + holder + "\",\"role\":\"ADMIN\"}", holderToken)
                .statusCode()).isEqualTo(403);
        assertThat(request("POST", "/api/user/assignPermission",
                "{\"username\":\"" + other + "\",\"permission\":\"USER_DELETE\"}", holderToken).statusCode()).isEqualTo(403);
        assertThat(request("PUT", "/api/user/update", "{\"username\":\"" + holder + "\",\"role\":\"ADMIN\"}", holderToken)
                .statusCode()).isEqualTo(403);
        assertThat(request("POST", "/api/user/add", "{\"username\":\"x_" + holder + "\",\"email\":\"x_" + holder
                + "@test.local\",\"password\":\"" + PASSWORD + "\",\"role\":\"ADMIN\"}", holderToken).statusCode()).isEqualTo(403);
        assertThat(request("PUT", "/api/roles/USER/permissions/USER_DELETE", null, holderToken).statusCode()).isEqualTo(403);

        // Cuentas administrativas y contraseñas ajenas: intocables.
        assertThat(request("POST", "/api/user/" + ADMIN + "/reset-password", "{\"temporaryPassword\":\"Tomada#123\"}", holderToken)
                .statusCode()).isEqualTo(403);
        assertThat(request("PUT", "/api/user/update", "{\"username\":\"" + ADMIN + "\",\"email\":\"otro@test.local\"}", holderToken)
                .statusCode()).isEqualTo(403);
        assertThat(request("DELETE", "/api/user/delete/" + ADMIN, null, holderToken).statusCode()).isEqualTo(403);
        assertThat(request("PUT", "/api/user/update", "{\"username\":\"" + other + "\",\"password\":\"Tomada#123\"}", holderToken)
                .statusCode()).isEqualTo(403);
        assertThat(token(ADMIN, ADMIN_PASSWORD)).isNotBlank();

        // Propiedad y restricciones del rol sobre incidentes: el permiso aislado no las elude.
        assertThat(request("GET", "/api/tickets/" + foreignTicket, null, holderToken).statusCode()).isEqualTo(403);
        assertThat(request("PATCH", "/api/tickets/" + foreignTicket + "/assign",
                "{\"technicianUsername\":\"" + createTechnician() + "\"}", holderToken).statusCode()).isEqualTo(403);

        // Dentro del límite del rol el permiso sí habilita: editar datos no sensibles de otra cuenta USER.
        assertThat(request("PUT", "/api/user/update", "{\"username\":\"" + other + "\",\"area\":\"Soporte\"}", holderToken)
                .statusCode()).isEqualTo(200);
        assertThat(account(other).get("area").asText()).isEqualTo("Soporte");
    }

    @Test
    void systemAlwaysKeepsAnActiveAdministrator() throws Exception {
        String admin = adminToken();

        assertThat(request("PUT", "/api/user/update", "{\"username\":\"" + ADMIN + "\",\"disabled\":true}", admin)
                .statusCode()).isEqualTo(409);
        assertThat(request("PUT", "/api/user/update", "{\"username\":\"" + ADMIN + "\",\"locked\":true}", admin)
                .statusCode()).isEqualTo(409);
        assertThat(request("POST", "/api/user/assignRole", "{\"username\":\"" + ADMIN + "\",\"role\":\"USER\"}", admin)
                .statusCode()).isEqualTo(409);
        assertThat(request("DELETE", "/api/user/delete/" + ADMIN, null, admin).statusCode()).isEqualTo(409);

        // Con un segundo administrador activo, este sí puede dejar el rol.
        String second = registerUser();
        assertThat(request("POST", "/api/user/assignRole", "{\"username\":\"" + second + "\",\"role\":\"ADMIN\"}", admin)
                .statusCode()).isEqualTo(200);
        assertThat(request("POST", "/api/user/assignRole", "{\"username\":\"" + second + "\",\"role\":\"USER\"}", admin)
                .statusCode()).isEqualTo(200);
        assertThat(account(second).get("role").asText()).isEqualTo("USER");
    }

    @Test
    void technicianWithActiveTicketsKeepsRoleUntilReassigned() throws Exception {
        String admin = adminToken();
        String technician = createTechnician();
        String replacement = createTechnician();
        long ticket = createTicket(token(registerUser(), PASSWORD));
        assertThat(request("PATCH", "/api/tickets/" + ticket + "/assign",
                "{\"technicianUsername\":\"" + technician + "\"}", admin).statusCode()).isEqualTo(200);

        assertThat(request("POST", "/api/user/assignRole", "{\"username\":\"" + technician + "\",\"role\":\"USER\"}", admin)
                .statusCode()).isEqualTo(409);
        assertThat(request("PUT", "/api/user/update", "{\"username\":\"" + technician + "\",\"disabled\":true}", admin)
                .statusCode()).isEqualTo(409);
        assertThat(request("DELETE", "/api/user/delete/" + technician, null, admin).statusCode()).isEqualTo(409);

        assertThat(request("PUT", "/api/tickets/" + ticket + "/reassign",
                "{\"technicianUsername\":\"" + replacement + "\",\"justification\":\"Cambio de rol\"}", admin).statusCode())
                .isEqualTo(200);
        assertThat(request("POST", "/api/user/assignRole", "{\"username\":\"" + technician + "\",\"role\":\"USER\"}", admin)
                .statusCode()).isEqualTo(200);

        // Sin el rol ya no es asignable aunque conserve su perfil técnico.
        long another = createTicket(token(registerUser(), PASSWORD));
        assertThat(request("PATCH", "/api/tickets/" + another + "/assign",
                "{\"technicianUsername\":\"" + technician + "\"}", admin).statusCode()).isEqualTo(400);
    }

    @Test
    void deletionIsLogicalAndInactiveAccountsCannotOperate() throws Exception {
        String admin = adminToken();
        String user = registerUser();
        String userToken = token(user, PASSWORD);
        long ticket = createTicket(userToken);

        assertThat(request("DELETE", "/api/user/delete/" + user, null, admin).statusCode()).isEqualTo(200);

        // El token previo y el login dejan de servir; la cuenta y su ticket permanecen.
        assertThat(request("GET", "/api/tickets", null, userToken).statusCode()).isEqualTo(401);
        assertThat(request("POST", "/api/auth/login",
                "{\"username\":\"" + user + "\",\"password\":\"" + PASSWORD + "\"}", null).statusCode()).isEqualTo(401);
        assertThat(account(user).get("disabled").asBoolean()).isTrue();
        assertThat(json(request("GET", "/api/tickets/" + ticket, null, admin)).get("applicantUsername").asText()).isEqualTo(user);
        assertThat(request("DELETE", "/api/user/delete/no_existe_" + user, null, admin).statusCode()).isEqualTo(404);

        // Una cuenta bloqueada tampoco opera con un token emitido antes del bloqueo.
        String locked = registerUser();
        String lockedToken = token(locked, PASSWORD);
        assertThat(request("PUT", "/api/user/update", "{\"username\":\"" + locked + "\",\"locked\":true}", admin)
                .statusCode()).isEqualTo(200);
        assertThat(request("GET", "/api/tickets", null, lockedToken).statusCode()).isEqualTo(401);
    }

    /** El alta y la actualización administrativas validan el correo igual que el registro. */
    @Test
    void malformedEmailIsRejectedOnAdminCreateAndUpdate() throws Exception {
        String admin = adminToken();
        String user = registerUser();
        String original = account(user).get("email").asText();

        assertThat(request("PUT", "/api/user/update", "{\"username\":\"" + user + "\",\"email\":\"not-an-email\"}", admin)
                .statusCode()).isEqualTo(400);
        assertThat(request("PUT", "/api/user/update",
                "{\"username\":\"" + user + "\",\"email\":\"" + "a".repeat(200) + "@test.local\"}", admin).statusCode())
                .isEqualTo(400);
        assertThat(account(user).get("email").asText()).isEqualTo(original);
        assertThat(request("POST", "/api/user/add", "{\"username\":\"x_" + user + "\",\"email\":\"sin-arroba\","
                + "\"password\":\"" + PASSWORD + "\"}", admin).statusCode()).isEqualTo(400);

        // Un correo válido se sigue aceptando y normalizando; omitirlo no lo toca.
        assertThat(request("PUT", "/api/user/update",
                "{\"username\":\"" + user + "\",\"email\":\" Nuevo_" + user + "@Test.Local \"}", admin).statusCode()).isEqualTo(200);
        assertThat(account(user).get("email").asText()).isEqualTo("nuevo_" + user + "@test.local");
        assertThat(request("PUT", "/api/user/update", "{\"username\":\"" + user + "\",\"area\":\"Soporte\"}", admin)
                .statusCode()).isEqualTo(200);
        assertThat(account(user).get("email").asText()).isEqualTo("nuevo_" + user + "@test.local");
    }

    @Test
    void usernameWithMarkupIsRejected() throws Exception {
        String body = "{\"username\":\"\\\"><svg onload=alert(1)>\",\"email\":\"x_%s@test.local\",\"password\":\"" + PASSWORD + "\"}";
        assertThat(request("POST", "/api/auth/register", String.format(body, System.nanoTime()), null).statusCode())
                .isEqualTo(400);
        assertThat(request("POST", "/api/user/add", String.format(body, System.nanoTime()), adminToken()).statusCode())
                .isEqualTo(400);
    }

    @Test
    void technicianCannotReadOtherAccounts() throws Exception {
        String technician = createTechnician();
        String technicianToken = token(technician, PASSWORD);

        assertThat(request("GET", "/api/user/all", null, technicianToken).statusCode()).isEqualTo(403);
        assertThat(request("GET", "/api/user/" + ADMIN + "/permissions", null, technicianToken).statusCode())
                .isEqualTo(403);
        assertThat(request("GET", "/api/technicians", null, adminToken()).statusCode()).isEqualTo(200);
    }

    /**
     * Dos administradores degradados a la vez: cada operación bloqueaba primero su cuenta y luego
     * el conjunto de administradores, y se interbloqueaban. No toca a superadmin, que sigue activo.
     */
    @Test
    void concurrentAdminDemotionsDoNotDeadlock() throws Exception {
        String admin = adminToken();
        for (int round = 0; round < 8; round++) {
            String first = registerUser();
            String second = registerUser();
            assertThat(assignRole(first, "ADMIN", admin)).isEqualTo(200);
            assertThat(assignRole(second, "ADMIN", admin)).isEqualTo(200);
            CountDownLatch start = new CountDownLatch(1);

            var one = CompletableFuture.supplyAsync(() -> demoteWhenReleased(start, first, admin));
            var other = CompletableFuture.supplyAsync(() -> demoteWhenReleased(start, second, admin));
            start.countDown();

            assertThat(List.of(one.get(), other.get())).as("ronda %d", round).containsOnly(200);
            assertThat(account(first).get("role").asText()).isEqualTo("USER");
            assertThat(account(second).get("role").asText()).isEqualTo("USER");
        }
    }

    private int assignRole(String username, String role, String token) throws Exception {
        return request("POST", "/api/user/assignRole",
                "{\"username\":\"" + username + "\",\"role\":\"" + role + "\"}", token).statusCode();
    }

    private int demoteWhenReleased(CountDownLatch start, String username, String token) {
        try {
            start.await();
            return assignRole(username, "USER", token);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode account(String username) throws Exception {
        HttpResponse<String> response = request("GET", "/api/user/all", null, adminToken());
        assertThat(response.statusCode()).isEqualTo(200);
        for (JsonNode item : json(response)) {
            if (username.equals(item.get("username").asText())) {
                return item;
            }
        }
        throw new AssertionError("No existe la cuenta " + username);
    }
}
