'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { createApp, settle, game, historyPage, counts, user } = require('./ambiente.cjs');

test('histórico: edição invalida resposta em voo antes de terminar o debounce', async () => {
  const app = createApp('historico');
  app.node('busca').value = 'Atual';
  app.node('busca').dispatch('input');
  app.request('api/partidas?').respond(historyPage([game({ adversario: 'Obsoleto' })]));
  await settle();
  assert.doesNotMatch(app.node('tabela-historico').textContent, /Obsoleto/);
  assert.match(app.node('tabela-historico').textContent, /Carregando/);
  await app.advance(250);
  app.request('api/partidas?', 1).respond(historyPage([game({ adversario: 'Atual' })]));
  await settle();
  assert.match(app.node('tabela-historico').textContent, /Atual/);
});

test('histórico: erro de consulta obsoleta não apaga resultado mais recente', async () => {
  const app = createApp('historico');
  app.node('filtro-modo').value = 'Blitz';
  app.node('filtro-modo').dispatch('change');
  app.request('api/partidas?', 1).respond(historyPage([game({ adversario: 'Consulta atual' })]));
  await settle();
  app.request('api/partidas?').reject();
  await settle();
  assert.match(app.node('tabela-historico').textContent, /Consulta atual/);
  assert.equal(app.hasFailureFeedback(), false);
});

test('histórico: falha limpa dados antigos e tentar novamente mantém filtros', async () => {
  const app = createApp('historico');
  app.request('api/partidas?').respond(historyPage([game({ adversario: 'Dado anterior' })]));
  await settle();
  app.node('filtro-modo').value = 'Clássico';
  app.node('filtro-modo').dispatch('change');
  assert.doesNotMatch(app.node('tabela-historico').textContent, /Dado anterior/);
  app.request('api/partidas?', 1).respond({ erro: 'Falha temporária.' }, 500);
  await settle();
  assert.doesNotMatch(app.node('tabela-historico').textContent, /Dado anterior/);
  assert.equal(app.node('proxima').disabled, true);
  app.node('aviso-historico').querySelector('button').dispatch('click');
  const retry = app.request('api/partidas?', 2);
  assert.equal(new URL(retry.url, 'http://adx.test/').searchParams.get('modo'), 'Clássico');
  retry.respond(historyPage([game({ modo: 'Clássico', adversario: 'Consulta recuperada' })]));
  await settle();
  assert.match(app.node('tabela-historico').textContent, /Consulta recuperada/);
  assert.equal(app.hasFailureFeedback(), false);
});

test('histórico: filtro imediato cancela a busca ainda agendada', async () => {
  const app = createApp('historico');
  app.request('api/partidas?').respond(historyPage());
  await settle();
  app.node('busca').value = 'Ana';
  app.node('busca').dispatch('input');
  app.node('filtro-modo').value = 'Blitz';
  app.node('filtro-modo').dispatch('change');
  app.request('api/partidas?', 1).respond(historyPage());
  await app.advance(250);
  assert.equal(app.requests.filter(request => request.url.startsWith('api/partidas?')).length, 2);
});

for (const [screen, endpoint, key, body] of [
  ['ranking', 'api/ranking', 'ranking', { jogadores: [] }],
  ['painel', 'api/painel', 'painel', { resumo: counts(), ultimas: [], modos: [], posicao: 1, totalJogadores: 1 }],
  ['registrar', 'api/partidas?', 'recentes', historyPage([])],
  ['perfil', 'api/perfil', 'perfil', { usuario: user() }],
  ['partida', 'api/partidas/', 'partida', { partida: game(), totalPartidas: 1, retrospecto: { resumo: counts(), partidas: [game()] } }]
]) {
  test(`recuperação: ${screen} permite repetir leitura após indisponibilidade`, async () => {
    const app = createApp(screen, { search: screen === 'partida' ? '?id=8' : '' });
    app.request(endpoint).reject();
    await settle();
    assert.ok(app.hasFailureFeedback());
    app.node('aviso-' + key).querySelector('button').dispatch('click');
    app.request(endpoint, 1).respond(body);
    await settle();
    assert.equal(app.hasFailureFeedback(), false);
    assert.equal(app.window.location.href, app.originalHref);
  });
}

test('sessão: indisponibilidade é recuperável sem abandonar a tela', async () => {
  const app = createApp('ranking', { autoMe: false });
  app.request('api/ranking').respond({ jogadores: [] });
  app.request('api/me').respond({}, 500);
  await settle();
  app.node('aviso-sessao').querySelector('button').dispatch('click');
  app.request('api/me', 1).respond({ usuario: user({ nomeExibicao: 'Ana recuperada' }) });
  await settle();
  assert.equal(app.node('nome-usuario').textContent, 'Ana recuperada');
  assert.equal(app.hasFailureFeedback(), false);
  assert.equal(app.window.location.href, app.originalHref);
});

test('logout: HTTP 401 confirma sessão já encerrada', async () => {
  const app = createApp('ranking');
  app.request('api/ranking').respond({ jogadores: [] });
  app.node('sair').dispatch('click');
  app.request('api/logout').respond({}, 401);
  await settle();
  assert.equal(app.window.location.href, 'index.html');
});

test('logout: cliques repetidos aguardam a mesma resposta e falha permite nova tentativa', async () => {
  const app = createApp('ranking');
  app.request('api/ranking').respond({ jogadores: [] });
  app.node('sair').dispatch('click');
  app.node('sair').dispatch('click');
  assert.equal(app.requests.filter(request => request.url === 'api/logout').length, 1);
  app.request('api/logout').reject();
  await settle();
  assert.equal(app.node('sair').getAttribute('aria-busy'), 'false');
  app.node('sair').dispatch('click');
  app.request('api/logout', 1).respond({ sucesso: true });
  await settle();
  assert.equal(app.window.location.href, 'index.html');
});

test('acessibilidade: login associa erros, anuncia validação e foca primeiro campo inválido', () => {
  const app = createApp('login', { autoMe: false });
  app.node('form-login').dispatch('submit');
  assert.equal(app.document.activeElement, app.node('email'));
  assert.equal(app.node('mensagem').getAttribute('role'), 'alert');
  assert.match(app.node('mensagem').textContent, /Revise os campos/);
  for (const id of ['email', 'senha']) {
    const field = app.node(id);
    const error = field.closest('.campo').querySelector('.erro');
    assert.ok(error.id);
    assert.ok(field.getAttribute('aria-describedby').split(/\s+/).includes(error.id));
  }
});

test('acessibilidade: grupos de opções ligam erro aos radios e recebem foco', async () => {
  const app = createApp('registrar');
  app.request('api/partidas?').respond(historyPage([]));
  app.node('adversario').value = 'Bruno Reis';
  app.node('form-partida').dispatch('submit');
  for (const id of ['grupo-modo', 'grupo-resultado']) {
    const group = app.node(id);
    const error = group.querySelector('.erro');
    assert.equal(group.getAttribute('aria-invalid'), 'true');
    for (const radio of group.querySelectorAll('input')) {
      assert.equal(radio.getAttribute('aria-invalid'), 'true');
      assert.ok(radio.getAttribute('aria-describedby').split(/\s+/).includes(error.id));
    }
  }
  assert.equal(app.document.activeElement, app.node('grupo-modo').querySelector('input'));
  await settle();
});

test('acessibilidade: erro de cadastro devolvido pela API recebe foco e anúncio', async () => {
  const app = createApp('cadastro');
  app.node('nome').value = 'Ana Bispo';
  app.node('email').value = 'ana@example.com';
  app.node('senha').value = 'Senha123!';
  app.node('confirmar').value = 'Senha123!';
  app.node('form-cadastro').dispatch('submit');
  app.request('api/cadastro').respond({ erros: { email: 'E-mail já cadastrado.' } }, 400);
  await settle();
  assert.equal(app.document.activeElement, app.node('email'));
  assert.equal(app.node('mensagem').getAttribute('role'), 'alert');
  assert.match(app.node('email-erro').textContent, /já cadastrado/);
});

test('registro: não apresenta horário do navegador como data já confirmada', () => {
  const app = createApp('registrar');
  assert.equal(app.node('data').value, '');
  assert.match(app.node('data').getAttribute('placeholder'), /Definida ao salvar/);
});

test('segurança: mensagem de falha recebida da API é inserida como texto', async () => {
  const app = createApp('ranking');
  const message = '<img src=x onerror="alert(1)">';
  app.request('api/ranking').respond({ erro: message }, 500);
  await settle();
  assert.equal(app.node('aviso-ranking').querySelectorAll('img').length, 0);
  assert.ok(app.node('aviso-ranking').textContent.includes(message));
});

test('recuperação: cliques repetidos consultam uma vez e nova falha permite outra tentativa', async () => {
  const app = createApp('ranking');
  app.request('api/ranking').respond({ erro: 'Falha inicial.' }, 500);
  await settle();
  const firstRetry = app.node('aviso-ranking').querySelector('button');
  firstRetry.dispatch('click');
  firstRetry.dispatch('click');
  assert.equal(firstRetry.disabled, true);
  assert.equal(app.requests.filter(request => request.url === 'api/ranking').length, 2);
  app.request('api/ranking', 1).respond({ erro: 'Falha temporária.' }, 500);
  await settle();
  const secondRetry = app.node('aviso-ranking').querySelector('button');
  assert.notEqual(secondRetry, firstRetry);
  assert.equal(secondRetry.disabled, false);
  secondRetry.dispatch('click');
  secondRetry.dispatch('click');
  assert.equal(app.requests.filter(request => request.url === 'api/ranking').length, 3);
  app.request('api/ranking', 2).respond({ jogadores: [] });
  await settle();
  assert.equal(app.hasFailureFeedback(), false);
});

test('detalhe: nova consulta com 404 encerra aviso e tentativa da falha anterior', async () => {
  const app = createApp('partida', { search: '?id=8' });
  app.request('api/partidas/').respond({ erro: 'Falha temporária.' }, 500);
  await settle();
  const retry = app.node('aviso-partida').querySelector('button');
  retry.dispatch('click');
  app.request('api/partidas/', 1).respond({ erro: 'Partida não encontrada.' }, 404);
  await settle();
  assert.match(app.node('detalhe').textContent, /Partida não encontrada.*Volte ao histórico/);
  assert.equal(app.node('aviso-partida').classList.contains('visivel'), false);
  assert.equal(app.node('aviso-partida').querySelector('button'), null);
  // Até uma referência antiga ao botão já consumido não pode disparar leitura.
  retry.dispatch('click');
  assert.equal(app.requests.filter(request => request.url.startsWith('api/partidas/')).length, 2);
});
