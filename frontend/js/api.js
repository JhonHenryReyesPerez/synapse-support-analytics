/**
 * Synapse Support Analytics - Cliente API Centralizado (Fetch Wrapper)
 */
const API = {
  /**
   * Realiza una petición HTTP autenticada hacia la API REST de Spring Boot.
   * El frontend NUNCA llama directamente a n8n, Qdrant ni OpenRouter.
   *
   * @param {string} endpoint - Ruta relativa comenzando con / (ej. /auth/login)
   * @param {RequestInit} [options={}] - Opciones de fetch (método, body, headers)
   * @returns {Promise<{ ok: boolean, status: number, data: any, error?: string, raw?: any }>}
   */
  async request(endpoint, options = {}) {
    const url = `${CONFIG.API_BASE_URL}${endpoint}`;
    const headers = {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      ...(options.headers || {})
    };

    const token = localStorage.getItem(CONFIG.STORAGE_TOKEN);
    if (token && !headers['Authorization']) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const fetchConfig = {
      ...options,
      headers
    };

    try {
      const response = await fetch(url, fetchConfig);
      const status = response.status;

      let data = null;
      const contentType = response.headers.get('content-type') || '';
      if (contentType.includes('application/json')) {
        try {
          data = await response.json();
        } catch {
          data = null;
        }
      } else {
        const text = await response.text();
        data = text ? { message: text } : null;
      }

      // 1. Manejo de 401 Unauthorized (Sin sesión, token expirado o invalidado por cambio de credenciales)
      if (status === 401) {
        // Si no estamos ya en la pantalla de login, limpiamos y redirigimos
        if (!window.location.pathname.endsWith('index.html') && !window.location.pathname.endsWith('/')) {
          localStorage.removeItem(CONFIG.STORAGE_TOKEN);
          localStorage.removeItem(CONFIG.STORAGE_USER);
          UI.showToast('Tu sesión ha expirado o no es válida. Por favor, inicia sesión nuevamente.', 'error', 'Sesión Expirada');
          setTimeout(() => {
            window.location.href = CONFIG.ROUTES.LOGIN;
          }, 1500);
        }
        const errorMsg = (data && (data.error || data.message)) || 'Credenciales no autorizadas';
        return { ok: false, status, data, error: errorMsg };
      }

      // 2. Manejo de 403 Forbidden con cambio de contraseña obligatorio
      if (status === 403 && data && data.code === 'PASSWORD_CHANGE_REQUIRED') {
        UI.showToast('Es obligatorio actualizar tu contraseña antes de continuar.', 'warning', 'Acción Requerida');
        if (typeof window.triggerMandatoryPasswordChange === 'function') {
          window.triggerMandatoryPasswordChange();
        }
        return { ok: false, status, data, error: data.error || 'Cambio de contraseña requerido' };
      }

      // 3. Manejo de 429 Too Many Requests (Bloqueo temporal por intentos fallidos de login)
      if (status === 429) {
        const retrySec = (data && data.retryAfterSeconds) || response.headers.get('Retry-After') || 60;
        const msg = (data && data.error) || `Demasiados intentos fallidos. Intenta de nuevo en ${retrySec} segundos.`;
        return { ok: false, status, data, error: msg };
      }

      // 4. Manejo de 502 / 503 / 504 (Errores de orquestación de n8n / OpenRouter)
      if (status >= 502 && status <= 504) {
        const msg = (data && (data.error || data.message)) || 'El servicio de asistencia inteligente no se encuentra disponible temporalmente.';
        return { ok: false, status, data, error: msg };
      }

      // 5. Manejo de errores de validación 400 y conflictos 409
      if (!response.ok) {
        let errorMsg = 'Ha ocurrido un error en la solicitud';
        let fieldErrors;
        if (data) {
          // Validación de Spring: {error: 'Validación fallida', errors: {campo: detalle}}.
          // El detalle por campo es lo que permite corregir la entrada: va antes que el resumen.
          if (data.errors && typeof data.errors === 'object' && Object.keys(data.errors).length > 0) {
            fieldErrors = data.errors;
            errorMsg = `${data.error || 'Validación fallida'}: ${Object.values(data.errors).join('; ')}`;
          } else if (data.error) errorMsg = data.error;
          else if (data.message) errorMsg = data.message;
        }
        return { ok: false, status, data, error: errorMsg, fieldErrors };
      }

      return { ok: true, status, data };
    } catch (networkError) {
      console.error('Error de red al conectar con el backend:', networkError);
      const msg = 'No se pudo conectar con el servidor backend. Verifica que esté en ejecución en ' + CONFIG.API_BASE_URL;
      return { ok: false, status: 0, data: null, error: msg };
    }
  },

  // Métodos semánticos directos
  get(endpoint, options = {}) {
    return this.request(endpoint, { ...options, method: 'GET' });
  },

  post(endpoint, body = {}, options = {}) {
    return this.request(endpoint, {
      ...options,
      method: 'POST',
      body: JSON.stringify(body)
    });
  },

  put(endpoint, body = {}, options = {}) {
    return this.request(endpoint, {
      ...options,
      method: 'PUT',
      body: JSON.stringify(body)
    });
  },

  patch(endpoint, body = {}, options = {}) {
    return this.request(endpoint, {
      ...options,
      method: 'PATCH',
      body: JSON.stringify(body)
    });
  },

  delete(endpoint, options = {}) {
    return this.request(endpoint, { ...options, method: 'DELETE' });
  }
};

window.API = API;
