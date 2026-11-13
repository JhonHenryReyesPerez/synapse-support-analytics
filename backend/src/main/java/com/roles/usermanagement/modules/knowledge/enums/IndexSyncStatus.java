package com.roles.usermanagement.modules.knowledge.enums;

/**
 * Estado de sincronización del artículo con el índice vectorial (Qdrant), independiente del
 * estado editorial (active/verified). La base relacional es la fuente de verdad: el índice
 * es derivado y la elegibilidad se revalida en cada consulta.
 */
public enum IndexSyncStatus {
    /** Nunca indexado o ya retirado del índice; no hay nada pendiente. */
    NOT_INDEXED,
    /** El índice no refleja el estado actual: falta indexar la versión vigente o retirar la anterior. */
    PENDING,
    /** El índice contiene la versión vigente del artículo elegible. */
    INDEXED,
    /** El último intento de sincronización falló; el artículo relacional no se altera. */
    ERROR
}
