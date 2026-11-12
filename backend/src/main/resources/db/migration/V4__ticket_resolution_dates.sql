-- B08: fecha de resolución, fecha de cierre y nota de solución.
-- Para los tickets previos, las fechas se derivan del historial de estados (primer evento
-- RESOLVED / CLOSED). Si un ticket resuelto no tiene ese evento queda en NULL: es un dato
-- incompleto y la analítica lo reporta como excluido; no se inventa a partir de updated_at.

ALTER TABLE public.tickets ADD COLUMN resolved_at timestamp(6) without time zone;
ALTER TABLE public.tickets ADD COLUMN closed_at timestamp(6) without time zone;
ALTER TABLE public.tickets ADD COLUMN resolution_note text;

UPDATE public.tickets t
SET resolved_at = h.first_resolved
FROM (
    SELECT ticket_id, min(changed_at) AS first_resolved
    FROM public.ticket_status_history
    WHERE new_status = 'RESOLVED'
    GROUP BY ticket_id
) h
WHERE h.ticket_id = t.id AND t.status IN ('RESOLVED', 'CLOSED');

UPDATE public.tickets t
SET closed_at = h.first_closed
FROM (
    SELECT ticket_id, min(changed_at) AS first_closed
    FROM public.ticket_status_history
    WHERE new_status = 'CLOSED'
    GROUP BY ticket_id
) h
WHERE h.ticket_id = t.id AND t.status = 'CLOSED';
