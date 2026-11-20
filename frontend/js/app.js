/**
 * Synapse Support Analytics - Controlador Principal de la Aplicación (App)
 */
document.addEventListener('DOMContentLoaded', async () => {
  // 1. Proteger acceso: si no hay token o la sesión no es válida, redirigir a login
  if (!AUTH.requireAuth()) {
    return;
  }

  const currentUser = AUTH.getUser();
  if (currentUser && currentUser.mustChangePassword) {
    window.location.href = CONFIG.ROUTES.LOGIN;
    return;
  }

  // 2. Renderizar datos del usuario en sidebar y header
  renderUserProfile(currentUser);

  // 3. Inicializar navegación entre secciones
  initNavigation(currentUser);

  // Cerrar sesión se registra antes de las cargas remotas: debe funcionar aunque el backend no responda.
  const btnLogout = document.getElementById('btn-app-logout');
  if (btnLogout) {
    btnLogout.addEventListener('click', () => {
      if (confirm('¿Deseas cerrar tu sesión?')) {
        AUTH.logout();
      }
    });
  }

  // 4. Inicializar módulos base
  if (window.TICKETS) await TICKETS.init();
  if (window.ANALYTICS) await ANALYTICS.init();
  if (window.KNOWLEDGE) await KNOWLEDGE.init();
  if (window.RAG) await RAG.init();
  if (currentUser && currentUser.role === 'ADMIN' && window.ADMIN) {
    await ADMIN.init();
  }
});

function renderUserProfile(user) {
  if (!user) return;

  const nameEl = document.getElementById('sidebar-user-name');
  const roleEl = document.getElementById('sidebar-user-role');
  const avatarEl = document.getElementById('sidebar-user-avatar');

  const username = user.username || 'Usuario';
  const role = user.role || 'USER';

  if (nameEl) nameEl.textContent = user.fullName || username;
  if (roleEl) roleEl.textContent = role;
  if (avatarEl) avatarEl.textContent = username.charAt(0).toUpperCase();

  // Control de visibilidad por rol (ej. sección Admin para no administradores)
  if (role !== 'ADMIN') {
    document.querySelectorAll('.admin-only').forEach(el => {
      el.style.display = 'none';
    });
  }
  if (role === 'USER') {
    document.querySelectorAll('.tech-admin-only').forEach(el => {
      el.style.display = 'none';
    });
  } else {
    // Solo el rol USER registra incidentes.
    const btnNewTicket = document.getElementById('btn-new-ticket');
    if (btnNewTicket) btnNewTicket.style.display = 'none';
  }
}

function initNavigation(user) {
  const navItems = document.querySelectorAll('.sidebar-nav .nav-item');
  const views = document.querySelectorAll('.view-section');
  const pageTitle = document.getElementById('header-view-title');
  const description = document.getElementById('header-view-description');
  const eyebrow = document.getElementById('header-view-eyebrow');
  const navigation = document.getElementById('app-navigation');
  const menu = document.getElementById('btn-navigation-toggle');

  const setMenuOpen = open => {
    navigation.classList.toggle('is-menu-open', open);
    menu.setAttribute('aria-expanded', String(open));
  };
  menu.addEventListener('click', () => setMenuOpen(menu.getAttribute('aria-expanded') !== 'true'));
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && menu.getAttribute('aria-expanded') === 'true') {
      setMenuOpen(false);
      menu.focus();
    }
  });

  const viewTitles = {
    'view-dashboard': ['El pulso del soporte.', 'Volumen, conocimiento y resolución en una misma lectura.', 'Análisis de incidentes'],
    'view-tickets': ['Incidentes', 'Cada solicitud, su contexto y el siguiente paso.', 'Gestión de soporte'],
    'view-knowledge': ['Conocimiento compartido.', 'Procedimientos documentados para resolver mejor.', 'Biblioteca técnica'],
    'view-admin': ['Administración', 'Personas, accesos y categorías de soporte.', 'Organización']
  };

  navItems.forEach(item => {
    item.addEventListener('click', () => {
      const targetView = item.dataset.view;
      if (!targetView) return;

      // Actualizar estado activo en navegación
      navItems.forEach(n => {
        n.classList.remove('is-active');
        n.removeAttribute('aria-current');
      });
      item.classList.add('is-active');
      item.setAttribute('aria-current', 'page');
      setMenuOpen(false);

      // Actualizar sección activa
      views.forEach(v => {
        if (v.id === targetView) {
          v.classList.add('is-active');
        } else {
          v.classList.remove('is-active');
        }
      });

      // Actualizar título en barra superior
      if (pageTitle && viewTitles[targetView]) {
        pageTitle.textContent = viewTitles[targetView][0];
        description.textContent = viewTitles[targetView][1];
        eyebrow.textContent = `Synapse / ${viewTitles[targetView][2]}`;
        pageTitle.focus({ preventScroll: true });
      }

      // Disparar recargas específicas según vista
      if (targetView === 'view-dashboard' && window.ANALYTICS) {
        ANALYTICS.loadAnalytics();
      } else if (targetView === 'view-tickets' && window.TICKETS) {
        TICKETS.loadTickets();
      } else if (targetView === 'view-knowledge' && window.KNOWLEDGE) {
        KNOWLEDGE.loadArticles();
      } else if (targetView === 'view-admin' && window.ADMIN) {
        ADMIN.loadUsers();
        ADMIN.loadAdminCategories();
      }
    });
  });
}
