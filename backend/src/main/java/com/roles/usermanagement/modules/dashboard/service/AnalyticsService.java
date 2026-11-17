package com.roles.usermanagement.modules.dashboard.service;

import com.roles.usermanagement.modules.category.repository.CategoryRepository;
import com.roles.usermanagement.modules.dashboard.dto.AnalyticsSummaryResponse;
import com.roles.usermanagement.modules.dashboard.dto.AnalyticsSummaryResponse.CategoryCount;
import com.roles.usermanagement.modules.dashboard.dto.AnalyticsSummaryResponse.CategoryResolutionTime;
import com.roles.usermanagement.modules.dashboard.dto.AnalyticsSummaryResponse.Cohort;
import com.roles.usermanagement.modules.dashboard.dto.AnalyticsSummaryResponse.PriorityCount;
import com.roles.usermanagement.modules.dashboard.dto.AnalyticsSummaryResponse.Resolution;
import com.roles.usermanagement.modules.dashboard.dto.AnalyticsSummaryResponse.ResolutionTime;
import com.roles.usermanagement.modules.dashboard.dto.AnalyticsSummaryResponse.Trend;
import com.roles.usermanagement.modules.dashboard.dto.AnalyticsSummaryResponse.TrendPoint;
import com.roles.usermanagement.modules.dashboard.enums.TrendGranularity;
import com.roles.usermanagement.modules.dashboard.repository.AnalyticsQueryRepository;
import com.roles.usermanagement.modules.dashboard.repository.AnalyticsQueryRepository.CohortFilter;
import com.roles.usermanagement.modules.ticket.enums.TicketPriority;
import com.roles.usermanagement.modules.ticket.service.TicketAccessPolicy;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Analítica de incidentes (RF06, RF07). Cada valor proviene de una consulta agregada sobre la base
 * relacional; no hay caché ni estimaciones. El alcance se aplica antes de agregar: ADMIN ve todo,
 * USER sus incidentes y TECHNICIAN los que tiene asignados.
 */
@Service
public class AnalyticsService {

    /** Tope de puntos de la tendencia para acotar el tamaño de la respuesta. */
    static final int MAX_TREND_POINTS = 1100;

    private final AnalyticsQueryRepository queries;
    private final CategoryRepository categories;
    private final TicketAccessPolicy policy;

    public AnalyticsService(AnalyticsQueryRepository queries, CategoryRepository categories, TicketAccessPolicy policy) {
        this.queries = queries;
        this.categories = categories;
        this.policy = policy;
    }

    // REPEATABLE_READ: las consultas del resumen comparten instantánea. Con READ COMMITTED cada una
    // ve lo confirmado hasta ese momento y el total puede no coincidir con sus desgloses.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AnalyticsSummaryResponse summary(LocalDate from, LocalDate to, Long categoryId, TicketPriority priority,
                                            TrendGranularity granularity, Authentication auth) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "to no puede ser anterior a from");
        }
        if (categoryId != null && !categories.existsById(categoryId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La categoría indicada no existe");
        }
        TrendGranularity unit = granularity == null ? TrendGranularity.WEEK : granularity;

        String scope;
        String applicant = null;
        String technician = null;
        if (policy.isAdmin(auth)) {
            scope = "GLOBAL";
        } else if (policy.isTechnician(auth)) {
            scope = "ASSIGNED";
            technician = auth.getName();
        } else {
            scope = "OWN";
            applicant = auth.getName();
        }
        // Inicio inclusivo y fin exclusivo: el día "to" completo entra mediante el inicio del día siguiente.
        CohortFilter filter = new CohortFilter(
                from == null ? null : from.atStartOfDay(),
                to == null ? null : to.plusDays(1).atStartOfDay(),
                categoryId, priority, applicant, technician);

        long[] totals = queries.totalAndResolved(filter);
        long total = totals[0];
        long resolved = totals[1];

        List<CategoryCount> byCategory = queries.countByCategory(filter).stream()
                .map(row -> new CategoryCount((Long) row[0], (String) row[1], count(row[2]), percentage(count(row[2]), total)))
                .toList();
        long top = byCategory.stream().mapToLong(CategoryCount::count).max().orElse(0);
        List<CategoryCount> mostRecurrent = byCategory.stream().filter(item -> top > 0 && item.count() == top).toList();

        return new AnalyticsSummaryResponse(
                new Cohort(scope, from, to, categoryId, priority, TimeZone.getDefault().getID(), LocalDateTime.now()),
                total,
                byCategory,
                new Trend(unit, trend(filter, from, to, unit)),
                new Resolution(resolved, percentage(resolved, total)),
                resolutionTime(filter),
                mostRecurrent,
                byPriority(filter, total));
    }

    private List<PriorityCount> byPriority(CohortFilter filter, long total) {
        Map<TicketPriority, Long> counts = new EnumMap<>(TicketPriority.class);
        queries.countByPriority(filter).forEach(row -> counts.put((TicketPriority) row[0], count(row[1])));
        List<PriorityCount> result = new ArrayList<>();
        for (TicketPriority priority : TicketPriority.values()) {
            long value = counts.getOrDefault(priority, 0L);
            result.add(new PriorityCount(priority, value, percentage(value, total)));
        }
        return result;
    }

    private ResolutionTime resolutionTime(CohortFilter filter) {
        Object[] overall = queries.resolutionTime(filter);
        long sample = count(overall[0]);
        Double averageHours = sample == 0 || overall[1] == null ? null : hours(overall[1]);
        List<CategoryResolutionTime> byCategory = queries.resolutionTimeByCategory(filter).stream()
                .map(row -> new CategoryResolutionTime((Long) row[0], (String) row[1], hours(row[3]), count(row[2])))
                .toList();
        return new ResolutionTime("HOURS", averageHours, sample, queries.excludedFromResolutionTime(filter), byCategory);
    }

    /**
     * Serie continua: los días o semanas sin incidentes aparecen con cero. Sin from/to, los límites
     * son la primera y la última creación de la cohorte. Las semanas comienzan el lunes.
     */
    private List<TrendPoint> trend(CohortFilter filter, LocalDate from, LocalDate to, TrendGranularity unit) {
        Map<LocalDate, Long> daily = new LinkedHashMap<>();
        queries.countByCreationDay(filter).forEach(row -> daily.put(toLocalDate(row[0]), count(row[1])));

        LocalDate start = from;
        LocalDate end = to;
        if (start == null || end == null) {
            Object[] bounds = queries.creationBounds(filter);
            if (bounds[0] == null) {
                return List.of();
            }
            if (start == null) start = ((LocalDateTime) bounds[0]).toLocalDate();
            if (end == null) end = ((LocalDateTime) bounds[1]).toLocalDate();
        }
        if (end.isBefore(start)) {
            return List.of();
        }
        if (unit == TrendGranularity.WEEK) {
            start = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
        long step = unit == TrendGranularity.WEEK ? 7 : 1;
        if (ChronoUnit.DAYS.between(start, end) / step + 1 > MAX_TREND_POINTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El rango genera demasiados períodos; reduce las fechas o usa granularity=WEEK");
        }
        List<TrendPoint> points = new ArrayList<>();
        for (LocalDate period = start; !period.isAfter(end); period = period.plusDays(step)) {
            long value = 0;
            for (int day = 0; day < step; day++) {
                value += daily.getOrDefault(period.plusDays(day), 0L);
            }
            points.add(new TrendPoint(period, value));
        }
        return points;
    }

    private static LocalDate toLocalDate(Object value) {
        return value instanceof java.sql.Date date ? date.toLocalDate() : (LocalDate) value;
    }

    private static long count(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    private static double hours(Object seconds) {
        return ((Number) seconds).doubleValue() / 3600.0;
    }

    /** Convención documentada: con total cero, el porcentaje es cero. */
    private static double percentage(long part, long total) {
        return total == 0 ? 0.0 : 100.0 * part / total;
    }
}
