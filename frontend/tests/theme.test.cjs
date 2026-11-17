const test = require('node:test');
const assert = require('node:assert/strict');
const { loadFrontend } = require('./harness');

function page(saved, systemDark, storageBlocked = false) {
  const listeners = {};
  const root = { dataset: {} };
  const button = {
    setAttribute(name, value) { this[name] = value; },
    addEventListener(name, callback) { this[name] = callback; }
  };
  const storage = {
    getItem() { if (storageBlocked) throw Error('blocked'); return saved; },
    setItem(key, value) { if (storageBlocked) throw Error('blocked'); saved = value; }
  };
  const ctx = loadFrontend(['theme.js'], {
    Event,
    matchMedia: () => ({ matches: systemDark }),
    localStorage: storage,
    document: {
      documentElement: root,
      querySelectorAll: () => [button],
      dispatchEvent() {},
      addEventListener: (name, callback) => { listeners[name] = callback; }
    }
  });
  listeners.DOMContentLoaded();
  return { ctx, root, button, saved: () => saved };
}

test('uses the system theme until the user chooses, then persists the toggle', () => {
  const current = page(null, true);
  assert.equal(current.root.dataset.theme, 'dark');
  assert.equal(current.button['aria-pressed'], 'true');
  assert.equal(current.button.textContent, 'Modo claro');
  current.button.click();
  assert.equal(current.root.dataset.theme, 'light');
  assert.equal(current.saved(), 'light');
  assert.equal(current.button.textContent, 'Modo oscuro');
  assert.equal(page(current.saved(), true).root.dataset.theme, 'light');
  assert.equal(page('dark', false).root.dataset.theme, 'dark');
});

test('theme switching still works when browser storage is blocked', () => {
  const current = page(null, false, true);
  assert.equal(current.root.dataset.theme, 'light');
  assert.doesNotThrow(() => current.button.click());
  assert.equal(current.root.dataset.theme, 'dark');
});

test('existing charts update their labels and borders without fetching new data', () => {
  const ctx = loadFrontend(['analytics.js'], {
    getComputedStyle: () => ({ getPropertyValue: name => ({
      '--text-muted': '#b4c1d3', '--border-color-subtle': '#2d3d55', '--bg-surface': '#172033'
    })[name] })
  });
  const chart = {
    options: { plugins: { legend: { labels: {} } }, scales: { x: { ticks: {}, grid: {}, border: {} } } },
    data: { datasets: [{}] },
    update(mode) { this.updated = mode; }
  };
  ctx.ANALYTICS.charts.category = chart;
  ctx.ANALYTICS.applyTheme();
  assert.equal(chart.options.plugins.legend.labels.color, '#b4c1d3');
  assert.equal(chart.options.scales.x.ticks.color, '#b4c1d3');
  assert.equal(chart.options.scales.x.grid.color, '#2d3d55');
  assert.equal(chart.data.datasets[0].borderColor, '#172033');
  assert.equal(chart.updated, 'none');
});
