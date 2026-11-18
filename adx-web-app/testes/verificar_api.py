# -*- coding: utf-8 -*-
"""Bateria de testes da API do ADX (24 verificações das regras de negócio).

Script original de 24 verificações, mantido para referência da entrega.
Para a suíte ampliada, repetível e com banco temporário, prefira:
  python testes/executar_todos.py

Para usar este script legado, prepare uma cópia separada do projeto com banco
novo e jogadores de demonstração; não apague o banco que você utiliza:
  1. Nessa cópia, inicie: mvn compile exec:java -Dexec.args="--demo"
  2. Em outro terminal:   python testes/verificar_api.py http://localhost:8080
O script cria contas e partidas de teste, por isso deve ser executado em um banco recém-criado.
O resultado também é gravado em resultados_testes.json.
"""
import datetime, http.cookiejar, json, sys, urllib.error, urllib.parse, urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080"
SAIDA = sys.argv[2] if len(sys.argv) > 2 else "resultados_testes.json"

class SemRedirecionar(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *a, **k):
        return None

class Cliente:
    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        self.op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar), SemRedirecionar())
    def req(self, metodo, caminho, dados=None, csrf=True, cookie=None):
        corpo, h = None, {}
        if dados is not None:
            corpo = urllib.parse.urlencode(dados).encode()
            h["Content-Type"] = "application/x-www-form-urlencoded; charset=UTF-8"
        if csrf:
            h["X-Requested-With"] = "ADX"
        if cookie:
            h["Cookie"] = cookie
        r = urllib.request.Request(BASE + caminho, data=corpo, headers=h, method=metodo)
        try:
            resp = self.op.open(r); st, txt, hd = resp.status, resp.read().decode("utf-8"), resp.headers
        except urllib.error.HTTPError as e:
            st, txt, hd = e.code, e.read().decode("utf-8"), e.headers
        try:
            js = json.loads(txt) if txt else None
        except ValueError:
            js = None
        return st, js, hd, txt

resultados = []
def check(codigo, regra, descricao, ok, obtido):
    resultados.append({"codigo": codigo, "regra": regra, "descricao": descricao, "ok": bool(ok), "obtido": str(obtido)})
    print(("[OK]   " if ok else "[FALHA]"), codigo, regra, "-", descricao, "->", obtido)

hoje = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=-3))).strftime("%d/%m/%Y")
A, B, L, X = Cliente(), Cliente(), Cliente(), Cliente()
SENHA = "senhaForte123"

# ---------- RN003: acesso restrito ----------
st, _, hd, _ = A.req("GET", "/painel.html")
st2, _, _, _ = A.req("GET", "/api/painel")
st3, _, _, _ = A.req("GET", "/api/partidas")
st4, _, _, _ = A.req("GET", "/cadastro.html")
check("T01", "RN003", "Sem login, páginas internas redirecionam para o login e a API responde 401; login e cadastro são públicos",
      st == 302 and hd.get("Location", "").endswith("/index.html") and st2 == 401 and st3 == 401 and st4 == 200,
      f"painel.html={st}, /api/painel={st2}, /api/partidas={st3}, cadastro.html={st4}")

# ---------- segurança: CSRF ----------
st, js, _, _ = A.req("POST", "/api/cadastro", {"nome": "Teste Csrf", "email": "csrf@x.com", "senha": SENHA, "confirmar": SENHA}, csrf=False)
check("T02", "RNF05", "Requisição que altera dados sem o cabeçalho X-Requested-With é recusada", st == 403, f"HTTP {st}")

# ---------- RN001: cadastro ----------
st, js, _, _ = A.req("POST", "/api/cadastro", {"nome": "Caio", "email": "email-invalido", "senha": "12345", "confirmar": "123"})
check("T03", "RN001", "Cadastro inválido (1 nome, e-mail ruim, senha curta, confirmação diferente) é recusado com erro em cada campo",
      st == 400 and set(js["erros"]) == {"nome", "email", "senha", "confirmar"}, f"HTTP {st}, campos={sorted(js['erros'])}")
st, js, _, _ = A.req("POST", "/api/cadastro", {"nome": "Caio Fernandes Soares", "email": "caio.soares@exemplo.com", "senha": SENHA, "confirmar": SENHA})
check("T04", "RN001", "Cadastro válido cria a conta", st == 201, f"HTTP {st}")
st, js, _, _ = A.req("POST", "/api/cadastro", {"nome": "Outra Pessoa", "email": "CAIO.SOARES@Exemplo.com", "senha": SENHA, "confirmar": SENHA})
check("T05", "RN001", "E-mail já cadastrado (mesmo com maiúsculas) é recusado: o e-mail é identificador único",
      st == 409 and "email" in js["erros"], f"HTTP {st}, {js['erros']['email']}")
for nome, email in (("Marina Lopes Dias", "marina@exemplo.com"), ("Teste Bloqueio Conta", "bloqueio@exemplo.com")):
    A.req("POST", "/api/cadastro", {"nome": nome, "email": email, "senha": SENHA, "confirmar": SENHA})

# ---------- RN003: login, bloqueio e sessão ----------
st, js, _, _ = L.req("POST", "/api/login", {"email": "bloqueio@exemplo.com", "senha": "errada-1"})
s1 = (st, js)
st, js, _, _ = L.req("POST", "/api/login", {"email": "bloqueio@exemplo.com", "senha": "errada-2"})
s2 = (st, js)
st, js, _, _ = L.req("POST", "/api/login", {"email": "bloqueio@exemplo.com", "senha": "errada-3"})
s3 = (st, js)
st, js, _, _ = L.req("POST", "/api/login", {"email": "bloqueio@exemplo.com", "senha": SENHA})
s4 = (st, js)
check("T06", "RN003", "Após 3 tentativas com senha incorreta o acesso é bloqueado por 15 minutos, mesmo com a senha certa",
      s1[0] == 401 and s2[0] == 401 and s3[0] == 423 and "15 minutos" in s3[1]["erro"] and s4[0] == 423,
      f"tentativas: {s1[0]}, {s2[0]}, {s3[0]}; senha correta durante o bloqueio: {s4[0]}")
st_a, js_a, _, _ = X.req("POST", "/api/login", {"email": "naoexiste@exemplo.com", "senha": "qualquer123"})
st_b, js_b, _, _ = X.req("POST", "/api/login", {"email": "marina@exemplo.com", "senha": "senha-errada"})
check("T07", "RNF05", "Login com e-mail inexistente e com senha errada dão a mesma resposta (não revela quais e-mails existem)",
      st_a == 401 and st_b == 401 and js_a == js_b, f"HTTP {st_a}/{st_b}: {js_a['erro']}")
st, js, hd, _ = A.req("POST", "/api/login", {"email": "caio.soares@exemplo.com", "senha": SENHA})
cookies = hd.get_all("Set-Cookie") or []
ck = next((c for c in cookies if c.startswith("ADXSESSAO")), "")
check("T08", "RN003", "Login correto abre a sessão; cookie com HttpOnly, SameSite=Lax e validade de 30 dias",
      st == 200 and "HttpOnly" in ck and "SameSite=Lax" in ck and "Max-Age=2592000" in ck,
      f"HTTP {st}; cookie: {ck.split(';')[0].split('=')[0]}; HttpOnly={'HttpOnly' in ck}; SameSite=Lax={'SameSite=Lax' in ck}; Max-Age=2592000={'Max-Age=2592000' in ck}")
cookie_antigo = ck.split(";")[0]
st, js, _, _ = A.req("GET", "/api/me")
check("T09", "RN003", "Com a sessão aberta, a API identifica o jogador", st == 200 and js["usuario"]["nomeExibicao"] == "Caio Fernandes",
      f"HTTP {st}, nome de exibição padrão = {js['usuario']['nomeExibicao']}")

# ---------- RN004: registro de partidas ----------
st, js, _, _ = A.req("POST", "/api/partidas", {"adversario": "", "modo": "XYZ", "resultado": ""})
check("T10", "RN004", "Partida com campos obrigatórios em branco ou inválidos é recusada",
      st == 400 and set(js["erros"]) == {"adversario", "modo", "resultado"}, f"HTTP {st}, campos={sorted(js['erros'])}")

partidas = [
    ("Rafael Moura", "Blitz", "Vitória"), ("Juliana Prates", "Clássico", "Derrota"), ("Bruno Tavares", "Rápido", "Vitória"),
    ("Letícia Andrade", "Rápido", "Empate"), ("Marcos Vilela", "Blitz", "Vitória"), ("Diego Sampaio", "Clássico", "Vitória"),
    ("Rafael Moura", "Rápido", "Derrota"), ("Paula Ribeiro", "Blitz", "Vitória"), ("Juliana Prates", "Rápido", "Empate"),
    ("Bruno Tavares", "Clássico", "Vitória"), ("Rafael Moura", "Clássico", "Empate"), ("Rafael Moura", "Blitz", "Vitória")]
codigos, primeira = [], None
for i, (adv, modo, res) in enumerate(partidas):
    dados = {"adversario": adv, "modo": modo, "resultado": res}
    if i == 0:
        dados["data"] = "01/01/2000"      # tentativa de escolher a data: deve ser ignorada
    st, js, _, _ = A.req("POST", "/api/partidas", dados)
    codigos.append(st)
    if i == 0:
        primeira = js["partida"]
check("T11", "RN004", "12 partidas válidas são registradas (HTTP 201)", all(c == 201 for c in codigos), f"{codigos.count(201)} de 12 com HTTP 201")
check("T12", "RN004", "A data da partida é gerada pelo servidor; uma data enviada pelo cliente é ignorada",
      primeira["data"] == hoje, f"data gravada = {primeira['registradaEm']} (hoje = {hoje})")

# ---------- RN002/RN004: sem exclusão nem alteração ----------
pid = primeira["id"]
st_d, js_d, hd_d, _ = A.req("DELETE", f"/api/partidas/{pid}")
st_p, _, _, _ = A.req("PUT", f"/api/partidas/{pid}", {"resultado": "Derrota"})
check("T13", "RN002", "Partidas registradas não podem ser excluídas nem alteradas (HTTP 405)",
      st_d == 405 and st_p == 405 and hd_d.get("Allow") == "GET, POST", f"DELETE={st_d}, PUT={st_p}, Allow={hd_d.get('Allow')}")

# ---------- histórico: paginação e filtros ----------
st, h1, _, _ = A.req("GET", "/api/partidas")
st2, h2, _, _ = A.req("GET", "/api/partidas?pagina=2")
check("T14", "RN002", "Histórico em páginas de 10, da mais recente para a mais antiga (12 partidas: 10 + 2)",
      h1["total"] == 12 and len(h1["itens"]) == 10 and h1["paginas"] == 2 and h1["itens"][0]["numero"] == 12
      and len(h2["itens"]) == 2 and h2["itens"][-1]["numero"] == 1 and (h1["de"], h1["ate"]) == (1, 10),
      f"total={h1['total']}, página 1={len(h1['itens'])}, página 2={len(h2['itens'])}, mais recente = nº {h1['itens'][0]['numero']}")
def total(q):
    s, j, _, _ = A.req("GET", "/api/partidas?" + urllib.parse.urlencode(q)); return s, j["total"]
f = {"modo Blitz": total({"modo": "Blitz"}), "resultado Empate": total({"resultado": "Empate"}),
     "busca 'rafa'": total({"busca": "rafa"}), "busca 'RAFA'": total({"busca": "RAFA"}),
     "Blitz + Vitória": total({"modo": "Blitz", "resultado": "Vitória"})}
check("T15", "RN002", "Filtros do histórico: por modo, por resultado, por nome (sem diferenciar maiúsculas) e combinados",
      f["modo Blitz"][1] == 4 and f["resultado Empate"][1] == 3 and f["busca 'rafa'"][1] == 4 and f["busca 'RAFA'"][1] == 4 and f["Blitz + Vitória"][1] == 4,
      ", ".join(f"{k}={v[1]}" for k, v in f.items()))
inj = [total({"busca": "' OR '1'='1"}), total({"busca": "%"}), total({"busca": "_"})]
check("T16", "RNF03", "Tentativa de injeção de SQL e curingas (%, _) na busca não retornam dados indevidos nem causam erro",
      all(s == 200 and t == 0 for s, t in inj), f"resultados: {[t for _, t in inj]}, HTTP {[s for s, _ in inj]}")

# ---------- detalhes e retrospecto ----------
ultima = h1["itens"][0]
st, d, _, _ = A.req("GET", f"/api/partidas/{ultima['id']}")
r = d["retrospecto"]["resumo"]
check("T17", "RF01", "Detalhes da partida nº 12 e retrospecto contra Rafael Moura (4 jogos: 2 vitórias, 1 empate, 1 derrota)",
      st == 200 and d["partida"]["numero"] == 12 and d["totalPartidas"] == 12
      and (r["total"], r["vitorias"], r["empates"], r["derrotas"]) == (4, 2, 1, 1),
      f"partida nº {d['partida']['numero']} de {d['totalPartidas']}; retrospecto {r['total']} jogos: {r['vitorias']}V {r['empates']}E {r['derrotas']}D")

# ---------- isolamento entre jogadores ----------
B.req("POST", "/api/login", {"email": "marina@exemplo.com", "senha": SENHA})
st, _, _, _ = B.req("GET", f"/api/partidas/{ultima['id']}")
st2, hb, _, _ = B.req("GET", "/api/partidas")
check("T18", "RNF03", "Um jogador não consegue ver a partida de outro (HTTP 404) e seu histórico começa vazio",
      st == 404 and hb["total"] == 0, f"partida de outro jogador: HTTP {st}; histórico próprio: {hb['total']} partidas")

# ---------- painel e ranking ----------
st, p, _, _ = A.req("GET", "/api/painel")
c = p["resumo"]
modos = {m["modo"]: (m["vitorias"], m["empates"], m["derrotas"]) for m in p["modos"]}
check("T19", "RN002", "Painel: estatísticas acumuladas calculadas pelo sistema (12 jogos, 7V 3E 2D, aproveitamento 70,8%) e por modo de jogo",
      (c["total"], c["vitorias"], c["empates"], c["derrotas"]) == (12, 7, 3, 2) and c["aproveitamento"] == 70.8
      and modos == {"Clássico": (2, 1, 1), "Rápido": (1, 2, 1), "Blitz": (4, 0, 0)} and len(p["ultimas"]) == 5,
      f"{c['total']} jogos, {c['vitorias']}V {c['empates']}E {c['derrotas']}D, {str(c['aproveitamento']).replace('.', ',')}%; por modo={modos}")
st, rk, _, _ = A.req("GET", "/api/ranking")
js_ = rk["jogadores"]
ordenado = all((js_[i]["vitorias"], js_[i]["aproveitamento"]) >= (js_[i + 1]["vitorias"], js_[i + 1]["aproveitamento"]) for i in range(len(js_) - 1))
eu = [j for j in js_ if j["voce"]]
check("T20", "RN002", "Ranking ordenado por vitórias (desempate pelo aproveitamento); o jogador logado aparece destacado e o painel mostra sua posição",
      ordenado and len(eu) == 1 and eu[0]["posicao"] == 6 and p["posicao"] == 6 and p["totalJogadores"] == len(js_) == 10,
      f"{len(js_)} jogadores; Caio em {eu[0]['posicao']}º com {eu[0]['vitorias']} vitórias; ordem correta={ordenado}")

# ---------- perfil ----------
st, js, _, _ = A.req("POST", "/api/perfil", {"nomeExibicao": "C", "modoPreferido": "Blitz", "sobre": ""})
ok_inv = st == 400 and "nomeExibicao" in js["erros"]
st2, js2, _, _ = A.req("POST", "/api/perfil", {"nomeExibicao": "Caio Soares", "modoPreferido": "Blitz", "sobre": "Jogo xadrez desde 2019."})
st3, js3, _, _ = A.req("GET", "/api/perfil")
check("T21", "RN005", "Perfil: nome de exibição inválido é recusado; alterações válidas são salvas e permanecem ao consultar de novo",
      ok_inv and st2 == 200 and js3["usuario"]["nomeExibicao"] == "Caio Soares" and js3["usuario"]["modoPreferido"] == "Blitz"
      and js3["usuario"]["sobre"] == "Jogo xadrez desde 2019." and js3["usuario"]["nome"] == "Caio Fernandes Soares",
      f"salvo: {js3['usuario']['nomeExibicao']} / {js3['usuario']['modoPreferido']}; nome completo inalterado: {js3['usuario']['nome']}")

# ---------- escape de conteúdo ----------
xss = "<img src=x onerror=alert(1)>"
st, js, _, txt = B.req("POST", "/api/partidas", {"adversario": xss, "modo": "Blitz", "resultado": "Vitória"})
check("T22", "RNF03", "Texto com HTML digitado pelo jogador é guardado como texto e devolvido escapado na API",
      st == 201 and "<img" not in txt and js["partida"]["adversario"] == xss, f"HTTP {st}; JSON sem '<img' literal={'<img' not in txt}")

# ---------- logout ----------
st, _, _, _ = A.req("POST", "/api/logout")
st2, _, _, _ = A.req("GET", "/api/me")
st3, _, _, _ = A.req("GET", "/api/painel")
st4, _, _, _ = X.req("GET", "/api/me", cookie=cookie_antigo)
check("T23", "RN003", "Logout encerra a sessão; a API volta a responder 401 e o cookie antigo deixa de valer",
      st == 200 and st2 == 401 and st3 == 401 and st4 == 401, f"logout={st}, /api/me={st2}, /api/painel={st3}, cookie antigo={st4}")

# ---------- cabeçalhos de segurança ----------
_, _, hd, _ = X.req("GET", "/index.html")
check("T24", "RNF05", "Respostas trazem cabeçalhos de segurança (CSP, X-Frame-Options, X-Content-Type-Options)",
      all(hd.get(k) for k in ("Content-Security-Policy", "X-Frame-Options", "X-Content-Type-Options")),
      f"X-Frame-Options={hd.get('X-Frame-Options')}, X-Content-Type-Options={hd.get('X-Content-Type-Options')}")

falhas = [r for r in resultados if not r["ok"]]
json.dump(resultados, open(SAIDA, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(f"\n{len(resultados) - len(falhas)} de {len(resultados)} verificações passaram.")
sys.exit(1 if falhas else 0)
