'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { createApp, settle, game, historyPage, counts, user } = require('./ambiente.cjs');

async function loadedHistory(items = [game()], overrides = {}) {
  const app = createApp('historico');
  app.request('api/partidas?').respond(historyPage(items, overrides));
  await settle();
  return app;
}

function validLogin(app) {
  app.node('email').value = ' ana@example.com ';
  app.node('senha').value = 'Senha123!';
}
function validSignup(app) {
  app.node('nome').value = ' Ana Bispo ';
  app.node('email').value = ' ana@example.com ';
  app.node('senha').value = 'Senha123!';
  app.node('confirmar').value = 'Senha123!';
}
function validGame(app, adversario = '!') {
  const lista = app.node('adversario-lista');
  lista.value = adversario;
  lista.dispatch('change');
  if (adversario === '!') app.node('adversario').value = ' Bruno Reis ';
  const mode = app.document.querySelector('input[name="modo"][value="Blitz"]');
  const result = app.document.querySelector('input[name="resultado"][value="Vitória"]');
  assert.ok(mode && result, 'As opções esperadas precisam existir no HTML real');
  mode.checked = true;
  result.checked = true;
}

test('histórico: renderiza partidas, total, usuário e link do detalhe', async () => {
  const app = await loadedHistory();
  const rows = app.node('tabela-historico');
  assert.match(rows.textContent, /Bruno Reis/);
  assert.match(rows.textContent, /Blitz/);
  assert.match(rows.textContent, /Vitória/);
  assert.equal(rows.querySelector('a').getAttribute('href'), 'partida.html?id=8');
  assert.equal(app.node('contagem').textContent, 'Mostrando 1 a 1 de 1 partidas');
  assert.equal(app.node('anterior').disabled, true);
  assert.equal(app.node('proxima').disabled, true);
  assert.equal(app.node('nome-usuario').textContent, 'Ana Bispo');
  assert.equal(app.window.location.href, app.originalHref);
});

test('histórico: estado vazio explica que nenhum resultado foi encontrado', async () => {
  const app = await loadedHistory([]);
  assert.match(app.node('tabela-historico').textContent, /Nenhuma partida encontrada/);
  assert.equal(app.node('contagem').textContent, '0 partidas');
  assert.equal(app.node('anterior').disabled, true);
  assert.equal(app.node('proxima').disabled, true);
});

test('histórico: próxima e anterior consultam as páginas corretas', async () => {
  const app = await loadedHistory([game()], { paginas: 3, total: 3 });
  assert.equal(app.node('proxima').disabled, false);
  app.node('proxima').dispatch('click');
  let request = app.request('api/partidas?', 1);
  assert.equal(new URL(request.url, 'http://adx.test/').searchParams.get('pagina'), '2');
  request.respond(historyPage([game({ numero: 2 })], { pagina: 2, paginas: 3, total: 3, de: 2, ate: 2 }));
  await settle();
  assert.equal(app.node('anterior').disabled, false);
  assert.equal(app.node('contagem').textContent, 'Mostrando 2 a 2 de 3 partidas');
  app.node('anterior').dispatch('click');
  request = app.request('api/partidas?', 2);
  assert.equal(new URL(request.url, 'http://adx.test/').searchParams.get('pagina'), '1');
  request.respond(historyPage());
  await settle();
});

test('histórico: filtros são codificados e voltam à primeira página', async () => {
  const app = await loadedHistory([game()], { pagina: 2, paginas: 3, total: 3 });
  app.node('busca').value = '  A&B + José  ';
  app.node('filtro-modo').value = 'Rápido';
  app.node('filtro-resultado').value = 'Empate';
  app.node('filtro-resultado').dispatch('change');
  const request = app.request('api/partidas?', 1);
  const params = new URL(request.url, 'http://adx.test/').searchParams;
  assert.equal(params.get('busca'), 'A&B + José');
  assert.equal(params.get('modo'), 'Rápido');
  assert.equal(params.get('resultado'), 'Empate');
  assert.equal(params.get('pagina'), '1');
  request.respond(historyPage([]));
  await settle();
});

test('histórico: digitação rápida consulta apenas a busca final depois da pausa', async () => {
  const app = await loadedHistory();
  app.node('busca').value = 'B';
  app.node('busca').dispatch('input');
  await app.advance(100);
  app.node('busca').value = 'Bruno';
  app.node('busca').dispatch('input');
  await app.advance(249);
  assert.equal(app.requests.filter(request => request.url.startsWith('api/partidas?')).length, 1);
  await app.advance(1);
  const request = app.request('api/partidas?', 1);
  assert.equal(new URL(request.url, 'http://adx.test/').searchParams.get('busca'), 'Bruno');
  request.respond(historyPage());
  await settle();
});

test('regressão: resposta antiga não deve substituir o modo selecionado mais recentemente', async () => {
  const app = await loadedHistory();
  app.node('filtro-modo').value = 'Clássico';
  app.node('filtro-modo').dispatch('change');
  app.node('filtro-modo').value = 'Blitz';
  app.node('filtro-modo').dispatch('change');
  app.request('api/partidas?', 2).respond(historyPage([game({ adversario: 'Resposta atual', modo: 'Blitz' })]));
  await settle();
  app.request('api/partidas?', 1).respond(historyPage([game({ adversario: 'Resposta obsoleta', modo: 'Clássico' })]));
  await settle();
  assert.equal(app.node('filtro-modo').value, 'Blitz');
  assert.match(app.node('tabela-historico').textContent, /Resposta atual/, 'A tabela precisa continuar mostrando a última seleção');
  assert.doesNotMatch(app.node('tabela-historico').textContent, /Resposta obsoleta/);
});

test('regressão: busca anterior lenta não deve sobrescrever a busca atual', async () => {
  const app = await loadedHistory();
  app.node('busca').value = 'Antigo';
  app.node('busca').dispatch('input');
  await app.advance(250);
  app.node('busca').value = 'Atual';
  app.node('busca').dispatch('input');
  await app.advance(250);
  app.request('api/partidas?', 2).respond(historyPage([game({ adversario: 'Atual' })]));
  await settle();
  app.request('api/partidas?', 1).respond(historyPage([game({ adversario: 'Antigo' })]));
  await settle();
  assert.match(app.node('tabela-historico').textContent, /Atual/, 'O resultado deve corresponder ao texto ainda exibido na busca');
  assert.doesNotMatch(app.node('tabela-historico').textContent, /Antigo/);
});

for (const failure of ['HTTP 500', 'rede']) {
  test(`regressão: histórico avisa sobre falha de ${failure} ao trocar filtro`, async () => {
    const app = await loadedHistory([game({ adversario: 'Dado anterior' })]);
    app.node('filtro-modo').value = 'Clássico';
    app.node('filtro-modo').dispatch('change');
    const request = app.request('api/partidas?', 1);
    if (failure === 'rede') request.reject(); else request.respond({ erro: 'Falha ao consultar partidas.' }, 500);
    await settle();
    assert.ok(app.hasFailureFeedback(), 'Uma consulta falha precisa mostrar aviso; dados antigos não podem parecer resultados do novo filtro');
    assert.equal(app.window.location.href, app.originalHref);
  });
}

test('regressão: histórico com corpo HTML de falha também precisa de mensagem compreensível', async () => {
  const app = createApp('historico');
  app.request('api/partidas?').respond('<html><h1>Internal Server Error</h1></html>', 502, true);
  await settle();
  assert.ok(app.hasFailureFeedback(), 'A indisponibilidade deve ser comunicada mesmo quando o servidor não envia JSON');
  assert.doesNotMatch(app.document.body.innerHTML, /Internal Server Error/);
});

for (const [screen, endpoint] of [
  ['historico', 'api/partidas?'], ['ranking', 'api/ranking'], ['painel', 'api/painel'],
  ['partida', 'api/partidas/'], ['perfil', 'api/perfil'], ['registrar', 'api/partidas?']
]) {
  test(`sessão: ${screen} redireciona ao receber HTTP 401`, async () => {
    const app = createApp(screen, { search: screen === 'partida' ? '?id=8' : '' });
    app.request(endpoint).respond({ erro: 'Sessão expirada.' }, 401);
    await settle();
    assert.equal(app.window.location.href, 'index.html');
  });
}

for (const [screen, endpoint] of [['ranking', 'api/ranking'], ['painel', 'api/painel'], ['perfil', 'api/perfil'], ['registrar', 'api/partidas?']]) {
  for (const failure of ['HTTP 500', 'rede']) {
    test(`regressão: ${screen} informa falha de ${failure} no carregamento`, async () => {
      const app = createApp(screen);
      if (failure === 'rede') app.request(endpoint).reject(); else app.request(endpoint).respond({ erro: 'Falha temporária de leitura.' }, 500);
      await settle();
      assert.ok(app.hasFailureFeedback(), 'A tela precisa diferenciar falha de consulta de conteúdo vazio ou em carregamento');
      assert.equal(app.window.location.href, app.originalHref);
    });
  }
}

test('ranking: mostra classificação, aproveitamento e identifica o próprio jogador', async () => {
  const app = createApp('ranking');
  app.request('api/ranking').respond({ jogadores: [
    { posicao: 1, nome: 'Ana Bispo', total: 4, vitorias: 3, empates: 1, derrotas: 0, aproveitamento: 87.5, voce: true },
    { posicao: 2, nome: 'Bruno Reis', total: 3, vitorias: 1, empates: 1, derrotas: 1, aproveitamento: 50, voce: false }
  ] });
  await settle();
  const rows = app.node('tabela-ranking').querySelectorAll('tr');
  assert.equal(rows.length, 2);
  assert.match(rows[0].textContent, /1º.*Ana Bispo.*\(você\).*87,5%/);
  assert.equal(rows[0].classList.contains('destaque'), true);
  assert.equal(rows[1].classList.contains('destaque'), false);
  assert.equal(app.node('ranking-posicao').textContent, '1º');
  assert.equal(app.node('ranking-participantes').textContent, 'de 2 jogadores');
  assert.equal(rows[0].getAttribute('id'), 'minha-classificacao');
  assert.equal(rows[0].querySelector('meter').getAttribute('value'), '87.5');
});

test('ranking: ausência de resultados mantém a tabela acessível e oculta o atalho pessoal', async () => {
  const app = createApp('ranking');
  app.request('api/ranking').respond({ jogadores: [] });
  await settle();
  assert.match(app.node('tabela-ranking').textContent, /Ainda não há jogadores/);
  assert.equal(app.node('ranking-minha').getAttribute('hidden'), '');
});

test('painel: mostra resumo, aproveitamento, partidas recentes e posição', async () => {
  const app = createApp('painel');
  app.request('api/painel').respond({ resumo: counts(), ultimas: [game()], modos: [{ modo: 'Blitz', ...counts() }], posicao: 2, totalJogadores: 8 });
  await settle();
  assert.match(app.node('placar').textContent, /3partidas1vitórias1empates1derrotas/);
  const valores = app.node('placar').querySelectorAll('.placar-atual');
  assert.deepEqual(valores.map(node => node.textContent), ['3', '1', '1', '1']);
  assert.ok(app.node('placar').querySelectorAll('.placar-anterior').every(node => node.getAttribute('aria-hidden') === 'true'));
  assert.equal(app.node('aproveitamento').textContent, '50,0%');
  assert.match(app.node('ultimas').textContent, /Bruno Reis/);
  assert.match(app.node('modos').textContent, /Blitz/);
  assert.equal(app.node('posicao').textContent, '2º');
  assert.equal(app.node('posicao-texto').textContent, 'entre 8 jogadores');
});

test('painel: usuário sem partidas recebe orientação e zero por cento', async () => {
  const app = createApp('painel');
  app.request('api/painel').respond({ resumo: counts({ total: 0, vitorias: 0, empates: 0, derrotas: 0, aproveitamento: 0 }), ultimas: [], evolucao: [], modos: [], posicao: 1, totalJogadores: 1 });
  await settle();
  assert.match(app.node('evolucao-estado').textContent, /primeira partida/);
  assert.equal(app.node('placar').querySelectorAll('.em-transicao').length, 0);
  assert.equal(app.node('evolucao-grafico').getAttribute('hidden'), '');
  assert.match(app.node('ultimas').textContent, /Você ainda não registrou partidas/);
  assert.equal(app.node('aproveitamento').textContent, '0,0%');
});

test('painel: uma partida gera um ponto central e informa seu resultado', async () => {
  const app = createApp('painel');
  const partida = { data: '01/11/2026', adversario: 'Ana <Silva>', resultado: 'Vitória', aproveitamento: 100 };
  app.request('api/painel').respond({ resumo: counts(), ultimas: [], modos: [], posicao: 1, totalJogadores: 1, evolucao: [partida] });
  await settle();
  assert.equal(app.node('evolucao-grafico').getAttribute('hidden'), null);
  assert.equal(app.node('evolucao-linha').getAttribute('points'), '50,0');
  assert.equal(app.node('evolucao-preenchimento').getAttribute('points'), '');
  assert.equal(app.node('evolucao-valor').textContent, '100,0%');
  assert.equal(app.node('evolucao-guia').getAttribute('x1'), '50');
  assert.equal(app.node('evolucao-partida').textContent, 'Vitória contra Ana <Silva>');
  assert.equal(app.node('evolucao-partida').querySelector('silva'), null);
});

test('painel: pontos do mesmo dia são selecionáveis com ponteiro, clique e teclado', async () => {
  const app = createApp('painel');
  const pontos = Array.from({ length: 10 }, (_, i) => ({
    data: '01/11/2026', adversario: 'Adversário ' + (i + 1), resultado: i ? 'Empate' : 'Derrota', aproveitamento: i * 5
  }));
  app.request('api/painel').respond({ resumo: counts(), ultimas: [], modos: [], posicao: 1, totalJogadores: 1, evolucao: pontos });
  await settle();
  const botoes = app.node('evolucao-pontos').querySelectorAll('button');
  assert.equal(botoes.length, 10);
  assert.match(botoes[9].getAttribute('aria-label'), /Ponto 10 de 10/);
  assert.equal(botoes[9].getAttribute('tabindex'), '0');
  assert.equal(app.node('evolucao-valor').textContent, '45,0%');
  botoes[4].dispatch('pointerenter');
  assert.equal(app.node('evolucao-partida').textContent, 'Empate contra Adversário 5');
  botoes[0].dispatch('click');
  assert.equal(app.node('evolucao-valor').textContent, '0,0%');
  assert.equal(app.node('evolucao-guia').getAttribute('x2'), '0');
  botoes[0].focus();
  botoes[0].dispatch('keydown', { key: 'ArrowRight' });
  assert.equal(app.document.activeElement, botoes[1]);
  assert.equal(app.node('evolucao-valor').textContent, '5,0%');
  botoes[1].dispatch('keydown', { key: 'End' });
  assert.equal(botoes[9].getAttribute('aria-pressed'), 'true');
  botoes[9].dispatch('keydown', { key: 'Home' });
  assert.equal(app.document.activeElement, botoes[0]);
  assert.equal(botoes.filter(b => b.getAttribute('tabindex') === '0').length, 1);
});

test('detalhe: renderiza partida, retrospecto e marca a partida atual', async () => {
  const app = createApp('partida', { search: '?id=8' });
  assert.equal(app.request('api/partidas/').url, 'api/partidas/8');
  app.request('api/partidas/').respond({ partida: game(), totalPartidas: 5, retrospecto: { resumo: counts(), partidas: [game(), game({ id: 7, numero: 2, resultado: 'Empate' })] } });
  await settle();
  assert.equal(app.node('titulo-partida').textContent, 'Partida nº 3');
  assert.match(app.node('dados').textContent, /Bruno Reis/);
  assert.equal(app.node('retrospecto-titulo').textContent, 'Retrospecto contra Bruno Reis');
  assert.equal(app.node('retrospecto-resumo').textContent, '3 partidas: 1 vitória, 1 empate e 1 derrota');
  assert.equal((app.node('retrospecto-lista').textContent.match(/esta partida/g) || []).length, 1);
});

test('detalhe: HTTP 404 orienta a voltar ao histórico', async () => {
  const app = createApp('partida', { search: '?id=999' });
  app.request('api/partidas/').respond({ erro: 'Não encontrada.' }, 404);
  await settle();
  assert.match(app.node('detalhe').textContent, /Partida não encontrada.*Volte ao histórico/);
});

for (const failure of ['HTTP 500', 'rede']) {
  test(`regressão: detalhe distingue falha de ${failure} de partida inexistente`, async () => {
    const app = createApp('partida', { search: '?id=8' });
    if (failure === 'rede') app.request('api/partidas/').reject(); else app.request('api/partidas/').respond({ erro: 'Falha de leitura.' }, 500);
    await settle();
    assert.doesNotMatch(app.node('detalhe').textContent, /Partida não encontrada/i, 'Uma falha de infraestrutura não comprova que a partida deixou de existir');
    assert.ok(app.hasFailureFeedback(), 'O usuário deve receber uma mensagem de falha temporária');
  });
}

const attack = '<img src=x onerror="alert(1)">&\'texto';
for (const field of ['adversario', 'data', 'modo', 'resultado']) {
  test(`segurança: histórico trata ${field} vindo da API como texto`, async () => {
    const app = await loadedHistory([game({ [field]: attack })]);
    const table = app.node('tabela-historico');
    assert.match(table.innerHTML, /&lt;img/);
    assert.equal(table.querySelectorAll('img').length, 0);
    assert.ok(table.textContent.includes(attack));
  });
}

test('segurança: nome do ranking não injeta elementos HTML', async () => {
  const app = createApp('ranking');
  app.request('api/ranking').respond({ jogadores: [{ posicao: 1, nome: attack, total: 1, vitorias: 1, empates: 0, derrotas: 0, aproveitamento: 100, voce: false }] });
  await settle();
  assert.equal(app.node('tabela-ranking').querySelectorAll('img').length, 0);
  assert.ok(app.node('tabela-ranking').textContent.includes(attack));
});

for (const field of ['adversario', 'data', 'modo', 'resultado', 'registradaEm']) {
  test(`segurança: detalhe trata ${field} vindo da API como texto`, async () => {
    const app = createApp('partida', { search: '?id=8' });
    const partida = game({ [field]: attack });
    app.request('api/partidas/').respond({ partida, totalPartidas: 1, retrospecto: { resumo: counts({ total: 1 }), partidas: [partida] } });
    await settle();
    assert.equal(app.node('detalhe').querySelectorAll('img').length, 0);
    assert.ok(app.node('dados').textContent.includes(attack));
    if (field === 'adversario') assert.ok(app.node('retrospecto-titulo').textContent.includes(attack));
  });
}

test('login: campos inválidos impedem envio e recebem aria-invalid', async () => {
  const app = createApp('login', { autoMe: false });
  app.request('api/me').respond({}, 401);
  app.node('email').value = 'email-invalido';
  app.node('senha').value = '';
  const event = app.node('form-login').dispatch('submit');
  await settle();
  assert.equal(event.defaultPrevented, true);
  assert.equal(app.requests.filter(request => request.url === 'api/login').length, 0);
  assert.equal(app.node('email').getAttribute('aria-invalid'), 'true');
  assert.equal(app.node('senha').getAttribute('aria-invalid'), 'true');
  assert.match(app.node('email').closest('.campo').textContent, /Informe um e-mail válido/);
});

test('login: requisição usa formulário codificado e sucesso abre painel', async () => {
  const app = createApp('login', { autoMe: false });
  app.request('api/me').respond({}, 401);
  validLogin(app);
  app.node('senha').value = 'Senha & + ç';
  app.node('form-login').dispatch('submit');
  app.node('form-login').dispatch('submit');
  assert.equal(app.requests.filter(call => call.url === 'api/login').length, 1);
  assert.equal(app.node('entrar-rotulo').textContent, 'Entrando…');
  assert.equal(app.node('mensagem').getAttribute('role'), 'status');
  assert.equal(app.node('mensagem').textContent, 'Entrando…');
  assert.equal(app.node('form-login').querySelector('button[type=submit]').getAttribute('aria-busy'), 'true');
  const request = app.request('api/login');
  assert.equal(request.init.method, 'POST');
  assert.equal(request.init.credentials, 'same-origin');
  assert.match(request.init.headers['Content-Type'], /application\/x-www-form-urlencoded/);
  const form = new URLSearchParams(request.init.body);
  assert.equal(form.get('email'), 'ana@example.com');
  assert.equal(form.get('senha'), 'Senha & + ç');
  assert.equal(app.node('form-login').querySelector('button[type=submit]').disabled, true);
  request.respond({ usuario: user() });
  await settle();
  assert.equal(app.window.location.href, 'painel.html');
});

for (const failure of ['HTTP 400', 'HTTP 429', 'HTTP 500', 'rede', 'HTML inválido']) {
  test(`login: falha de ${failure} mostra mensagem e permite tentar novamente`, async () => {
    const app = createApp('login', { autoMe: false });
    app.request('api/me').respond({}, 401);
    validLogin(app);
    app.node('form-login').dispatch('submit');
    const request = app.request('api/login');
    if (failure === 'rede') request.reject();
    else if (failure === 'HTML inválido') request.respond('<html>Erro do proxy</html>', 502, true);
    else request.respond({ erro: 'Falha ao entrar. Tente novamente.' }, Number(failure.slice(5)));
    await settle();
    assert.equal(app.window.location.href, app.originalHref);
    assert.match(app.node('mensagem').className, /falha/);
    assert.ok(app.node('mensagem').textContent.length > 5);
    assert.equal(app.node('entrar-rotulo').textContent, 'Entrar');
    assert.equal(app.node('form-login').querySelector('button[type=submit]').getAttribute('aria-busy'), null);
    assert.equal(app.node('form-login').querySelector('button[type=submit]').disabled, false);
  });
}

test('login: sessão já autenticada abre diretamente o painel', async () => {
  const app = createApp('login');
  await settle();
  assert.equal(app.window.location.href, 'painel.html');
});

test('login: mostrar e ocultar senha atualiza tipo e estado acessível', async () => {
  const app = createApp('login', { autoMe: false });
  app.request('api/me').respond({}, 401);
  const toggle = app.document.querySelector('.mostrar-senha');
  toggle.dispatch('click');
  assert.equal(app.node('senha').type, 'text');
  assert.equal(toggle.getAttribute('aria-pressed'), 'true');
  toggle.dispatch('click');
  assert.equal(app.node('senha').type, 'password');
  assert.equal(toggle.getAttribute('aria-pressed'), 'false');
  await settle();
});

test('cadastro: valida nome completo, e-mail, tamanho da senha e confirmação', () => {
  const app = createApp('cadastro');
  app.node('nome').value = 'Ana';
  app.node('email').value = 'inválido';
  app.node('senha').value = 'curta';
  app.node('confirmar').value = 'diferente';
  app.node('form-cadastro').dispatch('submit');
  assert.equal(app.requests.length, 0);
  for (const field of ['nome', 'email', 'senha', 'confirmar']) assert.equal(app.node(field).getAttribute('aria-invalid'), 'true');
});

test('cadastro: erros de campos devolvidos pelo servidor aparecem junto aos campos', async () => {
  const app = createApp('cadastro');
  validSignup(app);
  app.node('form-cadastro').dispatch('submit');
  app.request('api/cadastro').respond({ erros: { email: 'Este e-mail já foi cadastrado.' } }, 400);
  await settle();
  assert.equal(app.node('email').getAttribute('aria-invalid'), 'true');
  assert.match(app.node('email').closest('.campo').textContent, /já foi cadastrado/);
  assert.equal(app.node('form-cadastro').querySelector('button[type=submit]').disabled, false);
});

test('cadastro: sucesso informa criação e redireciona após mensagem', async () => {
  const app = createApp('cadastro');
  validSignup(app);
  app.node('form-cadastro').dispatch('submit');
  const form = new URLSearchParams(app.request('api/cadastro').init.body);
  assert.equal(form.get('nome'), 'Ana Bispo');
  assert.equal(form.get('confirmar'), 'Senha123!');
  app.request('api/cadastro').respond({ sucesso: true }, 201);
  await settle();
  assert.match(app.node('mensagem').textContent, /Conta criada/);
  assert.equal(app.window.location.href, app.originalHref);
  await app.advance(1400);
  assert.equal(app.window.location.href, 'index.html');
});

test('registro: campos ausentes impedem envio de partida', async () => {
  const app = createApp('registrar');
  app.request('api/partidas?').respond(historyPage([]));
  app.node('form-partida').dispatch('submit');
  await settle();
  assert.equal(app.requests.filter(request => request.init.method === 'POST').length, 0);
  assert.equal(app.node('adversario-lista').getAttribute('aria-invalid'), 'true');
  assert.equal(app.node('grupo-modo').classList.contains('invalido'), true);
  assert.equal(app.node('grupo-resultado').classList.contains('invalido'), true);
});

test('registro: sucesso informa data, limpa formulário e atualiza partidas recentes', async () => {
  const app = createApp('registrar');
  app.request('api/partidas?').respond(historyPage([]));
  validGame(app);
  app.node('data').value = '2024-02-29';
  app.node('form-partida').dispatch('submit');
  const request = app.request(request => request.url === 'api/partidas' && request.init.method === 'POST');
  const form = new URLSearchParams(request.init.body);
  assert.equal(form.get('adversario'), 'Bruno Reis');
  assert.equal(form.get('dataPartida'), '2024-02-29');
  assert.equal(form.get('modo'), 'Blitz');
  assert.equal(form.get('resultado'), 'Vitória');
  app.request('api/partidas/adversarios').respond({ adversarios: ['Ana Lima'] });
  await settle();
  assert.equal(app.node('adversario-lista').value, '!');
  assert.equal(app.node('adversario').value, ' Bruno Reis ');
  request.respond({ partida: game({ data: '29/02/2024' }) }, 201);
  await settle();
  app.request('api/partidas?', 1).respond(historyPage());
  await settle();
  assert.match(app.node('mensagem').textContent, /29\/02\/2024/);
  assert.equal(app.node('adversario').value, '');
  assert.equal(app.node('adversario-lista').value, '');
  assert.equal(app.node('data').value, app.node('data').getAttribute('max'));
  assert.equal(app.node('adversario').disabled, true);
  assert.deepEqual(app.node('adversarios-disponiveis').querySelectorAll('option').map(option => option.value),
    ['Ana Lima', 'Bruno Reis']);
  assert.match(app.node('recentes').textContent, /Bruno Reis/);
  assert.equal(app.node('form-partida').querySelector('button[type=submit]').disabled, false);
});

test('registro: calendário impede data vazia, inexistente ou futura', () => {
  const app = createApp('registrar');
  validGame(app);
  for (const value of ['', '2023-02-29', '9999-01-01']) {
    app.node('data').value = value;
    app.node('form-partida').dispatch('submit');
    assert.equal(app.document.activeElement, app.node('data'));
    assert.equal(app.node('data').getAttribute('aria-invalid'), 'true');
    assert.equal(app.requests.some(request => request.init.method === 'POST'), false);
  }
});

test('registro: erro do servidor mantém a data escolhida para correção', async () => {
  const app = createApp('registrar');
  validGame(app);
  app.node('data').value = '2024-02-29';
  app.node('form-partida').dispatch('submit');
  app.request(request => request.init.method === 'POST').respond({ erros: { dataPartida: 'Confira a data.' } }, 400);
  await settle();
  assert.equal(app.document.activeElement, app.node('data'));
  assert.equal(app.node('data-erro').textContent, 'Confira a data.');
  assert.equal(app.node('data').value, '2024-02-29');
});

test('registro: escolhe nome da lista pelo valor, em ordem alfabética e sem duplicar a grafia', async () => {
  const app = createApp('registrar');
  const literal = 'José <Silva>';
  app.request('api/partidas/adversarios').respond({ adversarios: ['Zeca', literal, 'jOsÉ <sILVA>', 'Adélia'] });
  await settle();
  const opcoes = app.node('adversarios-disponiveis').querySelectorAll('option');
  assert.deepEqual(opcoes.map(option => option.textContent), ['Adélia', literal, 'Zeca']);
  assert.equal(opcoes[1].children.length, 1);
  assert.equal(opcoes[1].value, literal);
  validGame(app, literal);
  app.node('form-partida').dispatch('submit');
  const envio = app.request(call => call.url === 'api/partidas' && call.init.method === 'POST');
  assert.equal(new URLSearchParams(envio.init.body).get('adversario'), literal);
  assert.equal(app.node('adversario').disabled, true);
});

for (const estado of ['lista vazia', 'falha de rede']) {
  test(`registro: permite digitar o adversário quando há ${estado}`, async () => {
    const app = createApp('registrar');
    const carregar = app.request('api/partidas/adversarios');
    if (estado === 'lista vazia') carregar.respond({ adversarios: [] });
    else carregar.reject();
    await settle();
    assert.equal(app.node('adversario-lista').value, '!');
    assert.equal(app.node('adversario-manual').getAttribute('hidden'), null);
    assert.equal(app.node('adversario').disabled, false);
    assert.match(app.node('adversarios-aviso').textContent, /digit/i);
    app.node('form-partida').dispatch('submit');
    assert.equal(app.document.activeElement, app.node('adversario'));
    assert.equal(app.requests.filter(request => request.init.method === 'POST').length, 0);
  });
}

for (const failure of ['HTTP 500', 'rede']) {
  test(`registro: falha de ${failure} preserva dados e permite novo envio`, async () => {
    const app = createApp('registrar');
    app.request('api/partidas?').respond(historyPage([]));
    validGame(app);
    app.node('form-partida').dispatch('submit');
    const request = app.request(request => request.url === 'api/partidas' && request.init.method === 'POST');
    if (failure === 'rede') request.reject(); else request.respond({ erro: 'Falha ao salvar.' }, 500);
    await settle();
    assert.match(app.node('mensagem').className, /falha/);
    assert.equal(app.node('adversario').value, ' Bruno Reis ');
    assert.equal(app.node('form-partida').querySelector('button[type=submit]').disabled, false);
  });
}

test('perfil: preenche dados e salva nome, modo e biografia normalizados', async () => {
  const app = createApp('perfil');
  app.request('api/perfil').respond({ usuario: user() });
  await settle();
  assert.equal(app.node('nome-completo').value, 'Ana Bispo Silva');
  assert.equal(app.node('email-atual').textContent, 'ana@example.com');
  app.node('nome-exibicao').value = ' Nova Ana ';
  app.node('sobre').value = ' Nova biografia ';
  app.node('form-perfil').dispatch('submit');
  const request = app.request('api/perfil', 1);
  const form = new URLSearchParams(request.init.body);
  assert.equal(form.get('nomeExibicao'), 'Nova Ana');
  assert.equal(form.get('sobre'), 'Nova biografia');
  request.respond({ usuario: user({ nomeExibicao: 'Nova Ana', sobre: 'Nova biografia' }) });
  await settle();
  assert.equal(app.node('nome-usuario').textContent, 'Nova Ana');
  assert.equal(app.node('mensagem-perfil').textContent, 'Alterações salvas.');
});

for (const failure of ['HTTP 500', 'rede']) {
  test(`perfil: salvar com falha de ${failure} informa erro e preserva edição`, async () => {
    const app = createApp('perfil');
    app.request('api/perfil').respond({ usuario: user() });
    await settle();
    app.node('sobre').value = 'Minha edição ainda não salva';
    app.node('form-perfil').dispatch('submit');
    if (failure === 'rede') app.request('api/perfil', 1).reject(); else app.request('api/perfil', 1).respond({ erro: 'Falha ao salvar.' }, 500);
    await settle();
    assert.match(app.node('mensagem-perfil').className, /falha/);
    assert.equal(app.node('sobre').value, 'Minha edição ainda não salva');
  });
}

for (const screen of ['registrar', 'perfil']) {
  test(`sessão: envio de ${screen} redireciona ao receber HTTP 401`, async () => {
    const app = createApp(screen);
    if (screen === 'registrar') {
      app.request('api/partidas?').respond(historyPage([]));
      validGame(app);
      app.node('form-partida').dispatch('submit');
      app.request(request => request.url === 'api/partidas' && request.init.method === 'POST').respond({}, 401);
    } else {
      app.request('api/perfil').respond({ usuario: user() });
      await settle();
      app.node('form-perfil').dispatch('submit');
      app.request('api/perfil', 1).respond({}, 401);
    }
    await settle();
    assert.equal(app.window.location.href, 'index.html');
  });
}

test('logout: sucesso só redireciona depois da resposta do servidor', async () => {
  const app = createApp('ranking');
  app.request('api/ranking').respond({ jogadores: [] });
  await settle();
  assert.equal(app.node('sair').dispatch('click').defaultPrevented, true);
  assert.equal(app.window.location.href, app.originalHref);
  assert.equal(app.request('api/logout').init.method, 'POST');
  app.request('api/logout').respond({ sucesso: true });
  await settle();
  assert.equal(app.window.location.href, 'index.html');
});

for (const failure of ['HTTP 500', 'rede']) {
  test(`regressão: logout com falha de ${failure} não deve simular saída bem-sucedida`, async () => {
    const app = createApp('ranking');
    app.request('api/ranking').respond({ jogadores: [] });
    await settle();
    app.node('sair').dispatch('click');
    if (failure === 'rede') app.request('api/logout').reject(); else app.request('api/logout').respond({ erro: 'Falha ao encerrar sessão.' }, 500);
    await settle();
    assert.equal(app.window.location.href, app.originalHref, 'A sessão pode continuar ativa no servidor; a tela deve mostrar falha ao sair');
    assert.ok(app.hasFailureFeedback(), 'O usuário precisa saber que o logout não foi confirmado');
  });
}

for (const failure of ['HTTP 500', 'rede']) {
  test(`regressão: falha de ${failure} na consulta de sessão não equivale a HTTP 401`, async () => {
    const app = createApp('ranking', { autoMe: false });
    app.request('api/ranking').respond({ jogadores: [] });
    if (failure === 'rede') app.request('api/me').reject(); else app.request('api/me').respond({ erro: 'Falha de leitura da sessão.' }, 500);
    await settle();
    assert.equal(app.window.location.href, app.originalHref, 'Indisponibilidade temporária não comprova ausência de sessão');
    assert.ok(app.hasFailureFeedback());
  });
}
