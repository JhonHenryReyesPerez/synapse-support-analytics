#!/bin/sh
# Importa los flujos versionados, los publica y arranca n8n.
# Se ejecuta en cada inicio del contenedor: los flujos del repositorio reemplazan a los guardados.
set -e

for file in /workflows/*.json; do
  n8n import:workflow --input="$file"
done

n8n publish:workflow --id=campusdeskIndex01
n8n publish:workflow --id=campusdeskAssist1
n8n publish:workflow --id=synapseKnowledgeV2

exec n8n start
