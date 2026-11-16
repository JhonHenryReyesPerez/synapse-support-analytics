/**
 * Synapse Support Analytics - Módulo de Analítica y Dashboard (RF06 / RF07)
 */
const ANALYTICS = {
  charts: {
    category: null,
    trend: null,
    priority: null
  },

  async init() {
    this.bindEvents();
    await this.loadAnalytics();
  },

  bindEvents() {
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

  async loadAnalytics() {
    const from = document.getElementById('analytics-filter-from')?.value || '';
    const to = document.getElementById('analytics-filter-to')?.value || '';
    const categoryId = document.getElementById('analytics-filter-category')?.value || '';
    const priority = document.getElementById('analytics-filter-priority')?.value || '';
    const granularity = document.getElementById('analytics-filter-granularity')?.value || 'DAY';

    let query = `?granularity=${encodeURIComponent(granularity)}`;
    if (from) query += `&from=${encodeURIComponent(from)}`;
    if (to) query += `&to=${encodeURIComponent(to)}`;
    if (categoryId) query += `&categoryId=${encodeURIComponent(categoryId)}`;
    if (priority) query += `&priority=${encodeURIComponent(priority)}`;

    const btnApply = document.getElementById('btn-apply-analytics-filters');
    UI.setLoading(btnApply, true, 'Actualizando datos...');

    const res = await API.get(`/analytics/summary${query}`);
    UI.setLoading(btnApply, false);

    if (!res.ok) {
      UI.showToast(res.error || 'Error al cargar los indicadores analíticos.', 'error');
      return;
    }

    this.renderDashboard(res.data);
  },

  renderDashboard(data) {
    // 1. KPI 1: Total de incidentes
    const totalEl = document.getElementById('kpi-total-incidents');
    if (totalEl) totalEl.textContent = data.totalIncidents ?? 0;

    // 2. KPI 4: Tasa de resolución
    const resRateEl = document.getElementById('kpi-resolution-rate');
    const resCountEl = document.getElementById('kpi-resolved-count');
    if (resRateEl) {
      const rate = data.resolution?.ratePercentage != null ? data.resolution.ratePercentage.toFixed(1) : '0.0';
      resRateEl.textContent = `${rate}%`;
    }
    if (resCountEl) {
      resCountEl.textContent = `${data.resolution?.resolvedCount || 0} incidentes resueltos o cerrados`;
    }

    // 3. KPI 5: Tiempo promedio de resolución en horas
    const avgHoursEl = document.getElementById('kpi-avg-resolution-time');
    const sampleSizeEl = document.getElementById('kpi-resolution-sample');
    if (avgHoursEl) {
      const avg = data.resolutionTime?.averageHours;
      avgHoursEl.textContent = (avg != null) ? `${avg.toFixed(1)} h` : '—';
    }
    if (sampleSizeEl) {
      sampleSizeEl.textContent = `Muestra: ${data.resolutionTime?.sampleSize || 0} casos calculados`;
    }

    // 4. KPI 6: Categoría más recurrente
    const recurEl = document.getElementById('kpi-most-recurrent');
    if (recurEl) {
      const recurrents = data.mostRecurrentCategories || [];
      if (recurrents.length > 0) {
        recurEl.textContent = recurrents.map(c => c.category).join(', ');
      } else {
        recurEl.textContent = '—';
      }
    }

    // Renderizar Gráficos con Chart.js (o fallback)
    this.renderCategoryChart(data.byCategory || []);
    this.renderTrendChart(data.trend?.points || [], data.trend?.granularity || 'Día');
    this.renderPriorityChart(data.byPriority || []);
    // Casos y porcentaje salen de byCategory; el promedio, de resolutionTime.byCategory (solo resueltos).
    const avgByCategory = Object.fromEntries(
      (data.resolutionTime?.byCategory || []).map(c => [c.categoryId, c.averageHours]));
    this.renderCategoryBreakdownTable(
      (data.byCategory || []).map(c => ({ ...c, averageHours: avgByCategory[c.categoryId] })));
  },

  renderCategoryChart(byCategory) {
    const ctx = document.getElementById('chart-category');
    if (!ctx || typeof Chart === 'undefined') return;

    if (this.charts.category) {
      this.charts.category.destroy();
    }

    const labels = byCategory.map(c => c.category || c.name || `Cat #${c.categoryId}`);
    const values = byCategory.map(c => c.count);

    this.charts.category = new Chart(ctx, {
      type: 'doughnut',
      data: {
        labels,
        datasets: [{
          data: values,
          backgroundColor: [
            '#2563eb', '#3b82f6', '#60a5fa', '#93c5fd',
            '#4f46e5', '#6366f1', '#818cf8', '#a5b4fc',
            '#059669', '#10b981', '#34d399', '#6ee7b7'
          ],
          borderWidth: 1,
          borderColor: '#ffffff'
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { position: 'right', labels: { boxWidth: 12, font: { size: 11 } } },
          tooltip: {
            callbacks: {
              label: (context) => {
                const total = context.dataset.data.reduce((a, b) => a + b, 0);
                const val = context.raw;
                const pct = total > 0 ? ((val / total) * 100).toFixed(1) : 0;
                return ` ${val} incidentes (${pct}%)`;
              }
            }
          }
        }
      }
    });
  },

  renderTrendChart(points, granularityLabel) {
    const ctx = document.getElementById('chart-trend');
    if (!ctx || typeof Chart === 'undefined') return;

    if (this.charts.trend) {
      this.charts.trend.destroy();
    }

    const labels = points.map(p => p.periodStart);
    const values = points.map(p => p.count);

    this.charts.trend = new Chart(ctx, {
      type: 'line',
      data: {
        labels,
        datasets: [{
          label: 'Incidentes registrados',
          data: values,
          borderColor: '#2563eb',
          backgroundColor: 'rgba(37, 99, 235, 0.1)',
          fill: true,
          tension: 0.25,
          pointRadius: points.length > 30 ? 2 : 4,
          pointBackgroundColor: '#2563eb'
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        scales: {
          x: {
            grid: { display: false },
            ticks: { maxTicksLimit: 12, font: { size: 10 } }
          },
          y: {
            beginAtZero: true,
            ticks: { precision: 0, font: { size: 10 } },
            grid: { color: '#f1f5f9' }
          }
        },
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              title: (items) => `Período: ${items[0].label}`,
              label: (item) => ` ${item.raw} incidentes`
            }
          }
        }
      }
    });
  },

  renderPriorityChart(byPriority) {
    const ctx = document.getElementById('chart-priority');
    if (!ctx || typeof Chart === 'undefined') return;

    if (this.charts.priority) {
      this.charts.priority.destroy();
    }

    const labels = byPriority.map(p => p.priority);
    const values = byPriority.map(p => p.count);
    const colors = {
      'BAJA': '#10b981',
      'MEDIA': '#3b82f6',
      'ALTA': '#f59e0b',
      'CRITICA': '#ef4444'
    };

    this.charts.priority = new Chart(ctx, {
      type: 'bar',
      data: {
        labels,
        datasets: [{
          label: 'Incidentes',
          data: values,
          backgroundColor: labels.map(l => colors[l] || '#64748b'),
          borderRadius: 6
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        scales: {
          x: { grid: { display: false } },
          y: { beginAtZero: true, ticks: { precision: 0 }, grid: { color: '#f1f5f9' } }
        },
        plugins: {
          legend: { display: false }
        }
      }
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
      const count = c.count || 0;
      const pct = c.percentage != null ? `${c.percentage.toFixed(1)}%` : '—';
      const avgH = c.averageHours != null ? `${c.averageHours.toFixed(1)} h` : '—';

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
