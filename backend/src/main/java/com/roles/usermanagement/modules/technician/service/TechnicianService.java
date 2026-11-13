package com.roles.usermanagement.modules.technician.service;

import com.roles.usermanagement.modules.lifecycle.enums.TicketStatus;
import com.roles.usermanagement.modules.technician.dto.TechnicianAvailabilityResponse;
import com.roles.usermanagement.modules.technician.dto.TechnicianResponse;
import com.roles.usermanagement.modules.technician.enums.TechnicianAvailabilityStatus;
import com.roles.usermanagement.modules.technician.repository.TechnicianRepository;
import com.roles.usermanagement.modules.ticket.repository.TicketRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta de técnicos para la asignación. Un técnico es una cuenta activa con rol TECHNICIAN;
 * el perfil solo aporta especialidad y disponibilidad (sin perfil se considera AVAILABLE).
 * La carga se calcula desde los tickets ASSIGNED e IN_PROGRESS, no desde un contador persistido.
 */
@Service
public class TechnicianService {

    private static final List<TicketStatus> ACTIVE_STATUSES = List.of(TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS);

    private final TechnicianRepository technicianRepository;
    private final TicketRepository ticketRepository;

    public TechnicianService(TechnicianRepository technicianRepository, TicketRepository ticketRepository) {
        this.technicianRepository = technicianRepository;
        this.ticketRepository = ticketRepository;
    }

    /** Todos los técnicos vigentes, sin importar su disponibilidad. */
    @Transactional(readOnly = true)
    public List<TechnicianResponse> listAll() {
        Map<String, Integer> load = activeLoad();
        return technicianRepository.findActiveTechnicians().stream()
                .map(row -> new TechnicianResponse(
                        (String) row[0],
                        (String) row[1],
                        availability(row),
                        load.getOrDefault((String) row[0], 0),
                        (LocalDateTime) row[3]))
                .toList();
    }

    /** Técnicos disponibles, del menos al más cargado. */
    @Transactional(readOnly = true)
    public List<TechnicianAvailabilityResponse> listAvailable() {
        Map<String, Integer> load = activeLoad();
        return technicianRepository.findActiveTechnicians().stream()
                .filter(row -> availability(row) == TechnicianAvailabilityStatus.AVAILABLE)
                .map(row -> new TechnicianAvailabilityResponse(
                        (String) row[0], (String) row[1], load.getOrDefault((String) row[0], 0)))
                .sorted(Comparator.comparing(TechnicianAvailabilityResponse::activeTicketsCount)
                        .thenComparing(TechnicianAvailabilityResponse::username))
                .toList();
    }

    private Map<String, Integer> activeLoad() {
        Map<String, Integer> load = new HashMap<>();
        ticketRepository.countByTechnicianAndStatusIn(ACTIVE_STATUSES)
                .forEach(row -> load.put((String) row[0], ((Number) row[1]).intValue()));
        return load;
    }

    private static TechnicianAvailabilityStatus availability(Object[] row) {
        return row[2] == null ? TechnicianAvailabilityStatus.AVAILABLE : (TechnicianAvailabilityStatus) row[2];
    }
}
