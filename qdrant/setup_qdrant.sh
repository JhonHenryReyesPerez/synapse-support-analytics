#!/bin/sh
# Crea la colección de artículos en Qdrant si todavía no existe. Es idempotente.
set -e

QDRANT_HOST="${QDRANT_HOST:-qdrant}"
QDRANT_PORT="${QDRANT_PORT:-6333}"
COLLECTION_NAME="articulos_soporte"

echo "Esperando a que Qdrant esté disponible en http://${QDRANT_HOST}:${QDRANT_PORT}..."
until curl -sf "http://${QDRANT_HOST}:${QDRANT_PORT}/readyz" >/dev/null 2>&1 || curl -sf "http://${QDRANT_HOST}:${QDRANT_PORT}/healthz" >/dev/null 2>&1; do
  echo "Qdrant no está listo aún. Reintentando en 1 segundo..."
  sleep 1
done

STATUS_CODE=$(curl -s -o /dev/null -w '%{http_code}' "${BASE_URL}/collections/${COLLECTION_NAME}")

if [ "$STATUS_CODE" = "404" ]; then
  echo "Creando la colección '${COLLECTION_NAME}'..."
  curl -sf -X PUT "${BASE_URL}/collections/${COLLECTION_NAME}" \
    -H "Content-Type: application/json" \
    -d @/qdrant/collection_config.json
  echo ""
  # Índice del campo por el que el flujo de indexación borra los puntos de un artículo.
  curl -sf -X PUT "${BASE_URL}/collections/${COLLECTION_NAME}/index" \
    -H "Content-Type: application/json" \
    -d '{"field_name": "articleId", "field_schema": "integer"}'
  echo ""
  echo "Colección creada."
elif [ "$STATUS_CODE" = "200" ]; then
  echo "La colección '${COLLECTION_NAME}' ya existe; no se modifica."
else
  echo "Respuesta inesperada de Qdrant al consultar la colección: HTTP ${STATUS_CODE}." >&2
  exit 1
fi
