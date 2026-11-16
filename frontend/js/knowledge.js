/**
 * Synapse Support Analytics - Módulo de Base de Conocimiento (Artículos Técnicos)
 */
const KNOWLEDGE = {
  currentPage: 0,
  // shortcut: sin paginación, se pide el máximo del backend (100); añadir páginas si hay más artículos.
  pageSize: 100,
  currentScope: 'PUBLISHED',

  async init() {
    this.bindEvents();
    await this.loadArticles();
  },

  bindEvents() {
    // Selector de Scope (Publicados / Mis Artículos / Todos)
    const scopeSelect = document.getElementById('kb-scope-select');
    if (scopeSelect) {
      scopeSelect.addEventListener('change', () => {
        this.currentScope = scopeSelect.value;
        this.currentPage = 0;
        this.loadArticles();
      });
    }

    // Modal Crear Artículo
    const btnNew = document.getElementById('btn-new-article');
    if (btnNew) {
      btnNew.addEventListener('click', () => {
        const form = document.getElementById('form-create-article');
        if (form) form.reset();
        UI.openModal('modal-create-article');
      });
    }

    const formCreate = document.getElementById('form-create-article');
    if (formCreate) {
      formCreate.addEventListener('submit', async (e) => {
        e.preventDefault();
        await this.handleCreateArticle();
      });
    }

    // Botón de Reconciliación Masiva (ADMIN)
    const btnReconcile = document.getElementById('btn-reconcile-index');
    if (btnReconcile) {
      btnReconcile.addEventListener('click', async () => {
        await this.handleReconcile();
      });
    }
  },

  async loadArticles() {
    const grid = document.getElementById('articles-grid');
    if (!grid) return;

    grid.innerHTML = `<div style="grid-column: 1 / -1; text-align: center; padding: 3rem;"><span class="spinner"></span> Cargando artículos...</div>`;

    const res = await API.get(`/knowledge/articles?scope=${this.currentScope}&page=${this.currentPage}&size=${this.pageSize}`);
    if (!res.ok) {
      grid.innerHTML = `<div style="grid-column: 1 / -1; color: var(--color-danger); text-align: center;">${UI.escapeHtml(res.error || 'Error al consultar artículos')}</div>`;
      return;
    }

    const pageData = res.data;
    const articles = pageData.content || [];

    if (articles.length === 0) {
      grid.innerHTML = `<div style="grid-column: 1 / -1; text-align: center; color: var(--text-muted); padding: 3rem;">No hay artículos técnicos en este alcance.</div>`;
      return;
    }

    const currentUser = AUTH.getUser();
    const isAdmin = currentUser && currentUser.role === 'ADMIN';

    grid.innerHTML = articles.map(art => {
      // Estado de Verificación y Activación
      let statusBadge = '<span class="badge badge-draft">Borrador</span>';
      if (art.verified && art.active) {
        statusBadge = '<span class="badge badge-verified">✓ Verificado y Activo</span>';
      } else if (art.verified && !art.active) {
        statusBadge = '<span class="badge badge-inactive">Inactivo (Verificado)</span>';
      }

      // Estado de Indexación
      const indexStatus = art.indexStatus || 'NOT_INDEXED';
      const indexClass = `badge-index-${indexStatus.toLowerCase().replace('_', '-')}`;
      const indexBadge = `<span class="badge ${indexClass}" title="Estado en el vector store Qdrant">Índice: ${indexStatus}</span>`;

      // Acciones de Administración
      let adminActions = '';
      if (isAdmin) {
        adminActions = `
          <div style="display: flex; gap: 4px; margin-top: 0.5rem; flex-wrap: wrap;">
            ${!art.verified ? `
              <button class="btn btn-outline btn-sm" onclick="KNOWLEDGE.handleVerify(${art.id})">Verificar</button>
            ` : ''}
            ${art.verified && !art.active ? `
              <button class="btn btn-success btn-sm" onclick="KNOWLEDGE.handleActivate(${art.id})">Activar e Indexar</button>
            ` : ''}
            ${art.active ? `
              <button class="btn btn-danger btn-sm" onclick="KNOWLEDGE.handleDeactivate(${art.id})">Desactivar</button>
            ` : ''}
            ${indexStatus === 'ERROR' ? `
              <button class="btn btn-outline btn-sm" onclick="KNOWLEDGE.handleReindex(${art.id})">Reintentar Index</button>
            ` : ''}
          </div>
        `;
      }

      return `
        <article class="article-card">
          <header class="article-card-header">
            <div class="article-badges">
              ${statusBadge}
              ${indexBadge}
              <span class="badge" style="background:var(--status-closed-bg); color:var(--status-closed-text);">v${art.contentVersion || 1}</span>
            </div>
            <h3 class="article-card-title">${UI.escapeHtml(art.title)}</h3>
          </header>
          <div class="article-card-body">
            <div>
              <div class="article-snippet-label">Problema:</div>
              <p>${UI.escapeHtml(art.problem || '')}</p>
            </div>
            <div>
              <div class="article-snippet-label">Solución Documentada:</div>
              <p style="white-space: pre-line;">${UI.escapeHtml((art.solution || '').slice(0, 180))}...</p>
            </div>
            ${adminActions}
          </div>
          <footer class="article-card-footer">
            <span style="font-size:0.75rem; color:var(--text-subtle);">Autor: ${UI.escapeHtml(art.authorUsername || 'Técnico')}</span>
            <button class="btn btn-outline btn-sm" onclick="KNOWLEDGE.viewArticle(${art.id})">Ver Completo</button>
          </footer>
        </article>
      `;
    }).join('');
  },

  async viewArticle(id) {
    const res = await API.get(`/knowledge/articles/${id}`);
    if (!res.ok) {
      UI.showToast(res.error || 'No se pudo cargar el artículo.', 'error');
      return;
    }

    const art = res.data;
    const body = `
      <div style="display:flex; flex-direction:column; gap: 1rem;">
        <div>
          <h4 style="font-size:var(--font-size-xs); color:var(--text-muted); text-transform:uppercase;">Problema:</h4>
          <p>${UI.escapeHtml(art.problem)}</p>
        </div>
        <div>
          <h4 style="font-size:var(--font-size-xs); color:var(--text-muted); text-transform:uppercase;">Síntomas:</h4>
          <p>${UI.escapeHtml(art.symptoms)}</p>
        </div>
        <div>
          <h4 style="font-size:var(--font-size-xs); color:var(--text-muted); text-transform:uppercase;">Causa Raíz:</h4>
          <p>${UI.escapeHtml(art.cause)}</p>
        </div>
        <div>
          <h4 style="font-size:var(--font-size-xs); color:var(--text-muted); text-transform:uppercase;">Procedimiento de Solución:</h4>
          <div style="background:var(--bg-surface-alt); border:1px solid var(--border-color); border-radius:var(--radius-md); padding:1rem; white-space:pre-wrap; font-size:var(--font-size-sm);">
            ${UI.escapeHtml(art.solution)}
          </div>
        </div>
        <div style="font-size:var(--font-size-xs); color:var(--text-subtle); display:flex; justify-content:space-between; margin-top:0.5rem;">
          <span>Autor: ${UI.escapeHtml(art.authorUsername || '—')}</span>
          <span>Versión: v${art.contentVersion} · ${UI.formatDate(art.updatedAt || art.createdAt)}</span>
        </div>
      </div>
    `;

    document.getElementById('modal-generic-title').textContent = art.title;
    document.getElementById('modal-generic-body').innerHTML = body;
    document.getElementById('btn-generic-confirm').style.display = 'none';
    UI.openModal('modal-generic');
  },

  async handleCreateArticle() {
    const title = document.getElementById('article-title').value.trim();
    const problem = document.getElementById('article-problem').value.trim();
    const symptoms = document.getElementById('article-symptoms').value.trim();
    const cause = document.getElementById('article-cause').value.trim();
    const solution = document.getElementById('article-solution').value.trim();

    if (!title || !problem || !symptoms || !cause || !solution) {
      UI.showToast('Todos los cinco campos del artículo son obligatorios.', 'warning');
      return;
    }

    const btn = document.getElementById('btn-submit-article');
    UI.setLoading(btn, true, 'Guardando borrador...');

    const res = await API.post('/knowledge/articles', {
      title, problem, symptoms, cause, solution
    });

    UI.setLoading(btn, false);

    if (!res.ok) {
      UI.showToast(res.error || 'Error al guardar el artículo.', 'error');
      return;
    }

    UI.showToast('Artículo creado en estado Borrador.', 'success');
    UI.closeModal('modal-create-article');
    await this.loadArticles();
  },

  async handleVerify(id) {
    const res = await API.post(`/knowledge/articles/${id}/verify`);
    if (!res.ok) {
      UI.showToast(res.error || 'Error al verificar artículo.', 'error');
      return;
    }
    UI.showToast('Artículo verificado exitosamente.', 'success');
    await this.loadArticles();
  },

  async handleActivate(id) {
    const res = await API.post(`/knowledge/articles/${id}/activate`);
    if (!res.ok) {
      UI.showToast(res.error || 'Error al activar artículo.', 'error');
      return;
    }
    UI.showToast('Artículo activado y enviado a indexación vectorial.', 'success');
    await this.loadArticles();
  },

  async handleDeactivate(id) {
    const res = await API.post(`/knowledge/articles/${id}/deactivate`);
    if (!res.ok) {
      UI.showToast(res.error || 'Error al desactivar artículo.', 'error');
      return;
    }
    UI.showToast('Artículo desactivado de las fuentes activas.', 'info');
    await this.loadArticles();
  },

  async handleReindex(id) {
    const res = await API.post(`/knowledge/articles/${id}/reindex`);
    if (!res.ok) {
      UI.showToast(res.error || 'Error al reindexar artículo.', 'error');
      return;
    }
    UI.showToast('Sincronización con Qdrant reenviada.', 'success');
    await this.loadArticles();
  },

  async handleReconcile() {
    const btn = document.getElementById('btn-reconcile-index');
    UI.setLoading(btn, true, 'Sincronizando...');
    const res = await API.post('/knowledge/articles/index/reconcile');
    UI.setLoading(btn, false);

    if (!res.ok) {
      UI.showToast(res.error || 'Error en la sincronización general.', 'error');
      return;
    }

    UI.showToast('Reconciliación de artículos ejecutada exitosamente.', 'success');
    await this.loadArticles();
  }
};

window.KNOWLEDGE = KNOWLEDGE;
