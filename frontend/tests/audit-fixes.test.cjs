/**
 * Regresiones de la auditoría del 16 de noviembre de 2026 (hallazgos 2 a 8 y 11).
 * Dobles de la API y DOM mínimo: comprueban la lógica, no el render de un navegador.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const { loadFrontend, deferred } = require('./harness');

const ok = data => ({ ok: true, status: 200, data });
const fail = (error, status = 409, data = null) => ({ ok: false, status, data, error });
const ticket = id => ({ id, title: `Incidente ${id}`, description: 'd', status: 'IN_PROGRESS', priority: 'MEDIA' });
const tick = () => new Promise(r => setTimeout(r, 0));

function fakeApi(routes) {
  const calls = [];
  const handle = method => async (url, body) => {
    calls.push({ method, url, body });
    const route = routes[`${method} ${url}`];
    if (route === undefined) return ok(method === 'GET' ? [] : {});
    return typeof route === 'function' ? route(body) : route;
  };
  return { calls, get: handle('GET'), post: handle('POST'), put: handle('PUT'), patch: handle('PATCH') };
}

function appWith(routes, scripts = ['ui.js', 'tickets.js', 'assistant.js'], user = { username: 'laura', role: 'USER' }) {
  const api = fakeApi(routes);
  const ctx = loadFrontend(scripts, { API: api, AUTH: { getUser: () => user } });
  return { ctx, api, el: id => ctx.document.getElementById(id) };
}

// ---------- 2. Borradores y contexto ----------

test('2: el comentario de A que termina con B abierto no borra el borrador de B', async () => {
  const posting = deferred();
  const { ctx, api, el } = appWith({
    'GET /tickets/1': ok(ticket(1)), 'GET /tickets/2': ok(ticket(2)),
    'POST /tickets/1/comments': posting.promise
  });
  await ctx.TICKETS.openTicketDetail(1);
  el('comment-input').value = 'comentario para A';
  const sending = ctx.TICKETS.handleAddComment();
  await ctx.TICKETS.openTicketDetail(2);
  el('comment-input').value = 'borrador de B';
  const before = api.calls.length;
  posting.resolve(ok({ id: 9 }));
  await sending;

  assert.equal(el('comment-input').value, 'borrador de B');
  assert.equal(ctx.TICKETS.currentTicket.id, 2);
  assert.deepEqual(api.calls.slice(before), []); // no recarga comentarios de A sobre la vista de B
});

test('2: al volver a A con un borrador nuevo, la respuesta del comentario anterior lo conserva', async () => {
  const posting = deferred();
  const { ctx, api, el } = appWith({
    'GET /tickets/1': ok(ticket(1)), 'GET /tickets/2': ok(ticket(2)),
    'POST /tickets/1/comments': posting.promise
  });
  await ctx.TICKETS.openTicketDetail(1);
  el('comment-input').value = 'primer comentario';
  const sending = ctx.TICKETS.handleAddComment();
  await ctx.TICKETS.openTicketDetail(2);
  await ctx.TICKETS.openTicketDetail(1);
  el('comment-input').value = 'segundo borrador';
  posting.resolve(ok({ id: 9 }));
  await sending;

  assert.equal(el('comment-input').value, 'segundo borrador');
  assert.equal(api.calls.at(-1).url, '/tickets/1/comments'); // A sigue abierto: sí se refresca
});

test('2: sin cambios de contexto el comentario enviado se limpia', async () => {
  const { ctx, el } = appWith({ 'GET /tickets/1': ok(ticket(1)) });
  await ctx.TICKETS.openTicketDetail(1);
  el('comment-input').value = '  hola  ';
  await ctx.TICKETS.handleAddComment();
  assert.equal(el('comment-input').value, '');
});

test('2: una transición de A que termina con B abierto no reabre A, pero actualiza la lista', async () => {
  const transition = deferred();
  const { ctx, api, el } = appWith({
    'GET /tickets/1': ok(ticket(1)), 'GET /tickets/2': ok(ticket(2)),
    'POST /tickets/1/transitions': transition.promise
  });
  await ctx.TICKETS.openTicketDetail(1);
  const running = ctx.TICKETS.executeTransition(1, 'START_PROGRESS');
  await ctx.TICKETS.openTicketDetail(2);
  const before = api.calls.length;
  transition.resolve(ok({ newStatus: 'IN_PROGRESS' }));

  assert.equal(await running, true);
  assert.equal(ctx.TICKETS.currentTicket.id, 2);
  assert.equal(el('detail-ticket-title').textContent, 'Incidente 2');
  const later = api.calls.slice(before).map(c => c.url);
  assert.equal(later.includes('/tickets/1'), false);
  assert.ok(later.some(url => url.startsWith('/tickets?')), later.join(' '));
});

test('2: una transición tardía no reabre el detalle cerrado y sí actualiza la lista', async () => {
  const transition = deferred();
  const { ctx, api } = appWith({
    'GET /tickets/1': ok(ticket(1)),
    'POST /tickets/1/transitions': transition.promise
  });
  await ctx.TICKETS.openTicketDetail(1);
  const running = ctx.TICKETS.executeTransition(1, 'START_PROGRESS');
  ctx.UI.closeModal('modal-ticket-detail');
  const before = api.calls.length;
  transition.resolve(ok({ newStatus: 'IN_PROGRESS' }));

  assert.equal(await running, true);
  assert.equal(ctx.UI.modalStack.length, 0);
  assert.equal(api.calls.slice(before).some(c => c.url === '/tickets/1'), false);
  assert.ok(api.calls.slice(before).some(c => c.url.startsWith('/tickets?')));
});

test('2: cerrar el detalle mientras se refresca tampoco permite reabrirlo', async () => {
  const refresh = deferred();
  let reads = 0;
  const { ctx } = appWith({
    'GET /tickets/1': () => (++reads === 1 ? ok(ticket(1)) : refresh.promise),
    'POST /tickets/1/transitions': ok({ newStatus: 'IN_PROGRESS' })
  });
  await ctx.TICKETS.openTicketDetail(1);
  const running = ctx.TICKETS.executeTransition(1, 'START_PROGRESS');
  await tick();
  assert.equal(reads, 2);
  ctx.UI.closeModal('modal-ticket-detail');
  refresh.resolve(ok(ticket(1)));
  assert.equal(await running, true);
  assert.equal(ctx.UI.modalStack.length, 0);
});

test('2: cerrar y volver al mismo incidente invalida el refresco de la transición anterior', async () => {
  const transition = deferred();
  const { ctx, api } = appWith({
    'GET /tickets/1': ok(ticket(1)),
    'POST /tickets/1/transitions': transition.promise
  });
  await ctx.TICKETS.openTicketDetail(1);
  const running = ctx.TICKETS.executeTransition(1, 'START_PROGRESS');
  ctx.UI.closeModal('modal-ticket-detail');
  await ctx.TICKETS.openTicketDetail(1);
  const before = api.calls.length;
  transition.resolve(ok({ newStatus: 'IN_PROGRESS' }));
  await running;
  assert.equal(api.calls.slice(before).some(c => c.url === '/tickets/1'), false);
  assert.equal(ctx.UI.modalStack.length, 1);
});

test('2: una transición refresca normalmente el detalle que permanece abierto', async () => {
  let reads = 0;
  const { ctx, el } = appWith({
    'GET /tickets/1': () => ok({ ...ticket(1), title: ++reads === 1 ? 'Anterior' : 'Actualizado' }),
    'POST /tickets/1/transitions': ok({ newStatus: 'IN_PROGRESS' })
  });
  await ctx.TICKETS.openTicketDetail(1);
  assert.equal(await ctx.TICKETS.executeTransition(1, 'START_PROGRESS'), true);
  assert.equal(el('detail-ticket-title').textContent, 'Actualizado');
  assert.equal(ctx.UI.modalStack.length, 1);
});

test('2: una asignación tardía tampoco reabre el detalle cerrado', async () => {
  const assignment = deferred();
  const { ctx } = appWith({
    'GET /tickets/1': ok(ticket(1)),
    'GET /technicians/available': ok([{ username: 'marta' }]),
    'PATCH /tickets/1/assign': assignment.promise
  });
  await ctx.TICKETS.openTicketDetail(1);
  await ctx.TICKETS.promptAssignTechnician(1, false);
  ctx.document.getElementById('assign-tech-select').value = 'marta';
  const running = ctx.document.getElementById('btn-generic-confirm').onclick();
  ctx.UI.closeModal('modal-generic');
  ctx.UI.closeModal('modal-ticket-detail');
  assignment.resolve(ok({}));
  await running;
  assert.equal(ctx.UI.modalStack.length, 0);
});

// ---------- 3. Conocimiento ----------

test('3: una lista de un alcance anterior no se pinta bajo el alcance actual', async () => {
  const published = deferred();
  const article = (id, title) => ({ id, title, problem: 'p', solution: 's' });
  const { ctx, el } = appWith({
    'GET /knowledge/articles?scope=PUBLISHED&page=0&size=20': published.promise,
    'GET /knowledge/articles?scope=MINE&page=0&size=20': ok({ content: [article(2, 'MIO')] })
  }, ['ui.js', 'knowledge.js']);

  const old = ctx.KNOWLEDGE.loadArticles();
  ctx.KNOWLEDGE.currentScope = 'MINE';
  await ctx.KNOWLEDGE.loadArticles();
  published.resolve(ok({ content: [article(1, 'PUBLICADO')] }));
  await old;
  assert.match(el('articles-grid').innerHTML, /MIO/);
  assert.doesNotMatch(el('articles-grid').innerHTML, /PUBLICADO/);

  // Un error antiguo tampoco reemplaza la lista vigente.
  const failing = deferred();
  ctx.API.get = async url => (url.includes('PUBLISHED') ? failing.promise : ok({ content: [article(2, 'MIO')] }));
  ctx.KNOWLEDGE.currentScope = 'PUBLISHED';
  const oldError = ctx.KNOWLEDGE.loadArticles();
  ctx.KNOWLEDGE.currentScope = 'MINE';
  await ctx.KNOWLEDGE.loadArticles();
  failing.resolve(fail('caído', 500));
  await oldError;
  assert.match(el('articles-grid').innerHTML, /MIO/);
});

test('3: un artículo que llega tarde no sustituye al pedido después ni al modal ya ocupado', async () => {
  const first = deferred();
  const art = (id, title) => ({ id, title, problem: '', symptoms: '', cause: '', solution: '', contentVersion: 1 });
  const { ctx, el } = appWith({
    'GET /knowledge/articles/1': first.promise,
    'GET /knowledge/articles/2': ok(art(2, 'SEGUNDO'))
  }, ['ui.js', 'knowledge.js']);

  const old = ctx.KNOWLEDGE.viewArticle(1);
  await ctx.KNOWLEDGE.viewArticle(2);
  first.resolve(ok(art(1, 'PRIMERO')));
  await old;
  assert.equal(el('modal-generic-title').textContent, 'SEGUNDO');

  // Modal compartido ocupado por otra acción mientras el artículo estaba en camino.
  ctx.UI.closeModal('modal-generic');
  const third = deferred();
  ctx.API.get = async () => third.promise;
  const pending = ctx.KNOWLEDGE.viewArticle(3);
  el('modal-generic-title').textContent = 'Resolver Incidente';
  ctx.UI.openModal('modal-generic');
  third.resolve(ok(art(3, 'TERCERO')));
  await pending;
  assert.equal(el('modal-generic-title').textContent, 'Resolver Incidente');
});

// ---------- 4. Cohorte de analítica ----------

function cohortLabel(data, categories) {
  const ctx = loadFrontend(['ui.js', 'analytics.js'], {
    API: {}, getComputedStyle: () => ({ getPropertyValue: () => '#706575' }),
    ...(categories ? { TICKETS: { categories } } : {})
  });
  for (const name of ['renderCategoryChart', 'renderTrendChart', 'renderPriorityChart', 'renderCategoryBreakdownTable']) {
    ctx.ANALYTICS[name] = () => {};
  }
  ctx.ANALYTICS.renderDashboard(data);
  return ctx.document.getElementById('analytics-cohort').textContent;
}

test('4: una categoría filtrada sin incidentes no se describe como «Todas las categorías»', () => {
  const empty = { totalIncidents: 0, byCategory: [], cohort: { scope: 'OWN', categoryId: 8 } };
  assert.match(cohortLabel(empty, [{ id: 8, name: 'Conectividad' }]), /Mis incidentes .* Conectividad /);
  assert.match(cohortLabel(empty), /Categoría #8/);
  assert.doesNotMatch(cohortLabel(empty), /Todas las categorías/);
  assert.match(cohortLabel({ totalIncidents: 0, byCategory: [], cohort: { scope: 'OWN' } }), /Todas las categorías/);
  const withRows = { byCategory: [{ categoryId: 8, category: 'Redes' }], cohort: { categoryId: 8 } };
  assert.match(cohortLabel(withRows), /Redes/);
});

// ---------- 5. Vigencia de las fuentes ----------

test('5: una fuente histórica (current=false) se advierte y conserva su evidencia y versión', () => {
  const { ctx, el } = appWith({});
  const answered = current => ({
    id: 4, ticketId: 1, requesterUsername: 'laura', status: 'ANSWERED', answer: 'r',
    sources: [{ title: 'VPN', articleVersion: 3, similarity: 0.9, evidence: 'TEXTO-USADO', current }]
  });
  ctx.ASSISTANT.setTicket(1);
  ctx.ASSISTANT.renderResponse(answered(true));
  const vigente = el('assistant-result-container').innerHTML;
  ctx.ASSISTANT.renderResponse(answered(false));
  const historica = el('assistant-result-container').innerHTML;

  assert.doesNotMatch(vigente, /Fuente histórica/);
  assert.match(historica, /Fuente histórica/);
  assert.match(historica, /TEXTO-USADO/);
  assert.match(historica, /v3/);
  assert.match(vigente, /v3/);
});

// ---------- 6. Error distinto de vacío ----------

test('6: un fallo al cargar comentarios o historial no se muestra como lista vacía y ofrece reintento', async () => {
  const { ctx, el } = appWith({
    'GET /tickets/1': ok(ticket(1)),
    'GET /tickets/1/comments': fail('Acceso denegado', 403),
    'GET /tickets/1/history': fail('Error interno', 500)
  });
  await ctx.TICKETS.openTicketDetail(1);

  const comments = el('detail-comments-list').innerHTML;
  const history = el('detail-history-timeline').innerHTML;
  assert.doesNotMatch(comments, /Sin comentarios|No hay comentarios/);
  assert.match(comments, /No se pudieron cargar los comentarios.*Acceso denegado/s);
  assert.match(comments, /TICKETS\.loadComments\(1\)/);
  assert.doesNotMatch(history, /Sin historial/);
  assert.match(history, /TICKETS\.loadHistory\(1\)/);
});

test('6: listas realmente vacías siguen diciéndolo', async () => {
  const { ctx, el } = appWith({ 'GET /tickets/1': ok(ticket(1)) });
  await ctx.TICKETS.openTicketDetail(1);
  assert.match(el('detail-comments-list').innerHTML, /No hay comentarios registrados/);
  assert.match(el('detail-history-timeline').innerHTML, /Sin historial/);
});

test('6: si falla el refresco del mismo incidente se conservan los comentarios ya mostrados', async () => {
  let attempt = 0;
  const { ctx, el } = appWith({
    'GET /tickets/1': ok(ticket(1)),
    'GET /tickets/1/comments': () => (++attempt === 1
      ? ok([{ authorUsername: 'ana', content: 'YA-VISIBLE', visibility: 'PUBLIC' }]) : fail('sin red', 0))
  });
  await ctx.TICKETS.openTicketDetail(1);
  await ctx.TICKETS.loadComments(1);
  assert.match(el('detail-comments-list').innerHTML, /YA-VISIBLE/);
  assert.equal(ctx.toasts.at(-1).type, 'error');
});

// ---------- 7. Detalle de validación ----------

test('7: el cliente entrega el detalle por campo de una validación fallida', async () => {
  const body = { error: 'Validación fallida', errors: { title: 'El título no puede superar 150 caracteres' } };
  const ctx = loadFrontend(['ui.js', 'api.js'], {
    fetch: async () => ({
      ok: false, status: 400, headers: { get: () => 'application/json' }, json: async () => body
    })
  });
  ctx.CONFIG.API_BASE_URL = 'http://backend/api';
  const res = await ctx.API.post('/tickets', {});

  assert.equal(res.error, 'Validación fallida: El título no puede superar 150 caracteres');
  assert.deepEqual(res.fieldErrors, body.errors);

  const marked = {};
  const title = ctx.document.getElementById('create-ticket-title');
  const desc = ctx.document.getElementById('create-ticket-description');
  title.setAttribute = (k, v) => { marked.title = v; };
  desc.setAttribute = (k, v) => { marked.desc = v; };
  ctx.UI.markFieldErrors(res.fieldErrors, { title: 'create-ticket-title', description: 'create-ticket-description' });
  assert.deepEqual(marked, { title: 'true', desc: 'false' });
  assert.equal(ctx.document.activeElement, title);
});

// ---------- 8. Cerrar sesión con la carga inicial pendiente ----------

test('8: cerrar sesión funciona aunque la carga inicial no haya terminado', async () => {
  const stuck = deferred();
  const listeners = {};
  let ready, loggedOut = false;
  const element = id => ({
    id, style: {}, dataset: {}, textContent: '', classList: { toggle() {}, add() {}, remove() {} },
    setAttribute() {}, getAttribute: () => 'false', focus() {},
    addEventListener(type, fn) { listeners[`${id}:${type}`] = fn; }
  });
  const elements = {};
  loadFrontend(['app.js'], {
    document: {
      addEventListener(type, fn) { if (type === 'DOMContentLoaded') ready = fn; },
      getElementById: id => (elements[id] ??= element(id)),
      querySelectorAll: () => []
    },
    AUTH: { requireAuth: () => true, getUser: () => ({ username: 'ana', role: 'USER' }), logout() { loggedOut = true; } },
    TICKETS: { init: () => stuck.promise },
    confirm: () => true
  });

  ready(); // la inicialización queda esperando a TICKETS.init
  await tick();
  assert.equal(typeof listeners['btn-app-logout:click'], 'function');
  listeners['btn-app-logout:click']();
  assert.equal(loggedOut, true);
});

// ---------- 11. Reintento del asistente ----------

test('11: tras un fallo de transporte se repite la misma operación con el mismo requestId', async () => {
  let attempt = 0;
  const { ctx, api, el } = appWith({
    'POST /tickets/1/assistance': () => (++attempt === 1
      ? fail('No se pudo conectar', 0)
      : ok({ id: 7, ticketId: 1, status: 'ANSWERED', answer: 'r', requesterUsername: 'laura' }))
  });
  await ctx.ASSISTANT.ask(1, '¿por qué falla?');
  assert.match(el('assistant-result-container').innerHTML, /se recuperará esa misma consulta/);
  await ctx.ASSISTANT.ask(1, '¿por qué falla?');
  await ctx.ASSISTANT.ask(1, '¿por qué falla?'); // ya resuelta: pedir otra respuesta es una operación nueva

  const [first, second, third] = api.calls.filter(c => c.method === 'POST').map(c => c.body);
  assert.equal(second.requestId, first.requestId);
  assert.equal(second.retryOfQueryId, undefined);
  assert.notEqual(third.requestId, first.requestId);
});

test('11: una pregunta distinta o un 409 no reutilizan el requestId', async () => {
  const replies = [fail('sin red', 0), fail('Ya tienes una consulta en proceso', 409), fail('x', 409)];
  const { ctx, api } = appWith({ 'POST /tickets/1/assistance': () => replies.shift() });
  await ctx.ASSISTANT.ask(1, 'pregunta A');
  await ctx.ASSISTANT.ask(1, 'pregunta B');
  await ctx.ASSISTANT.ask(1, 'pregunta B');
  const ids = api.calls.map(c => c.body.requestId);
  assert.equal(new Set(ids).size, 3);
});

test('11: tras un error terminal el reintento es una consulta nueva enlazada con retryOfQueryId', async () => {
  const replies = [
    fail('Tiempo agotado', 504, { code: 'N8N_TIMEOUT', queryId: 85, requestId: 'x' }),
    fail('sin red', 0),
    ok({ id: 86, ticketId: 1, status: 'NO_RESULTS', requesterUsername: 'laura' }),
    ok({ id: 87, ticketId: 1, status: 'NO_RESULTS', requesterUsername: 'laura' })
  ];
  const { ctx, api } = appWith({ 'POST /tickets/1/assistance': () => replies.shift() });
  await ctx.ASSISTANT.ask(1);
  await ctx.ASSISTANT.ask(1);
  await ctx.ASSISTANT.ask(1); // el reintento enlazado quedó incierto: se repite idéntico
  await ctx.ASSISTANT.ask(1);

  const [first, retry, repeat, fresh] = api.calls.filter(c => c.method === 'POST').map(c => c.body);
  assert.equal(first.retryOfQueryId, undefined);
  assert.equal(retry.retryOfQueryId, 85);
  assert.notEqual(retry.requestId, first.requestId);
  assert.deepEqual(repeat, retry);
  assert.equal(fresh.retryOfQueryId, undefined);
});

test('11: una consulta recuperada que sigue PENDING conserva su identidad; si resultó ERROR se enlaza', async () => {
  const replies = [
    fail('sin red', 0),
    ok({ id: 90, ticketId: 1, status: 'PENDING', requesterUsername: 'laura' }),
    ok({ id: 90, ticketId: 1, status: 'ERROR', requesterUsername: 'laura' }),
    ok({ id: 91, ticketId: 1, status: 'ANSWERED', answer: 'r', requesterUsername: 'laura' })
  ];
  const { ctx, api } = appWith({ 'POST /tickets/1/assistance': () => replies.shift() });
  for (let i = 0; i < 4; i++) await ctx.ASSISTANT.ask(1);

  const bodies = api.calls.filter(c => c.method === 'POST').map(c => c.body);
  assert.equal(bodies[1].requestId, bodies[0].requestId);
  assert.equal(bodies[2].requestId, bodies[0].requestId);
  assert.notEqual(bodies[3].requestId, bodies[0].requestId);
  assert.equal(bodies[3].retryOfQueryId, 90);
});

test('11: consultar B no pierde la identidad incierta de A al volver sin recargar', async () => {
  let attempts = 0;
  const { ctx, api } = appWith({
    'POST /tickets/1/assistance': () => (++attempts === 1 ? fail('sin red', 0)
      : ok({ id: 90, ticketId: 1, status: 'NO_RESULTS' })),
    'POST /tickets/2/assistance': ok({ id: 91, ticketId: 2, status: 'NO_RESULTS' })
  });
  await ctx.ASSISTANT.ask(1, 'pregunta A');
  await ctx.ASSISTANT.ask(2, 'pregunta B');
  await ctx.ASSISTANT.ask(1, 'pregunta A');
  await ctx.ASSISTANT.ask(1, 'pregunta A');
  const bodies = api.calls.filter(c => c.method === 'POST' && c.url === '/tickets/1/assistance').map(c => c.body);
  assert.deepEqual(bodies[1], bodies[0]);
  assert.notEqual(bodies[2].requestId, bodies[0].requestId); // ya resuelta: ahora sí es otra consulta
});

test('11: un ERROR tardío de A conserva su enlace de reintento mientras B está abierto', async () => {
  const oldAsk = deferred();
  let attempts = 0;
  const { ctx, api } = appWith({
    'POST /tickets/1/assistance': () => (++attempts === 1 ? oldAsk.promise
      : ok({ id: 92, ticketId: 1, status: 'NO_RESULTS' })),
    'POST /tickets/2/assistance': ok({ id: 91, ticketId: 2, status: 'NO_RESULTS' })
  });
  const running = ctx.ASSISTANT.ask(1, 'pregunta A');
  await ctx.ASSISTANT.ask(2, 'pregunta B');
  oldAsk.resolve(fail('Tiempo agotado', 504, { queryId: 90, code: 'N8N_TIMEOUT' }));
  await running;
  assert.equal(ctx.ASSISTANT.currentTicketId, 2);
  await ctx.ASSISTANT.ask(1, 'pregunta A');
  const bodies = api.calls.filter(c => c.method === 'POST' && c.url === '/tickets/1/assistance').map(c => c.body);
  assert.equal(bodies[1].retryOfQueryId, 90);
  assert.notEqual(bodies[1].requestId, bodies[0].requestId);
});
