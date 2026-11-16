#!/bin/sh
set -e

QDRANT_HOST="${QDRANT_HOST:-qdrant}"
QDRANT_PORT="${QDRANT_PORT:-6333}"
COLLECTION_NAME="articulos_soporte"

echo "Esperando a que Qdrant esté disponible en http://${QDRANT_HOST}:${QDRANT_PORT}..."
until curl -sf "http://${QDRANT_HOST}:${QDRANT_PORT}/readyz" >/dev/null 2>&1 || curl -sf "http://${QDRANT_HOST}:${QDRANT_PORT}/healthz" >/dev/null 2>&1; do
  echo "Qdrant no está listo aún. Reintentando en 1 segundo..."
  sleep 1
done

echo "Qdrant está listo. Verificando colección '${COLLECTION_NAME}'..."
STATUS_CODE=$(curl -s -o /dev/null -w "%{http_code}" "http://${QDRANT_HOST}:${QDRANT_PORT}/collections/${COLLECTION_NAME}")

if [ "$STATUS_CODE" -eq 404 ]; then
  echo "Creando colección '${COLLECTION_NAME}' (1536 dimensiones, distancia Coseno)..."
  curl -s -X PUT "http://${QDRANT_HOST}:${QDRANT_PORT}/collections/${COLLECTION_NAME}" \
    -H "Content-Type: application/json" \
    -d @/qdrant/collection_config.json
  echo ""
  echo "Colección '${COLLECTION_NAME}' creada exitosamente."

  echo "Configurando índices de payload en Qdrant..."
  curl -s -X PUT "http://${QDRANT_HOST}:${QDRANT_PORT}/collections/${COLLECTION_NAME}/index" \
    -H "Content-Type: application/json" \
    -d '{"field_name": "category", "field_schema": "keyword"}'
  echo ""
  curl -s -X PUT "http://${QDRANT_HOST}:${QDRANT_PORT}/collections/${COLLECTION_NAME}/index" \
    -H "Content-Type: application/json" \
    -d '{"field_name": "categoria", "field_schema": "keyword"}'
  echo ""
  curl -s -X PUT "http://${QDRANT_HOST}:${QDRANT_PORT}/collections/${COLLECTION_NAME}/index" \
    -H "Content-Type: application/json" \
    -d '{"field_name": "estado", "field_schema": "keyword"}'
  echo ""
  curl -s -X PUT "http://${QDRANT_HOST}:${QDRANT_PORT}/collections/${COLLECTION_NAME}/index" \
    -H "Content-Type: application/json" \
    -d '{"field_name": "articleId", "field_schema": "integer"}'
  echo ""
  echo "Índices de payload creados exitosamente."
else
  echo "La colección '${COLLECTION_NAME}' ya existe (HTTP $STATUS_CODE). No requiere inicialización."
fi

echo "Inicialización de Qdrant completada con éxito."
exit 0
