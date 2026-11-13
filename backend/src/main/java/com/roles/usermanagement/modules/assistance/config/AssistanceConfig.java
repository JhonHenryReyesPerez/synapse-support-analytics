package com.roles.usermanagement.modules.assistance.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activa las propiedades del asistente y la tarea periódica que recupera consultas abandonadas. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(AssistanceProperties.class)
public class AssistanceConfig {
}
