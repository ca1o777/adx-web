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
  function api(metodo, url, dados, tipoArquivo) {
    var opcoes = { method: metodo, credentials: "same-origin", headers: { "X-Requested-With": "ADX" } };
    if (tipoArquivo) {
      opcoes.headers["Content-Type"] = tipoArquivo;
      opcoes.body = dados;
    } else if (dados) {
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

  function ocupado(formulario) { return formulario.getAttribute("aria-busy") === "true"; }
  function aoEnviar(formulario, acao) {
    formulario.addEventListener("submit", function (ev) {
      ev.preventDefault();
      if (!ocupado(formulario) && formulario.getAttribute("hidden") === null) acao();
    });
  }
  function aguardarEnvio(formulario, promessa) {
    var controles = $$("button, input", formulario).filter(function (controle) { return !controle.disabled; });
    formulario.setAttribute("aria-busy", "true");
    controles.forEach(function (controle) { controle.disabled = true; });
    return promessa.finally(function () {
      formulario.removeAttribute("aria-busy");
      controles.forEach(function (controle) { controle.disabled = false; });
    });
  }
  function alternarFormulario(anterior, proximo, campo) {
    anterior.setAttribute("hidden", "");
    proximo.removeAttribute("hidden");
    campo.focus();
  }
  function validarEmail(campo) {
    var email = campo.value.trim();
    return marcar(campo, email.length > 120 || !EMAIL_OK.test(email), "Informe um e-mail válido.");
  }
  function validarCodigo(campo) {
    return marcar(campo, !/^[A-Z0-9]{16}$/i.test(campo.value.trim()), "Informe os 16 caracteres do código recebido.");
  }
  function atualizarLinkCaixa(link, endereco) {
    link.setAttribute("hidden", "");
    link.removeAttribute("href");
    if (!endereco) return;
    var url;
    try { url = new URL(endereco); } catch (erro) { return; }
    if (url.protocol !== "http:" || (url.hostname !== "127.0.0.1" && url.hostname !== "[::1]")) return;
    if (url.href !== url.origin + "/") return;
    link.setAttribute("href", url.href);
    link.removeAttribute("hidden");
  }

  /* ---------- cabeçalho (usuário logado e sair) ---------- */
  var URL_FOTO = "api/perfil/foto";
  var versaoFoto = "";
  function mostrarAvatar(el, iniciais, aoCarregar, semFoto) {
    if (!el) return;
    el.textContent = iniciais || "";
    if (semFoto) return;
    var foto = document.createElement("img");
    foto.className = "imagem-avatar";
    foto.setAttribute("alt", "");
    foto.setAttribute("hidden", "");
    foto.addEventListener("load", function () {
      if ($("img", el) !== foto) return;
      foto.removeAttribute("hidden");
      if (aoCarregar) aoCarregar();
    });
    foto.addEventListener("error", function () {
      if ($("img", el) === foto) el.textContent = iniciais || "";
    });
    el.appendChild(foto);
    foto.setAttribute("src", URL_FOTO + versaoFoto);
  }
  function mostrarUsuario(u) {
    mostrarAvatar($("#avatar"), u.iniciais);
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
    var luz = $("#luz-tabuleiro");
    if (luz && window.matchMedia) {
      var ponteiro = window.matchMedia("(hover: hover) and (pointer: fine) and (prefers-reduced-motion: no-preference)");
      luz.addEventListener("pointermove", function (ev) {
        if (!ponteiro.matches || ev.pointerType === "touch") return;
        luz.style.setProperty("--luz-x", ev.offsetX + "px");
        luz.style.setProperty("--luz-y", ev.offsetY + "px");
      });
    }
    api("GET", "api/me").then(function (r) { if (r.ok) window.location.href = "painel.html"; });
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

  function mostrarEvolucao(pontos) {
    var grafico = $("#evolucao-grafico"), estado = $("#evolucao-estado");
    var periodo = $("#evolucao-periodo"), marcadores = $("#evolucao-pontos");
    marcadores.textContent = "";
    if (!Array.isArray(pontos) || !pontos.length) {
      grafico.setAttribute("hidden", "");
      estado.removeAttribute("hidden");
      estado.textContent = Array.isArray(pontos) ? "Registre sua primeira partida para ver a evolução." : "Evolução indisponível.";
      periodo.textContent = "";
      return;
    }

    grafico.removeAttribute("hidden");
    estado.setAttribute("hidden", "");
    var ultimo = pontos.length - 1;
    periodo.textContent = ultimo ? "Últimas " + pontos.length + " partidas" : "1 partida";
    $("#evolucao-inicio").textContent = pontos[0].data;
    $("#evolucao-fim").textContent = ultimo ? pontos[ultimo].data : "";
    $("#evolucao-datas").classList.toggle("unica", ultimo === 0);
    marcadores.setAttribute("style", "--alvo:" + (100 / Math.max(1, ultimo)) + "%");
    var coordenadas = [], botoes = [];

    function selecionar(indice) {
      var ponto = pontos[indice];
      botoes.forEach(function (botao, i) {
        botao.setAttribute("aria-pressed", String(indice === i));
        botao.setAttribute("tabindex", indice === i ? "0" : "-1");
      });
      $("#evolucao-partida").textContent = ponto.resultado + " contra " + ponto.adversario;
      $("#evolucao-data").textContent = ponto.data;
      $("#evolucao-valor").textContent = porcentagem(ponto.aproveitamento);
    }

    pontos.forEach(function (ponto, i) {
      var x = ultimo ? 100 * i / ultimo : 50;
      var y = 100 - ponto.aproveitamento;
      coordenadas.push(x + "," + y);
      var botao = document.createElement("button");
      botao.type = "button";
      botao.className = "evolucao-ponto";
      botao.setAttribute("style", "left:" + x + "%;top:" + y + "%");
      botao.setAttribute("aria-label", "Ponto " + (i + 1) + " de " + pontos.length + ", " +
        ponto.data + ", " + ponto.resultado + " contra " + ponto.adversario +
        ", aproveitamento de " + porcentagem(ponto.aproveitamento));
      ["pointerenter", "focus", "click"].forEach(function (evento) {
        botao.addEventListener(evento, function () { selecionar(i); });
      });
      botao.addEventListener("keydown", function (evento) {
        var destinos = { ArrowLeft: Math.max(0, i - 1), ArrowRight: Math.min(ultimo, i + 1), Home: 0, End: ultimo };
        if (!Object.hasOwn(destinos, evento.key)) return;
        evento.preventDefault();
        botoes[destinos[evento.key]].focus();
      });
      botoes.push(botao);
      marcadores.appendChild(botao);
    });
    $("#evolucao-linha").setAttribute("points", coordenadas.join(" "));
    $("#evolucao-preenchimento").setAttribute("points", ultimo ? "0,100 " + coordenadas.join(" ") + " 100,100" : "");
    selecionar(ultimo);
  }

  telas.painel = function carregarPainel() {
    api("GET", "api/painel").then(function (r) {
      if (exigirLogin(r)) return;
      if (!r.ok) {
        avisoLeitura("painel", r, "Não foi possível carregar o painel.", carregarPainel);
        $("#placar").textContent = "Dados indisponíveis.";
        $("#barra").textContent = "";
        $("#aproveitamento").textContent = "—";
        mostrarEvolucao();
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
      mostrarEvolucao(d.evolucao);
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
    var lista = $("#adversario-lista"), nomeManual = $("#adversario"), campoManual = $("#adversario-manual");
    var grupo = $("#adversarios-disponiveis"), aviso = $("#adversarios-aviso");
    var digitarNome = "!", nomes = new Map();
    var data = $("#data");
    var calendario = new Intl.DateTimeFormat("sv-SE", {
      timeZone: "America/Sao_Paulo", year: "numeric", month: "2-digit", day: "2-digit"
    });

    function exibirCampoManual() {
      nomeManual.disabled = lista.value !== digitarNome;
      if (nomeManual.disabled) campoManual.setAttribute("hidden", "");
      else campoManual.removeAttribute("hidden");
      marcar(lista, false);
      marcar(nomeManual, false);
    }
    lista.addEventListener("change", function () {
      exibirCampoManual();
      if (!nomeManual.disabled) nomeManual.focus();
    });

    function incluirNomes(novos) {
      var selecionado = lista.value;
      novos.forEach(function (nome) {
        var chave = nome.toLocaleLowerCase("pt-BR");
        if (!nomes.has(chave)) nomes.set(chave, nome);
      });
      grupo.textContent = "";
      Array.from(nomes.values()).sort(function (a, b) {
        return a.localeCompare(b, "pt-BR");
      }).forEach(function (nome) {
        var opcao = document.createElement("option");
        opcao.value = nome;
        opcao.textContent = nome;
        grupo.appendChild(opcao);
      });
      if (nomes.size) grupo.removeAttribute("hidden");
      lista.value = selecionado;
    }

    api("GET", "api/partidas/adversarios").then(function (r) {
      if (exigirLogin(r)) return;
      var carregou = r.ok && Array.isArray(r.corpo.adversarios);
      if (carregou) incluirNomes(r.corpo.adversarios);
      if (nomes.size) {
        aviso.textContent = "";
      } else {
        aviso.textContent = carregou ? "Ainda não há adversários na lista. Digite um nome." :
          "A lista está indisponível. Você pode digitar o nome.";
        if (!lista.value) {
          lista.value = digitarNome;
          exibirCampoManual();
        }
      }
    });

    function atualizarLimiteData() {
      var hoje = calendario.format(new Date());
      data.setAttribute("max", hoje);
      return hoje;
    }
    function preencherDataAtual() {
      data.value = atualizarLimiteData();
      marcar(data, false);
    }
    function validarData() {
      var hoje = atualizarLimiteData();
      var instante = Date.parse(data.value);
      if (!Number.isFinite(instante) || new Date(instante).toISOString().slice(0, 10) !== data.value ||
          data.value < "0001-01-01") {
        return marcar(data, true, "Selecione uma data válida para a partida.");
      }
      return marcar(data, data.value > hoje, "Escolha hoje ou uma data anterior.");
    }
    data.addEventListener("focus", atualizarLimiteData);
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
    preencherDataAtual();
    carregarRecentes();

    $("#form-partida").addEventListener("submit", function (ev) {
      ev.preventDefault();
      var adv = lista.value === digitarNome ? nomeManual : lista;
      var modo = $('input[name="modo"]:checked');
      var res = $('input[name="resultado"]:checked');
      var ok = marcar(adv, adv.value.trim().length < 2, adv === lista ? "Selecione um adversário." : "Informe o nome do adversário.");
      marcarGrupo("#grupo-modo", !modo, "Escolha o modo de jogo.");
      marcarGrupo("#grupo-resultado", !res, "Escolha o resultado da partida.");
      var dataOk = validarData();
      if (!ok || !modo || !res || !dataOk) { anunciarValidacao(ev.target, $("#mensagem")); return; }

      var botao = $("button[type=submit]", ev.target);
      botao.disabled = true;
      api("POST", "api/partidas", { adversario: adv.value.trim(), modo: modo.value, resultado: res.value, dataPartida: data.value })
        .then(function (r) {
          botao.disabled = false;
          if (exigirLogin(r)) return;
          if (r.ok) {
            mostrarMensagem($("#mensagem"), "sucesso",
              "A partida de " + r.corpo.partida.data + " foi salva no seu histórico.");
            if (adv === nomeManual) {
              incluirNomes([r.corpo.partida.adversario]);
              aviso.textContent = "";
            }
            ev.target.reset();
            exibirCampoManual();
            preencherDataAtual();
            carregarRecentes();
            return;
          }
          var e = r.corpo.erros || {};
          if (e.adversario) marcar(adv, true, e.adversario);
          if (e.dataPartida) marcar(data, true, e.dataPartida);
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

  function ativarFotoPerfil(iniciais) {
    var form = $("#form-foto"), campo = $("#arquivo-foto"), mensagem = $("#mensagem-foto");
    var remover = $("#remover-foto");

    function desenhar(semFoto) {
      remover.setAttribute("hidden", "");
      mostrarAvatar($("#avatar-grande"), iniciais(), function () {
        remover.removeAttribute("hidden");
      }, semFoto);
    }
    function atualizar(metodo, dados, tipo) {
      aguardarEnvio(form, api(metodo, URL_FOTO, dados, tipo).then(function (r) {
        if (exigirLogin(r)) return;
        if (!r.ok) {
          mostrarMensagem(mensagem, "falha", erroResposta(r, "Não foi possível atualizar a foto."));
          return;
        }
        var removida = metodo === "DELETE";
        versaoFoto = "?v=" + Date.now();
        campo.value = "";
        marcar(campo, false);
        desenhar(removida);
        mostrarAvatar($("#avatar"), iniciais(), null, removida);
        mostrarMensagem(mensagem, "sucesso", r.corpo.mensagem || (removida ? "Foto removida." : "Foto atualizada."));
        return removida;
      })).then(function (removida) {
        if (removida) campo.focus();
      });
    }
    aoEnviar(form, function () {
      var arquivo = campo.files && campo.files[0];
      var erro = "";
      if (!arquivo) erro = "Escolha uma imagem.";
      else if (arquivo.type !== "image/jpeg" && arquivo.type !== "image/png") erro = "Escolha um arquivo JPG ou PNG.";
      else if (!arquivo.size || arquivo.size > 2 * 1024 * 1024) erro = "Escolha uma imagem de até 2 MB.";
      if (!marcar(campo, !!erro, erro)) {
        anunciarValidacao(form, mensagem);
        return;
      }
      atualizar("POST", arquivo, arquivo.type);
    });
    remover.addEventListener("click", function () {
      if (!ocupado(form) && remover.getAttribute("hidden") === null) atualizar("DELETE");
    });
    return desenhar;
  }

  function ativarAlteracaoEmail(atualizarEmail) {
    var solicitar = $("#form-email-solicitar"), confirmar = $("#form-email-confirmar");
    var email = $("#novo-email"), senha = $("#senha-atual"), codigo = $("#codigo-email");
    var mensagem = $("#mensagem-email"), caixa = $("#caixa-email");

    aoEnviar(solicitar, function () {
      var valido = validarEmail(email);
      valido = marcar(senha, !senha.value, "Informe sua senha atual.") && valido;
      if (!valido) { anunciarValidacao(solicitar, mensagem); return; }
      var resposta = api("POST", "api/conta/email/solicitar", { email: email.value.trim(), senha: senha.value });
      senha.value = "";
      aguardarEnvio(solicitar, resposta.then(function (r) {
        if (exigirLogin(r)) return;
        if (!r.ok) {
          mostrarMensagem(mensagem, "falha", erroResposta(r, "Não foi possível solicitar o código."));
          return;
        }
        mostrarMensagem(mensagem, "sucesso", r.corpo.mensagem || "Confira o código no seu e-mail atual.");
        atualizarLinkCaixa(caixa, r.corpo.caixaDeEntrada);
        alternarFormulario(solicitar, confirmar, codigo);
      }));
    });
    aoEnviar(confirmar, function () {
      if (!validarCodigo(codigo)) { anunciarValidacao(confirmar, mensagem); return; }
      aguardarEnvio(confirmar, api("POST", "api/conta/email/confirmar", { codigo: codigo.value.trim() }).then(function (r) {
        if (exigirLogin(r)) return;
        if (!r.ok) {
          mostrarMensagem(mensagem, "falha", erroResposta(r, "Não foi possível confirmar a alteração."));
          return;
        }
        atualizarEmail(r.corpo.usuario.email);
        solicitar.reset();
        confirmar.reset();
        atualizarLinkCaixa(caixa);
        alternarFormulario(confirmar, solicitar, email);
        mostrarMensagem(mensagem, "sucesso", r.corpo.mensagem || "E-mail atualizado.");
      }));
    });
    $("#ja-tenho-codigo-email").addEventListener("click", function () {
      if (ocupado(solicitar)) return;
      senha.value = "";
      alternarFormulario(solicitar, confirmar, codigo);
    });
    $("#voltar-email").addEventListener("click", function () {
      if (ocupado(confirmar)) return;
      confirmar.reset();
      marcar(codigo, false);
      atualizarLinkCaixa(caixa);
      alternarFormulario(confirmar, solicitar, email);
    });
  }

  telas["recuperar-senha"] = function () {
    var solicitar = $("#form-recuperacao-solicitar"), confirmar = $("#form-recuperacao-confirmar");
    var email = $("#email-recuperacao"), emailConfirmacao = $("#email-confirmacao");
    var codigo = $("#codigo-recuperacao"), senha = $("#senha-nova"), repeticao = $("#confirmar-senha-nova");
    var mensagem = $("#mensagem-recuperacao"), caixa = $("#caixa-recuperacao");

    function mostrarConfirmacao(endereco) {
      emailConfirmacao.value = endereco;
      alternarFormulario(solicitar, confirmar, codigo);
    }
    aoEnviar(solicitar, function () {
      if (!validarEmail(email)) { anunciarValidacao(solicitar, mensagem); return; }
      var endereco = email.value.trim();
      aguardarEnvio(solicitar, api("POST", "api/conta/senha/solicitar", { email: endereco }).then(function (r) {
        if (!r.ok) {
          mostrarMensagem(mensagem, "falha", erroResposta(r, "Não foi possível solicitar o código."));
          return;
        }
        mostrarMensagem(mensagem, "sucesso", r.corpo.mensagem || "Se o e-mail estiver cadastrado, você receberá um código.");
        atualizarLinkCaixa(caixa, r.corpo.caixaDeEntrada);
        mostrarConfirmacao(endereco);
      }));
    });
    aoEnviar(confirmar, function () {
      var valido = validarCodigo(codigo);
      valido = marcar(senha, senha.value.length < 8 || senha.value.length > 72, "Use de 8 a 72 caracteres.") && valido;
      valido = marcar(repeticao, !repeticao.value || repeticao.value !== senha.value, "As senhas precisam ser iguais.") && valido;
      if (!valido) { anunciarValidacao(confirmar, mensagem); return; }
      aguardarEnvio(confirmar, api("POST", "api/conta/senha/confirmar", {
        email: emailConfirmacao.value, codigo: codigo.value.trim(), senha: senha.value, confirmar: repeticao.value
      }).then(function (r) {
        if (!r.ok) {
          mostrarMensagem(mensagem, "falha", erroResposta(r, "Não foi possível redefinir a senha."));
          return;
        }
        confirmar.reset();
        solicitar.reset();
        confirmar.setAttribute("hidden", "");
        atualizarLinkCaixa(caixa);
        mostrarMensagem(mensagem, "sucesso", r.corpo.mensagem || "Senha redefinida.");
        var login = $("#voltar-login");
        login.textContent = "Entrar com a nova senha";
        login.focus();
      }));
    });
    $("#ja-tenho-codigo-senha").addEventListener("click", function () {
      if (ocupado(solicitar)) return;
      if (!validarEmail(email)) { anunciarValidacao(solicitar, mensagem); return; }
      mostrarConfirmacao(email.value.trim());
    });
    $("#voltar-recuperacao").addEventListener("click", function () {
      if (ocupado(confirmar)) return;
      confirmar.reset();
      [codigo, senha, repeticao].forEach(function (campo) { marcar(campo, false); });
      atualizarLinkCaixa(caixa);
      alternarFormulario(confirmar, solicitar, email);
    });
  };

  telas.perfil = function () {
    var usuario = null;
    var emailAtualizado = false;
    var desenharFoto = ativarFotoPerfil(function () { return usuario && usuario.iniciais; });
    function preencher(u) {
      usuario = u;
      $("#nome-exibicao").value = u.nomeExibicao;
      $("#nome-completo").value = u.nome;
      $("#modo-preferido").value = u.modoPreferido;
      $("#sobre").value = u.sobre;
      if (!emailAtualizado) $("#email-atual").textContent = u.email;
      desenharFoto();
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
    ativarAlteracaoEmail(function (email) {
      emailAtualizado = true;
      $("#email-atual").textContent = email;
    });
  };

  document.addEventListener("DOMContentLoaded", function () {
    var tela = document.body.getAttribute("data-tela");
    if (tela !== "login" && tela !== "cadastro" && tela !== "recuperar-senha") {
      ativarSair();
      carregarUsuario();
    }
    if (telas[tela]) telas[tela]();
  });
})();
