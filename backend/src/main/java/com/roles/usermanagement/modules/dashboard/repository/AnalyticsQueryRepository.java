package com.roles.usermanagement.modules.dashboard.repository;

import com.roles.usermanagement.modules.lifecycle.enums.TicketStatus;
import com.roles.usermanagement.modules.ticket.enums.TicketPriority;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * Consultas agregadas de analítica. Todas comparten la misma cláusula de cohorte y usan
 * parámetros con nombre; ningún filtro del usuario se concatena en la consulta.
 * El SQL equivalente para verificación independiente está en docs/backend/ANALITICA_SQL.md.
 */
@Repository
public class AnalyticsQueryRepository {

    /** Filtros de la cohorte ya resueltos; null significa "sin filtro". */
    public record CohortFilter(LocalDateTime fromInclusive, LocalDateTime toExclusive, Long categoryId,
                               TicketPriority priority, String applicantUsername, String technicianUsername) {
    }

    private static final List<TicketStatus> RESOLVED_STATUSES = List.of(TicketStatus.RESOLVED, TicketStatus.CLOSED);

    @PersistenceContext
    private EntityManager entityManager;

    /** [total, resueltos (RESOLVED o CLOSED)]. */
    public long[] totalAndResolved(CohortFilter filter) {
        Object[] row = query("select count(t), coalesce(sum(case when t.status in :resolved then 1 else 0 end), 0) "
                + "from Ticket t" + where(filter), Object[].class, filter)
                .setParameter("resolved", RESOLVED_STATUSES)
                .getSingleResult();
        return new long[]{((Number) row[0]).longValue(), ((Number) row[1]).longValue()};
    }

    /** Filas [categoryId, categoryName, count]. */
    public List<Object[]> countByCategory(CohortFilter filter) {
        return query("select c.id, c.name, count(t) from Ticket t join t.category c" + where(filter)
                + " group by c.id, c.name order by count(t) desc, c.name asc", Object[].class, filter).getResultList();
    }

    /** Filas [priority, count]. */
    public List<Object[]> countByPriority(CohortFilter filter) {
        return query("select t.priority, count(t) from Ticket t" + where(filter) + " group by t.priority",
                Object[].class, filter).getResultList();
    }

    /** Filas [día de creación (LocalDate), count], solo días con incidentes. */
    public List<Object[]> countByCreationDay(CohortFilter filter) {
        return query("select cast(t.createdAt as LocalDate), count(t) from Ticket t" + where(filter)
                + " group by cast(t.createdAt as LocalDate) order by cast(t.createdAt as LocalDate)",
                Object[].class, filter).getResultList();
    }

    /** [muestra, promedio en segundos (null si la muestra es 0)] entre resueltos con fecha válida. */
    public Object[] resolutionTime(CohortFilter filter) {
        return query("select count(t), avg((t.resolvedAt - t.createdAt) by second) from Ticket t" + where(filter)
                + VALID_RESOLUTION, Object[].class, filter)
                .setParameter("resolved", RESOLVED_STATUSES)
                .getSingleResult();
    }

    /** Filas [categoryId, categoryName, muestra, promedio en segundos]. */
    public List<Object[]> resolutionTimeByCategory(CohortFilter filter) {
        return query("select c.id, c.name, count(t), avg((t.resolvedAt - t.createdAt) by second) "
                + "from Ticket t join t.category c" + where(filter) + VALID_RESOLUTION
                + " group by c.id, c.name order by c.name asc", Object[].class, filter)
                .setParameter("resolved", RESOLVED_STATUSES)
                .getResultList();
    }

    /** Resueltos sin fecha de resolución o con fecha anterior a la creación. */
    public long excludedFromResolutionTime(CohortFilter filter) {
        return query("select count(t) from Ticket t" + where(filter)
                + " and t.status in :resolved and (t.resolvedAt is null or t.resolvedAt < t.createdAt)", Long.class, filter)
                .setParameter("resolved", RESOLVED_STATUSES)
                .getSingleResult();
    }

    /** [primera creación, última creación] de la cohorte; nulls si está vacía. */
    public Object[] creationBounds(CohortFilter filter) {
        return query("select min(t.createdAt), max(t.createdAt) from Ticket t" + where(filter), Object[].class, filter)
                .getSingleResult();
    }

    private static final String VALID_RESOLUTION =
            " and t.status in :resolved and t.resolvedAt is not null and t.resolvedAt >= t.createdAt";

    private static String where(CohortFilter filter) {
        StringBuilder clause = new StringBuilder(" where 1 = 1");
        if (filter.fromInclusive() != null) clause.append(" and t.createdAt >= :from");
        if (filter.toExclusive() != null) clause.append(" and t.createdAt < :to");
        if (filter.categoryId() != null) clause.append(" and t.category.id = :categoryId");
        if (filter.priority() != null) clause.append(" and t.priority = :priority");
        if (filter.applicantUsername() != null) clause.append(" and t.applicant.username = :applicant");
        if (filter.technicianUsername() != null) clause.append(" and t.technician.username = :technician");
        return clause.toString();
    }

    private <T> TypedQuery<T> query(String hql, Class<T> type, CohortFilter filter) {
        TypedQuery<T> query = entityManager.createQuery(hql, type);
        if (filter.fromInclusive() != null) query.setParameter("from", filter.fromInclusive());
        if (filter.toExclusive() != null) query.setParameter("to", filter.toExclusive());
        if (filter.categoryId() != null) query.setParameter("categoryId", filter.categoryId());
        if (filter.priority() != null) query.setParameter("priority", filter.priority());
        if (filter.applicantUsername() != null) query.setParameter("applicant", filter.applicantUsername());
        if (filter.technicianUsername() != null) query.setParameter("technician", filter.technicianUsername());
        return query;
    }
}
