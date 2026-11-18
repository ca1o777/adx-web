'use strict';

// Verificação estrutural dos arquivos servidos. Não simula leitor de tela nem layout.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = process.env.ADX_APP_ROOT || path.resolve(__dirname, '../..');
const web = path.join(root, 'src/main/webapp');

function elements(file) {
  const html = fs.readFileSync(path.join(web, file), 'utf8');
  return [...html.matchAll(/<([a-z][a-z0-9-]*)\b([^>]*?)>/gi)].map(match => {
    const attributes = {};
    for (const item of match[2].matchAll(/([^\s=/>]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+)))?/g)) {
      attributes[item[1]] = item[2] ?? item[3] ?? item[4] ?? '';
    }
    return { tag: match[1].toLowerCase(), ...attributes };
  });
}

function tokens(value) { return (value || '').split(/\s+/).filter(Boolean); }

for (const file of fs.readdirSync(web).filter(name => name.endsWith('.html')).sort()) {
  test(`HTML ${file}: IDs únicos e referências acessíveis existentes`, () => {
    const nodes = elements(file);
    const ids = nodes.filter(node => node.id).map(node => node.id);
    assert.equal(ids.length, new Set(ids).size, 'IDs duplicados tornam os rótulos ambíguos');
    for (const node of nodes) {
      for (const attribute of ['aria-describedby', 'aria-labelledby', 'aria-controls']) {
        for (const id of tokens(node[attribute])) {
          assert.ok(ids.includes(id), `${attribute} aponta para #${id}, que não existe`);
        }
      }
      if (node.tag === 'label' && node.for) assert.ok(ids.includes(node.for), 'O rótulo precisa de um controle existente');
    }
  });
}

for (const [file, controls] of [
  ['index.html', ['email', 'senha']],
  ['cadastro.html', ['nome', 'email', 'senha', 'confirmar']],
  ['perfil.html', ['nome-exibicao', 'arquivo-foto', 'novo-email', 'senha-atual', 'codigo-email']],
  ['recuperar-senha.html', ['email-recuperacao', 'codigo-recuperacao', 'senha-nova', 'confirmar-senha-nova']],
  ['registrar-partida.html', ['adversario']]
]) {
  test(`HTML ${file}: erros de validação estão associados aos campos`, () => {
    const nodes = elements(file);
    for (const id of controls) {
      const field = nodes.find(node => node.id === id);
      assert.ok(field, `Campo #${id} precisa existir`);
      const descriptions = tokens(field['aria-describedby']);
      assert.ok(descriptions.some(description => nodes.some(node => node.id === description && tokens(node.class).includes('erro'))),
        `O erro de #${id} precisa ser lido quando o campo recebe foco`);
    }
    for (const error of nodes.filter(node => tokens(node.class).includes('erro'))) {
      assert.ok(error.id, 'Cada mensagem de campo precisa de ID');
      assert.ok(nodes.some(node => tokens(node['aria-describedby']).includes(error.id)), 'Cada mensagem de erro precisa estar associada a um controle ou grupo');
    }
    for (const message of nodes.filter(node => tokens(node.class).includes('mensagem'))) {
      assert.ok(['status', 'alert'].includes(message.role), 'Mensagens dinâmicas precisam ser anunciadas');
      assert.equal(message['aria-atomic'], 'true', 'O anúncio precisa incluir toda a mensagem');
    }
  });
}

for (const name of ['modo', 'resultado']) {
  test(`HTML registro: grupo ${name} e suas opções descrevem o erro`, () => {
    const nodes = elements('registrar-partida.html');
    const group = nodes.find(node => node.id === `grupo-${name}`);
    assert.equal(group.tag, 'fieldset', 'Opções relacionadas precisam de agrupamento semântico');
    const errorIds = tokens(group['aria-describedby']).filter(id => nodes.some(node => node.id === id && tokens(node.class).includes('erro')));
    assert.ok(errorIds.length, 'O grupo precisa descrever sua mensagem de erro');
    const radios = nodes.filter(node => node.tag === 'input' && node.type === 'radio' && node.name === name);
    assert.equal(radios.length, 3);
    for (const radio of radios) {
      assert.ok(errorIds.some(id => tokens(radio['aria-describedby']).includes(id)), 'Cada opção precisa manter acesso à mensagem de erro');
      assert.ok(Object.hasOwn(radio, 'required'), 'A obrigatoriedade também precisa ser informada pelo HTML');
    }
  });
}

test('HTML registro: data informa que o horário será definido ao salvar', () => {
  const nodes = elements('registrar-partida.html');
  const date = nodes.find(node => node.id === 'data');
  assert.ok(Object.hasOwn(date, 'readonly'));
  assert.ok(!date.value, 'O HTML não deve apresentar uma data anterior ao salvamento como definitiva');
  assert.match(date.placeholder, /salvar.*Brasília/i);
  assert.ok(tokens(date['aria-describedby']).length, 'A data precisa incluir explicação acessível');
});
