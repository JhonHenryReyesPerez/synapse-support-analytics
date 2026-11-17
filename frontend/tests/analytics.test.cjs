const test = require('node:test');
const assert = require('node:assert/strict');
const { loadFrontend, deferred } = require('./harness');

function page(get) {
  return loadFrontend(['ui.js', 'analytics.js'], {
    API: { get },
    getComputedStyle: () => ({ getPropertyValue: () => '#706575' })
  });
}

test('a late analytics response cannot replace the latest cohort or clear its loading state', async () => {
  const first = deferred(), second = deferred();
  let calls = 0;
  const ctx = page(() => ++calls === 1 ? first.promise : second.promise);
  const rendered = [];
  ctx.ANALYTICS.renderDashboard = data => rendered.push(data);
  const oldRequest = ctx.ANALYTICS.loadAnalytics();
  const latestRequest = ctx.ANALYTICS.loadAnalytics();
  first.resolve({ ok: true, data: { totalIncidents: 100 } });
  await oldRequest;
  assert.equal(rendered.length, 0);
  assert.equal(ctx.document.getElementById('btn-apply-analytics-filters').disabled, true);
  second.resolve({ ok: true, data: { totalIncidents: 7 } });
  await latestRequest;
  assert.deepEqual(rendered, [{ totalIncidents: 7 }]);
  assert.equal(ctx.document.getElementById('btn-apply-analytics-filters').disabled, false);
});

test('a failed refresh identifies retained data and offers retry', async () => {
  const ctx = page(async () => ({ ok: false, error: 'Servidor no disponible' }));
  ctx.ANALYTICS.lastData = { totalIncidents: 18 };
  await ctx.ANALYTICS.loadAnalytics();
  assert.match(ctx.document.getElementById('analytics-feedback-message').textContent, /resultados anteriores.*Servidor no disponible/);
  assert.equal(ctx.ANALYTICS.lastData.totalIncidents, 18);
  assert.equal(ctx.document.getElementById('btn-retry-analytics').hidden, false);
});

test('invalid dates invalidate an older pending response without sending another request', async () => {
  const request = deferred();
  let calls = 0, rendered = false;
  const ctx = page(() => { calls++; return request.promise; });
  ctx.ANALYTICS.renderDashboard = () => { rendered = true; };
  const pending = ctx.ANALYTICS.loadAnalytics();
  ctx.document.getElementById('analytics-filter-from').value = '2026-11-20';
  ctx.document.getElementById('analytics-filter-to').value = '2026-11-01';
  await ctx.ANALYTICS.loadAnalytics();
  request.resolve({ ok: true, data: {} });
  await pending;
  assert.equal(calls, 1);
  assert.equal(rendered, false);
  assert.match(ctx.document.getElementById('analytics-feedback-message').textContent, /rango no es válido/);
  assert.equal(ctx.document.getElementById('btn-apply-analytics-filters').disabled, false);
});

test('an empty cohort keeps a null average distinct from zero and remains readable without Chart.js', () => {
  const ctx = page(() => { throw Error('No network expected'); });
  const data = {
    cohort: { scope: 'OWN' }, totalIncidents: 0,
    resolution: { resolvedCount: 0, ratePercentage: 0 },
    resolutionTime: { averageHours: null, sampleSize: 0, excludedCount: 0, byCategory: [] },
    trend: { granularity: 'DAY', points: [] }, mostRecurrentCategories: [], byCategory: [], byPriority: []
  };
  ctx.ANALYTICS.renderDashboard(data);
  assert.equal(ctx.document.getElementById('kpi-total-incidents').textContent, '0');
  assert.equal(ctx.document.getElementById('kpi-avg-resolution-time').textContent, '—');
  assert.match(ctx.document.getElementById('analytics-cohort').textContent, /Mis incidentes/);
  assert.match(ctx.document.getElementById('analytics-feedback-message').textContent, /No hay incidentes/);
  data.totalIncidents = 1;
  data.byCategory = [{ categoryId: 1, category: '<script>', count: 1, percentage: 100 }];
  ctx.ANALYTICS.renderDashboard(data);
  assert.match(ctx.document.getElementById('analytics-feedback-message').textContent, /tablas/);
  assert.match(ctx.document.getElementById('analytics-category-tbody').innerHTML, /&lt;script&gt;/);
  ctx.ANALYTICS.renderDashboard({});
  assert.equal(ctx.document.getElementById('kpi-total-incidents').textContent, '—');
  assert.match(ctx.document.getElementById('analytics-feedback-message').textContent, /incompleta/);
});

test('theme changes recolor all charts and tooltips and respect reduced motion without requests', () => {
  const tokens = {
    '--text-muted': '#beb0bd', '--text-main': '#eee5dc', '--color-accent': '#e6a07d',
    '--bg-surface-elevated': '#2c252e', '--chart-surface': '#1a171c', '--priority-alta': '#e2c180',
    '--chart-category-2': '#bda4cc'
  };
  const ctx = loadFrontend(['analytics.js'], {
    matchMedia: () => ({ matches: true }),
    getComputedStyle: () => ({ getPropertyValue: key => tokens[key] || '' }),
    API: { get() { throw Error('Theme must not request data'); } }
  });
  const chart = () => ({ options: { plugins: { tooltip: {}, legend: { labels: {} } } }, data: { datasets: [{}] }, update(mode) { this.updated = mode; } });
  ctx.ANALYTICS.charts = { category: chart(), trend: chart(), priority: chart() };
  ctx.ANALYTICS.categoryIds = [1];
  ctx.ANALYTICS.priorityKeys = ['ALTA'];
  ctx.ANALYTICS.applyTheme();
  assert.equal(ctx.ANALYTICS.charts.category.data.datasets[0].backgroundColor[0], '#bda4cc');
  assert.equal(ctx.ANALYTICS.charts.trend.data.datasets[0].borderColor, '#e6a07d');
  assert.equal(ctx.ANALYTICS.charts.priority.data.datasets[0].backgroundColor[0], '#e2c180');
  for (const c of Object.values(ctx.ANALYTICS.charts)) {
    assert.equal(c.options.animation, false);
    assert.equal(c.options.plugins.tooltip.bodyColor, '#eee5dc');
    assert.equal(c.updated, 'none');
  }
});
