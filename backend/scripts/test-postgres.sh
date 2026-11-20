#!/usr/bin/env bash
# Ejecuta la suite contra un PostgreSQL desechable con las migraciones reales de Flyway
# y Hibernate en modo validate. Uso: backend/scripts/test-postgres.sh [argumentos de gradle test]
set -euo pipefail
cd "$(dirname "$0")/.."

NAME=campusdesk-test-pg
PORT="${TEST_PG_PORT:-55432}"

if ! docker ps --format '{{.Names}}' | grep -qx "$NAME"; then
  docker rm -f "$NAME" >/dev/null 2>&1 || true
  docker run -d --name "$NAME" -e POSTGRES_PASSWORD=test -e POSTGRES_DB=postgres \
    -p "127.0.0.1:${PORT}:5432" postgres:17-alpine >/dev/null
fi
until docker exec "$NAME" pg_isready -q -U postgres; do sleep 1; done

# Base vacía en cada ejecución: prueba las migraciones desde cero.
docker exec "$NAME" psql -U postgres -q -c "DROP DATABASE IF EXISTS campusdesk_test" -c "CREATE DATABASE campusdesk_test"
# Base aparte para DemoDataSeedTests: carga db/seed/demo_data.sql sin mezclarse con las demás pruebas.
docker exec "$NAME" psql -U postgres -q -c "DROP DATABASE IF EXISTS campusdesk_seed" -c "CREATE DATABASE campusdesk_seed"

TEST_DB_URL="jdbc:postgresql://localhost:${PORT}/campusdesk_test" TEST_SEED_DB_URL="jdbc:postgresql://localhost:${PORT}/campusdesk_seed" \
TEST_DB_DRIVER=org.postgresql.Driver \
TEST_DB_USERNAME=postgres \
TEST_DB_PASSWORD=test \
TEST_FLYWAY=true \
TEST_DB_DDL=validate \
./gradlew test --rerun-tasks "$@"
