/**
 * Synapse Support Analytics - Utilidades de Interfaz de Usuario (UI)
 */
const UI = {
  /**
   * Muestra una notificación Toast flotante.
   * @param {string} message - Texto principal del toast
   * @param {'success'|'error'|'warning'|'info'} type - Tipo semántico
   * @param {string} [title] - Título opcional
   * @param {number} [durationMs=4500] - Tiempo antes de auto-cerrar
   */
  showToast(message, type = 'info', title = '', durationMs = 4500) {
    let container = document.getElementById('toast-container');
    if (!container) {
      container = document.createElement('div');
      container.id = 'toast-container';
      container.className = 'toast-container';
      container.setAttribute('aria-live', 'polite');
      document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.setAttribute('role', 'alert');

    const icons = {
      success: '✓',
      error: '✕',
      warning: '⚠',
      info: 'ℹ'
    };

    const defaultTitles = {
      success: 'Éxito',
      error: 'Error',
      warning: 'Atención',
      info: 'Información'
    };

    toast.innerHTML = `
      <span class="toast-icon">${icons[type] || 'ℹ'}</span>
      <div class="toast-content">
        <div class="toast-title">${this.escapeHtml(title || defaultTitles[type] || '')}</div>
        <div class="toast-message">${this.escapeHtml(message)}</div>
      </div>
      <button class="toast-close" aria-label="Cerrar">&times;</button>
    `;

    const closeBtn = toast.querySelector('.toast-close');
    const dismiss = () => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateX(100%)';
      setTimeout(() => toast.remove(), 200);
    };

    closeBtn.addEventListener('click', dismiss);
    container.appendChild(toast);

    if (durationMs > 0) {
      setTimeout(dismiss, durationMs);
    }
  },

  // Modales abiertos, el último es el superior: { id, opener }.
  modalStack: [],
  FOCUSABLE: 'a[href], button:not([disabled]), input:not([disabled]):not([type=hidden]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
  // Un único manejador para toda la pila; se registra con el primer modal y se retira con el último.
  onModalKeyDown: (e) => UI.handleModalKey(e),

  focusableIn(modal) {
    return Array.from(modal.querySelectorAll(this.FOCUSABLE)).filter(el => el.getClientRects().length > 0);
  },

  focusFirst(modal) {
    const items = this.focusableIn(modal);
    const target = items.find(el => !el.classList.contains('modal-close')) || items[0];
    if (target) target.focus();
  },

  /**
   * Abre un modal por su ID. Recuerda qué tenía el foco para devolverlo al cerrar.
   */
  openModal(modalId) {
    const modal = document.getElementById(modalId);
    if (!modal) return;
    // Ya abierto (p. ej. refresco del detalle): no apilar dos veces ni robar el foco.
    if (this.modalStack.some(m => m.id === modalId)) return;

    this.modalStack.push({ id: modalId, opener: document.activeElement });
    modal.classList.add('is-open');
    modal.setAttribute('aria-hidden', 'false');
    this.focusFirst(modal);

    if (this.modalStack.length === 1) {
      document.addEventListener('keydown', this.onModalKeyDown);
    }
  },

  /**
   * Cierra un modal por su ID y devuelve el foco a quien lo abrió.
   */
  closeModal(modalId) {
    const modal = document.getElementById(modalId);
    if (!modal) return;
    modal.classList.remove('is-open');
    modal.setAttribute('aria-hidden', 'true');

    const index = this.modalStack.findIndex(m => m.id === modalId);
    if (index === -1) return;
    const wasTop = index === this.modalStack.length - 1;
    const [entry] = this.modalStack.splice(index, 1);
    if (this.modalStack.length === 0) {
      document.removeEventListener('keydown', this.onModalKeyDown);
    }
    if (!wasTop) return; // el foco sigue en el modal superior

    const top = this.modalStack[this.modalStack.length - 1];
    const topModal = top ? document.getElementById(top.id) : null;
    const opener = entry.opener;
    if (opener && opener.isConnected && (!topModal || topModal.contains(opener))) {
      opener.focus();
    } else if (topModal) {
      this.focusFirst(topModal); // quien abrió ya no existe (p. ej. botones de acción redibujados)
    }
  },

  /**
   * Escape y Tab actúan solo sobre el modal superior. Un modal obligatorio
   * (data-prevent-close) no se cierra con Escape, pero sí retiene el foco.
   */
  handleModalKey(e) {
    const top = this.modalStack[this.modalStack.length - 1];
    const modal = top ? document.getElementById(top.id) : null;
    if (!modal) return;

    if (e.key === 'Escape') {
      if (!modal.dataset.preventClose) this.closeModal(top.id);
      return;
    }
    if (e.key !== 'Tab') return;

    const items = this.focusableIn(modal);
    if (items.length === 0) {
      e.preventDefault();
      return;
    }
    const first = items[0];
    const last = items[items.length - 1];
    const active = document.activeElement;
    if (!modal.contains(active)) {
      e.preventDefault();
      first.focus();
    } else if (e.shiftKey && active === first) {
      e.preventDefault();
      last.focus();
    } else if (!e.shiftKey && active === last) {
      e.preventDefault();
      first.focus();
    }
  },

  /**
   * Alterna el estado de carga en un botón.
   */
  setLoading(buttonEl, isLoading, loadingText = 'Cargando...') {
    if (!buttonEl) return;
    if (isLoading) {
      if (!buttonEl.classList.contains('is-loading')) buttonEl.dataset.originalHtml = buttonEl.innerHTML;
      buttonEl.disabled = true;
      buttonEl.classList.add('is-loading');
      buttonEl.innerHTML = `<span class="spinner" aria-hidden="true"></span> ${this.escapeHtml(loadingText)}`;
    } else {
      buttonEl.disabled = false;
      buttonEl.classList.remove('is-loading');
      if (buttonEl.dataset.originalHtml) {
        buttonEl.innerHTML = buttonEl.dataset.originalHtml;
      }
    }
  },

  /**
   * Escapa caracteres HTML para prevenir inyecciones XSS.
   */
  escapeHtml(str) {
    if (str === null || str === undefined) return '';
    // También las comillas: el resultado se usa dentro de atributos (value="...", data-...).
    return String(str)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  },

  /**
   * Formatea una fecha ISO 8601 (hora local Bogotá) a presentación legible.
   */
  formatDate(isoString) {
    if (!isoString) return '—';
    try {
      const date = new Date(isoString);
      if (isNaN(date.getTime())) return isoString;
      return new Intl.DateTimeFormat('es-CO', {
        dateStyle: 'medium',
        timeStyle: 'short'
      }).format(date);
    } catch {
      return isoString;
    }
  },

  /**
   * Evalúa la política de contraseñas de CampusDesk / Synapse en vivo.
   * Reglas: mín 6 caracteres, máx 72 bytes, al menos una mayúscula, al menos un carácter especial.
   */
  checkPasswordPolicy(password) {
    const val = password || '';
    const encoder = new TextEncoder();
    const byteLength = encoder.encode(val).length;
    
    return {
      hasMinLength: val.length >= 6,
      hasMaxBytes: byteLength <= 72,
      hasUppercase: /[A-Z]/.test(val),
      hasSpecial: /[^a-zA-Z0-9\s]/.test(val),
      isValid: val.length >= 6 && byteLength <= 72 && /[A-Z]/.test(val) && /[^a-zA-Z0-9\s]/.test(val)
    };
  }
};

window.UI = UI;
