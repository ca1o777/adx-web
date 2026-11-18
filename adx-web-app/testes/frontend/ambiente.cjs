'use strict';

// DOM mínimo para executar o script real, sem copiar suas funções internas.
// Não implementa layout, navegação real, execução de HTML inserido ou acessibilidade.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const ROOT = process.env.ADX_APP_ROOT || path.resolve(__dirname, '../..');
const WEB = path.join(ROOT, 'src/main/webapp');
const FILES = { login: 'index.html', cadastro: 'cadastro.html', registrar: 'registrar-partida.html' };
const VOIDS = new Set(['area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input', 'link', 'meta', 'param', 'source', 'track', 'wbr']);

function decode(text) {
  return String(text).replace(/&(#x[\da-f]+|#\d+|amp|lt|gt|quot|apos|nbsp);/gi, (_, key) => {
    if (key[0] === '#') return String.fromCodePoint(parseInt(key.slice(key[1].toLowerCase() === 'x' ? 2 : 1), key[1].toLowerCase() === 'x' ? 16 : 10));
    return { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'", nbsp: ' ' }[key.toLowerCase()];
  });
}
function encode(text) {
  return String(text).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

class Element {
  constructor(tagName = 'div', attributes = {}) {
    this.tagName = tagName.toLowerCase();
    this.attributes = { ...attributes };
    this.children = [];
    this.parentNode = null;
    this.listeners = new Map();
    this.value = attributes.value || '';
    this.defaultValue = this.value;
    this.checked = Object.hasOwn(attributes, 'checked');
    this.defaultChecked = this.checked;
    this.disabled = Object.hasOwn(attributes, 'disabled');
    this.classList = {
      contains: name => this.className.split(/\s+/).includes(name),
      toggle: (name, force) => {
        const tokens = new Set(this.className.split(/\s+/).filter(Boolean));
        const present = force === undefined ? !tokens.has(name) : !!force;
        if (present) tokens.add(name); else tokens.delete(name);
        this.className = [...tokens].join(' ');
        return present;
      },
      add: (...names) => names.forEach(name => this.classList.toggle(name, true)),
      remove: (...names) => names.forEach(name => this.classList.toggle(name, false))
    };
  }
  get id() { return this.attributes.id || ''; }
  get type() { return this.attributes.type || ''; }
  set type(value) { this.attributes.type = value; }
  get className() { return this.attributes.class || ''; }
  set className(value) { this.attributes.class = value; }
  get textContent() { return this.children.map(child => typeof child === 'string' ? decode(child) : child.textContent).join(''); }
  set textContent(value) { this.children = [encode(value)]; }
  get innerHTML() { return this.children.map(child => typeof child === 'string' ? child : child.outerHTML).join(''); }
  set innerHTML(value) { this.children = []; parseInto(String(value), this); }
  get outerHTML() {
    const attrs = Object.entries(this.attributes).map(([key, value]) => ` ${key}="${encode(value)}"`).join('');
    return `<${this.tagName}${attrs}>${this.innerHTML}${VOIDS.has(this.tagName) ? '' : `</${this.tagName}>`}`;
  }
  getAttribute(name) { return this.attributes[name] ?? null; }
  setAttribute(name, value) { this.attributes[name] = String(value); }
  removeAttribute(name) { delete this.attributes[name]; }
  focus() {
    let root = this;
    while (root.parentNode) root = root.parentNode;
    root.activeElement = this;
  }
  appendChild(child) { child.parentNode = this; this.children.push(child); return child; }
  addEventListener(type, callback) {
    if (!this.listeners.has(type)) this.listeners.set(type, []);
    this.listeners.get(type).push(callback);
  }
  dispatch(type) {
    const event = { type, target: this, defaultPrevented: false, preventDefault() { this.defaultPrevented = true; }, stopPropagation() {} };
    for (const callback of this.listeners.get(type) || []) callback(event);
    return event;
  }
  querySelector(selector) { return this.querySelectorAll(selector)[0] || null; }
  querySelectorAll(selector) {
    const result = [];
    const selectors = selector.split(',').map(part => part.trim());
    function visit(parent) {
      for (const child of parent.children) {
        if (typeof child === 'string') continue;
        if (selectors.some(part => matches(child, part))) result.push(child);
        visit(child);
      }
    }
    visit(this);
    return result;
  }
  closest(selector) {
    for (let current = this; current; current = current.parentNode) if (matches(current, selector)) return current;
    return null;
  }
  reset() {
    for (const field of this.querySelectorAll('input, textarea, select')) {
      field.value = field.defaultValue;
      field.checked = field.defaultChecked;
    }
  }
}

function matches(element, selector) {
  const segments = selector.trim().split(/\s+(?![^[]*\])/);
  const last = segments.pop();
  for (const attribute of last.matchAll(/\[([^=\]]+)(?:=["']?([^\]"']*)["']?)?\]/g)) {
    if (!Object.hasOwn(element.attributes, attribute[1]) || (attribute[2] !== undefined && element.attributes[attribute[1]] !== attribute[2])) return false;
  }
  let rest = last.replace(/\[[^\]]*\]/g, '');
  if (rest.includes(':checked') && !element.checked) return false;
  rest = rest.replace(':checked', '');
  for (const id of rest.matchAll(/#([\w-]+)/g)) if (element.id !== id[1]) return false;
  for (const cls of rest.matchAll(/\.([\w-]+)/g)) if (!element.classList.contains(cls[1])) return false;
  const tag = rest.replace(/[#.][\w-]+/g, '');
  if (tag && tag !== '*' && element.tagName !== tag.toLowerCase()) return false;
  if (!segments.length) return true;
  for (let parent = element.parentNode; parent; parent = parent.parentNode) if (matches(parent, segments.join(' '))) return true;
  return false;
}

function parseInto(html, root) {
  const stack = [root];
  for (const token of html.match(/<!--[\s\S]*?-->|<![^>]*>|<[^>]*>|[^<]+/g) || []) {
    if (/^<!/.test(token)) continue;
    if (/^<\//.test(token)) {
      const closing = token.match(/^<\/\s*([^\s>]+)/)[1].toLowerCase();
      for (let index = stack.length - 1; index > 0; index--) {
        if (stack[index].tagName === closing) { stack.length = index; break; }
      }
    } else if (token[0] === '<') {
      const name = token.match(/^<\s*([^\s/>]+)/);
      if (!name) continue;
      const attributes = {};
      for (const match of token.slice(name[0].length).matchAll(/([^\s=/>]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+)))?/g)) {
        attributes[match[1]] = decode(match[2] ?? match[3] ?? match[4] ?? '');
      }
      const element = stack[stack.length - 1].appendChild(new Element(name[1], attributes));
      if (!VOIDS.has(element.tagName) && !token.endsWith('/>')) stack.push(element);
    } else stack[stack.length - 1].children.push(token);
  }
  for (const element of root.querySelectorAll('select, textarea')) {
    element.value = element.tagName === 'textarea' ? element.textContent : (element.querySelector('option[selected]') || element.querySelector('option'))?.getAttribute('value') ?? element.querySelector('option')?.textContent ?? '';
    element.defaultValue = element.value;
  }
}

function response(body, status = 200, raw = false) {
  return { ok: status >= 200 && status < 300, status, text: async () => raw ? String(body) : JSON.stringify(body) };
}

async function settle() {
  // Real promises complete, fake timers only run when advance() is requested.
  for (let index = 0; index < 4; index++) await new Promise(setImmediate);
}

function createApp(screen, options = {}) {
  const file = FILES[screen] || `${screen}.html`;
  const document = new Element('document');
  parseInto(fs.readFileSync(path.join(WEB, file), 'utf8'), document);
  document.body = document.querySelector('body');
  document.activeElement = document.body;
  document.createElement = tag => new Element(tag);
  const originalHref = file + (options.search || '');
  const window = { location: { href: originalHref, search: options.search || '' } };
  const requests = [];
  const alerts = [];
  const timers = new Map();
  let now = 0, nextTimer = 1;
  const fetch = (url, init = {}) => {
    const request = { url: String(url), init, settled: false };
    requests.push(request);
    return new Promise((resolve, reject) => {
      request.respond = (body, status = 200, raw = false) => { request.settled = true; resolve(response(body, status, raw)); };
      request.reject = (error = new Error('Rede indisponível no teste')) => { request.settled = true; reject(error); };
      if (url === 'api/me' && options.autoMe !== false) request.respond({ usuario: { iniciais: 'AB', nomeExibicao: 'Ana Bispo' } });
    });
  };
  window.alert = text => alerts.push(String(text));
  vm.runInNewContext(fs.readFileSync(path.join(WEB, 'assets/js/app.js'), 'utf8'), {
    document, window, fetch, URLSearchParams, Date, AbortController,
    alert: window.alert,
    setTimeout(callback, delay = 0) { const id = nextTimer++; timers.set(id, { at: now + delay, callback }); return id; },
    clearTimeout(id) { timers.delete(id); }
  }, { filename: 'app.js' });
  document.dispatch('DOMContentLoaded');
  const app = {
    document, window, originalHref, requests, alerts,
    node(id) {
      const node = document.querySelector(`#${id}`);
      if (!node) throw new Error(`Elemento #${id} ausente no HTML original de ${screen}`);
      return node;
    },
    request(part, index = 0) {
      const selected = requests.filter(request => typeof part === 'function' ? part(request) : request.url.startsWith(part))[index];
      if (!selected) throw new Error(`Requisição não encontrada: ${part}; recebidas: ${requests.map(request => request.url).join(', ')}`);
      return selected;
    },
    async advance(milliseconds) {
      now += milliseconds;
      for (;;) {
        const entry = [...timers.entries()].sort((a, b) => a[1].at - b[1].at).find(([, timer]) => timer.at <= now);
        if (!entry) break;
        timers.delete(entry[0]); entry[1].callback();
      }
      await settle();
    },
    hasFailureFeedback() {
      // Recognize the app's existing message visibility convention, basic hidden
      // attributes and inline style. CSS layout remains outside this harness.
      function visibleText(element) {
        if (element.tagName === 'script' || element.tagName === 'style' || element.hidden ||
          element.getAttribute('hidden') !== null || element.getAttribute('aria-hidden') === 'true' ||
          /display\s*:\s*none|visibility\s*:\s*hidden/i.test(element.getAttribute('style') || '') ||
          (element.classList.contains('mensagem') && !element.classList.contains('visivel'))) return '';
        return element.children.map(child => typeof child === 'string' ? decode(child) : visibleText(child)).join('');
      }
      return /\berros?\b|\bfalh[ao]\w*|não foi possível|indispon|tente novamente|\bproblema|\bconexão/i.test(visibleText(document.body) + alerts.join(' '));
    }
  };
  return app;
}

function game(overrides = {}) {
  return { id: 8, numero: 3, adversario: 'Bruno Reis', modo: 'Blitz', resultado: 'Vitória', data: '20/10/2026 às 22:00', registradaEm: '20/10/2026 às 22:01', ...overrides };
}
function historyPage(items = [game()], overrides = {}) {
  return { pagina: 1, paginas: 1, total: items.length, de: items.length ? 1 : 0, ate: items.length, itens: items, ...overrides };
}
function counts(overrides = {}) { return { total: 3, vitorias: 1, empates: 1, derrotas: 1, aproveitamento: 50, ...overrides }; }
function user(overrides = {}) {
  return { nomeExibicao: 'Ana Bispo', nome: 'Ana Bispo Silva', email: 'ana@example.com', iniciais: 'AB', modoPreferido: 'Blitz', sobre: 'Gosto de finais.', ...overrides };
}
module.exports = { createApp, settle, game, historyPage, counts, user };
