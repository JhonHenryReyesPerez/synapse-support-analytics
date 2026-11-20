/**
 * Pruebas de regresión del frontend. Desde frontend/: node --test tests/frontend.test.js tests/auth-session.test.cjs
 * Usan dobles de la API: no llaman al backend ni a servicios de IA.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const { loadFrontend, deferred } = require('./harness');

test('los iconos de la interfaz apuntan a simbolos SVG locales y no quedan emojis', () => {
  const fs = require('node:fs');
  const path = require('node:path');
  const root = path.join(__dirname, '..');
  const sprite = fs.readFileSync(path.join(root, 'assets/icons.svg'), 'utf8');
  const symbols = new Set(Array.from(sprite.matchAll(/<symbol id="([^"]+)"/g), m => m[1]));
  const files = ['index.html', 'app.html', ...fs.readdirSync(path.join(root, 'js')).map(f => `js/${f}`)];
  for (const file of files) {
    const source = fs.readFileSync(path.join(root, file), 'utf8');
    assert.doesNotMatch(source, /\p{Extended_Pictographic}/u, file);
    for (const match of source.matchAll(/icons\.svg#([\w-]+)|(?:UI|this)\.icon\('([\w-]+)'\)/g)) {
      assert.ok(symbols.has(match[1] || match[2]), `${file}: ${match[0]}`);
    }
  }
  const ctx = loadFrontend(['ui.js']);
  assert.match(ctx.UI.icon('check'), /aria-hidden="true" focusable="false"/);
});

const ok = data => ({ ok: true, status: 200, data });
const fail = (error, status = 409, data = null) => ({ ok: false, status, data, error });
const ticket = id => ({ id, title: `Incidente ${id}`, description: 'd', status: 'IN_PROGRESS', priority: 'MEDIA' });
const query = (id, ticketId, extra = {}) => ({
  id, ticketId, requesterUsername: 'laura', question: `Pregunta ${id}`, status: 'ANSWERED', answer: 'r', ...extra
});
const page = (content, pageNumber, totalPages) => ({ content, page: pageNumber, size: 20, totalElements: 0, totalPages });

/** API de prueba: cada ruta devuelve lo que diga `routes` (valor, función o promesa diferida). */
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

function appWith(routes, user = { username: 'laura', role: 'USER' }) {
  const api = fakeApi(routes);
  const ctx = loadFrontend(['ui.js', 'tickets.js', 'assistant.js'], { API: api, AUTH: { getUser: () => user } });
  return { ctx, api, el: id => ctx.document.getElementById(id) };
}

// ---------- 1. Respuestas asíncronas obsoletas ----------

test('el detalle de un incidente pedido antes no pisa al que se abrió después', async () => {
  const slow = deferred();
  const { ctx, el } = appWith({ 'GET /tickets/1': slow.promise, 'GET /tickets/2': ok(ticket(2)) });

  const first = ctx.TICKETS.openTicketDetail(1);
  await ctx.TICKETS.openTicketDetail(2);
  slow.resolve(ok(ticket(1)));
  await first;

  assert.equal(ctx.TICKETS.currentTicket.id, 2);
  assert.equal(el('detail-ticket-title').textContent, 'Incidente 2');
  assert.equal(ctx.ASSISTANT.currentTicketId, 2);
});

test('comentarios e historial de estados del incidente anterior se descartan', async () => {
  const comments = deferred();
  const history = deferred();
  const { ctx, el } = appWith({
    'GET /tickets/1': ok(ticket(1)),
    'GET /tickets/2': ok(ticket(2)),
    'GET /tickets/1/comments': comments.promise,
    'GET /tickets/1/history': history.promise
  });

  const first = ctx.TICKETS.openTicketDetail(1);
  await new Promise(r => setTimeout(r, 0)); // deja que el detalle de 1 llegue y pida sus comentarios
  await ctx.TICKETS.openTicketDetail(2);
  comments.resolve(ok([{ authorUsername: 'x', content: 'comentario del incidente 1' }]));
  history.resolve(ok([{ newStatus: 'ESTADO-DEL-1', changedBy: 'x' }]));
  await first;

  assert.doesNotMatch(el('detail-comments-list').innerHTML, /incidente 1/);
  assert.doesNotMatch(el('detail-history-timeline').innerHTML, /ESTADO-DEL-1/);
});

test('un listado de filtros anteriores no reemplaza al más reciente', async () => {
  const slow = deferred();
  let n = 0;
  const { ctx, el } = appWith({
    'GET /tickets?page=0&size=10': () => (++n === 1 ? slow.promise : ok(page([ticket(22)], 0, 1)))
  });

  const first = ctx.TICKETS.loadTickets();
  await ctx.TICKETS.loadTickets();
  slow.resolve(ok(page([ticket(11)], 0, 1)));
  await first;

  assert.match(el('tickets-table-body').innerHTML, /Incidente 22/);
  assert.doesNotMatch(el('tickets-table-body').innerHTML, /Incidente 11/);
});

test('la respuesta del asistente de otro incidente no se muestra ni bloquea el botón', async () => {
  const slow = deferred();
  const { ctx, el } = appWith({ 'POST /tickets/1/assistance': slow.promise });

  const asking = ctx.ASSISTANT.ask(1, '¿por qué?');
  assert.equal(el('btn-ask-assistant').disabled, true);
  ctx.ASSISTANT.setTicket(2);
  assert.equal(el('btn-ask-assistant').disabled, false);
  slow.resolve(ok(query(9, 1, { answer: 'RESPUESTA-DEL-1' })));
  await asking;

  assert.equal(el('assistant-result-container').innerHTML, '');
  assert.equal(ctx.ASSISTANT.historyById[9], undefined);
});

test('el historial de asistencia de otro incidente no se muestra', async () => {
  const slow = deferred();
  const { ctx, el } = appWith({ 'GET /tickets/1/assistance?page=0': slow.promise });

  ctx.ASSISTANT.setTicket(1);
  const loading = ctx.ASSISTANT.loadHistory(1);
  ctx.ASSISTANT.setTicket(2);
  slow.resolve(ok(page([query(9, 1)], 0, 1)));
  await loading;

  assert.equal(el('assistant-history-list').innerHTML, '');
  assert.equal(ctx.ASSISTANT.historyIds.length, 0);
});

// ---------- 2. Transiciones ----------

test('volver de A a B y a A descarta el primer detalle de A', async () => {
  const slow = deferred();
  let requests = 0;
  const { ctx } = appWith({
    'GET /tickets/1': () => (++requests === 1 ? slow.promise : ok({ ...ticket(1), title: 'Actualizado' })),
    'GET /tickets/2': ok(ticket(2))
  });
  const first = ctx.TICKETS.openTicketDetail(1);
  await ctx.TICKETS.openTicketDetail(2);
  await ctx.TICKETS.openTicketDetail(1);
  slow.resolve(ok({ ...ticket(1), title: 'Obsoleto' }));
  await first;
  assert.equal(ctx.TICKETS.currentTicket.title, 'Actualizado');
});

test('volver de A a B y a A no restaura la respuesta pendiente de A', async () => {
  const slow = deferred();
  const { ctx, el } = appWith({ 'POST /tickets/1/assistance': slow.promise });
  const asking = ctx.ASSISTANT.ask(1);
  ctx.ASSISTANT.setTicket(2);
  ctx.ASSISTANT.setTicket(1);
  slow.resolve(ok(query(9, 1, { answer: 'OBSOLETA' })));
  await asking;
  assert.equal(el('assistant-result-container').innerHTML, '');
  assert.equal(el('btn-ask-assistant').disabled, false);
});

test('una recarga del historial invalida paginas pendientes y evita doble carga', async () => {
  const slow = deferred();
  let firstPageCalls = 0;
  const { ctx, api, el } = appWith({
    'GET /tickets/1/assistance?page=0': () => ok(page([query(++firstPageCalls, 1)], 0, 2)),
    'GET /tickets/1/assistance?page=1': slow.promise
  });
  ctx.ASSISTANT.setTicket(1);
  await ctx.ASSISTANT.loadHistory(1);
  const older = ctx.ASSISTANT.loadHistory(1, 1);
  assert.equal(el('btn-assistant-history-more').disabled, true);
  await ctx.ASSISTANT.loadHistory(1, 1);
  assert.equal(api.calls.filter(c => c.url.endsWith('page=1')).length, 1);
  await ctx.ASSISTANT.loadHistory(1);
  slow.resolve(ok(page([query(99, 1)], 1, 2)));
  await older;
  assert.deepEqual(Array.from(ctx.ASSISTANT.historyIds), [2]);
  assert.equal(ctx.ASSISTANT.historyPage, 0);
});

test('volver al mismo incidente descarta su historial anterior', async () => {
  const slow = deferred();
  const { ctx } = appWith({ 'GET /tickets/1/assistance?page=0': slow.promise });
  ctx.ASSISTANT.setTicket(1);
  const loading = ctx.ASSISTANT.loadHistory(1);
  ctx.ASSISTANT.setTicket(2);
  ctx.ASSISTANT.setTicket(1);
  slow.resolve(ok(page([query(9, 1)], 0, 1)));
  await loading;
  assert.equal(ctx.ASSISTANT.historyIds.length, 0);
});

test('si resolver falla, el modal y la nota se conservan; al lograrlo se cierra', async () => {
  let attempt = 0;
  const { ctx, el } = appWith({
    'GET /tickets/5': ok(ticket(5)),
    'POST /tickets/5/transitions': () => (++attempt === 1 ? fail('Estado no permitido') : ok({ newStatus: 'RESOLVED' }))
  });
  const isOpen = id => ctx.UI.modalStack.some(m => m.id === id);

  ctx.TICKETS.promptResolveTicket(5);
  el('resolve-note-input').value = 'Se reinició el servicio';
  await el('btn-generic-confirm').onclick();

  assert.equal(isOpen('modal-generic'), true);
  assert.equal(el('resolve-note-input').value, 'Se reinició el servicio');
  assert.equal(el('btn-generic-confirm').disabled, false);
  assert.equal(ctx.toasts.at(-1).type, 'error');

  await el('btn-generic-confirm').onclick();
  assert.equal(isOpen('modal-generic'), false);
});

test('executeTransition informa éxito o fallo', async () => {
  const { ctx } = appWith({
    'GET /tickets/5': ok(ticket(5)),
    'POST /tickets/5/transitions': body => (body.action === 'RESOLVE' ? fail('no') : ok({ newStatus: 'CLOSED' }))
  });
  assert.equal(await ctx.TICKETS.executeTransition(5, 'RESOLVE'), false);
  assert.equal(await ctx.TICKETS.executeTransition(5, 'CONFIRM_CLOSE'), true);
});

// ---------- 3 y 7. Historial paginado y calificación ----------

test('«Cargar anteriores» añade la página siguiente sin duplicar y conserva las consultas cargadas', async () => {
  const firstPage = Array.from({ length: 20 }, (_, i) => query(100 - i, 1));
  const secondPage = [query(81, 1), ...Array.from({ length: 5 }, (_, i) => query(80 - i, 1))]; // 81 repetida
  const { ctx, api, el } = appWith({
    'GET /tickets/1/assistance?page=0': ok(page(firstPage, 0, 2)),
    'GET /tickets/1/assistance?page=1': ok(page(secondPage, 1, 2))
  });

  ctx.ASSISTANT.setTicket(1);
  await ctx.ASSISTANT.loadHistory(1);
  assert.equal(ctx.ASSISTANT.historyIds.length, 20);
  assert.match(el('assistant-history-list').innerHTML, /ASSISTANT\.loadHistory\(1, 1\)/);

  await ctx.ASSISTANT.loadHistory(1, 1);
  assert.equal(ctx.ASSISTANT.historyIds.length, 25);
  assert.equal(api.calls.at(-1).url, '/tickets/1/assistance?page=1');
  assert.doesNotMatch(el('assistant-history-list').innerHTML, /Cargar anteriores/);
  assert.equal(ctx.ASSISTANT.historyById[100].question, 'Pregunta 100'); // la primera página sigue disponible

  ctx.ASSISTANT.setTicket(2);
  assert.deepEqual(Object.keys(ctx.ASSISTANT.historyById), []);
  assert.equal(ctx.ASSISTANT.historyPage, 0);
});

test('un fallo al cargar más consultas no borra las ya mostradas', async () => {
  const { ctx, el } = appWith({
    'GET /tickets/1/assistance?page=0': ok(page([query(7, 1)], 0, 2)),
    'GET /tickets/1/assistance?page=1': fail('sin red', 0)
  });
  ctx.ASSISTANT.setTicket(1);
  await ctx.ASSISTANT.loadHistory(1);
  await ctx.ASSISTANT.loadHistory(1, 1);

  assert.match(el('assistant-history-list').innerHTML, /Pregunta 7/);
  assert.equal(ctx.toasts.at(-1).type, 'error');
  assert.equal(el('btn-assistant-history-more').disabled, false);
});

test('calificar actualiza la consulta del incidente abierto sin volver a pedir el historial', async () => {
  const { ctx, api, el } = appWith({
    'GET /tickets/1/assistance?page=0': ok(page([query(7, 1)], 0, 1)),
    'PUT /assistance/queries/7/rating': ok({ queryId: 7, score: 4, comment: 'útil' })
  });
  ctx.prompt = () => 'útil';
  ctx.ASSISTANT.setTicket(1);
  await ctx.ASSISTANT.loadHistory(1);
  ctx.ASSISTANT.renderResponse(ctx.ASSISTANT.historyById[7]);
  const requestsBefore = api.calls.filter(c => c.method === 'GET').length;

  await ctx.ASSISTANT.handleRate(7, 4);

  assert.equal(api.calls.filter(c => c.method === 'GET').length, requestsBefore);
  assert.match(el('assistant-history-list').innerHTML, /icons\.svg#star/);
  assert.match(el('assistant-history-list').innerHTML, /4\/5/);
  assert.match(el('assistant-result-container').innerHTML, /Calificada/);
});

test('una calificación que termina tras cambiar de incidente no toca el historial del nuevo', async () => {
  const slow = deferred();
  const { ctx, el } = appWith({
    'GET /tickets/1/assistance?page=0': ok(page([query(7, 1)], 0, 1)),
    'PUT /assistance/queries/7/rating': slow.promise
  });
  ctx.prompt = () => '';
  ctx.ASSISTANT.setTicket(1);
  await ctx.ASSISTANT.loadHistory(1);

  const rating = ctx.ASSISTANT.handleRate(7, 5);
  ctx.ASSISTANT.setTicket(2);
  slow.resolve(ok({ queryId: 7, score: 5 }));
  await rating;

  assert.equal(el('assistant-history-list').innerHTML, '');
});

test('una consulta histórica de otro incidente no se muestra en el abierto', () => {
  const { ctx, el } = appWith({});
  ctx.ASSISTANT.setTicket(2);
  ctx.ASSISTANT.renderResponse(query(7, 1, { answer: 'DEL-INCIDENTE-1' }));
  assert.equal(el('assistant-result-container').innerHTML, '');
});

test('solo quien hizo la consulta ve estrellas para calificar', () => {
  const asOwner = appWith({}, { username: 'laura', role: 'USER' });
  assert.match(asOwner.ctx.ASSISTANT.renderRatingSection(query(7, 1)), /handleRate\(7, 5\)/);

  const asAdmin = appWith({}, { username: 'admin.demo', role: 'ADMIN' });
  assert.equal(asAdmin.ctx.ASSISTANT.renderRatingSection(query(7, 1)), '');
  const readOnly = asAdmin.ctx.ASSISTANT.renderRatingSection(query(7, 1, { rating: { score: 3 } }));
  assert.doesNotMatch(readOnly, /handleRate/);
  assert.match(readOnly, /disabled/);
});

// ---------- 6. Modales ----------

function modalWith(ctx, id, focusableCount) {
  const modal = ctx.document.getElementById(id);
  modal.children = Array.from({ length: focusableCount }, () => ctx.document.createElement());
  return modal;
}

test('Escape cierra solo el modal superior y el manejador se retira con el último', () => {
  const ctx = loadFrontend(['ui.js']);
  modalWith(ctx, 'detalle', 2);
  modalWith(ctx, 'generico', 2);

  ctx.UI.openModal('detalle');
  ctx.UI.openModal('generico');
  ctx.UI.openModal('detalle'); // reabrir uno ya abierto no lo apila otra vez
  assert.equal(ctx.UI.modalStack.length, 2);
  assert.equal(ctx.document.keydownListeners.size, 1);

  ctx.document.pressKey('Escape');
  assert.deepEqual(Array.from(ctx.UI.modalStack, m => m.id), ['detalle']);
  assert.equal(ctx.document.keydownListeners.size, 1);

  ctx.document.pressKey('Escape');
  assert.equal(ctx.UI.modalStack.length, 0);
  assert.equal(ctx.document.keydownListeners.size, 0);
});

test('cerrar por código también retira el manejador y devuelve el foco a quien abrió', () => {
  const ctx = loadFrontend(['ui.js']);
  const opener = ctx.document.createElement();
  const modal = modalWith(ctx, 'detalle', 2);
  opener.focus();

  ctx.UI.openModal('detalle');
  assert.equal(ctx.document.activeElement, modal.children[0]);
  ctx.UI.closeModal('detalle');

  assert.equal(ctx.document.keydownListeners.size, 0);
  assert.equal(ctx.document.activeElement, opener);
});

test('al cerrar el modal superior el foco vuelve al modal de abajo', () => {
  const ctx = loadFrontend(['ui.js']);
  const lower = modalWith(ctx, 'detalle', 2);
  modalWith(ctx, 'generico', 2);

  ctx.UI.openModal('detalle');
  lower.children[1].focus(); // botón dentro del detalle que abre el genérico
  ctx.UI.openModal('generico');
  ctx.UI.closeModal('generico');
  assert.equal(ctx.document.activeElement, lower.children[1]);

  // Si ese botón ya no existe (acciones redibujadas), el foco cae en el modal de abajo.
  ctx.UI.openModal('generico');
  lower.children[1].isConnected = false;
  ctx.UI.closeModal('generico');
  assert.equal(ctx.document.activeElement, lower.children[0]);
});

test('Tab no sale del modal superior', () => {
  const ctx = loadFrontend(['ui.js']);
  const modal = modalWith(ctx, 'detalle', 3);
  const [first, , last] = modal.children;
  ctx.UI.openModal('detalle');

  last.focus();
  assert.equal(ctx.document.pressKey('Tab').prevented, true);
  assert.equal(ctx.document.activeElement, first);

  assert.equal(ctx.document.pressKey('Tab', { shiftKey: true }).prevented, true);
  assert.equal(ctx.document.activeElement, last);

  ctx.document.createElement().focus(); // foco fuera del modal
  ctx.document.pressKey('Tab');
  assert.equal(ctx.document.activeElement, first);
});

test('el modal obligatorio no se cierra con Escape pero sí por código', () => {
  const ctx = loadFrontend(['ui.js']);
  modalWith(ctx, 'cambio-clave', 2).dataset.preventClose = 'true';

  ctx.UI.openModal('cambio-clave');
  ctx.document.pressKey('Escape');
  assert.equal(ctx.UI.modalStack.length, 1);

  ctx.UI.closeModal('cambio-clave');
  assert.equal(ctx.UI.modalStack.length, 0);
});
