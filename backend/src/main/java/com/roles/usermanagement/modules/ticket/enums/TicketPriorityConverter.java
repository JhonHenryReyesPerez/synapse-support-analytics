package com.roles.usermanagement.modules.ticket.enums;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/** Aplica a los parámetros de consulta los mismos alias que acepta el cuerpo JSON. */
@Component
public class TicketPriorityConverter implements Converter<String, TicketPriority> {
    @Override
    public TicketPriority convert(String source) {
        return TicketPriority.fromString(source);
    }
}
