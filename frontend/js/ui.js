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

  /**
   * Abre un modal por su ID.
   */
  openModal(modalId) {
    const modal = document.getElementById(modalId);
    if (!modal) return;
    modal.classList.add('is-open');
    modal.setAttribute('aria-hidden', 'false');
    
    // Auto-foco al primer input si existe
    const firstInput = modal.querySelector('input:not([type=hidden]), button:not(.modal-close)');
    if (firstInput) firstInput.focus();

    // Manejo de tecla Escape
    const onKeyDown = (e) => {
      if (e.key === 'Escape' && !modal.dataset.preventClose) {
        this.closeModal(modalId);
        document.removeEventListener('keydown', onKeyDown);
      }
    };
    document.addEventListener('keydown', onKeyDown);
  },

  /**
   * Cierra un modal por su ID.
   */
  closeModal(modalId) {
    const modal = document.getElementById(modalId);
    if (!modal) return;
    modal.classList.remove('is-open');
    modal.setAttribute('aria-hidden', 'true');
  },

  /**
   * Alterna el estado de carga en un botón.
   */
  setLoading(buttonEl, isLoading, loadingText = 'Cargando...') {
    if (!buttonEl) return;
    if (isLoading) {
      buttonEl.dataset.originalHtml = buttonEl.innerHTML;
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
    const div = document.createElement('div');
    div.textContent = String(str);
    return div.innerHTML;
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
