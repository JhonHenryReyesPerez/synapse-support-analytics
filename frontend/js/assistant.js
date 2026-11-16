/**
 * Synapse Support Analytics - Módulo del Asistente RAG y Calificación
 */
const ASSISTANT = {
  currentTicketId: null,
  activeQueryId: null,
  historyById: {},

  async ask(ticketId, customQuestion = '') {
    this.currentTicketId = ticketId;
    const container = document.getElementById('assistant-result-container');
    const btnSubmit = document.getElementById('btn-ask-assistant');

    if (!ticketId) {
      UI.showToast('No hay un incidente seleccionado para consultar.', 'warning');
      return;
    }

    // Generar UUID v4 para idempotencia (requestId)
    const requestId = this.generateUuid();

    const payload = {
      requestId
    };
    if (customQuestion && customQuestion.trim()) {
      payload.question = customQuestion.trim();
    }

    if (container) {
      container.innerHTML = `
        <div style="text-align: center; padding: 2.5rem 1rem;">
          <span class="spinner" style="width: 2rem; height: 2rem; color: var(--color-accent);"></span>
          <div style="font-weight: var(--font-weight-semibold); margin-top: 1rem; color: var(--text-main);">
            Consultando base de conocimiento mediante RAG...
          </div>
          <div style="font-size: var(--font-size-xs); color: var(--text-muted); margin-top: 0.25rem;">
            Generando embeddings, buscando en Qdrant y sintetizando la respuesta fundamentada.
          </div>
        </div>
      `;
    }
    UI.setLoading(btnSubmit, true, 'Consultando Asistente...');

    const res = await API.post(`/tickets/${ticketId}/assistance`, payload);
    UI.setLoading(btnSubmit, false);

    if (!res.ok) {
      if (container) {
        container.innerHTML = `
          <div class="alert-no-results">
            <strong>No fue posible completar la consulta:</strong><br/>
            ${UI.escapeHtml(res.error || 'Ocurrió un error en la orquestación.')}
          </div>
        `;
      }
      return;
    }

    this.renderResponse(res.data);
    await this.loadHistory(ticketId);
  },

  renderResponse(query) {
    const container = document.getElementById('assistant-result-container');
    if (!container) return;

    this.activeQueryId = query.id;

    // Caso 1: sin respuesta (NO_RESULTS, ERROR o PENDING): solo el mensaje del backend, sin calificación
    if (query.status !== 'ANSWERED') {
      container.innerHTML = `
        <div class="alert-no-results">
          <div style="font-weight: var(--font-weight-semibold); margin-bottom: 0.25rem;">
            🔍 ${UI.escapeHtml(query.statusLabel || 'Sin resultados')}
          </div>
          <p>${UI.escapeHtml(query.message || 'No se encontraron artículos suficientemente relevantes en la base de conocimiento para este incidente.')}</p>
        </div>
      `;
      return;
    }

    // Caso 2: Respondida con fuentes (ANSWERED)
    let limitedNotice = '';
    if (query.limitedEvidence) {
      limitedNotice = `
        <div class="alert-limited-evidence">
          <span>⚠️</span>
          <span><strong>Evidencia limitada:</strong> Menos de tres artículos superaron el umbral de similitud configurado.</span>
        </div>
      `;
    }

    // Pasos recomendados
    let stepsHtml = '';
    if (Array.isArray(query.recommendedSteps) && query.recommendedSteps.length > 0) {
      stepsHtml = `
        <div style="font-weight: var(--font-weight-semibold); margin-bottom: 0.5rem; font-size: var(--font-size-sm);">
          Pasos de resolución recomendados:
        </div>
        <ol class="steps-list">
          ${query.recommendedSteps.map(step => `<li>${UI.escapeHtml(step)}</li>`).join('')}
        </ol>
      `;
    }

    // Fuentes citadas
    let sourcesHtml = '';
    if (Array.isArray(query.sources) && query.sources.length > 0) {
      sourcesHtml = `
        <div style="font-weight: var(--font-weight-semibold); margin-top: 1rem; font-size: var(--font-size-sm); color: var(--text-main);">
          Fuentes consultadas y fundamentación:
        </div>
        <div class="sources-container">
          ${query.sources.map((src, index) => {
            const simScore = (typeof src.similarity === 'number') ? src.similarity.toFixed(4) : '—';
            return `
              <div class="source-card">
                <div class="source-header">
                  <div class="source-title">
                    [Fuente ${index + 1}] ${UI.escapeHtml(src.title)}
                  </div>
                  <span class="similarity-badge" title="Métrica de similitud coseno (no es probabilidad)">
                    cos: ${simScore}
                  </span>
                </div>
                ${src.evidence ? `
                  <button type="button" class="source-evidence-toggle" onclick="ASSISTANT.toggleEvidence('ev-${query.id}-${index}')">
                    Ver fragmento de evidencia utilizada ▼
                  </button>
                  <div id="ev-${query.id}-${index}" class="source-evidence-box">
                    ${UI.escapeHtml(src.evidence)}
                  </div>
                ` : ''}
              </div>
            `;
          }).join('')}
        </div>
      `;
    }

    // Sección de Calificación
    const ratingHtml = this.renderRatingSection(query);

    container.innerHTML = `
      <div class="assistant-response-box">
        ${limitedNotice}
        <div class="assistant-answer-text">
          ${UI.escapeHtml(query.answer || '')}
        </div>
        ${stepsHtml}
        ${sourcesHtml}
        ${ratingHtml}
      </div>
    `;
  },

  renderRatingSection(query) {
    const existingScore = query.rating ? query.rating.score : 0;
    const existingComment = query.rating ? (query.rating.comment || '') : '';
    const isRated = existingScore > 0;

    let starsHtml = '';
    for (let s = 1; s <= 5; s++) {
      starsHtml += `
        <button type="button" class="star-btn ${s <= existingScore ? 'is-active' : ''}" 
          onclick="ASSISTANT.handleRate(${query.id}, ${s})" title="${s} estrellas">
          ★
        </button>
      `;
    }

    return `
      <div class="rating-section" id="rating-section-${query.id}">
        <div style="font-weight: var(--font-weight-semibold); font-size: var(--font-size-xs); color: var(--text-main);">
          ¿Te fue útil esta orientación técnica? ${isRated ? '<span class="badge badge-resolved">Calificada</span>' : ''}
        </div>
        <div class="rating-stars">
          ${starsHtml}
        </div>
        ${isRated && existingComment ? `
          <div style="font-size: var(--font-size-xs); color: var(--text-muted); font-style: italic;">
            "${UI.escapeHtml(existingComment)}"
          </div>
        ` : ''}
      </div>
    `;
  },

  async handleRate(queryId, score) {
    const comment = prompt('Comentario opcional sobre la utilidad de la respuesta:');
    if (comment === null) return; // Cancelar no califica

    const res = await API.put(`/assistance/queries/${queryId}/rating`, {
      score: parseInt(score, 10),
      comment: comment.trim() || undefined
    });

    if (!res.ok) {
      UI.showToast(res.error || 'No fue posible registrar la calificación.', 'error');
      return;
    }

    UI.showToast(`Calificación de ${score}/5 guardada exitosamente.`, 'success');
    if (this.currentTicketId) {
      await this.loadHistory(this.currentTicketId);
    }
  },

  toggleEvidence(elementId) {
    const el = document.getElementById(elementId);
    if (el) {
      el.classList.toggle('is-open');
    }
  },

  async loadHistory(ticketId) {
    const historyList = document.getElementById('assistant-history-list');
    if (!historyList) return;

    const res = await API.get(`/tickets/${ticketId}/assistance`);
    if (!res.ok) {
      historyList.innerHTML = `<span style="font-size: var(--font-size-xs); color: var(--text-subtle);">Sin consultas registradas.</span>`;
      return;
    }

    const queries = res.data?.content || res.data || [];
    if (!Array.isArray(queries) || queries.length === 0) {
      historyList.innerHTML = `<span style="font-size: var(--font-size-xs); color: var(--text-subtle);">Aún no se han realizado consultas RAG en este incidente.</span>`;
      return;
    }

    // El enlace solo lleva el id: meter la consulta en el onclick permitía ejecutar código desde la pregunta.
    this.historyById = Object.fromEntries(queries.map(q => [q.id, q]));
    historyList.innerHTML = queries.map(q => {
      const statusClass = q.status === 'ANSWERED' ? 'badge-resolved' : (q.status === 'NO_RESULTS' ? 'badge-assigned' : 'badge-closed');
      const scoreBadge = q.rating ? `<span class="badge badge-priority-media">★ ${q.rating.score}/5</span>` : '';

      return `
        <div style="padding: 0.5rem 0; border-bottom: 1px solid var(--border-color-subtle);">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px;">
            <span class="badge ${statusClass}">${UI.escapeHtml(q.statusLabel || q.status)}</span>
            ${scoreBadge}
            <span style="font-size: 0.7rem; color: var(--text-subtle);">${UI.formatDate(q.createdAt)}</span>
          </div>
          <div style="font-size: var(--font-size-xs); font-weight: var(--font-weight-medium); cursor: pointer; color: var(--color-accent);"
            onclick="ASSISTANT.renderResponse(ASSISTANT.historyById[${q.id}])">
            "${UI.escapeHtml(q.question || 'Consulta general del incidente')}"
          </div>
        </div>
      `;
    }).join('');
  },

  generateUuid() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
      const r = Math.random() * 16 | 0, v = c === 'x' ? r : (r & 0x3 | 0x8);
      return v.toString(16);
    });
  }
};

window.ASSISTANT = ASSISTANT;
