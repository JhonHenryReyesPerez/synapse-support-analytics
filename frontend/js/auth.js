/**
 * Synapse Support Analytics - Servicio de Autenticación y Sesión
 */
const AUTH = {
  /**
   * Obtiene el token JWT actual almacenado.
   */
  getToken() {
    return localStorage.getItem(CONFIG.STORAGE_TOKEN);
  },

  /**
   * Obtiene la información del usuario autenticado actual.
   */
  getUser() {
    try {
      const stored = localStorage.getItem(CONFIG.STORAGE_USER);
      return stored ? JSON.parse(stored) : null;
    } catch {
      return null;
    }
  },

  /**
   * Indica si existe una sesión activa localmente.
   */
  isAuthenticated() {
    return Boolean(this.getToken());
  },

  /**
   * Inicia sesión con usuario o correo y contraseña.
   * Endpoints: POST /api/auth/login y subsecuente GET /api/auth/me
   */
  async login(identifier, password) {
    const res = await API.post('/auth/login', {
      username: identifier.trim(),
      password: password
    });

    if (!res.ok) {
      return res;
    }

    const { token, username, mustChangePassword } = res.data;
    localStorage.setItem(CONFIG.STORAGE_TOKEN, token);

    // Obtener datos de rol y permisos mediante GET /api/auth/me
    let userDetails = { username, role: 'USER' };
    const meRes = await API.get('/auth/me');
    if (meRes.ok && meRes.data) {
      userDetails = {
        ...userDetails,
        ...meRes.data
      };
    }
    userDetails.mustChangePassword = Boolean(mustChangePassword);
    localStorage.setItem(CONFIG.STORAGE_USER, JSON.stringify(userDetails));

    return {
      ok: true,
      mustChangePassword: Boolean(mustChangePassword),
      user: userDetails
    };
  },

  /**
   * Registra una nueva cuenta pública con rol USER por defecto.
   * Endpoint: POST /api/auth/register
   */
  async register(username, email, password) {
    return await API.post('/auth/register', {
      username: username.trim(),
      email: email.trim(),
      password: password
    });
  },

  /**
   * Actualiza la contraseña actual por una nueva que cumpla con la política.
   * Endpoint: POST /api/auth/change-password
   * Nota crítica: Spring Boot invalida todos los tokens previos y devuelve uno nuevo en la respuesta.
   */
  async changePassword(currentPassword, newPassword) {
    const res = await API.post('/auth/change-password', {
      currentPassword: currentPassword,
      newPassword: newPassword
    });

    if (!res.ok) {
      return res;
    }

    // Actualizar token recibido
    if (res.data && res.data.token) {
      localStorage.setItem(CONFIG.STORAGE_TOKEN, res.data.token);
    }

    // Actualizar bandera en sesión local
    const currentUser = this.getUser();
    if (currentUser) {
      currentUser.mustChangePassword = false;
      localStorage.setItem(CONFIG.STORAGE_USER, JSON.stringify(currentUser));
    }

    return { ok: true, message: res.data.message };
  },

  /**
   * Cierra la sesión activa y redirige a la pantalla de ingreso.
   */
  logout() {
    localStorage.removeItem(CONFIG.STORAGE_TOKEN);
    localStorage.removeItem(CONFIG.STORAGE_USER);
    window.location.href = CONFIG.ROUTES.LOGIN;
  },

  /**
   * Verifica la sesión al cargar una página protegida.
   * Si no está autenticado, redirige a login.
   */
  requireAuth() {
    if (!this.isAuthenticated()) {
      window.location.href = CONFIG.ROUTES.LOGIN;
      return false;
    }
    return true;
  }
};

window.AUTH = AUTH;
