package com.roles.usermanagement.modules.technician.repository;

import com.roles.usermanagement.modules.technician.entity.TechnicianProfile;
import com.roles.usermanagement.modules.technician.enums.TechnicianAvailabilityStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for technician profiles.
 * Primary key is username (String), not a numeric id.
 */
public interface TechnicianRepository extends JpaRepository<TechnicianProfile, String> {

    /**
     * Returns technicians with specified availability,
     * ordered from lowest to highest active ticket count.
     */
    List<TechnicianProfile> findByAvailabilityStatusOrderByActiveTicketsCountAsc(
            TechnicianAvailabilityStatus status);

    /**
     * Técnicos vigentes: cuentas activas con rol TECHNICIAN, tengan o no perfil.
     * Columnas: username, specialty, availabilityStatus, lastAssignedAt.
     */
    @org.springframework.data.jpa.repository.Query("""
            select u.username, p.specialty, p.availabilityStatus, p.lastAssignedAt
            from UserEntity u join u.roles r
            left join TechnicianProfile p on p.username = u.username
            where r.role = 'TECHNICIAN' and u.disabled = false and u.locked = false
            order by u.username
            """)
    List<Object[]> findActiveTechnicians();
}