package com.roles.usermanagement.domain.service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Política única para ESTABLECER contraseñas (registro, creación/edición administrativa,
 * cambio, restablecimiento y administrador inicial). El login no la aplica: solo autentica (B02).
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 6;
    /** BCrypt ignora los bytes posteriores al 72; se rechazan para que toda la clave cuente. */
    public static final int MAX_BYTES = 72;

    public static final String DESCRIPTION =
            "La contraseña debe tener entre " + MIN_LENGTH + " caracteres y " + MAX_BYTES
            + " bytes, al menos una letra mayúscula y al menos un carácter especial";

    // Mismas clases de caracteres que la regla original de registro, para no cambiar la política vigente.
    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern SPECIAL = Pattern.compile("[^a-zA-Z0-9\\s]");

    private PasswordPolicy() { }

    /** Devuelve el mensaje de incumplimiento, o vacío si la contraseña es válida. */
    public static Optional<String> violation(String password) {
        if (password == null || password.isBlank()) {
            return Optional.of("La contraseña es obligatoria");
        }
        if (password.length() < MIN_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES
                || !UPPERCASE.matcher(password).find()
                || !SPECIAL.matcher(password).find()) {
            return Optional.of(DESCRIPTION);
        }
        return Optional.empty();
    }

    public static boolean isValid(String password) {
        return violation(password).isEmpty();
    }
}
