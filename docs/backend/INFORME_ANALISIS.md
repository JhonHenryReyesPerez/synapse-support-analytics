# Informe de análisis de datos

Indicadores calculados sobre el conjunto de datos iniciales, consultas SQL utilizadas,
interpretación y conclusiones.

## 1. Alcance y advertencia sobre los datos

| Aspecto | Valor |
|---|---|
| Fuente | PostgreSQL 17, tabla `tickets` y catálogo `categories` |
| Conjunto | Datos **sintéticos** de `backend/src/main/resources/db/seed/demo_data.sql` |
| Tamaño | 180 incidentes creados entre el 2026-05-25 y el 2026-11-02 |
| Fecha de corte | 2026-11-09 08:00 (America/Bogota); ningún evento es posterior |
| Reproducibilidad | El script es determinista: cada carga produce los mismos registros y las mismas cifras |
| Cálculo | SQL agregado. Ningún modelo de IA calcula, estima ni corrige estos valores |

Los datos son sintéticos y fueron diseñados con dos patrones deliberados: un crecimiento gradual
del volumen y una caída de conectividad en la semana del 14 de septiembre. Las conclusiones de este
informe describen ese conjunto y demuestran el método de análisis; **no son hallazgos sobre una
organización real**. Con datos reales, las mismas consultas y el mismo endpoint producen el informe equivalente.

Las cifras de este documento se obtuvieron ejecutando las consultas de la sección 4 con `psql`
sobre la base cargada. La prueba `DemoDataSeedTests` comprueba, sobre ese mismo conjunto, que
`GET /api/analytics/summary` devuelve exactamente los valores de SQL independiente para los siete indicadores.

## 2. Definiciones

Las definiciones completas están en [`ANALITICA_SQL.md`](ANALITICA_SQL.md). Las que importan para leer este informe:

- **Cohorte:** incidentes por fecha de creación. Aquí, todos (sin filtros).
- **Resuelto:** estado `RESOLVED` o `CLOSED`.
- **Tiempo de resolución:** `resolved_at − created_at` en horas transcurridas, no laborables. El tiempo que tarda el usuario en confirmar el cierre no cuenta.
- **Semana:** comienza el lunes.
- **Estado actual:** los valores reflejan el estado de cada incidente en la fecha de corte, no una reconstrucción histórica.

## 3. Indicadores

### 3.1 Total de incidentes y tasa de resolución (indicadores 1 y 4)

| Total | Resueltos | Tasa de resolución |
|---:|---:|---:|
| 180 | 156 | 86,67 % |

| Estado | Incidentes |
|---|---:|
| CLOSED | 138 |
| RESOLVED | 18 |
| OPEN | 10 |
| ASSIGNED | 8 |
| IN_PROGRESS | 6 |

Hay 24 incidentes sin resolver (13,33 %): 10 sin asignar, 8 asignados sin iniciar y 6 en atención.
Otros 18 están resueltos pero el solicitante no ha confirmado el cierre.

### 3.2 Incidentes por categoría y categoría más recurrente (indicadores 2 y 6)

| Categoría | Incidentes | % del total | Tiempo promedio (h) | Muestra |
|---|---:|---:|---:|---:|
| Conectividad | 55 | 30,56 | 9,31 | 45 |
| Hardware | 31 | 17,22 | 33,64 | 29 |
| Accesos | 30 | 16,67 | 8,44 | 27 |
| Software | 26 | 14,44 | 15,61 | 23 |
| Aplicaciones | 24 | 13,33 | 20,58 | 20 |
| Servicios informáticos | 9 | 5,00 | 11,23 | 7 |
| Otros | 5 | 2,78 | 11,21 | 5 |

**Categoría más recurrente: Conectividad**, con 55 incidentes. «Muestra» es el número de incidentes
resueltos de la categoría con los que se calculó el promedio.

### 3.3 Tendencia temporal (indicador 3)

| Mes | Incidentes |
|---|---:|
| 2026-05 | 2 |
| 2026-06 | 14 |
| 2026-07 | 18 |
| 2026-08 | 30 |
| 2026-09 | 56 |
| 2026-10 | 58 |
| 2026-11 (hasta el día 2) | 2 |

| Semana (lunes) | Incidentes | De ellos, Conectividad |
|---|---:|---:|
| 2026-05-25 | 2 | 1 |
| 2026-06-01 | 1 | 0 |
| 2026-06-08 | 3 | 1 |
| 2026-06-15 | 4 | 0 |
| 2026-06-22 | 4 | 1 |
| 2026-06-29 | 5 | 3 |
| 2026-07-06 | 1 | 1 |
| 2026-07-13 | 4 | 0 |
| 2026-07-20 | 6 | 0 |
| 2026-07-27 | 4 | 0 |
| 2026-08-03 | 7 | 2 |
| 2026-08-10 | 3 | 0 |
| 2026-08-17 | 12 | 0 |
| 2026-08-24 | 6 | 0 |
| 2026-08-31 | 4 | 3 |
| 2026-09-07 | 7 | 1 |
| **2026-09-14** | **33** | **26** |
| 2026-09-21 | 8 | 1 |
| 2026-09-28 | 10 | 2 |
| 2026-10-05 | 12 | 2 |
| 2026-10-12 | 11 | 5 |
| 2026-10-19 | 16 | 2 |
| 2026-10-26 | 15 | 4 |
| 2026-11-02 | 2 | 0 |

La semana del 2 de noviembre solo tiene un día con incidentes (el conjunto termina ese lunes): no
debe leerse como una caída del volumen.

### 3.4 Tiempo promedio de resolución (indicador 5)

| Medida | Valor |
|---|---:|
| Promedio | 16,20 h |
| Mediana | 11,72 h |
| Máximo | 66,62 h |
| Incidentes en la muestra | 156 |
| Excluidos por datos incompletos o incoherentes | 0 |

La mediana es menor que el promedio: unos pocos incidentes largos, sobre todo de Hardware, elevan la media.

### 3.5 Incidentes por prioridad (indicador 7)

| Prioridad | Incidentes | % del total | Resueltos | Tasa de resolución | Tiempo promedio (h) |
|---|---:|---:|---:|---:|---:|
| BAJA | 48 | 26,67 | 39 | 81,25 % | 27,57 |
| MEDIA | 58 | 32,22 | 49 | 84,48 % | 15,19 |
| ALTA | 49 | 27,22 | 46 | 93,88 % | 12,21 |
| CRITICA | 25 | 13,89 | 22 | 88,00 % | 6,67 |

### 3.6 Datos complementarios

No forman parte de los siete indicadores obligatorios; apoyan la interpretación.

| Técnico | Asignados | Activos (ASSIGNED o IN_PROGRESS) | Tiempo promedio (h) |
|---|---:|---:|---:|
| tec.software | 52 | 2 | 13,12 |
| tec.redes | 49 | 6 | 8,66 |
| tec.aplicaciones | 40 | 5 | 15,82 |
| tec.hardware | 29 | 1 | 33,77 |

| Conectividad | Incidentes | Resueltos | Tiempo promedio (h) |
|---|---:|---:|---:|
| Semana del 14 de septiembre | 26 | 24 | 7,98 |
| Resto del período | 29 | — | 10,84 |

De 170 asignaciones iniciales, 10 terminaron en reasignación (5,9 %). El promedio semanal de
incidentes, sin contar la semana del 14 de septiembre, es de 6,39.

## 4. Consultas SQL utilizadas

Son las de [`ANALITICA_SQL.md`](ANALITICA_SQL.md) sin filtros de cohorte, más las complementarias.

```sql
-- 3.1 Total, resueltos y tasa de resolución
SELECT count(*) AS total,
       count(*) FILTER (WHERE status IN ('RESOLVED', 'CLOSED')) AS resueltos,
       round(100.0 * count(*) FILTER (WHERE status IN ('RESOLVED', 'CLOSED')) / count(*), 2) AS tasa
FROM tickets;

SELECT status, count(*) FROM tickets GROUP BY status ORDER BY 2 DESC;

-- 3.2 Por categoría, porcentaje, tiempo promedio y muestra
SELECT c.name, count(*) AS incidentes,
       round(100.0 * count(*) / sum(count(*)) OVER (), 2) AS porcentaje,
       round((avg(extract(epoch FROM (t.resolved_at - t.created_at)))
              FILTER (WHERE t.resolved_at IS NOT NULL) / 3600.0)::numeric, 2) AS horas,
       count(*) FILTER (WHERE t.resolved_at IS NOT NULL) AS muestra
FROM tickets t JOIN categories c ON c.id = t.category_id
GROUP BY c.name ORDER BY incidentes DESC, c.name;

-- 3.3 Tendencia mensual y semanal (date_trunc('week') inicia en lunes)
SELECT to_char(date_trunc('month', created_at), 'YYYY-MM') AS mes, count(*) FROM tickets GROUP BY 1 ORDER BY 1;

SELECT date_trunc('week', created_at)::date AS semana, count(*) AS incidentes,
       count(*) FILTER (WHERE category_id = (SELECT id FROM categories WHERE name = 'Conectividad')) AS conectividad
FROM tickets GROUP BY 1 ORDER BY 1;

-- 3.4 Tiempo de resolución: promedio, mediana y máximo
SELECT count(*) AS muestra,
       round((avg(extract(epoch FROM (resolved_at - created_at))) / 3600.0)::numeric, 2) AS promedio,
       round((percentile_cont(0.5) WITHIN GROUP (ORDER BY extract(epoch FROM (resolved_at - created_at)) / 3600.0))::numeric, 2) AS mediana,
       round(max(extract(epoch FROM (resolved_at - created_at)) / 3600.0)::numeric, 2) AS maximo
FROM tickets
WHERE status IN ('RESOLVED', 'CLOSED') AND resolved_at IS NOT NULL AND resolved_at >= created_at;

-- 3.5 Por prioridad
SELECT priority, count(*) AS incidentes,
       round(100.0 * count(*) / sum(count(*)) OVER (), 2) AS porcentaje,
       count(*) FILTER (WHERE status IN ('RESOLVED', 'CLOSED')) AS resueltos,
       round((avg(extract(epoch FROM (resolved_at - created_at)))
              FILTER (WHERE resolved_at IS NOT NULL) / 3600.0)::numeric, 2) AS horas
FROM tickets GROUP BY priority;

-- 3.6 Carga y tiempo por técnico
SELECT technician_username, count(*) AS asignados,
       count(*) FILTER (WHERE status IN ('ASSIGNED', 'IN_PROGRESS')) AS activos,
       round((avg(extract(epoch FROM (resolved_at - created_at)))
              FILTER (WHERE resolved_at IS NOT NULL) / 3600.0)::numeric, 2) AS horas
FROM tickets WHERE technician_username IS NOT NULL GROUP BY 1 ORDER BY 2 DESC;

-- 3.6 Conectividad en la semana del pico frente al resto
SELECT (created_at >= '2026-09-14' AND created_at < '2026-09-21') AS semana_del_pico, count(*) AS incidentes,
       round((avg(extract(epoch FROM (resolved_at - created_at)))
              FILTER (WHERE resolved_at IS NOT NULL) / 3600.0)::numeric, 2) AS horas
FROM tickets WHERE category_id = (SELECT id FROM categories WHERE name = 'Conectividad') GROUP BY 1;

-- 3.6 Reasignaciones
SELECT assignment_type, count(*) FROM ticket_assignment_logs GROUP BY 1;
```

Los mismos indicadores por la API (token de un ADMIN):

```
GET /api/analytics/summary?granularity=WEEK
GET /api/analytics/summary?from=2026-09-14&to=2026-09-20&categoryId=<id de Conectividad>
```

## 5. Interpretación

**Volumen.** El número de incidentes crece mes a mes: de 14 en junio a 58 en octubre. Sin contar la
semana del 14 de septiembre, las cuatro semanas completas de octubre registran entre 11 y 16
incidentes, frente a entre 1 y 5 en junio.

**Concentración.** Conectividad reúne el 30,56 % de los incidentes, casi el doble que la segunda
categoría (Hardware, 17,22 %). Sin embargo, 26 de sus 55 incidentes pertenecen a una sola semana.

**Tiempos.** El promedio general de 16,20 h esconde diferencias grandes: Hardware tarda 33,64 h y
Accesos 8,44 h, cuatro veces menos. Por prioridad el orden es el esperado: los críticos se
resuelven en 6,67 h y los de prioridad baja en 27,57 h.

**Pendientes.** Los incidentes de prioridad baja y media tienen las tasas de resolución más bajas
(81,25 % y 84,48 %), coherente con que se atienden después.

## 6. Conclusiones

1. **Conectividad es la categoría más recurrente, pero casi la mitad de su volumen proviene de un único evento.**
   Evidencia: 55 de 180 incidentes (30,56 %); 26 de ellos en la semana del 14 de septiembre, que
   sumó 33 incidentes frente a un promedio de 6,39 en las demás semanas. Sin ese evento,
   Conectividad tendría 29 incidentes, por debajo de Hardware (31) y de Accesos (30). Una caída
   general se atiende mejor con un incidente principal y comunicación a los usuarios que con decenas
   de casos individuales; el artículo «Sin internet en toda una sede» documenta ese procedimiento.

2. **Hardware es el cuello de botella en tiempo de resolución.**
   Evidencia: 33,64 h de promedio con 29 incidentes resueltos, frente a 16,20 h del total; es el
   doble del promedio general y cuatro veces el de Accesos (8,44 h). El técnico de hardware tiene la
   menor carga (29 asignados) y el mayor tiempo (33,77 h), de modo que la demora no se explica por
   volumen sino por el tipo de trabajo (reemplazo de partes, pruebas de equipo).

3. **La prioridad sí ordena la atención.**
   Evidencia: el tiempo promedio baja de forma monótona con la prioridad: 27,57 h (BAJA), 15,19 h
   (MEDIA), 12,21 h (ALTA) y 6,67 h (CRITICA). Los incidentes de prioridad alta tienen además la
   mayor tasa de resolución (93,88 %).

4. **La demanda crece más rápido que la capacidad visible en los datos.**
   Evidencia: el volumen mensual se multiplicó por 4,1 entre junio (14) y octubre (58) con los
   mismos cuatro técnicos. Hay 24 incidentes sin resolver, 10 de ellos todavía sin asignar. Los
   datos no permiten afirmar que el tiempo de resolución haya empeorado, pero sí que la carga por
   técnico aumenta.

5. **La base de conocimiento cubre las categorías con más demanda.**
   Evidencia: Conectividad, Hardware, Accesos, Software y Aplicaciones suman 166 de los 180
   incidentes (92,2 %) y tienen entre 3 y 7 artículos publicados cada una (agrupados por tema en el
   script de datos; el modelo no asigna categoría a los artículos). Esto indica cobertura
   temática; **no mide** la calidad de las respuestas del asistente, que debe evaluarse con los
   workflows reales y con las calificaciones de los usuarios.

## 7. Limitaciones

- Datos sintéticos: los patrones fueron diseñados, no observados.
- Las horas son transcurridas, sin calendario laboral ni acuerdos de nivel de servicio.
- Los valores reflejan el estado en la fecha de corte; no hay reconstrucción del estado semana a semana.
- No se incluyen consultas al asistente ni calificaciones: el conjunto inicial no fabrica respuestas de IA.
- La semana del 2 de noviembre está incompleta.
