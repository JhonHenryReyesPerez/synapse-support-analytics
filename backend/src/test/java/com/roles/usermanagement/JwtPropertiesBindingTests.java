package com.roles.usermanagement;

import com.roles.usermanagement.web.config.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/** B01: el arranque debe fallar si la configuración JWT obligatoria falta o es débil. */
class JwtPropertiesBindingTests {

    @Configuration
    @EnableConfigurationProperties(JwtProperties.class)
    static class Config { }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Config.class)
            .withPropertyValues("security.jwt.issuer=campusdesk-api", "security.jwt.expiration=30m");

    @Test
    void failsWhenSecretIsMissing() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenSecretIsTooShort() {
        runner.withPropertyValues("security.jwt.secret=short-secret")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void startsWithStrongSecret() {
        runner.withPropertyValues("security.jwt.secret=" + "a".repeat(32))
                .run(context -> assertThat(context).hasNotFailed()
                        .getBean(JwtProperties.class).extracting(JwtProperties::issuer).isEqualTo("campusdesk-api"));
    }
}
