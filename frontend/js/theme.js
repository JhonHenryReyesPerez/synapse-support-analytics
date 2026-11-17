/** Aplica el tema antes de pintar la página y conserva la elección entre pantallas. */
const THEME = {
  isDark: false,

  apply(isDark) {
    this.isDark = isDark;
    document.documentElement.dataset.theme = isDark ? 'dark' : 'light';
    document.querySelectorAll('[data-theme-toggle]').forEach(button => {
      button.setAttribute('aria-pressed', String(isDark));
      button.textContent = isDark ? 'Modo claro' : 'Modo oscuro';
    });
    const themeColor = document.querySelector?.('meta[name="theme-color"]');
    if (themeColor) themeColor.content = isDark ? '#1a171c' : '#f4f0e8';
    document.dispatchEvent(new Event('themechange'));
  },

  toggle() {
    this.apply(!this.isDark);
    try { localStorage.setItem('synapse_theme', this.isDark ? 'dark' : 'light'); } catch {}
  }
};
window.THEME = THEME;

let savedTheme;
try { savedTheme = localStorage.getItem('synapse_theme'); } catch {}
THEME.apply(savedTheme === 'dark' || (savedTheme !== 'light' && window.matchMedia('(prefers-color-scheme: dark)').matches));
document.addEventListener('DOMContentLoaded', () => {
  THEME.apply(THEME.isDark);
  document.querySelectorAll('[data-theme-toggle]').forEach(button => {
    button.addEventListener('click', () => THEME.toggle());
  });
});
