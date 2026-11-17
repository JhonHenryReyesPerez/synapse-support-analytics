/** Siete indicadores de Spring Boot; el redondeo es solo de presentación. */
const ANALYTICS = {
  charts: { category: null, trend: null, priority: null },
  loadSeq: 0,
  lastData: null,
  categoryIds: [],
  priorityKeys: [],
  numbers: new Intl.NumberFormat('es-CO'),
  decimals: new Intl.NumberFormat('es-CO', { minimumFractionDigits: 1, maximumFractionDigits: 1 }),
  priorityLabels: { BAJA: 'Baja', MEDIA: 'Media', ALTA: 'Alta', CRITICA: 'Crítica' },

  async init() {
    this.bindEvents();
    document.getElementById('btn-retry-analytics')?.addEventListener('click', () => this.loadAnalytics());
    window.matchMedia?.('(prefers-reduced-motion: reduce)').addEventListener('change', () => this.applyTheme());
    await this.loadAnalytics();
  },

  bindEvents() {
    document.addEventListener('themechange', () => this.applyTheme());
    const btnApply = document.getElementById('btn-apply-analytics-filters');
    if (btnApply) {
      btnApply.addEventListener('click', () => {
        this.loadAnalytics();
      });
    }

    const btnReset = document.getElementById('btn-reset-analytics-filters');
    if (btnReset) {
      btnReset.addEventListener('click', () => {
        document.getElementById('analytics-filter-from').value = '';
        document.getElementById('analytics-filter-to').value = '';
        document.getElementById('analytics-filter-category').value = '';
        document.getElementById('analytics-filter-priority').value = '';
        document.getElementById('analytics-filter-granularity').value = 'DAY';
        this.loadAnalytics();
      });
    }
  },

  setFeedback(message, error = false) {
    const box = document.getElementById('analytics-feedback');
    if (!box) return;
    box.hidden = !message;
    box.classList.toggle('is-error', error);
    document.getElementById('analytics-feedback-message').textContent = message;
    document.getElementById('btn-retry-analytics').hidden = !error;
  },

  async loadAnalytics() {
    const from = document.getElementById('analytics-filter-from')?.value || '';
    const to = document.getElementById('analytics-filter-to')?.value || '';
    const categoryId = document.getElementById('analytics-filter-category')?.value || '';
    const priority = document.getElementById('analytics-filter-priority')?.value || '';
    const granularity = document.getElementById('analytics-filter-granularity')?.value || 'DAY';
    const seq = ++this.loadSeq;
    const button = document.getElementById('btn-apply-analytics-filters');
    const results = document.getElementById('analytics-results');
    if (from && to && from > to) {
      UI.setLoading(button, false);
      results?.setAttribute('aria-busy', 'false');
      this.setFeedback('El rango no es válido. Corrige las fechas y vuelve a filtrar. Los resultados visibles conservan su cohorte anterior.', true);
      return;
    }
    let query = `?granularity=${encodeURIComponent(granularity)}`;
    if (from) query += `&from=${encodeURIComponent(from)}`;
    if (to) query += `&to=${encodeURIComponent(to)}`;
    if (categoryId) query += `&categoryId=${encodeURIComponent(categoryId)}`;
    if (priority) query += `&priority=${encodeURIComponent(priority)}`;

    UI.setLoading(button, true, 'Actualizando…');
    results?.setAttribute('aria-busy', 'true');
    this.setFeedback(this.lastData ? 'Actualizando. Mientras tanto se muestran los resultados de la cohorte anterior.' : 'Cargando indicadores…');
    const res = await API.get(`/analytics/summary${query}`);
    if (seq !== this.loadSeq) return;
    UI.setLoading(button, false);
    results?.setAttribute('aria-busy', 'false');
    if (!res.ok) {
      const previous = this.lastData ? 'Se mantienen los resultados anteriores. ' : '';
      this.setFeedback(previous + (res.error || 'No se pudieron cargar los indicadores. Intenta de nuevo.'), true);
      return;
    }
    this.lastData = res.data || {};
    this.renderDashboard(this.lastData);
  },

  renderDashboard(data) {
    const put = (id, value) => { const el = document.getElementById(id); if (el) el.textContent = value; };
    const n = value => this.numbers.format(value);
    const d = value => this.decimals.format(value);
    put('kpi-total-incidents', data.totalIncidents == null ? '—' : n(data.totalIncidents));
    put('kpi-resolution-rate', data.resolution?.ratePercentage == null ? '—' : `${d(data.resolution.ratePercentage)} %`);
    put('kpi-resolved-count', data.resolution?.resolvedCount != null ? `${n(data.resolution.resolvedCount)} resueltos o cerrados` : 'Sin información de resolución');
    put('kpi-avg-resolution-time', data.resolutionTime?.averageHours == null ? '—' : `${d(data.resolutionTime.averageHours)} h`);
    put('kpi-resolution-sample', data.resolutionTime?.sampleSize != null ? `Horas transcurridas · muestra: ${n(data.resolutionTime.sampleSize)}` : 'Sin información de tiempos');
    put('kpi-most-recurrent', (data.mostRecurrentCategories || []).map(c => c.category).join(' / ') || '—');
    const cohort = data.cohort || {};
    const scope = { GLOBAL: 'Global', OWN: 'Mis incidentes', ASSIGNED: 'Mis asignados' }[cohort.scope] || 'Alcance de la sesión';
    const period = cohort.from || cohort.to ? `${cohort.from || 'Inicio'} — ${cohort.to || 'Hoy'}` : 'Todo el historial';
    const category = (data.byCategory || []).find(c => c.categoryId === cohort.categoryId)?.category;
    put('analytics-cohort', [scope, period, category || 'Todas las categorías', this.priorityLabels[cohort.priority] || 'Todas las prioridades'].join(' · '));
    put('analytics-generated', cohort.generatedAt ? `Calculado: ${UI.formatDate(cohort.generatedAt)} · ${cohort.timeZone || 'America/Bogota'} · Estado actual de la cohorte` : '');
    put('analytics-exclusions', data.resolutionTime?.excludedCount != null ? `${n(data.resolutionTime.excludedCount)} casos excluidos del promedio por fechas no válidas. El promedio incluye solo resoluciones con fecha válida.` : '');
    put('analytics-trend-caption', data.trend?.granularity === 'WEEK' ? 'Incidentes registrados por semana · inicio el lunes' : 'Incidentes registrados por día');
    put('analytics-priority-summary', (data.byPriority || []).map(p => `${this.priorityLabels[p.priority] || p.priority}: ${n(p.count)} (${d(p.percentage)} %)` ).join(' · '));

    this.renderCategoryChart(data.byCategory || []);
    this.renderTrendChart(data.trend?.points || [], data.trend?.granularity);
    this.renderPriorityChart(data.byPriority || []);
    const averages = Object.fromEntries((data.resolutionTime?.byCategory || []).map(c => [c.categoryId, c.averageHours]));
    this.renderCategoryBreakdownTable((data.byCategory || []).map(c => ({ ...c, averageHours: averages[c.categoryId] })));
    const trendBody = document.getElementById('analytics-trend-tbody');
    if (trendBody) trendBody.innerHTML = (data.trend?.points || []).map(p => `<tr><td>${UI.escapeHtml(p.periodStart)}</td><td>${n(p.count)}</td></tr>`).join('') || '<tr><td colspan="2">Sin datos en esta cohorte.</td></tr>';
    const incomplete = data.totalIncidents == null || !data.resolution || !data.resolutionTime || !data.trend || !Array.isArray(data.byCategory) || !Array.isArray(data.byPriority);
    this.setFeedback(incomplete ? 'La respuesta está incompleta. Los indicadores no disponibles se muestran con —.' : data.totalIncidents === 0 ? 'No hay incidentes para esta cohorte. Puedes ampliar el período o cambiar los filtros.' : typeof Chart === 'undefined' ? 'No se pudieron cargar los gráficos. Puedes consultar los valores en las tablas y los resúmenes.' : '');
    this.applyTheme();
  },

  chartOptions() {
    return {
      responsive: true, maintainAspectRatio: false,
      animation: window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches ? false : { duration: 180 },
      plugins: { legend: { display: false }, tooltip: { padding: 12 } },
      scales: {
        x: { grid: { display: false }, ticks: { maxTicksLimit: 7, maxRotation: 0, font: { family: 'IBM Plex Sans', size: 12 } } },
        y: { beginAtZero: true, ticks: { precision: 0, font: { family: 'IBM Plex Sans', size: 12 } } }
      }
    };
  },

  applyTheme() {
    const styles = getComputedStyle(document.documentElement);
    const color = token => (styles.getPropertyValue(token) || '').trim();
    const text = color('--text-muted');
    const border = color('--border-color-subtle');
    const surface = color('--chart-surface') || color('--bg-surface');
    const palette = Array.from({ length: 7 }, (_, i) => color(`--chart-category-${i + 1}`));
    Object.entries(this.charts).forEach(([name, chart]) => {
      if (!chart) return;
      chart.options.animation = window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches ? false : { duration: 180 };
      if (chart.options.plugins?.legend?.labels) chart.options.plugins.legend.labels.color = text;
      if (chart.options.plugins?.tooltip) Object.assign(chart.options.plugins.tooltip, {
        backgroundColor: color('--bg-surface-elevated'), titleColor: color('--text-main'), bodyColor: color('--text-main'), borderColor: color('--border-color'), borderWidth: 1
      });
      Object.values(chart.options.scales || {}).forEach(scale => {
        scale.ticks ||= {}; scale.grid ||= {}; scale.border ||= {};
        scale.ticks.color = text;
        scale.grid.color = border;
        scale.border.color = border;
      });
      const dataset = chart.data.datasets[0];
      if (name === 'category') {
        dataset.borderColor = surface;
        dataset.backgroundColor = this.categoryIds.map((id, i) => palette[Math.abs(Number(id) || i) % palette.length]);
      } else if (name === 'trend') {
        dataset.borderColor = color('--color-accent');
        dataset.pointBackgroundColor = color('--color-accent');
      } else if (name === 'priority') {
        dataset.backgroundColor = this.priorityKeys.map(key => color(`--priority-${key.toLowerCase()}`));
      }
      chart.update('none');
    });
  },

  renderCategoryChart(byCategory) {
    const ctx = document.getElementById('chart-category');
    if (!ctx || typeof Chart === 'undefined') return;
    this.charts.category?.destroy();
    this.categoryIds = byCategory.map(c => c.categoryId);
    this.charts.category = new Chart(ctx, {
      type: 'doughnut',
      data: { labels: byCategory.map(c => c.category), datasets: [{ data: byCategory.map(c => c.count), borderWidth: 2 }] },
      options: {
        responsive: true, maintainAspectRatio: false, cutout: '76%',
        plugins: {
          legend: { position: 'bottom', labels: { boxWidth: 8, boxHeight: 8, padding: 12, font: { family: 'IBM Plex Sans', size: 12 } } },
          tooltip: { callbacks: { label: item => `${this.numbers.format(item.raw)} incidentes (${this.decimals.format(byCategory[item.dataIndex].percentage)} %)` } }
        }
      }
    });
  },

  renderTrendChart(points, granularity) {
    const ctx = document.getElementById('chart-trend');
    if (!ctx || typeof Chart === 'undefined') return;
    this.charts.trend?.destroy();
    const date = value => new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'short' }).format(new Date(`${value}T12:00:00`));
    const options = this.chartOptions();
    options.plugins.tooltip.callbacks = {
      title: items => `${granularity === 'WEEK' ? 'Semana del' : 'Día'} ${points[items[0].dataIndex].periodStart}`,
      label: item => `${this.numbers.format(item.raw)} incidentes`
    };
    this.charts.trend = new Chart(ctx, {
      type: 'line',
      data: { labels: points.map(p => date(p.periodStart)), datasets: [{
        label: 'Incidentes registrados', data: points.map(p => p.count),
        fill: false, tension: 0, borderWidth: 2, pointRadius: points.length > 30 ? 0 : 2, pointHoverRadius: 5
      }] },
      options
    });
  },

  renderPriorityChart(byPriority) {
    const ctx = document.getElementById('chart-priority');
    if (!ctx || typeof Chart === 'undefined') return;
    this.charts.priority?.destroy();
    this.priorityKeys = byPriority.map(p => p.priority);
    const options = this.chartOptions();
    options.plugins.tooltip.callbacks = { label: item => `${this.numbers.format(item.raw)} incidentes (${this.decimals.format(byPriority[item.dataIndex].percentage)} %)` };
    this.charts.priority = new Chart(ctx, {
      type: 'bar',
      data: { labels: byPriority.map(p => this.priorityLabels[p.priority] || p.priority), datasets: [{ label: 'Incidentes', data: byPriority.map(p => p.count), borderRadius: 2, maxBarThickness: 44 }] },
      options
    });
  },

  renderCategoryBreakdownTable(catData) {
    const tbody = document.getElementById('analytics-category-tbody');
    if (!tbody) return;

    if (!catData || catData.length === 0) {
      tbody.innerHTML = `<tr><td colspan="4" style="text-align:center; color:var(--text-muted);">Sin datos para la cohorte seleccionada.</td></tr>`;
      return;
    }

    tbody.innerHTML = catData.map(c => {
      const catName = c.category || c.name || `Categoría #${c.categoryId || c.id}`;
      const count = this.numbers.format(c.count);
      const pct = c.percentage != null ? `${this.decimals.format(c.percentage)} %` : '—';
      const avgH = c.averageHours != null ? `${this.decimals.format(c.averageHours)} h` : '—';

      return `
        <tr>
          <td style="font-weight: var(--font-weight-medium);">${UI.escapeHtml(catName)}</td>
          <td>${count}</td>
          <td>${pct}</td>
          <td style="font-family: var(--font-family-mono);">${avgH}</td>
        </tr>
      `;
    }).join('');
  }
};

window.ANALYTICS = ANALYTICS;
