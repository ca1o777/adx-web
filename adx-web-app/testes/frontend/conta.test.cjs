'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { createApp, settle, user } = require('./ambiente.cjs');

const codigo = 'a23b45c67d89efgh';
const senha = 'senha-exemplo';

function params(request) {
  return Object.fromEntries(new URLSearchParams(request.init.body));
}

async function perfil() {
  const app = createApp('perfil');
  app.request(call => call.url === 'api/perfil').respond({ usuario: user() });
  await settle();
  return app;
}

test('perfil: envia solicitação de e-mail e confirma antes de exibir o novo endereço', async () => {
  const app = await perfil();
  app.node('novo-email').value = 'novo@example.com';
  app.node('senha-atual').value = senha;
  app.node('form-email-solicitar').dispatch('submit');
  app.node('form-email-solicitar').dispatch('submit');
  const chamadas = app.requests.filter(call => call.url === 'api/conta/email/solicitar');
  assert.equal(chamadas.length, 1);
  assert.deepEqual(params(chamadas[0]), { email: 'novo@example.com', senha });
  assert.equal(app.node('senha-atual').value, '');
  chamadas[0].respond({ mensagem: 'Confira a caixa de entrada.' }, 202);
  await settle();
  assert.equal(app.node('email-atual').textContent, 'ana@example.com');
  assert.equal(app.document.activeElement, app.node('codigo-email'));
  app.node('codigo-email').value = codigo;
  app.node('form-email-confirmar').dispatch('submit');
  const mudar = app.request('api/conta/email/confirmar');
  assert.deepEqual(params(mudar), { codigo });
  mudar.respond({ usuario: user({ email: 'novo@example.com' }), mensagem: 'E-mail atualizado.' });
  await settle();
  assert.equal(app.node('email-atual').textContent, 'novo@example.com');
  assert.equal(app.node('codigo-email').value, '');
  assert.equal(app.node('mensagem-email').textContent, 'E-mail atualizado.');
});

test('perfil: senha atual incorreta conserva o e-mail e permite nova tentativa', async () => {
  const app = await perfil();
  app.node('novo-email').value = 'novo@example.com';
  app.node('senha-atual').value = 'incorreta';
  app.node('form-email-solicitar').dispatch('submit');
  app.request('api/conta/email/solicitar').respond({ erro: 'Senha atual incorreta.' }, 403);
  await settle();
  assert.equal(app.node('email-atual').textContent, 'ana@example.com');
  assert.equal(app.node('form-email-solicitar').getAttribute('hidden'), null);
  assert.equal(app.node('form-email-solicitar').querySelector('button[type=submit]').disabled, false);
  assert.equal(app.node('mensagem-email').getAttribute('role'), 'alert');
  assert.equal(app.window.location.href, 'perfil.html');
});

test('recuperação pública: solicita por e-mail, valida confirmação e volta ao login com nova senha', async () => {
  const app = createApp('recuperar-senha');
  assert.equal(app.requests.length, 0);
  app.node('email-recuperacao').value = 'ana@example.com';
  app.node('form-recuperacao-solicitar').dispatch('submit');
  const solicitacao = app.request('api/conta/senha/solicitar');
  assert.deepEqual(params(solicitacao), { email: 'ana@example.com' });
  solicitacao.respond({ mensagem: 'Se o e-mail estiver cadastrado, você receberá um código.' }, 202);
  await settle();
  assert.equal(app.node('email-confirmacao').value, 'ana@example.com');
  assert.equal(app.document.activeElement, app.node('codigo-recuperacao'));
  app.node('codigo-recuperacao').value = codigo;
  app.node('senha-nova').value = senha;
  app.node('confirmar-senha-nova').value = 'outra-senha';
  app.node('form-recuperacao-confirmar').dispatch('submit');
  assert.equal(app.document.activeElement, app.node('confirmar-senha-nova'));
  assert.equal(app.requests.length, 1);
  app.node('confirmar-senha-nova').value = senha;
  app.node('form-recuperacao-confirmar').dispatch('submit');
  const resposta = app.request('api/conta/senha/confirmar');
  assert.deepEqual(params(resposta), { email: 'ana@example.com', codigo, senha, confirmar: senha });
  resposta.respond({ mensagem: 'Senha redefinida.' });
  await settle();
  assert.equal(app.node('senha-nova').value, '');
  assert.equal(app.node('codigo-recuperacao').value, '');
  assert.equal(app.node('mensagem-recuperacao').textContent, 'Senha redefinida.');
  assert.equal(app.document.activeElement, app.node('voltar-login'));
  assert.equal(app.node('voltar-login').getAttribute('href'), 'index.html');
});

test('perfil: foto envia o arquivo em bytes, atualiza os avatares e permite remover', async () => {
  const app = await perfil();
  const form = app.node('form-foto');
  const campo = app.node('arquivo-foto');
  campo.files = [{ type: 'image/png', size: 2 * 1024 * 1024 + 1 }];
  form.dispatch('submit');
  assert.equal(app.document.activeElement, campo);
  assert.equal(app.requests.filter(call => call.url === 'api/perfil/foto').length, 0);
  const foto = { type: 'image/png', size: 100 };
  campo.files = [foto];
  form.dispatch('submit');
  const post = app.request('api/perfil/foto');
  assert.equal(post.init.method, 'POST');
  assert.equal(post.init.body, foto);
  assert.equal(post.init.headers['Content-Type'], 'image/png');
  assert.equal(post.init.headers['X-Requested-With'], 'ADX');
  post.respond({ mensagem: 'Foto atualizada.' });
  await settle();
  for (const id of ['avatar', 'avatar-grande']) {
    const imagem = app.node(id).querySelector('img');
    assert.match(imagem.getAttribute('src'), /^api\/perfil\/foto\?v=\d+$/);
    imagem.dispatch('load');
    assert.equal(imagem.getAttribute('hidden'), null);
  }
  app.node('remover-foto').dispatch('click');
  const remocao = app.request('api/perfil/foto', 1);
  assert.equal(remocao.init.method, 'DELETE');
  remocao.respond({ mensagem: 'Foto removida.' });
  await settle();
  assert.equal(app.node('avatar-grande').textContent, 'AB');
  assert.equal(app.node('avatar-grande').querySelector('img'), null);
  assert.equal(app.node('mensagem-foto').textContent, 'Foto removida.');
});
