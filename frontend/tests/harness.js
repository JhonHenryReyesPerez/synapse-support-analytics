/**
 * Carga los scripts del frontend en un contexto aislado con un DOM mínimo de prueba.
 * No es un navegador: sirve para la lógica (carreras, estado, contratos), no para el render real.
 */
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function createElement(doc, id) {
  const classes = new Set();
  return {
    id,
    innerHTML: '',
    value: '',
    textContent: '',
    style: {},
    dataset: {},
    disabled: false,
    isConnected: true,
    children: [], // elementos enfocables que la prueba declara dentro de un modal
    classList: {
      add: c => classes.add(c),
      remove: c => classes.delete(c),
      contains: c => classes.has(c),
      toggle: () => {}
    },
    setAttribute() {},
    addEventListener() {},
    appendChild() {},
    reset() {},
    focus() { doc.activeElement = this; },
    contains(other) { return this.children.includes(other); },
    querySelectorAll() { return this.children; },
    getClientRects() { return [{}]; }
  };
}

function createDocument() {
  const elements = new Map();
  const doc = {
    activeElement: null,
    keydownListeners: new Set(),
    getElementById(id) {
      if (!elements.has(id)) elements.set(id, createElement(doc, id));
      return elements.get(id);
    },
    createElement: () => createElement(doc, ''),
    addEventListener(type, fn) { if (type === 'keydown') doc.keydownListeners.add(fn); },
    removeEventListener(type, fn) { if (type === 'keydown') doc.keydownListeners.delete(fn); },
    pressKey(key, extra = {}) {
      const event = { key, shiftKey: false, prevented: false, preventDefault() { this.prevented = true; }, ...extra };
      [...doc.keydownListeners].forEach(fn => fn(event));
      return event;
    }
  };
  return doc;
}

/** Promesa que la prueba resuelve cuando quiere: permite ordenar respuestas a voluntad. */
function deferred() {
  let resolve;
  const promise = new Promise(r => { resolve = r; });
  return { promise, resolve };
}

/**
 * @param {string[]} scripts archivos de js/ a cargar, en orden
 * @param {object} globals dobles de API, AUTH, etc. visibles para los scripts
 */
function loadFrontend(scripts, globals = {}) {
  const store = new Map();
  const context = {
    console,
    setTimeout,
    document: createDocument(),
    localStorage: {
      getItem: k => (store.has(k) ? store.get(k) : null),
      setItem: (k, v) => store.set(k, String(v)),
      removeItem: k => store.delete(k)
    },
    CONFIG: { STORAGE_TOKEN: 'token', STORAGE_USER: 'user', ROUTES: { LOGIN: 'index.html', APP: 'app.html' } },
    ...globals
  };
  context.window = context;
  vm.createContext(context);
  for (const name of scripts) {
    const file = path.join(__dirname, '..', 'js', name);
    vm.runInContext(fs.readFileSync(file, 'utf8'), context, { filename: file });
  }
  if (context.UI) {
    context.toasts = [];
    context.UI.showToast = (message, type) => context.toasts.push({ message, type });
  }
  return context;
}

module.exports = { loadFrontend, deferred };
