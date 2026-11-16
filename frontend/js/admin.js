/**
 * Synapse Support Analytics - Módulo de Administración (Usuarios y Catálogos)
 */
const ADMIN = {
  async init() {
    this.bindEvents();
    await Promise.all([
      this.loadUsers(),
      this.loadAdminCategories()
    ]);
  },

  bindEvents() {
    // Modal y Formulario Crear Usuario (Exclusivo Administradores)
    const btnNewUser = document.getElementById('btn-new-user');
    if (btnNewUser) {
      btnNewUser.addEventListener('click', () => {
        const form = document.getElementById('form-create-user');
        if (form) form.reset();
        document.getElementById('user-must-change')?.setAttribute('checked', 'checked');
        UI.openModal('modal-create-user');
      });
    }

    const formUser = document.getElementById('form-create-user');
    if (formUser) {
      formUser.addEventListener('submit', async (e) => {
        e.preventDefault();
        await this.handleCreateUser();
      });
    }

    // Validador de contraseña para nuevo usuario
    const userPassInput = document.getElementById('user-password');
    if (userPassInput) {
      const polLen = document.getElementById('usr-pol-length');
      const polUpp = document.getElementById('usr-pol-upper');
      const polSpc = document.getElementById('usr-pol-special');

      userPassInput.addEventListener('input', () => {
        const check = UI.checkPasswordPolicy(userPassInput.value);
        if (polLen) polLen.classList.toggle('valid', check.hasMinLength && check.hasMaxBytes);
        if (polUpp) polUpp.classList.toggle('valid', check.hasUppercase);
        if (polSpc) polSpc.classList.toggle('valid', check.hasSpecial);
      });
    }

    // Formulario Crear Categoría
    const formCat = document.getElementById('form-create-category');
    if (formCat) {
      formCat.addEventListener('submit', async (e) => {
        e.preventDefault();
        await this.handleCreateCategory();
      });
    }

    // Modal Crear Categoría
    const btnNewCat = document.getElementById('btn-new-category');
    if (btnNewCat) {
      btnNewCat.addEventListener('click', () => {
        const form = document.getElementById('form-create-category');
        if (form) form.reset();
        UI.openModal('modal-create-category');
      });
    }
  },

  async loadUsers() {
    const tbody = document.getElementById('users-table-body');
    if (!tbody) return;

    tbody.innerHTML = `<tr><td colspan="6" style="text-align:center; padding: 2rem;"><span class="spinner"></span> Cargando usuarios...</td></tr>`;

    const res = await API.get('/user/all');
    if (!res.ok) {
      tbody.innerHTML = `<tr><td colspan="6" style="text-align:center; color: var(--color-danger);">${UI.escapeHtml(res.error || 'Error al consultar usuarios')}</td></tr>`;
      return;
    }

    const users = res.data || [];
    tbody.innerHTML = users.map(u => {
      // Roles del usuario
      const roleStr = Array.isArray(u.roles) ? u.roles.map(r => r.role || r).join(', ') : (u.role || 'USER');
      let roleBadgeClass = 'background:#e0e7ff; color:#3730a3;';
      if (roleStr === 'ADMIN') roleBadgeClass = 'background:#fef3c7; color:#92400e; font-weight:bold;';
      if (roleStr === 'TECHNICIAN') roleBadgeClass = 'background:#ede9fe; color:#5b21b6; font-weight:bold;';

      const isLocked = u.locked ? '<span class="badge badge-priority-critica">Bloqueado</span>' : '<span class="badge badge-resolved">Activo</span>';
      const mustChange = u.mustChangePassword ? '<span class="badge badge-priority-alta">Cambio pendiente</span>' : '';

      return `
        <tr>
          <td style="font-weight: var(--font-weight-semibold);">${UI.escapeHtml(u.username)}</td>
          <td>
            <div>${UI.escapeHtml(u.fullName || '—')}</div>
            <div style="font-size: 0.72rem; color: var(--text-muted);">${UI.escapeHtml(u.area || 'Sin dependencia')}</div>
          </td>
          <td>${UI.escapeHtml(u.email || '—')}</td>
          <td><span class="badge" style="${roleBadgeClass}">${UI.escapeHtml(roleStr)}</span> ${mustChange}</td>
          <td>${isLocked}</td>
          <td>
            <button class="btn btn-outline btn-sm" onclick="ADMIN.promptResetPassword('${u.username}')" title="Restablecer contraseña con clave temporal">
              🔑 Restablecer
            </button>
          </td>
        </tr>
      `;
    }).join('');
  },

  async handleCreateUser() {
    const username = document.getElementById('user-username').value.trim();
    const fullName = document.getElementById('user-fullname').value.trim();
    const email = document.getElementById('user-email').value.trim();
    const area = document.getElementById('user-area').value.trim();
    const role = document.getElementById('user-role').value;
    const password = document.getElementById('user-password').value;
    const mustChangePassword = document.getElementById('user-must-change')?.checked || false;

    if (!username || !email || !password || !fullName) {
      UI.showToast('Nombre de usuario, nombre completo, correo y contraseña son obligatorios.', 'warning');
      return;
    }

    const check = UI.checkPasswordPolicy(password);
    if (!check.isValid) {
      UI.showToast('La contraseña no cumple con la política de seguridad corporativa.', 'warning');
      return;
    }

    const btn = document.getElementById('btn-submit-user');
    UI.setLoading(btn, true, 'Registrando usuario corporativo...');

    const res = await API.post('/user/add', {
      username,
      fullName,
      email,
      area: area || undefined,
      role,
      password,
      mustChangePassword
    });

    UI.setLoading(btn, false);

    if (!res.ok) {
      UI.showToast(res.error || 'No fue posible crear la cuenta de usuario.', 'error');
      return;
    }

    UI.showToast(`Usuario "${username}" creado exitosamente con rol ${role}.`, 'success');
    UI.closeModal('modal-create-user');
    await this.loadUsers();
  },

  promptResetPassword(username) {
    if (!confirm(`¿Estás seguro de restablecer la contraseña para el usuario "${username}"? Se le asignará una clave temporal y se le exigirá cambiarla al iniciar sesión.`)) {
      return;
    }

    this.executeResetPassword(username);
  },

  async executeResetPassword(username) {
    const res = await API.post(`/user/${encodeURIComponent(username)}/reset-password`);
    if (!res.ok) {
      UI.showToast(res.error || 'No fue posible restablecer la contraseña.', 'error');
      return;
    }

    const tempPass = res.data?.temporaryPassword || res.data?.message || 'Temporal123!';
    alert(`Contraseña restablecida exitosamente para "${username}".\n\nClave temporal generada: ${tempPass}\n\nEl usuario deberá cambiarla obligatoriamente en su próximo ingreso.`);
    await this.loadUsers();
  },

  async loadAdminCategories() {
    const tbody = document.getElementById('admin-categories-tbody');
    if (!tbody) return;

    const res = await API.get('/categories');
    if (!res.ok) return;

    const categories = res.data || [];
    tbody.innerHTML = categories.map(cat => {
      const activeBadge = cat.active !== false 
        ? '<span class="badge badge-resolved">Activa</span>' 
        : '<span class="badge badge-inactive">Inactiva</span>';

      const toggleAction = cat.active !== false
        ? `<button class="btn btn-outline btn-sm" onclick="ADMIN.toggleCategory(${cat.id}, false)">Desactivar</button>`
        : `<button class="btn btn-success btn-sm" onclick="ADMIN.toggleCategory(${cat.id}, true)">Activar</button>`;

      return `
        <tr>
          <td style="font-family: var(--font-family-mono); font-weight: bold;">#${cat.id}</td>
          <td style="font-weight: var(--font-weight-semibold);">${UI.escapeHtml(cat.name)}</td>
          <td>${UI.escapeHtml(cat.description || '—')}</td>
          <td>${activeBadge}</td>
          <td>${toggleAction}</td>
        </tr>
      `;
    }).join('');
  },

  async toggleCategory(id, activate) {
    const endpoint = activate ? `/categories/${id}/activate` : `/categories/${id}/deactivate`;
    const res = await API.patch(endpoint);
    if (!res.ok) {
      UI.showToast(res.error || 'Error al modificar categoría.', 'error');
      return;
    }
    UI.showToast(`Categoría ${activate ? 'activada' : 'desactivada'} con éxito.`, 'success');
    await this.loadAdminCategories();
    if (window.TICKETS) await TICKETS.loadCategories();
  },

  async handleCreateCategory() {
    const name = document.getElementById('cat-name').value.trim();
    const description = document.getElementById('cat-description').value.trim();

    if (!name) {
      UI.showToast('El nombre de la categoría es obligatorio.', 'warning');
      return;
    }

    const btn = document.getElementById('btn-submit-category');
    UI.setLoading(btn, true, 'Creando categoría...');

    const res = await API.post('/categories', { name, description });
    UI.setLoading(btn, false);

    if (!res.ok) {
      UI.showToast(res.error || 'No se pudo crear la categoría.', 'error');
      return;
    }

    UI.showToast('Categoría creada exitosamente.', 'success');
    UI.closeModal('modal-create-category');
    await this.loadAdminCategories();
    if (window.TICKETS) await TICKETS.loadCategories();
  }
};

window.ADMIN = ADMIN;
