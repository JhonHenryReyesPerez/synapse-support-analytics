const test = require('node:test');
const assert = require('node:assert/strict');
const { loadFrontend } = require('./harness');

test('mobile navigation closes on Escape or selection, restores focus and identifies the active view', () => {
  let reloads = 0;
  const ctx = loadFrontend(['app.js'], { TICKETS: { loadTickets() { reloads++; } } });
  const doc = ctx.document;
  const menu = doc.getElementById('btn-navigation-toggle');
  const nav = doc.getElementById('app-navigation');
  const item = doc.getElementById('ticket-navigation');
  const view = doc.getElementById('view-tickets');
  for (const element of [menu, item]) {
    element.setAttribute = (name, value) => { element[name] = value; };
    element.getAttribute = name => element[name];
    element.removeAttribute = name => { delete element[name]; };
    element.addEventListener = (name, handler) => { element[name] = handler; };
  }
  nav.classList.toggle = (name, open) => { nav.open = open; };
  item.dataset.view = 'view-tickets';
  doc.querySelectorAll = selector => selector.includes('nav-item') ? [item] : [view];
  ctx.initNavigation({ role: 'USER' });
  menu.click();
  assert.equal(menu['aria-expanded'], 'true');
  assert.equal(nav.open, true);
  doc.pressKey('Escape');
  assert.equal(menu['aria-expanded'], 'false');
  assert.equal(doc.activeElement, menu);
  menu.click();
  item.click();
  assert.equal(nav.open, false);
  assert.equal(item['aria-current'], 'page');
  assert.equal(view.classList.contains('is-active'), true);
  assert.equal(doc.getElementById('header-view-title').textContent, 'Incidentes');
  assert.equal(doc.activeElement, doc.getElementById('header-view-title'));
  assert.equal(reloads, 1);
});
