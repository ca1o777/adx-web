/* ==========================================================
   ADX – Comportamento das telas
   Validação no navegador e comunicação com a API (Servlets).
   Todo texto vindo do servidor é escapado antes de ir para o HTML.
   ========================================================== */
(function () {
  "use strict";

  /* ---------- utilitários ---------- */
  function $(sel, raiz) { return (raiz || document).querySelector(sel); }
  function $$(sel, raiz) { return Array.prototype.slice.call((raiz || document).querySelectorAll(sel)); }

  function escapar(txt) {
    return String(txt).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }
  function classe(resultado) {
    return { "Vitória": "vitoria", "Empate": "empate", "Derrota": "derrota" }[resultado];
  }
  function selo(resultado) {
    return '<span class="resultado ' + classe(resultado) + '">' + escapar(resultado) + "</span>";
  }
  function porcentagem(valor) {
    return valor.toFixed(1).replace(".", ",") + "%";
  }
  function barra(c, fina) {
    var cls = "barra-desempenho" + (fina ? " barra-fina" : "");
    if (!c.total) return '<div class="' + cls + '"></div>';
    function seg(n, k, rotulo) {
      return n ? '<i class="' + k + '" style="width:' + (n / c.total) * 100 + '%" title="' + rotulo + ": " + n + '"></i>' : "";
    }
    return '<div class="' + cls + '" role="img" aria-label="' +
      c.vitorias + " vitórias, " + c.empates + " empates e " + c.derrotas + ' derrotas">' +
      seg(c.vitorias, "v", "Vitórias") + seg(c.empates, "e", "Empates") + seg(c.derrotas, "d", "Derrotas") + "</div>";
  }
  function linhaPartida(p, comNumero) {
    return "<tr>" +
      (comNumero ? '<td class="col-num">' + p.numero + "</td>" : "") +
      '<td class="numero">' + escapar(p.data) + "</td>" +
      "<td>" + escapar(p.adversario) + "</td>" +
      "<td>" + escapar(p.modo) + "</td>" +
      "<td>" + selo(p.resultado) + "</td>" +
      '<td class="col-acao"><a href="partida.html?id=' + Number(p.id) + '">Ver detalhes</a></td>' +
      "</tr>";
  }
  function plural(n, singular, pluralTxt) { return n + " " + (n === 1 ? singular : pluralTxt); }

  /* ---------- comunicação com a API ---------- */
  function api(metodo, url, dados) {
    var opcoes = { method: metodo, credentials: "same-origin", headers: { "X-Requested-With": "ADX" } };
    if (dados) {
      opcoes.headers["Content-Type"] = "application/x-www-form-urlencoded;charset=UTF-8";
      opcoes.body = new URLSearchParams(dados).toString();
    }
    return fetch(url, opcoes).then(function (r) {
      return r.text().then(function (txt) {
        var corpo = {};
        try { corpo = txt ? JSON.parse(txt) : {}; } catch (e) {
          return { ok: false, status: r.ok ? 502 : r.status, corpo: {} };
        }
        return { ok: r.ok, status: r.status, corpo: corpo };
      });
    }).catch(function () {
      return { ok: false, status: 0, corpo: { erro: "Não foi possível falar com o servidor. Verifique se a aplicação está em execução." } };
    });
  }
  /* sessão expirada: volta para o login */
  function exigirLogin(r) {
    if (r.status === 401) { window.location.href = "index.html"; return true; }
    return false;
  }

  /* ---------- validação ---------- */
  function associarErro(campo, erro, id) {
    if (!erro) return;
    if (!erro.id) erro.setAttribute("id", id);
    var descricoes = (campo.getAttribute("aria-describedby") || "").split(/\s+/).filter(Boolean);
    if (descricoes.indexOf(erro.id) < 0) descricoes.push(erro.id);
    campo.setAttribute("aria-describedby", descricoes.join(" "));
  }
  function marcar(campo, invalido, texto) {
    var caixa = campo.closest(".campo");
    caixa.classList.toggle("invalido", invalido);
    campo.setAttribute("aria-invalid", invalido ? "true" : "false");
    var erro = $(".erro", caixa);
    associarErro(campo, erro, campo.id + "-erro");
    if (erro && texto) erro.textContent = texto;
    return !invalido;
  }
  function marcarGrupo(id, invalido, texto) {
    var grupo = $(id), erro = $(".erro", grupo);
    grupo.classList.toggle("invalido", invalido);
    grupo.setAttribute("aria-invalid", invalido ? "true" : "false");
    associarErro(grupo, erro, grupo.id + "-erro");
    if (erro && texto) erro.textContent = texto;
    $$("input", grupo).forEach(function (campo) {
      campo.setAttribute("aria-invalid", invalido ? "true" : "false");
      associarErro(campo, erro, grupo.id + "-erro");
    });
  }
  function anunciarValidacao(formulario, mensagem) {
    mostrarMensagem(mensagem, "falha", "Revise os campos indicados antes de continuar.");
    var primeiro = $('input[aria-invalid="true"], select[aria-invalid="true"], textarea[aria-invalid="true"]', formulario);
    if (primeiro && typeof primeiro.focus === "function") primeiro.focus();
  }
  var EMAIL_OK = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

  function mostrarMensagem(el, tipo, texto) {
    if (!el) return;
    el.setAttribute("role", tipo === "falha" ? "alert" : "status");
    el.setAttribute("aria-live", tipo === "falha" ? "assertive" : "polite");
    el.setAttribute("aria-atomic", "true");
    el.className = "mensagem visivel " + tipo;
    el.textContent = texto;
  }
  function erroResposta(r, padrao) { return r.corpo && r.corpo.erro || padrao; }
  function avisoLeitura(chave, r, texto, repetir) {
    var el = $("#aviso-" + chave);
    if (!el) {
      el = document.createElement("div");
      el.setAttribute("id", "aviso-" + chave);
      ($("main") || document.body).appendChild(el);
    }
    mostrarMensagem(el, "falha", texto + " " + erroResposta(r, "Tente novamente."));
    if (repetir) {
      var botao = document.createElement("button");
      botao.type = "button";
      botao.className = "botao botao-secundario";
      botao.textContent = "Tentar novamente";
      botao.addEventListener("click", function () {
        if (botao.disabled) return;
        botao.disabled = true;
        repetir();
      });
      el.appendChild(botao);
    }
  }
  function limparAviso(chave) {
    var el = $("#aviso-" + chave);
    if (el) { el.className = "mensagem"; el.textContent = ""; }
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

  /* ---------- cabeçalho (usuário logado e sair) ---------- */
  function mostrarUsuario(u) {
    var av = $("#avatar"); if (av) av.textContent = u.iniciais;
    var nm = $("#nome-usuario"); if (nm) nm.textContent = u.nomeExibicao;
  }
  function carregarUsuario() {
    return api("GET", "api/me").then(function (r) {
      if (exigirLogin(r)) return null;
      if (!r.ok) {
        avisoLeitura("sessao", r, "Não foi possível consultar sua sessão.", carregarUsuario);
        return null;
      }
      limparAviso("sessao");
      mostrarUsuario(r.corpo.usuario);
      return r.corpo.usuario;
    });
  }
  function ativarSair() {
    var a = $("#sair");
    if (!a) return;
    var saindo = false;
    a.addEventListener("click", function (ev) {
      ev.preventDefault();
      if (saindo) return;
      saindo = true;
      a.setAttribute("aria-busy", "true");
      api("POST", "api/logout").then(function (r) {
        saindo = false;
        a.setAttribute("aria-busy", "false");
        if (r.ok || exigirLogin(r)) { window.location.href = "index.html"; return; }
        avisoLeitura("sair", r, "Não foi possível confirmar a saída. Clique em Sair para tentar novamente.");
      });
    });
  }

  /* ---------- telas ---------- */
  var telas = {};

  telas.login = function () {
    ativarMostrarSenha();
    api("GET", "api/me").then(function (r) { if (r.ok) window.location.href = "painel.html"; });
    var esqueci = $("#esqueci");
    if (esqueci) esqueci.addEventListener("click", function (ev) {
      ev.preventDefault();
      mostrarMensagem($("#mensagem"), "aviso", "A recuperação de senha por e-mail ainda não está disponível nesta versão.");
    });
    $("#form-login").addEventListener("submit", function (ev) {
      ev.preventDefault();
      var email = $("#email"), senha = $("#senha");
      var ok = marcar(email, !EMAIL_OK.test(email.value.trim()), "Informe um e-mail válido.");
      ok = marcar(senha, senha.value.length === 0, "Informe sua senha.") && ok;
      if (!ok) { anunciarValidacao(ev.target, $("#mensagem")); return; }
      var botao = $("button[type=submit]", ev.target);
      botao.disabled = true;
      api("POST", "api/login", { email: email.value.trim(), senha: senha.value }).then(function (r) {
        if (r.ok) { window.location.href = "painel.html"; return; }
        botao.disabled = false;
        mostrarMensagem($("#mensagem"), "falha", r.corpo.erro || "Não foi possível entrar.");
      });
    });
  };

  telas.cadastro = function () {
    ativarMostrarSenha();
    var campos = { nome: "#nome", email: "#email", senha: "#senha", confirmar: "#confirmar" };
    $("#form-cadastro").addEventListener("submit", function (ev) {
      ev.preventDefault();
      var nome = $("#nome"), email = $("#email"), senha = $("#senha"), conf = $("#confirmar");
      var ok = marcar(nome, nome.value.trim().split(/\s+/).length < 2, "Informe nome e sobrenome.");
      ok = marcar(email, !EMAIL_OK.test(email.value.trim()), "Informe um e-mail válido.") && ok;
      ok = marcar(senha, senha.value.length < 8, "A senha precisa ter pelo menos 8 caracteres.") && ok;
      ok = marcar(conf, conf.value !== senha.value || !conf.value, "As senhas não são iguais.") && ok;
      if (!ok) { anunciarValidacao(ev.target, $("#mensagem")); return; }
      var botao = $("button[type=submit]", ev.target);
      botao.disabled = true;
      api("POST", "api/cadastro", { nome: nome.value.trim(), email: email.value.trim(), senha: senha.value, confirmar: conf.value })
        .then(function (r) {
          if (r.ok) {
            mostrarMensagem($("#mensagem"), "sucesso", "Conta criada. Redirecionando para o login...");
            setTimeout(function () { window.location.href = "index.html"; }, 1400);
            return;
          }
          botao.disabled = false;
          if (r.corpo.erros) {
            Object.keys(r.corpo.erros).forEach(function (k) {
              if (campos[k]) marcar($(campos[k]), true, r.corpo.erros[k]);
            });
            anunciarValidacao(ev.target, $("#mensagem"));
          } else {
            mostrarMensagem($("#mensagem"), "falha", r.corpo.erro || "Não foi possível criar a conta.");
          }
        });
    });
  };

  telas.painel = function carregarPainel() {
    api("GET", "api/painel").then(function (r) {
      if (exigirLogin(r)) return;
      if (!r.ok) {
        avisoLeitura("painel", r, "Não foi possível carregar o painel.", carregarPainel);
        $("#placar").textContent = "Dados indisponíveis.";
        $("#barra").textContent = "";
        $("#aproveitamento").textContent = "—";
        $("#ultimas").innerHTML = '<tr><td colspan="5" class="vazio">Não foi possível carregar as partidas recentes.</td></tr>';
        $("#modos").textContent = "Dados indisponíveis.";
        $("#posicao").textContent = "—";
        $("#posicao-texto").textContent = "Posição indisponível.";
        return;
      }
      limparAviso("painel");
      var d = r.corpo, c = d.resumo;
      $("#placar").innerHTML =
        '<div><strong class="numero">' + c.total + "</strong><span>partidas</span></div>" +
        '<div class="v"><strong class="numero">' + c.vitorias + "</strong><span>vitórias</span></div>" +
        '<div class="e"><strong class="numero">' + c.empates + "</strong><span>empates</span></div>" +
        '<div class="d"><strong class="numero">' + c.derrotas + "</strong><span>derrotas</span></div>";
      $("#barra").innerHTML = barra(c);
      $("#aproveitamento").textContent = porcentagem(c.aproveitamento);
      $("#ultimas").innerHTML = d.ultimas.length
        ? d.ultimas.map(function (p) { return linhaPartida(p, false); }).join("")
        : '<tr><td colspan="5" class="vazio">Você ainda não registrou partidas. Comece por “Registrar partida”.</td></tr>';
      $("#modos").innerHTML = d.modos.map(function (m) {
        return '<div class="modo-linha"><div class="modo-topo">' + escapar(m.modo) +
          "<span>" + m.vitorias + "V " + m.empates + "E " + m.derrotas + "D</span></div>" + barra(m, true) + "</div>";
      }).join("");
      $("#posicao").innerHTML = d.posicao + "<small>º</small>";
      $("#posicao-texto").textContent = "entre " + d.totalJogadores + " jogadores";
    });
  };

  telas.registrar = function () {
    function atualizarData() {
      var data = $("#data");
      if (data) {
        data.value = "";
        data.setAttribute("placeholder", "Definida ao salvar (horário de Brasília)");
      }
    }
    function carregarRecentes() {
      api("GET", "api/partidas?porPagina=4").then(function (r) {
        if (exigirLogin(r)) return;
        if (!r.ok) {
          $("#recentes").innerHTML = '<li>Não foi possível carregar as partidas recentes.</li>';
          avisoLeitura("recentes", r, "Falha ao consultar partidas recentes.", carregarRecentes);
          return;
        }
        limparAviso("recentes");
        $("#recentes").innerHTML = r.corpo.itens.length
          ? r.corpo.itens.map(function (p) {
              return "<li><span>" + escapar(p.adversario) + '<br><small class="suave">' + escapar(p.modo) + " em " +
                escapar(p.data) + "</small></span>" + selo(p.resultado) + "</li>";
            }).join("")
          : '<li><span class="suave">Nenhuma partida registrada ainda.</span></li>';
      });
    }
    atualizarData();
    carregarRecentes();

    $("#form-partida").addEventListener("submit", function (ev) {
      ev.preventDefault();
      var adv = $("#adversario");
      var modo = $('input[name="modo"]:checked');
      var res = $('input[name="resultado"]:checked');
      var ok = marcar(adv, adv.value.trim().length < 2, "Informe o nome do adversário.");
      marcarGrupo("#grupo-modo", !modo, "Escolha o modo de jogo.");
      marcarGrupo("#grupo-resultado", !res, "Escolha o resultado da partida.");
      if (!ok || !modo || !res) { anunciarValidacao(ev.target, $("#mensagem")); return; }

      var botao = $("button[type=submit]", ev.target);
      botao.disabled = true;
      api("POST", "api/partidas", { adversario: adv.value.trim(), modo: modo.value, resultado: res.value })
        .then(function (r) {
          botao.disabled = false;
          if (exigirLogin(r)) return;
          if (r.ok) {
            mostrarMensagem($("#mensagem"), "sucesso",
              "Partida registrada em " + r.corpo.partida.registradaEm + ". Ela já aparece no seu histórico.");
            ev.target.reset();
            atualizarData();
            carregarRecentes();
            return;
          }
          var e = r.corpo.erros || {};
          if (e.adversario) marcar(adv, true, e.adversario);
          marcarGrupo("#grupo-modo", !!e.modo, e.modo);
          marcarGrupo("#grupo-resultado", !!e.resultado, e.resultado);
          if (r.corpo.erros) anunciarValidacao(ev.target, $("#mensagem"));
          else mostrarMensagem($("#mensagem"), "falha", r.corpo.erro || "Não foi possível registrar a partida.");
        });
    });
  };

  telas.historico = function () {
    var pagina = 1, temporizador = null, versaoConsulta = 0;
    var busca = $("#busca"), fModo = $("#filtro-modo"), fRes = $("#filtro-resultado");

    function carregando() {
      $("#tabela-historico").innerHTML = '<tr><td colspan="6" class="vazio">Carregando partidas...</td></tr>';
      $("#contagem").textContent = "Carregando...";
      $("#anterior").disabled = true;
      $("#proxima").disabled = true;
    }
    function desenhar() {
      clearTimeout(temporizador);
      var consultaAtual = ++versaoConsulta;
      carregando();
      var consulta = new URLSearchParams({
        busca: busca.value.trim(), modo: fModo.value, resultado: fRes.value, pagina: pagina
      }).toString();
      api("GET", "api/partidas?" + consulta).then(function (r) {
        if (consultaAtual !== versaoConsulta || exigirLogin(r)) return;
        if (!r.ok) {
          $("#tabela-historico").innerHTML = '<tr><td colspan="6" class="vazio">Não foi possível consultar o histórico. Tente novamente.</td></tr>';
          $("#contagem").textContent = "Histórico indisponível.";
          avisoLeitura("historico", r, "Falha ao carregar o histórico.", desenhar);
          return;
        }
        limparAviso("historico");
        var d = r.corpo;
        pagina = d.pagina;
        $("#tabela-historico").innerHTML = d.itens.length
          ? d.itens.map(function (p) { return linhaPartida(p, true); }).join("")
          : '<tr><td colspan="6" class="vazio">Nenhuma partida encontrada com esses filtros. Limpe a busca ou escolha outro modo de jogo.</td></tr>';
        $("#contagem").textContent = d.total
          ? "Mostrando " + d.de + " a " + d.ate + " de " + d.total + " partidas"
          : "0 partidas";
        $("#anterior").disabled = pagina <= 1;
        $("#proxima").disabled = pagina >= d.paginas;
      });
    }
    busca.addEventListener("input", function () {
      clearTimeout(temporizador);
      ++versaoConsulta;
      carregando();
      temporizador = setTimeout(function () { pagina = 1; desenhar(); }, 250);
    });
    [fModo, fRes].forEach(function (el) {
      el.addEventListener("change", function () { pagina = 1; desenhar(); });
    });
    $("#anterior").addEventListener("click", function () { pagina = Math.max(1, pagina - 1); desenhar(); });
    $("#proxima").addEventListener("click", function () { pagina++; desenhar(); });
    desenhar();
  };

  telas.partida = function carregarPartida() {
    var id = new URLSearchParams(window.location.search).get("id") || "0";
    api("GET", "api/partidas/" + encodeURIComponent(id)).then(function (r) {
      if (exigirLogin(r)) return;
      if (!r.ok) {
        if (r.status === 404) {
          limparAviso("partida");
          $("#detalhe").innerHTML = '<p class="vazio">Partida não encontrada. Volte ao histórico e escolha uma partida da lista.</p>';
        } else {
          avisoLeitura("partida", r, "Não foi possível carregar a partida.", carregarPartida);
        }
        return;
      }
      limparAviso("partida");
      var p = r.corpo.partida, retro = r.corpo.retrospecto, c = retro.resumo;
      $("#titulo-partida").textContent = "Partida nº " + p.numero;
      $("#confronto").innerHTML = selo(p.resultado) + "<h2>contra " + escapar(p.adversario) + "</h2>";
      $("#dados").innerHTML =
        "<div><dt>Adversário</dt><dd>" + escapar(p.adversario) + "</dd></div>" +
        '<div><dt>Data da partida</dt><dd class="numero">' + escapar(p.data) + "</dd></div>" +
        "<div><dt>Modo de jogo</dt><dd>" + escapar(p.modo) + "</dd></div>" +
        "<div><dt>Resultado</dt><dd>" + escapar(p.resultado) + "</dd></div>" +
        '<div><dt>Registrada em</dt><dd class="numero">' + escapar(p.registradaEm) + "</dd></div>" +
        '<div><dt>Número no histórico</dt><dd class="numero">' + p.numero + " de " + r.corpo.totalPartidas + "</dd></div>";

      $("#retrospecto-titulo").textContent = "Retrospecto contra " + p.adversario;
      $("#retrospecto-resumo").textContent = plural(c.total, "partida", "partidas") + ": " +
        plural(c.vitorias, "vitória", "vitórias") + ", " + plural(c.empates, "empate", "empates") + " e " +
        plural(c.derrotas, "derrota", "derrotas");
      $("#retrospecto-barra").innerHTML = barra(c);
      $("#retrospecto-lista").innerHTML = retro.partidas.map(function (x) {
        var atual = x.id === p.id ? ' <small class="suave">(esta partida)</small>' : "";
        return '<li><span><a href="partida.html?id=' + Number(x.id) + '">Partida nº ' + x.numero + "</a>" + atual +
          '<br><small class="suave">' + escapar(x.modo) + " em " + escapar(x.data) + "</small></span>" + selo(x.resultado) + "</li>";
      }).join("");
    });
  };

  telas.ranking = function carregarRanking() {
    api("GET", "api/ranking").then(function (r) {
      if (exigirLogin(r)) return;
      if (!r.ok) {
        $("#tabela-ranking").innerHTML = '<tr><td colspan="7" class="vazio">Não foi possível carregar o ranking.</td></tr>';
        avisoLeitura("ranking", r, "Falha ao consultar o ranking.", carregarRanking);
        return;
      }
      limparAviso("ranking");
      $("#tabela-ranking").innerHTML = r.corpo.jogadores.map(function (j) {
        return '<tr class="' + (j.voce ? "destaque" : "") + '">' +
          '<td class="col-num">' + j.posicao + "º</td>" +
          "<td><strong>" + escapar(j.nome) + "</strong>" + (j.voce ? ' <small class="suave">(você)</small>' : "") + "</td>" +
          '<td class="col-dir">' + j.total + "</td>" +
          '<td class="col-dir">' + j.vitorias + "</td>" +
          '<td class="col-dir">' + j.empates + "</td>" +
          '<td class="col-dir">' + j.derrotas + "</td>" +
          '<td class="col-dir">' + porcentagem(j.aproveitamento) + "</td></tr>";
      }).join("");
    });
  };

  telas.perfil = function () {
    var usuario = null;
    function preencher(u) {
      usuario = u;
      $("#nome-exibicao").value = u.nomeExibicao;
      $("#nome-completo").value = u.nome;
      $("#modo-preferido").value = u.modoPreferido;
      $("#sobre").value = u.sobre;
      $("#email-atual").textContent = u.email;
      $("#avatar-grande").textContent = u.iniciais;
    }
    function carregarPerfil() {
      api("GET", "api/perfil").then(function (r) {
        if (exigirLogin(r)) return;
        if (!r.ok) {
          avisoLeitura("perfil", r, "Não foi possível carregar seu perfil.", carregarPerfil);
          return;
        }
        limparAviso("perfil");
        preencher(r.corpo.usuario);
      });
    }
    carregarPerfil();

    $("#form-perfil").addEventListener("submit", function (ev) {
      ev.preventDefault();
      var nome = $("#nome-exibicao");
      if (!marcar(nome, nome.value.trim().length < 2, "Informe como você quer aparecer no ranking.")) {
        anunciarValidacao(ev.target, $("#mensagem-perfil"));
        return;
      }
      api("POST", "api/perfil", {
        nomeExibicao: nome.value.trim(), modoPreferido: $("#modo-preferido").value, sobre: $("#sobre").value.trim()
      }).then(function (r) {
        if (exigirLogin(r)) return;
        if (r.ok) {
          preencher(r.corpo.usuario);
          mostrarUsuario(r.corpo.usuario);
          mostrarMensagem($("#mensagem-perfil"), "sucesso", "Alterações salvas.");
          return;
        }
        var e = r.corpo.erros || {};
        if (e.nomeExibicao) marcar(nome, true, e.nomeExibicao);
        if (e.nomeExibicao) anunciarValidacao(ev.target, $("#mensagem-perfil"));
        mostrarMensagem($("#mensagem-perfil"), "falha", e.sobre || e.modoPreferido || r.corpo.erro || "Não foi possível salvar.");
      });
    });
    $("#alterar-email").addEventListener("click", function () {
      mostrarMensagem($("#mensagem-email"), "aviso", "A alteração de e-mail depende do envio de mensagens e ainda não está disponível nesta versão.");
    });
    $("#redefinir-senha").addEventListener("click", function () {
      mostrarMensagem($("#mensagem-senha"), "aviso", "A redefinição de senha depende do envio de mensagens e ainda não está disponível nesta versão.");
    });
  };

  document.addEventListener("DOMContentLoaded", function () {
    var tela = document.body.getAttribute("data-tela");
    if (tela !== "login" && tela !== "cadastro") {
      ativarSair();
      carregarUsuario();
    }
    if (telas[tela]) telas[tela]();
  });
})();
