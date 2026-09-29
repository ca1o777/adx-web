/* ==========================================================
   ADX – Comportamento das telas (frontend)
   Validações no navegador, cálculo das estatísticas, filtros
   do histórico e montagem do ranking a partir de dados.js.
   ========================================================== */
(function () {
  "use strict";

  var A = window.ADX;
  var CHAVE_NOVAS = "adx:partidas-novas";

  /* ---------- utilitários ---------- */
  function $(sel, raiz) { return (raiz || document).querySelector(sel); }
  function $$(sel, raiz) { return Array.prototype.slice.call((raiz || document).querySelectorAll(sel)); }

  function escapar(txt) {
    return String(txt).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }
  function doisDigitos(n) { return (n < 10 ? "0" : "") + n; }
  function formatarData(iso) {
    var d = new Date(iso);
    return doisDigitos(d.getDate()) + "/" + doisDigitos(d.getMonth() + 1) + "/" + d.getFullYear();
  }
  function formatarHora(iso) {
    var d = new Date(iso);
    return doisDigitos(d.getHours()) + ":" + doisDigitos(d.getMinutes());
  }
  function paraISO(d) {
    return d.getFullYear() + "-" + doisDigitos(d.getMonth() + 1) + "-" + doisDigitos(d.getDate()) +
      "T" + doisDigitos(d.getHours()) + ":" + doisDigitos(d.getMinutes());
  }
  function classe(resultado) {
    return { "Vitória": "vitoria", "Empate": "empate", "Derrota": "derrota" }[resultado];
  }
  function selo(resultado) {
    return '<span class="resultado ' + classe(resultado) + '">' + resultado + "</span>";
  }
  function porcentagem(valor) {
    return valor.toFixed(1).replace(".", ",") + "%";
  }

  /* partidas de demonstração + partidas registradas neste navegador */
  function lerNovas() {
    try { return JSON.parse(localStorage.getItem(CHAVE_NOVAS)) || []; } catch (e) { return []; }
  }
  function salvarNova(partida) {
    var novas = lerNovas();
    novas.push(partida);
    try { localStorage.setItem(CHAVE_NOVAS, JSON.stringify(novas)); } catch (e) { /* armazenamento indisponível */ }
  }
  function todasPartidas() {
    return A.partidas.concat(lerNovas()).sort(function (a, b) { return a.data < b.data ? 1 : -1; });
  }

  function contar(lista) {
    var c = { total: lista.length, v: 0, e: 0, d: 0 };
    lista.forEach(function (p) {
      if (p.resultado === "Vitória") c.v++;
      else if (p.resultado === "Empate") c.e++;
      else c.d++;
    });
    c.aproveitamento = c.total ? ((c.v + c.e / 2) / c.total) * 100 : 0;
    return c;
  }
  function barra(c, fina) {
    if (!c.total) return '<div class="barra-desempenho' + (fina ? " barra-fina" : "") + '"></div>';
    function seg(n, k, rotulo) {
      return n ? '<i class="' + k + '" style="width:' + (n / c.total) * 100 + '%" title="' + rotulo + ": " + n + '"></i>' : "";
    }
    return '<div class="barra-desempenho' + (fina ? " barra-fina" : "") + '" role="img" aria-label="' +
      c.v + " vitórias, " + c.e + " empates e " + c.d + ' derrotas">' +
      seg(c.v, "v", "Vitórias") + seg(c.e, "e", "Empates") + seg(c.d, "d", "Derrotas") + "</div>";
  }
  function linhaPartida(p, comNumero) {
    return "<tr>" +
      (comNumero ? '<td class="col-num">' + p.id + "</td>" : "") +
      '<td class="numero">' + formatarData(p.data) + "</td>" +
      "<td>" + escapar(p.adversario) + "</td>" +
      "<td>" + p.modo + "</td>" +
      "<td>" + selo(p.resultado) + "</td>" +
      '<td class="col-acao"><a href="partida.html?id=' + p.id + '">Ver detalhes</a></td>' +
      "</tr>";
  }

  function ranking() {
    var eu = contar(todasPartidas());
    var lista = A.jogadores.map(function (j) {
      var total = j.vitorias + j.empates + j.derrotas;
      return { nome: j.nome, total: total, v: j.vitorias, e: j.empates, d: j.derrotas,
               aproveitamento: ((j.vitorias + j.empates / 2) / total) * 100, eu: false };
    });
    lista.push({ nome: A.usuario.nomeExibicao, total: eu.total, v: eu.v, e: eu.e, d: eu.d,
                 aproveitamento: eu.aproveitamento, eu: true });
    /* critério: vitórias; desempate pelo aproveitamento */
    return lista.sort(function (a, b) { return b.v - a.v || b.aproveitamento - a.aproveitamento; });
  }

  /* ---------- validação ---------- */
  function marcar(campo, invalido, texto) {
    var caixa = campo.closest(".campo");
    caixa.classList.toggle("invalido", invalido);
    campo.setAttribute("aria-invalid", invalido ? "true" : "false");
    if (texto) $(".erro", caixa).textContent = texto;
    return !invalido;
  }
  var EMAIL_OK = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

  function mostrarMensagem(el, tipo, texto) {
    el.className = "mensagem visivel " + tipo;
    el.textContent = texto;
  }

  function ativarMostrarSenha() {
    $$(".mostrar-senha").forEach(function (btn) {
      btn.addEventListener("click", function () {
        var input = btn.parentNode.querySelector("input");
        var visivel = input.type === "text";
        input.type = visivel ? "password" : "text";
        btn.textContent = visivel ? "Mostrar" : "Ocultar";
        btn.setAttribute("aria-pressed", String(!visivel));
      });
    });
  }

  /* ---------- telas ---------- */
  var telas = {};

  telas.login = function () {
    ativarMostrarSenha();
    $("#form-login").addEventListener("submit", function (ev) {
      ev.preventDefault();
      var email = $("#email"), senha = $("#senha");
      var ok = marcar(email, !EMAIL_OK.test(email.value.trim()), "Informe um e-mail válido.");
      ok = marcar(senha, senha.value.length === 0, "Informe sua senha.") && ok;
      if (ok) window.location.href = "painel.html";   /* autenticação real: backend (2º bimestre) */
    });
  };

  telas.cadastro = function () {
    ativarMostrarSenha();
    $("#form-cadastro").addEventListener("submit", function (ev) {
      ev.preventDefault();
      var nome = $("#nome"), email = $("#email"), senha = $("#senha"), conf = $("#confirmar");
      var ok = marcar(nome, nome.value.trim().split(/\s+/).length < 2, "Informe nome e sobrenome.");
      ok = marcar(email, !EMAIL_OK.test(email.value.trim()), "Informe um e-mail válido.") && ok;
      ok = marcar(senha, senha.value.length < 8, "A senha precisa ter pelo menos 8 caracteres.") && ok;
      ok = marcar(conf, conf.value !== senha.value || !conf.value, "As senhas não são iguais.") && ok;
      if (!ok) return;
      mostrarMensagem($("#mensagem"), "sucesso", "Conta criada. Redirecionando para o login...");
      setTimeout(function () { window.location.href = "index.html"; }, 1400);
    });
  };

  telas.painel = function () {
    var lista = todasPartidas();
    var c = contar(lista);
    $("#placar").innerHTML =
      '<div><strong class="numero">' + c.total + "</strong><span>partidas</span></div>" +
      '<div class="v"><strong class="numero">' + c.v + "</strong><span>vitórias</span></div>" +
      '<div class="e"><strong class="numero">' + c.e + "</strong><span>empates</span></div>" +
      '<div class="d"><strong class="numero">' + c.d + "</strong><span>derrotas</span></div>";
    $("#barra").innerHTML = barra(c);
    $("#aproveitamento").textContent = porcentagem(c.aproveitamento);

    $("#ultimas").innerHTML = lista.slice(0, 5).map(function (p) { return linhaPartida(p, false); }).join("");

    $("#modos").innerHTML = ["Clássico", "Rápido", "Blitz"].map(function (modo) {
      var cm = contar(lista.filter(function (p) { return p.modo === modo; }));
      return '<div class="modo-linha"><div class="modo-topo">' + modo +
        "<span>" + cm.v + "V " + cm.e + "E " + cm.d + "D</span></div>" + barra(cm, true) + "</div>";
    }).join("");

    var r = ranking();
    var pos = r.findIndex(function (j) { return j.eu; }) + 1;
    $("#posicao").innerHTML = pos + "<small>º</small>";
    $("#posicao-texto").textContent = "entre " + r.length + " jogadores";
  };

  telas.registrar = function () {
    var agora = new Date();
    $("#data").value = formatarData(paraISO(agora)) + " às " + formatarHora(paraISO(agora));
    $("#recentes").innerHTML = todasPartidas().slice(0, 4).map(function (p) {
      return "<li><span>" + escapar(p.adversario) + '<br><small class="suave">' + p.modo + " em " +
        formatarData(p.data) + "</small></span>" + selo(p.resultado) + "</li>";
    }).join("");

    $("#form-partida").addEventListener("submit", function (ev) {
      ev.preventDefault();
      var adv = $("#adversario");
      var modo = $('input[name="modo"]:checked');
      var res = $('input[name="resultado"]:checked');
      var ok = marcar(adv, adv.value.trim().length < 2, "Informe o nome do adversário.");
      $("#grupo-modo").classList.toggle("invalido", !modo);
      $("#grupo-resultado").classList.toggle("invalido", !res);
      if (!ok || !modo || !res) return;

      var todas = todasPartidas();
      var novoId = todas.reduce(function (m, p) { return Math.max(m, p.id); }, 0) + 1;
      salvarNova({ id: novoId, data: paraISO(new Date()), adversario: adv.value.trim(),
                   modo: modo.value, resultado: res.value });
      mostrarMensagem($("#mensagem"), "sucesso", "Partida registrada. Ela já aparece no seu histórico.");
      ev.target.reset();
      $("#data").value = formatarData(paraISO(new Date())) + " às " + formatarHora(paraISO(new Date()));
    });
  };

  telas.historico = function () {
    var POR_PAGINA = 10, pagina = 1;
    var busca = $("#busca"), fModo = $("#filtro-modo"), fRes = $("#filtro-resultado");

    function filtradas() {
      var termo = busca.value.trim().toLowerCase();
      return todasPartidas().filter(function (p) {
        return (!termo || p.adversario.toLowerCase().indexOf(termo) !== -1) &&
               (!fModo.value || p.modo === fModo.value) &&
               (!fRes.value || p.resultado === fRes.value);
      });
    }
    function desenhar() {
      var lista = filtradas();
      var paginas = Math.max(1, Math.ceil(lista.length / POR_PAGINA));
      if (pagina > paginas) pagina = paginas;
      var ini = (pagina - 1) * POR_PAGINA;
      var visiveis = lista.slice(ini, ini + POR_PAGINA);
      $("#tabela-historico").innerHTML = visiveis.length
        ? visiveis.map(function (p) { return linhaPartida(p, true); }).join("")
        : '<tr><td colspan="6" class="vazio">Nenhuma partida encontrada com esses filtros. Limpe a busca ou escolha outro modo de jogo.</td></tr>';
      $("#contagem").textContent = lista.length
        ? "Mostrando " + (ini + 1) + " a " + (ini + visiveis.length) + " de " + lista.length + " partidas"
        : "0 partidas";
      $("#anterior").disabled = pagina === 1;
      $("#proxima").disabled = pagina === paginas;
    }
    [busca, fModo, fRes].forEach(function (el) {
      el.addEventListener("input", function () { pagina = 1; desenhar(); });
    });
    $("#anterior").addEventListener("click", function () { pagina--; desenhar(); });
    $("#proxima").addEventListener("click", function () { pagina++; desenhar(); });
    desenhar();
  };

  telas.partida = function () {
    var id = Number(new URLSearchParams(window.location.search).get("id")) || 24;
    var lista = todasPartidas();
    var p = lista.filter(function (x) { return x.id === id; })[0];
    if (!p) {
      $("#detalhe").innerHTML = '<p class="vazio">Partida não encontrada. Volte ao histórico e escolha uma partida da lista.</p>';
      return;
    }
    $("#titulo-partida").textContent = "Partida nº " + p.id;
    $("#confronto").innerHTML = selo(p.resultado) + "<h2>contra " + escapar(p.adversario) + "</h2>";
    $("#dados").innerHTML =
      "<div><dt>Adversário</dt><dd>" + escapar(p.adversario) + "</dd></div>" +
      "<div><dt>Data da partida</dt><dd class=\"numero\">" + formatarData(p.data) + "</dd></div>" +
      "<div><dt>Modo de jogo</dt><dd>" + p.modo + "</dd></div>" +
      "<div><dt>Resultado</dt><dd>" + p.resultado + "</dd></div>" +
      "<div><dt>Registrada em</dt><dd class=\"numero\">" + formatarData(p.data) + " às " + formatarHora(p.data) + "</dd></div>" +
      "<div><dt>Número no histórico</dt><dd class=\"numero\">" + p.id + " de " + lista.length + "</dd></div>";

    var contra = lista.filter(function (x) { return x.adversario === p.adversario; });
    var c = contar(contra);
    $("#retrospecto-titulo").textContent = "Retrospecto contra " + p.adversario;
    $("#retrospecto-resumo").textContent = c.total + " partidas: " + c.v + (c.v === 1 ? " vitória, " : " vitórias, ") +
      c.e + (c.e === 1 ? " empate e " : " empates e ") + c.d + (c.d === 1 ? " derrota" : " derrotas");
    $("#retrospecto-barra").innerHTML = barra(c);
    $("#retrospecto-lista").innerHTML = contra.map(function (x) {
      var atual = x.id === p.id ? ' <small class="suave">(esta partida)</small>' : "";
      return '<li><span><a href="partida.html?id=' + x.id + '">Partida nº ' + x.id + "</a>" + atual +
        '<br><small class="suave">' + x.modo + " em " + formatarData(x.data) + "</small></span>" + selo(x.resultado) + "</li>";
    }).join("");
  };

  telas.ranking = function () {
    $("#tabela-ranking").innerHTML = ranking().map(function (j, i) {
      return '<tr class="' + (j.eu ? "destaque" : "") + '">' +
        '<td class="col-num">' + (i + 1) + "º</td>" +
        "<td><strong>" + escapar(j.nome) + "</strong>" + (j.eu ? ' <small class="suave">(você)</small>' : "") + "</td>" +
        '<td class="col-dir">' + j.total + "</td>" +
        '<td class="col-dir">' + j.v + "</td>" +
        '<td class="col-dir">' + j.e + "</td>" +
        '<td class="col-dir">' + j.d + "</td>" +
        '<td class="col-dir">' + porcentagem(j.aproveitamento) + "</td></tr>";
    }).join("");
  };

  telas.perfil = function () {
    var u = A.usuario;
    $("#nome-exibicao").value = u.nomeExibicao;
    $("#nome-completo").value = u.nomeCompleto;
    $("#modo-preferido").value = u.modoPreferido;
    $("#email-atual").textContent = u.email;

    $("#form-perfil").addEventListener("submit", function (ev) {
      ev.preventDefault();
      var nome = $("#nome-exibicao");
      if (!marcar(nome, nome.value.trim().length < 2, "Informe como você quer aparecer no ranking.")) return;
      mostrarMensagem($("#mensagem-perfil"), "sucesso", "Alterações salvas.");
    });
    $("#alterar-email").addEventListener("click", function () {
      mostrarMensagem($("#mensagem-email"), "sucesso", "Enviamos um código de confirmação para " + u.email + ".");
    });
    $("#redefinir-senha").addEventListener("click", function () {
      mostrarMensagem($("#mensagem-senha"), "sucesso", "Enviamos um link de redefinição para " + u.email + ".");
    });
  };

  document.addEventListener("DOMContentLoaded", function () {
    var tela = document.body.getAttribute("data-tela");
    if (telas[tela]) telas[tela]();
  });
})();
