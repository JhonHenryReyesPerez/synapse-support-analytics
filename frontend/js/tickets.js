/**
 * Synapse Support Analytics - Módulo de Incidentes (Tickets)
 */
const TICKETS = {
  currentPage: 0,
  pageSize: 10,
  categories: [],
  currentTicket: null,

  async init() {
    await this.loadCategories();
    this.bindEvents();
    await this.loadTickets();
  },

  async loadCategories() {
    const res = await API.get('/categories');
    if (res.ok && Array.isArray(res.data)) {
      this.categories = res.data;
      this.renderCategorySelects();
    }
  },

  renderCategorySelects() {
    const selects = [
      document.getElementById('filter-ticket-category'),
      document.getElementById('create-ticket-category'),
      document.getElementById('analytics-filter-category')
    ];

    selects.forEach(select => {
      if (!select) return;
      // Mantener la opción por defecto
      const defaultText = select.dataset.defaultText || 'Todas las categorías';
      select.innerHTML = `<option value="">${defaultText}</option>`;
      this.categories.forEach(cat => {
        if (cat.active !== false) {
          select.innerHTML += `<option value="${cat.id}">${UI.escapeHtml(cat.name)}</option>`;
        }
      });
    });
  },

  bindEvents() {
    // Filtros de tabla
    const filterStatus = document.getElementById('filter-ticket-status');
    const filterPriority = document.getElementById('filter-ticket-priority');
    const filterCategory = document.getElementById('filter-ticket-category');

    [filterStatus, filterPriority, filterCategory].forEach(el => {
      if (el) {
        el.addEventListener('change', () => {
          this.currentPage = 0;
          this.loadTickets();
        });
      }
    });

    // Paginación
    const btnPrev = document.getElementById('btn-page-prev');
    const btnNext = document.getElementById('btn-page-next');
    if (btnPrev) {
      btnPrev.addEventListener('click', () => {
        if (this.currentPage > 0) {
          this.currentPage--;
          this.loadTickets();
        }
      });
    }
    if (btnNext) {
      btnNext.addEventListener('click', () => {
        this.currentPage++;
        this.loadTickets();
      });
    }

    // Modal Crear Incidente
    const btnNewTicket = document.getElementById('btn-new-ticket');
    if (btnNewTicket) {
      btnNewTicket.addEventListener('click', () => {
        const form = document.getElementById('form-create-ticket');
        if (form) form.reset();
        UI.openModal('modal-create-ticket');
      });
    }

    const formCreate = document.getElementById('form-create-ticket');
    if (formCreate) {
      formCreate.addEventListener('submit', async (e) => {
        e.preventDefault();
        await this.handleCreateTicket();
      });
    }

    // Enviar comentario
    const formComment = document.getElementById('form-add-comment');
    if (formComment) {
      formComment.addEventListener('submit', async (e) => {
        e.preventDefault();
        await this.handleAddComment();
      });
    }
  },

  async loadTickets() {
    const status = document.getElementById('filter-ticket-status')?.value || '';
    const priority = document.getElementById('filter-ticket-priority')?.value || '';
    const categoryId = document.getElementById('filter-ticket-category')?.value || '';

    let query = `?page=${this.currentPage}&size=${this.pageSize}`;
    if (status) query += `&status=${encodeURIComponent(status)}`;
    if (priority) query += `&priority=${encodeURIComponent(priority)}`;
    if (categoryId) query += `&categoryId=${encodeURIComponent(categoryId)}`;

    const tbody = document.getElementById('tickets-table-body');
    if (tbody) {
      tbody.innerHTML = `<tr><td colspan="7" style="text-align:center; padding: 2rem;"><span class="spinner"></span> Cargando incidentes...</td></tr>`;
    }

    const res = await API.get(`/tickets${query}`);
    if (!res.ok) {
      if (tbody) tbody.innerHTML = `<tr><td colspan="7" style="text-align:center; color: var(--color-danger);">${UI.escapeHtml(res.error || 'Error al cargar incidentes')}</td></tr>`;
      return;
    }

    this.renderTicketsTable(res.data);
  },

  renderTicketsTable(pageData) {
    const tbody = document.getElementById('tickets-table-body');
    if (!tbody) return;

    const tickets = pageData.content || [];
    if (tickets.length === 0) {
      tbody.innerHTML = `<tr><td colspan="7" style="text-align:center; padding: 2rem; color: var(--text-muted);">No se encontraron incidentes registrados.</td></tr>`;
      this.updatePagination(pageData);
      return;
    }

    tbody.innerHTML = tickets.map(t => {
      const statusClass = `badge-${(t.status || 'open').toLowerCase().replace('_', '-')}`;
      const statusLabel = t.statusLabel || t.status || 'Abierto';
      const priorityClass = `badge-priority-${(t.priority || 'media').toLowerCase()}`;

      return `
        <tr>
          <td style="font-family: var(--font-family-mono); font-weight: bold;">#${t.id}</td>
          <td style="font-weight: var(--font-weight-semibold);">
            <a href="javascript:void(0)" onclick="TICKETS.openTicketDetail(${t.id})">
              ${UI.escapeHtml(t.title)}
            </a>
          </td>
          <td><span class="badge ${statusClass}">${UI.escapeHtml(statusLabel)}</span></td>
          <td><span class="badge ${priorityClass}">${UI.escapeHtml(t.priority || 'MEDIA')}</span></td>
          <td>${UI.escapeHtml(t.category || t.categoryName || '—')}</td>
          <td>${UI.escapeHtml(t.applicantUsername || '—')}</td>
          <td>${UI.formatDate(t.createdAt)}</td>
        </tr>
      `;
    }).join('');

    this.updatePagination(pageData);
  },

  updatePagination(pageData) {
    const info = document.getElementById('tickets-page-info');
    const btnPrev = document.getElementById('btn-page-prev');
    const btnNext = document.getElementById('btn-page-next');

    const total = pageData.totalElements || 0;
    const totalPages = pageData.totalPages || 1;
    const current = (pageData.page || 0) + 1;

    if (info) {
      info.textContent = `Página ${current} de ${totalPages} (${total} incidentes en total)`;
    }
    if (btnPrev) btnPrev.disabled = this.currentPage <= 0;
    if (btnNext) btnNext.disabled = current >= totalPages;
  },

  async handleCreateTicket() {
    const title = document.getElementById('create-ticket-title').value.trim();
    const categoryId = document.getElementById('create-ticket-category').value;
    const priority = document.getElementById('create-ticket-priority').value;
    const description = document.getElementById('create-ticket-description').value.trim();

    if (!title || !categoryId || !priority || !description) {
      UI.showToast('Todos los campos son obligatorios.', 'warning');
      return;
    }

    const btnSubmit = document.getElementById('btn-submit-ticket');
    UI.setLoading(btnSubmit, true, 'Registrando incidente...');

    const res = await API.post('/tickets', {
      title,
      description,
      categoryId: parseInt(categoryId, 10),
      priority
    });

    UI.setLoading(btnSubmit, false);

    if (!res.ok) {
      UI.showToast(res.error || 'No se pudo crear el incidente.', 'error');
      return;
    }

    UI.showToast(`Incidente #${res.data.id} creado con éxito.`, 'success');
    UI.closeModal('modal-create-ticket');
    this.currentPage = 0;
    await this.loadTickets();
  },

  async openTicketDetail(ticketId) {
    const res = await API.get(`/tickets/${ticketId}`);
    if (!res.ok) {
      UI.showToast(res.error || 'No se pudo cargar el detalle del incidente.', 'error');
      return;
    }

    // Al cambiar de incidente no debe quedar a la vista la respuesta del anterior.
    if (!this.currentTicket || this.currentTicket.id !== res.data.id) {
      document.getElementById('assistant-result-container').innerHTML = '';
      document.getElementById('assistant-custom-question').value = '';
    }
    this.currentTicket = res.data;
    this.renderDetailModal(this.currentTicket);
    UI.openModal('modal-ticket-detail');

    // Cargar en paralelo comentarios, historial y consultas de asistencia
    await Promise.all([
      this.loadComments(ticketId),
      this.loadHistory(ticketId),
      ASSISTANT.loadHistory(ticketId)
    ]);
  },

  renderDetailModal(ticket) {
    document.getElementById('detail-ticket-id').textContent = `#${ticket.id}`;
    document.getElementById('detail-ticket-title').textContent = ticket.title;
    document.getElementById('detail-ticket-desc').textContent = ticket.description;
    document.getElementById('detail-ticket-applicant').textContent = ticket.applicantUsername || '—';
    document.getElementById('detail-ticket-technician').textContent = ticket.technicianUsername || 'Sin asignar';
    document.getElementById('detail-ticket-category').textContent = ticket.category || '—';
    document.getElementById('detail-ticket-created').textContent = UI.formatDate(ticket.createdAt);

    const statusBadge = document.getElementById('detail-ticket-status');
    const statusClass = `badge-${(ticket.status || 'open').toLowerCase().replace('_', '-')}`;
    statusBadge.className = `badge ${statusClass}`;
    statusBadge.textContent = ticket.statusLabel || ticket.status;

    const priorityBadge = document.getElementById('detail-ticket-priority');
    priorityBadge.className = `badge badge-priority-${(ticket.priority || 'media').toLowerCase()}`;
    priorityBadge.textContent = ticket.priority;

    // Nota de resolución
    const resNoteBox = document.getElementById('detail-resolution-box');
    if (ticket.resolutionNote) {
      resNoteBox.style.display = 'block';
      document.getElementById('detail-resolution-note').textContent = ticket.resolutionNote;
    } else {
      resNoteBox.style.display = 'none';
    }

    // Botones de acción según rol y estado del ticket
    this.renderActionButtons(ticket);
  },

  renderActionButtons(ticket) {
    const container = document.getElementById('detail-actions-container');
    if (!container) return;
    container.innerHTML = '';

    const user = AUTH.getUser();
    if (!user) return;

    const role = user.role || 'USER';
    const status = ticket.status;

    // 1. Asignar / Reasignar (solo ADMIN)
    if (role === 'ADMIN' && (status === 'OPEN' || status === 'ASSIGNED')) {
      const btn = document.createElement('button');
      btn.className = 'btn btn-outline btn-sm';
      btn.innerHTML = '👤 Asignar Técnico';
      btn.onclick = () => this.promptAssignTechnician(ticket.id, status === 'ASSIGNED');
      container.appendChild(btn);
    }

    // 2. Iniciar Atención (solo el técnico asignado; el backend rechaza al ADMIN)
    if (status === 'ASSIGNED' && role === 'TECHNICIAN') {
      const btn = document.createElement('button');
      btn.className = 'btn btn-primary btn-sm';
      btn.innerHTML = '▶ Iniciar Atención';
      btn.onclick = () => this.executeTransition(ticket.id, 'START_PROGRESS');
      container.appendChild(btn);
    }

    // 3. Resolver Incidente (solo el técnico asignado)
    if (status === 'IN_PROGRESS' && role === 'TECHNICIAN') {
      const btn = document.createElement('button');
      btn.className = 'btn btn-success btn-sm';
      btn.innerHTML = '✓ Resolver Incidente';
      btn.onclick = () => this.promptResolveTicket(ticket.id);
      container.appendChild(btn);
    }

    // 4. Confirmar Cierre (solo el solicitante)
    if (status === 'RESOLVED' && user.username === ticket.applicantUsername) {
      const btn = document.createElement('button');
      btn.className = 'btn btn-secondary btn-sm';
      btn.innerHTML = '🔒 Confirmar Cierre';
      btn.onclick = () => this.executeTransition(ticket.id, 'CONFIRM_CLOSE');
      container.appendChild(btn);
    }
  },

  async executeTransition(ticketId, action, extraPayload = {}) {
    const res = await API.post(`/tickets/${ticketId}/transitions`, {
      action,
      ...extraPayload
    });

    if (!res.ok) {
      UI.showToast(res.error || 'No se pudo realizar la transición de estado.', 'error');
      return;
    }

    UI.showToast(`Estado actualizado: ${res.data.newStatus}`, 'success');
    await this.openTicketDetail(ticketId);
    await this.loadTickets();
  },

  async promptAssignTechnician(ticketId, isReassign) {
    const res = await API.get('/technicians/available');
    if (!res.ok) {
      UI.showToast('No se pudo obtener la lista de técnicos disponibles.', 'error');
      return;
    }

    const techs = res.data || [];
    if (techs.length === 0) {
      UI.showToast('No hay técnicos disponibles en este momento.', 'warning');
      return;
    }

    const selectHtml = techs.map(t => `<option value="${UI.escapeHtml(t.username)}">${UI.escapeHtml(t.username)} (${UI.escapeHtml(t.specialty || 'General')})</option>`).join('');
    
    const justificationGroup = isReassign ? `
      <div class="form-group" style="margin-top: 1rem;">
        <label class="form-label" for="assign-justification">Justificación de la reasignación (obligatoria)</label>
        <textarea id="assign-justification" class="form-control" rows="2" placeholder="Motivo del cambio de técnico" required></textarea>
      </div>
    ` : '';

    const content = `
      <p style="margin-bottom: 1rem;">Selecciona el técnico que atenderá el incidente #${ticketId}:</p>
      <div class="form-group">
        <label class="form-label" for="assign-tech-select">Técnico Disponible</label>
        <select id="assign-tech-select" class="form-select">${selectHtml}</select>
      </div>
      ${justificationGroup}
    `;

    document.getElementById('modal-generic-body').innerHTML = content;
    document.getElementById('modal-generic-title').textContent = isReassign ? 'Reasignar Técnico' : 'Asignar Técnico';

    const btnConfirm = document.getElementById('btn-generic-confirm');
    btnConfirm.style.display = ''; // ver un artículo lo oculta
    btnConfirm.onclick = async () => {
      const technicianUsername = document.getElementById('assign-tech-select').value;
      const justification = document.getElementById('assign-justification')?.value?.trim();

      if (isReassign && !justification) {
        UI.showToast('La justificación es obligatoria para reasignar.', 'warning');
        return;
      }

      UI.setLoading(btnConfirm, true, 'Asignando...');
      // La primera asignación y la reasignación son endpoints distintos en el backend.
      const assignRes = isReassign
        ? await API.put(`/tickets/${ticketId}/reassign`, { technicianUsername, justification })
        : await API.patch(`/tickets/${ticketId}/assign`, { technicianUsername });
      UI.setLoading(btnConfirm, false);

      if (!assignRes.ok) {
        UI.showToast(assignRes.error || 'Error al asignar el técnico.', 'error');
        return;
      }

      UI.showToast('Técnico asignado exitosamente.', 'success');
      UI.closeModal('modal-generic');
      await this.openTicketDetail(ticketId);
      await this.loadTickets();
    };

    UI.openModal('modal-generic');
  },

  promptResolveTicket(ticketId) {
    const content = `
      <p style="margin-bottom: 1rem;">Para resolver este incidente, ingresa la nota técnica con el procedimiento o diagnóstico realizado:</p>
      <div class="form-group">
        <label class="form-label" for="resolve-note-input">Nota de Solución (obligatoria)</label>
        <textarea id="resolve-note-input" class="form-control" rows="4" placeholder="Describe brevemente la causa y los pasos tomados para solucionar la falla..." required></textarea>
      </div>
    `;

    document.getElementById('modal-generic-body').innerHTML = content;
    document.getElementById('modal-generic-title').textContent = 'Resolver Incidente';

    const btnConfirm = document.getElementById('btn-generic-confirm');
    btnConfirm.style.display = '';
    btnConfirm.onclick = async () => {
      const resolutionNote = document.getElementById('resolve-note-input').value.trim();
      if (!resolutionNote) {
        UI.showToast('La nota de solución es obligatoria.', 'warning');
        return;
      }

      UI.setLoading(btnConfirm, true, 'Registrando resolución...');
      await this.executeTransition(ticketId, 'RESOLVE', { resolutionNote });
      UI.setLoading(btnConfirm, false);
      UI.closeModal('modal-generic');
    };

    UI.openModal('modal-generic');
  },

  async loadComments(ticketId) {
    const list = document.getElementById('detail-comments-list');
    if (!list) return;

    list.innerHTML = `<span class="spinner"></span> Cargando comentarios...`;
    const res = await API.get(`/tickets/${ticketId}/comments`);
    if (!res.ok) {
      list.innerHTML = `<span style="color:var(--text-subtle);">Sin comentarios.</span>`;
      return;
    }

    const comments = res.data || [];
    if (comments.length === 0) {
      list.innerHTML = `<span style="color:var(--text-subtle); font-size:var(--font-size-xs);">No hay comentarios registrados en este incidente.</span>`;
      return;
    }

    list.innerHTML = comments.map(c => `
      <div class="comment-card ${c.visibility === 'INTERNAL' ? 'is-internal' : ''}">
        <div class="comment-header">
          <span class="comment-author">${UI.escapeHtml(c.authorUsername || 'Usuario')}</span>
          <span class="timeline-time">${UI.formatDate(c.createdAt)}</span>
        </div>
        <div class="comment-body">${UI.escapeHtml(c.content)}</div>
      </div>
    `).join('');
  },

  async handleAddComment() {
    if (!this.currentTicket) return;
    const content = document.getElementById('comment-input').value.trim();
    const isInternal = document.getElementById('comment-internal')?.checked || false;

    if (!content) return;

    const btn = document.getElementById('btn-submit-comment');
    UI.setLoading(btn, true, 'Publicando...');

    const res = await API.post(`/tickets/${this.currentTicket.id}/comments`, {
      content,
      visibility: isInternal ? 'INTERNAL' : 'PUBLIC'
    });

    UI.setLoading(btn, false);

    if (!res.ok) {
      UI.showToast(res.error || 'No se pudo registrar el comentario.', 'error');
      return;
    }

    document.getElementById('comment-input').value = '';
    await this.loadComments(this.currentTicket.id);
  },

  async loadHistory(ticketId) {
    const timeline = document.getElementById('detail-history-timeline');
    if (!timeline) return;

    timeline.innerHTML = `<li><span class="spinner"></span></li>`;
    const res = await API.get(`/tickets/${ticketId}/history`);
    if (!res.ok) {
      timeline.innerHTML = `<li>Sin historial.</li>`;
      return;
    }

    const history = res.data || [];
    timeline.innerHTML = history.map(h => `
      <li class="timeline-item">
        <div class="timeline-dot"></div>
        <div class="timeline-content">
          <div class="timeline-header">
            <strong>${UI.escapeHtml(h.newStatus || '')}</strong>
            <span class="timeline-time">${UI.formatDate(h.changedAt)}</span>
          </div>
          <div>Por: ${UI.escapeHtml(h.changedBy || 'Sistema')}</div>
        </div>
      </li>
    `).join('');
  }
};

window.TICKETS = TICKETS;
