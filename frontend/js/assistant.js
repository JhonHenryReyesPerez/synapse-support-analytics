/**
 * Synapse Support Analytics - Módulo del Asistente RAG y Calificación
 */
const ASSISTANT = {
  currentTicketId: null,
  activeQueryId: null,
  historyById: {},      // consultas cargadas del incidente actual, por id
  historyIds: [],       // orden en que se muestran (más recientes primero)
  historyPage: 0,
  historyTotalPages: 0,
  askSeq: 0,
  historySeq: 0,
  historyLoading: false,
  // Última consulta sin resultado confirmado o terminada en ERROR por incidente.
  // Repetir la misma pregunta reutiliza su requestId (el backend devuelve la consulta ya creada en
  // vez de generar otra) o, si terminó en ERROR, crea una nueva enlazada con retryOfQueryId.
  unsettledAsks: new Map(),

  /** Fija el incidente activo; al cambiar, descarta respuesta, pregunta e historial del anterior. */
  setTicket(ticketId) {
    if (this.currentTicketId === ticketId) return;
    this.currentTicketId = ticketId;
    this.askSeq++;
    this.historySeq++;
    this.historyLoading = false;
    this.activeQueryId = null;
    this.historyById = {};
    this.historyIds = [];
    this.historyPage = 0;
    this.historyTotalPages = 0;

    const container = document.getElementById('assistant-result-container');
    if (container) container.innerHTML = '';
    const question = document.getElementById('assistant-custom-question');
    if (question) question.value = '';
    const facts=document.getElementById('assistant-facts');if(facts)facts.value='';
    const related=document.getElementById('rag-ticket-related');if(related)related.textContent='';
    const clarification=document.getElementById('rag-clarify-feedback');if(clarification)clarification.textContent='';
    const historyList = document.getElementById('assistant-history-list');
    if (historyList) historyList.innerHTML = '';
    // Una consulta en curso del incidente anterior no debe bloquear el botón del nuevo.
    UI.setLoading(document.getElementById('btn-ask-assistant'), false);
  },

  async ask(ticketId, customQuestion = '') {
    const container = document.getElementById('assistant-result-container');
    const btnSubmit = document.getElementById('btn-ask-assistant');

    if (!ticketId) {
      UI.showToast('No hay un incidente seleccionado para consultar.', 'warning');
      return;
    }

    this.setTicket(ticketId);
    const seq = ++this.askSeq;

    const question = (customQuestion || '').trim();
    const retrievalProfileId=document.getElementById('assistant-profile')?.value||null;
    const stored = this.unsettledAsks.get(ticketId);
    const previous = stored && stored.question === question && stored.retrievalProfileId===retrievalProfileId ? stored : null;
    // UUID v4 para idempotencia: se conserva mientras el resultado del intento anterior sea incierto.
    const attempt = {
      ticketId, question, retrievalProfileId,
      requestId: (previous && previous.requestId) || this.generateUuid(),
      retryOfQueryId: previous ? previous.retryOfQueryId : undefined
    };
    this.unsettledAsks.set(ticketId, attempt);

    const payload = { requestId: attempt.requestId };
    if(retrievalProfileId)payload.retrievalProfileId=retrievalProfileId;
    if (question) payload.question = question;
    if (attempt.retryOfQueryId) payload.retryOfQueryId = attempt.retryOfQueryId;

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
    // Se anota el desenlace aunque ya se muestre otro incidente: decide cómo se repite esta pregunta.
    const uncertain = this.settleAsk(attempt, res);
    if (seq !== this.askSeq) return; // también descarta respuestas al volver de A a B y a A
    UI.setLoading(btnSubmit, false);

    if (!res.ok) {
      if (container) {
        container.innerHTML = `
          <div class="alert-no-results">
            <strong>No fue posible completar la consulta:</strong><br/>
            ${UI.escapeHtml(res.error || 'Ocurrió un error en la orquestación.')}
            ${uncertain ? '<br/>No se pudo confirmar si la consulta llegó a procesarse. Al consultar de nuevo con la misma pregunta se recuperará esa misma consulta, sin generar otra.' : ''}
          </div>
        `;
      }
      // Un fallo técnico deja la consulta registrada como ERROR: que aparezca en el historial.
      if (res.data && res.data.queryId) await this.loadHistory(ticketId);
      return;
    }

    this.historyById[res.data.id] = res.data;
    this.renderResponse(res.data);
    await this.loadHistory(ticketId);
  },

  /**
   * Actualiza el intento del incidente sin alterar los de otros incidentes.
   * @returns {boolean} true si no se sabe si el backend procesó la consulta.
   */
  settleAsk(attempt, res) {
    const failedQueryId = res.ok
      ? (res.data && res.data.status === 'ERROR' ? res.data.id : null)
      : (res.data && res.data.queryId) || null;
    // Sin respuesta, o un 5xx sin queryId, no dicen si la consulta se creó; PENDING sigue en curso.
    const uncertain = res.ok
      ? Boolean(res.data) && res.data.status === 'PENDING'
      : !failedQueryId && (res.status === 0 || res.status >= 500);
    if (this.unsettledAsks.get(attempt.ticketId) !== attempt) return uncertain; // ya hay un intento posterior
    if (failedQueryId) {
      // Terminó en ERROR: repetirla es una consulta nueva enlazada con la fallida.
      this.unsettledAsks.set(attempt.ticketId, {
        ticketId: attempt.ticketId, question: attempt.question, retrievalProfileId: attempt.retrievalProfileId, retryOfQueryId: failedQueryId
      });
    } else if (!uncertain) {
      this.unsettledAsks.delete(attempt.ticketId);
    }
    return uncertain;
  },

  renderResponse(query) {
    const container = document.getElementById('assistant-result-container');
    if (!container || !query) return;
    if (query.ticketId != null && query.ticketId !== this.currentTicketId) return; // consulta de otro incidente

    this.activeQueryId = query.id;
    const exportButton=document.getElementById('rag-export');if(exportButton)exportButton.hidden=false;
    let advanced=null;try{advanced=query.advancedJson?JSON.parse(query.advancedJson):null;}catch{/* historial v1 sin detalles avanzados */}
    const stepCitations=index=>{
      const refs=advanced?.citations?.find(c=>c.stepIndex===index)?.pointIds||[];
      return refs.map(point=>{
        const evidence=advanced.sources.find(s=>s.pointId===point);
        const sourceIndex=(query.sources||[]).findIndex(s=>s.articleId===evidence?.articleId);
        return sourceIndex<0?'':`<button type="button" class="btn btn-outline btn-sm" onclick="ASSISTANT.toggleEvidence('ev-${query.id}-${sourceIndex}')">Fuente ${sourceIndex+1} · ${UI.escapeHtml(evidence.section)}</button>`;
      }).join('');
    };

    // Caso 1: sin respuesta (NO_RESULTS, ERROR o PENDING): solo el mensaje del backend, sin calificación
    if (query.status !== 'ANSWERED') {
      container.innerHTML = `
        <div class="alert-no-results">
          <div style="font-weight: var(--font-weight-semibold); margin-bottom: 0.25rem;">
            ${UI.icon('search')} ${UI.escapeHtml(query.statusLabel || 'Sin resultados')}
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
          <span>${UI.icon('warning')}</span>
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
          ${query.recommendedSteps.map((step,index) => `<li>${UI.escapeHtml(step)}<div class="rag-citations">${stepCitations(index)}</div></li>`).join('')}
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
            const version = src.articleVersion != null ? ` · v${UI.escapeHtml(src.articleVersion)}` : '';
            // current === false: el artículo cambió o dejó de estar publicado después de esta respuesta.
            // La evidencia mostrada sigue siendo la que se usó entonces; solo se advierte su vigencia.
            const outdated = src.current === false ? `
                <div class="alert-limited-evidence" role="note">
                  <span>${UI.icon('warning')}</span>
                  <span><strong>Fuente histórica:</strong> este artículo fue modificado o dejó de estar publicado después de esta respuesta. El texto corresponde a la versión usada entonces y puede no estar vigente.</span>
                </div>` : '';
            return `
              <div class="source-card">
                <div class="source-header">
                  <div class="source-title">
                    [Fuente ${index + 1}] ${UI.escapeHtml(src.title)}${version}
                  </div>
                  <span class="similarity-badge" title="Métrica de similitud coseno (no es probabilidad)">
                    cos: ${simScore}
                  </span>
                </div>
                ${outdated}
                ${src.evidence ? `
                  <button type="button" class="source-evidence-toggle" onclick="ASSISTANT.toggleEvidence('ev-${query.id}-${index}')">
                    Ver fragmento de evidencia utilizada ${UI.icon('chevron-down')}
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
    // El backend solo admite la calificación de quien hizo la consulta; los demás la ven en solo lectura.
    const user = AUTH.getUser();
    const canRate = Boolean(user) && user.username === query.requesterUsername;
    if (!canRate && !isRated) return '';

    let starsHtml = '';
    for (let s = 1; s <= 5; s++) {
      starsHtml += `
        <button type="button" class="star-btn ${s <= existingScore ? 'is-active' : ''}"
          ${canRate ? `onclick="ASSISTANT.handleRate(${query.id}, ${s})"` : 'disabled'}
          title="${s} estrellas" aria-label="${s} de 5 estrellas">
          ${UI.icon('star')}
        </button>
      `;
    }

    const heading = canRate
      ? '¿Te fue útil esta orientación técnica?'
      : `Calificación de ${UI.escapeHtml(query.requesterUsername || 'quien hizo la consulta')}: ${existingScore}/5`;

    return `
      <div class="rating-section" id="rating-section-${query.id}">
        <div style="font-weight: var(--font-weight-semibold); font-size: var(--font-size-xs); color: var(--text-main);">
          ${heading} ${isRated && canRate ? '<span class="badge badge-resolved">Calificada</span>' : ''}
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
    // historyById solo guarda consultas del incidente abierto: si ya se cambió, no hay nada que refrescar.
    // Se actualiza la consulta cargada en vez de volver a pedir la página 0, que descartaría las anteriores.
    const query = this.historyById[queryId];
    if (!query || query.ticketId !== this.currentTicketId) return;
    query.rating = res.data;
    this.renderHistory();
    if (this.activeQueryId === queryId) this.renderResponse(query);
  },

  toggleEvidence(elementId) {
    const el = document.getElementById(elementId);
    if (el) {
      el.classList.toggle('is-open');
    }
  },

  /**
   * Carga una página del historial (PageResponse del backend, 20 por página, más recientes primero).
   * La página 0 reinicia la lista visible; las siguientes se añaden con «Cargar anteriores».
   */
  async loadHistory(ticketId, page = 0) {
    const historyList = document.getElementById('assistant-history-list');
    if (!historyList || this.currentTicketId !== ticketId) return;
    if (page > 0 && (this.historyLoading || page !== this.historyPage + 1)) return;

    const seq = ++this.historySeq;
    this.historyLoading = true;
    const moreButton = document.getElementById('btn-assistant-history-more');
    if (page > 0) UI.setLoading(moreButton, true);

    const res = await API.get(`/tickets/${ticketId}/assistance?page=${page}`);
    if (seq !== this.historySeq) return;
    this.historyLoading = false;
    if (!res.ok) {
      if (page > 0) UI.setLoading(moreButton, false);
      const msg = res.error || 'No se pudo cargar el historial de consultas.';
      if (page === 0) {
        historyList.innerHTML = `<span style="font-size: var(--font-size-xs); color: var(--color-danger);">${UI.escapeHtml(msg)}</span>`;
      } else {
        UI.showToast(msg, 'error');
      }
      return;
    }

    const data = res.data || {};
    const queries = Array.isArray(data.content) ? data.content : [];
    if (page === 0) this.historyIds = [];
    queries.forEach(q => {
      this.historyById[q.id] = q;
      // Una consulta nueva desplaza las páginas: la misma puede llegar dos veces.
      if (!this.historyIds.includes(q.id)) this.historyIds.push(q.id);
    });
    this.historyPage = Number.isInteger(data.page) ? data.page : page;
    this.historyTotalPages = data.totalPages || 0;
    this.renderHistory();

    if (page > 0) {
      const more = document.getElementById('btn-assistant-history-more');
      if (more) more.focus(); // el botón se redibuja; sin esto el foco se pierde
    }
  },

  renderHistory() {
    const historyList = document.getElementById('assistant-history-list');
    if (!historyList) return;

    if (this.historyIds.length === 0) {
      historyList.innerHTML = `<span style="font-size: var(--font-size-xs); color: var(--text-subtle);">Aún no se han realizado consultas RAG en este incidente.</span>`;
      return;
    }

    // El enlace solo lleva el id: meter la consulta en el onclick permitía ejecutar código desde la pregunta.
    const itemsHtml = this.historyIds.map(id => {
      const q = this.historyById[id];
      const statusClass = q.status === 'ANSWERED' ? 'badge-resolved' : (q.status === 'NO_RESULTS' ? 'badge-assigned' : 'badge-closed');
      const scoreBadge = q.rating ? `<span class="badge badge-priority-media">${UI.icon('star')} ${q.rating.score}/5</span>` : '';

      return `
        <div style="padding: 0.5rem 0; border-bottom: 1px solid var(--border-color-subtle);">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px;">
            <span class="badge ${statusClass}">${UI.escapeHtml(q.statusLabel || q.status)}</span>
            ${scoreBadge}
            <span style="font-size: 0.7rem; color: var(--text-subtle);">${UI.formatDate(q.createdAt)}</span>
          </div>
          <button type="button" class="assistant-history-link"
            onclick="ASSISTANT.renderResponse(ASSISTANT.historyById[${q.id}])">
            "${UI.escapeHtml(q.question || 'Consulta general del incidente')}"
          </button>
        </div>
      `;
    }).join('');

    const moreHtml = this.historyPage + 1 < this.historyTotalPages ? `
      <button type="button" class="btn btn-outline btn-sm btn-block" id="btn-assistant-history-more" style="margin-top: 0.5rem;"
        onclick="ASSISTANT.loadHistory(${Number(this.currentTicketId)}, ${this.historyPage + 1})">
        Cargar anteriores
      </button>
    ` : '';

    historyList.innerHTML = itemsHtml + moreHtml;
  },

  generateUuid() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
      const r = Math.random() * 16 | 0, v = c === 'x' ? r : (r & 0x3 | 0x8);
      return v.toString(16);
    });
  }
};

window.ASSISTANT = ASSISTANT;
