# Analítica: definiciones y SQL de verificación

Endpoint: `GET /api/analytics/summary` (módulo `modules/dashboard`). Devuelve los siete indicadores
de RF06/RF07 sobre **una misma cohorte**. Todos los valores salen de consultas agregadas a
PostgreSQL; no hay caché ni intervención de un modelo de IA.

## Convenciones

| Aspecto | Definición |
|---|---|
| Cohorte | Tickets cuyo `created_at` cae en el rango, con la categoría y prioridad indicadas y dentro del alcance del usuario |
| Rango | `from` inclusivo desde las 00:00; `to` incluye el día completo (`created_at < to + 1 día`) |
| Alcance | ADMIN: todos. USER: `applicant_username = usuario`. TECHNICIAN: `technician_username = usuario` |
| Zona horaria | Las fechas se guardan como hora local de `America/Bogota` (`APP_TIME_ZONE`); días y semanas se agrupan en esa zona |
| Semana | Comienza el lunes; las semanas y días sin incidentes aparecen con cero |
| Resuelto | Estado `RESOLVED` o `CLOSED` |
| Tiempo de resolución | `resolved_at − created_at` en horas transcurridas (no laborables); el tiempo que tarda el usuario en confirmar el cierre no cuenta |
| Datos incompletos | Un ticket resuelto sin `resolved_at`, o con `resolved_at < created_at`, se excluye del promedio y se reporta en `excludedCount` |
| Cohorte vacía | Conteos y porcentajes en 0; promedio `null` con `sampleSize` 0 |
| Redondeo | La API no redondea; al presentar, los porcentajes redondeados pueden no sumar 100 |
| Estado | Los valores reflejan el estado **actual** de cada ticket, no una reconstrucción histórica por semana |

## SQL equivalente

Los parámetros `:from`, `:to_exclusive`, `:category_id` y `:priority` son opcionales: quitar la
condición correspondiente cuando no se filtra. Para el alcance personal, añadir
`AND applicant_username = :usuario` o `AND technician_username = :usuario`.

Cláusula de cohorte usada en todas las consultas:

```sql
-- <cohorte>
WHERE t.created_at >= :from
  AND t.created_at <  :to_exclusive
  AND t.category_id = :category_id
  AND t.priority    = :priority
```

### 1. Total de incidentes y 4. Tasa de resolución

```sql
SELECT count(*)                                                        AS total,
       count(*) FILTER (WHERE t.status IN ('RESOLVED', 'CLOSED'))       AS resueltos,
       CASE WHEN count(*) = 0 THEN 0
            ELSE 100.0 * count(*) FILTER (WHERE t.status IN ('RESOLVED', 'CLOSED')) / count(*)
       END                                                             AS tasa_resolucion
FROM tickets t
-- <cohorte>
;
```

### 2. Incidentes por categoría y 6. Categoría más recurrente

```sql
SELECT c.id, c.name, count(*) AS incidentes,
       100.0 * count(*) / sum(count(*)) OVER () AS porcentaje
FROM tickets t
JOIN categories c ON c.id = t.category_id
-- <cohorte>
GROUP BY c.id, c.name
ORDER BY incidentes DESC, c.name;
```

La categoría más recurrente es la primera fila; si varias comparten el máximo, todas se reportan:

```sql
WITH por_categoria AS (
    SELECT c.id, c.name, count(*) AS incidentes
    FROM tickets t JOIN categories c ON c.id = t.category_id
    -- <cohorte>
    GROUP BY c.id, c.name
)
SELECT * FROM por_categoria WHERE incidentes = (SELECT max(incidentes) FROM por_categoria);
```

### 3. Tendencia temporal

```sql
-- Diaria
SELECT t.created_at::date AS dia, count(*) AS incidentes
FROM tickets t
-- <cohorte>
GROUP BY dia ORDER BY dia;

-- Semanal (date_trunc('week') inicia en lunes)
SELECT date_trunc('week', t.created_at)::date AS semana, count(*) AS incidentes
FROM tickets t
-- <cohorte>
GROUP BY semana ORDER BY semana;
```

Estas consultas solo devuelven períodos con incidentes; la API rellena con cero los intermedios
(equivale a un `LEFT JOIN` contra `generate_series`).

### 5. Tiempo promedio de resolución

```sql
-- Total
SELECT count(*) AS muestra,
       avg(extract(epoch FROM (t.resolved_at - t.created_at))) / 3600.0 AS horas_promedio
FROM tickets t
-- <cohorte>
  AND t.status IN ('RESOLVED', 'CLOSED')
  AND t.resolved_at IS NOT NULL
  AND t.resolved_at >= t.created_at;

-- Por categoría
SELECT c.id, c.name, count(*) AS muestra,
       avg(extract(epoch FROM (t.resolved_at - t.created_at))) / 3600.0 AS horas_promedio
FROM tickets t JOIN categories c ON c.id = t.category_id
-- <cohorte>
  AND t.status IN ('RESOLVED', 'CLOSED')
  AND t.resolved_at IS NOT NULL
  AND t.resolved_at >= t.created_at
GROUP BY c.id, c.name ORDER BY c.name;

-- Registros excluidos por mala calidad de datos
SELECT count(*) AS excluidos
FROM tickets t
-- <cohorte>
  AND t.status IN ('RESOLVED', 'CLOSED')
  AND (t.resolved_at IS NULL OR t.resolved_at < t.created_at);
```

### 7. Incidentes por prioridad

```sql
SELECT p.priority, count(t.id) AS incidentes
FROM (VALUES ('BAJA'), ('MEDIA'), ('ALTA'), ('CRITICA')) AS p(priority)
LEFT JOIN tickets t ON t.priority = p.priority
    -- las condiciones de <cohorte> van aquí, en el ON, para conservar las prioridades en cero
GROUP BY p.priority;
```

## Verificación automatizada

`AnalyticsTests` construye cohortes pequeñas con valores calculados a mano (incluyendo límites de
día y semana, empates, cohorte vacía, `CLOSED` y un resuelto sin fecha) y compara la API con
conteos SQL directos. Se ejecuta en H2 y, con `backend/scripts/test-postgres.sh`, en PostgreSQL.
