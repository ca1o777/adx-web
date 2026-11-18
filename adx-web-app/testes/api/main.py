# -*- coding: utf-8 -*-
"""Testes HTTP do ADX; somente biblioteca padrao, contas isoladas por UUID."""
from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
import datetime as dt
import http.client
import http.cookiejar
import json
from pathlib import Path
import socket
import sys
import threading
import time
import unittest
import urllib.error
import urllib.parse
import urllib.request
import uuid


PASSWORD = "TesteForte123!"
BASE_URL = "http://127.0.0.1:8080"
TIMEOUT = 8.0
REQUEST_COUNT = 0
COUNT_LOCK = threading.Lock()
RUN_ID = uuid.uuid4().hex
USER_KEYS = {"id", "nome", "email", "nomeExibicao", "modoPreferido", "sobre", "iniciais"}
MATCH_KEYS = {"id", "numero", "adversario", "modo", "resultado", "data", "hora", "registradaEm"}
BRASILIA = dt.timezone(dt.timedelta(hours=-3))


class InfrastructureError(RuntimeError):
    """Servidor indisponivel, timeout ou resposta ilegivel para o transporte."""


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class Response:
    def __init__(self, status, headers, text):
        self.status, self.headers, self.text = status, headers, text
        try:
            self.json = json.loads(text) if text else None
        except ValueError:
            self.json = None


class Client:
    """Um cookie jar por cliente; nao compartilhe clientes entre threads."""
    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(self.jar), NoRedirect())

    def request(self, method, path, data=None, csrf="ADX", cookie=None):
        global REQUEST_COUNT
        headers = {}
        body = None
        if data is not None:
            body = urllib.parse.urlencode(data).encode("utf-8")
            headers["Content-Type"] = "application/x-www-form-urlencoded; charset=UTF-8"
        if csrf is not None:
            headers["X-Requested-With"] = csrf
        if cookie is not None:
            headers["Cookie"] = cookie
        request = urllib.request.Request(BASE_URL + path, data=body, headers=headers, method=method)
        with COUNT_LOCK:
            REQUEST_COUNT += 1
        try:
            response = self.opener.open(request, timeout=TIMEOUT)
        except urllib.error.HTTPError as error:
            response = error
        except (urllib.error.URLError, TimeoutError, socket.timeout, ConnectionError, OSError,
                http.client.HTTPException) as error:
            raise InfrastructureError("Sem resposta de %s %s: %s" % (method, path, error)) from error
        try:
            with response:
                return Response(response.code, response.headers, response.read().decode("utf-8"))
        except (TimeoutError, socket.timeout, ConnectionError, OSError, UnicodeDecodeError,
                http.client.HTTPException) as error:
            raise InfrastructureError("Falha ao ler %s %s: %s" % (method, path, error)) from error

    def session_cookie(self):
        return next(("%s=%s" % (item.name, item.value)
                     for item in self.jar if item.name == "ADXSESSAO"), None)


def email_for(label):
    return "%s-%s-%s@example.test" % (label, RUN_ID[:12], uuid.uuid4().hex[:10])


def registration(email=None, name="Pessoa Teste Completa", password=PASSWORD):
    return {"nome": name, "email": email or email_for("cadastro"),
            "senha": password, "confirmar": password}


def query(path, **parameters):
    return path + "?" + urllib.parse.urlencode(parameters)


class AdxHttpTests(unittest.TestCase):
    """Cenarios independentes de usuarios demo e da quantidade global de contas."""

    @classmethod
    def setUpClass(cls):
        cls.primary, cls.literal, cls.profile = Client(), Client(), Client()
        cls.primary_email = email_for("historico")
        cls.literal_email = email_for("literal")
        cls.profile_email = email_for("perfil")
        for client, email in ((cls.primary, cls.primary_email), (cls.literal, cls.literal_email),
                              (cls.profile, cls.profile_email)):
            created = client.request("POST", "/api/cadastro", registration(email))
            if created.status != 201:
                raise AssertionError("Fixture: cadastro deve responder 201; recebeu %s: %s" %
                                     (created.status, created.text[:300]))
            login = client.request("POST", "/api/login", {"email": email, "senha": PASSWORD})
            if login.status != 200:
                raise AssertionError("Fixture: login deve responder 200; recebeu %s" % login.status)
        cls.primary_matches = []
        rows = [
            ("Rafael Moura", "Blitz", "Vitória"), ("Juliana Prates", "Clássico", "Derrota"),
            ("Bruno Tavares", "Rápido", "Vitória"), ("Letícia Andrade", "Rápido", "Empate"),
            ("Marcos Vilela", "Blitz", "Vitória"), ("Diego Sampaio", "Clássico", "Vitória"),
            ("Rafael Moura", "Rápido", "Derrota"), ("Paula Ribeiro", "Blitz", "Vitória"),
            ("Juliana Prates", "Rápido", "Empate"), ("Bruno Tavares", "Clássico", "Vitória"),
            ("Rafael Moura", "Clássico", "Empate"), ("Rafael Moura", "Blitz", "Vitória"),
        ]
        for opponent, mode, outcome in rows:
            response = cls.primary.request("POST", "/api/partidas", {
                "adversario": opponent, "modo": mode, "resultado": outcome})
            if response.status != 201:
                raise AssertionError("Fixture: partida deve responder 201; recebeu %s" % response.status)
            cls.primary_matches.append(response.json["partida"])
        cls.literal_matches = []
        for opponent in ("Literal 100%", "Literal nome_sobrenome", "Literal pasta\\arquivo"):
            response = cls.literal.request("POST", "/api/partidas", {
                "adversario": opponent, "modo": "Blitz", "resultado": "Empate"})
            if response.status != 201:
                raise AssertionError("Fixture literal: recebeu %s" % response.status)
            cls.literal_matches.append(response.json["partida"])

    def assert_status(self, response, expected):
        self.assertEqual(response.status, expected,
                         "HTTP esperado %s, obtido %s; corpo=%s" %
                         (expected, response.status, response.text[:400]))
        return response.json

    def json_ok(self, client, path):
        response = client.request("GET", path)
        data = self.assert_status(response, 200)
        self.assertIsInstance(data, dict, "API deve devolver objeto JSON")
        self.assertIn("application/json", response.headers.get("Content-Type", ""))
        return data

    def new_account(self, label, name="Pessoa Teste Completa", password=PASSWORD):
        client, email = Client(), email_for(label)
        self.assert_status(client.request("POST", "/api/cadastro", registration(email, name, password)), 201)
        return client, email

    def signed_account(self, label, name="Pessoa Teste Completa"):
        client, email = self.new_account(label, name)
        self.assert_status(client.request("POST", "/api/login", {"email": email, "senha": PASSWORD}), 200)
        return client, email

    def test_01_public_pages_and_protected_pages(self):
        """T01: login/cadastro publicos e seis telas internas protegidas."""
        client = Client()
        for path in ("/", "/index.html", "/cadastro.html", "/assets/js/app.js"):
            with self.subTest(publico=path):
                self.assert_status(client.request("GET", path), 200)
        for path in ("/painel.html", "/historico.html", "/partida.html", "/registrar-partida.html",
                     "/ranking.html", "/perfil.html"):
            with self.subTest(protegido=path):
                response = client.request("GET", path)
                self.assert_status(response, 302)
                self.assertTrue(response.headers.get("Location", "").endswith("/index.html"))

    def test_02_all_private_api_routes_require_authentication(self):
        """T01 expandido: todas as rotas privadas, leitura e escrita, exigem sessao."""
        client = Client()
        routes = [("GET", "/api/me"), ("GET", "/api/painel"), ("GET", "/api/perfil"),
                  ("GET", "/api/ranking"), ("GET", "/api/partidas"), ("GET", "/api/partidas/adversarios"),
                  ("GET", "/api/partidas/%s" % self.primary_matches[0]["id"]),
                  ("POST", "/api/partidas"), ("POST", "/api/perfil"), ("POST", "/api/logout")]
        for method, path in routes:
            with self.subTest(method=method, route=path):
                response = client.request(method, path, {} if method == "POST" else None)
                self.assert_status(response, 401)
                self.assertIsInstance(response.json.get("erro"), str)

    def test_03_csrf_on_every_mutating_route(self):
        """T02: cadastro, login, logout, perfil e partidas exigem o cabecalho ADX."""
        routes = [(Client(), "/api/cadastro", registration()),
                  (Client(), "/api/login", {"email": self.primary_email, "senha": PASSWORD}),
                  (self.primary, "/api/logout", {}),
                  (self.primary, "/api/perfil", {"nomeExibicao": "Invasor", "modoPreferido": "Blitz"}),
                  (self.primary, "/api/partidas", {"adversario": "Invasor", "modo": "Blitz", "resultado": "Vitória"})]
        for client, path, data in routes:
            with self.subTest(route=path):
                self.assert_status(client.request("POST", path, data, csrf=None), 403)
        self.assertEqual(self.json_ok(self.primary, "/api/partidas")["total"], 12)
        self.assertEqual(self.json_ok(self.primary, "/api/me")["usuario"]["email"], self.primary_email)

    def test_04_wrong_csrf_value_is_rejected(self):
        """O cabecalho presente com valor incorreto tambem e rejeitado."""
        self.assert_status(self.primary.request("POST", "/api/perfil", {}, csrf="XMLHttpRequest"), 403)

    def test_05_registration_reports_all_invalid_fields(self):
        """T03: cadastro devolve os quatro erros de validacao juntos."""
        response = Client().request("POST", "/api/cadastro", {
            "nome": "Caio", "email": "invalido", "senha": "12345", "confirmar": "123"})
        self.assertEqual(set(self.assert_status(response, 400)["erros"]), {"nome", "email", "senha", "confirmar"})

    def test_06_registration_and_duplicate_case_insensitive_email(self):
        """T04/T05: cria conta e impede duplicidade de e-mail ignorando caixa."""
        client, email = self.new_account("duplicado")
        response = client.request("POST", "/api/cadastro", registration(email.upper(), "Outra Pessoa"))
        self.assertIn("email", self.assert_status(response, 409)["erros"])

    def test_07_registration_length_boundaries(self):
        """Limites inclusivos: nome100, email120, senha8/72; acima deles rejeita."""
        client = Client()
        for length in (8, 72):
            with self.subTest(senha_valida=length):
                data = registration(password="s" * length)
                self.assert_status(client.request("POST", "/api/cadastro", data), 201)
        # A parte local varia por execucao mantendo exatamente 120 caracteres.
        long_email = RUN_ID + uuid.uuid4().hex + "e" * 43 + "@example.test"
        self.assertEqual(len(long_email), 120)
        self.assert_status(client.request("POST", "/api/cadastro",
                                         registration(long_email, "A " + "b" * 98)), 201)
        invalid = [("nome", "A " + "b" * 99), ("email", "a" * 108 + "@example.test"),
                   ("senha", "s" * 7), ("senha", "s" * 73), ("confirmar", "diferente")]
        for field, value in invalid:
            with self.subTest(campo=field, tamanho=len(value)):
                data = registration()
                data[field] = value
                if field == "senha":
                    data["confirmar"] = value
                self.assertIn(field, self.assert_status(client.request("POST", "/api/cadastro", data), 400)["erros"])

    def test_08_registration_trims_name_email_not_password(self):
        """Normaliza espacos do nome/e-mail sem remover espacos da senha."""
        client = Client()
        email, password = email_for("espacos"), " senhaComEspaco "
        self.assert_status(client.request("POST", "/api/cadastro", {
            "nome": "  Maria   Lopes  Dias ", "email": "  " + email.upper() + "  ",
            "senha": password, "confirmar": password}), 201)
        response = client.request("POST", "/api/login", {"email": email, "senha": password})
        user = self.assert_status(response, 200)["usuario"]
        self.assertEqual((user["nome"], user["nomeExibicao"], user["email"]),
                         ("Maria Lopes Dias", "Maria Lopes", email))

    def test_09_login_rejects_missing_fields(self):
        """Login vazio retorna os erros de e-mail e senha; nao abre sessao."""
        client = Client()
        self.assertEqual(set(self.assert_status(client.request("POST", "/api/login", {}), 400)["erros"]),
                         {"email", "senha"})
        self.assert_status(client.request("GET", "/api/me"), 401)

    def test_10_sequential_login_lockout(self):
        """T06: tres falhas sequenciais bloqueiam inclusive a senha correta."""
        client, email = self.new_account("bloqueio")
        statuses = [client.request("POST", "/api/login", {"email": email, "senha": "incorreta"}).status
                    for _ in range(3)]
        self.assertEqual(statuses, [401, 401, 423])
        response = client.request("POST", "/api/login", {"email": email, "senha": PASSWORD})
        self.assert_status(response, 423)
        self.assertIn("15 minutos", response.json["erro"])
        self.assert_status(client.request("GET", "/api/me"), 401)

    def test_11_concurrent_login_failures_must_also_lock(self):
        """REGRESSAO: tres erros simultaneos devem bloquear a senha correta."""
        observations = []
        for round_number in range(1, 4):
            _, email = self.new_account("concorrente")
            barrier = threading.Barrier(3)

            def wrong_login(_):
                client = Client()
                barrier.wait(timeout=TIMEOUT)
                return client.request("POST", "/api/login", {"email": email, "senha": "incorreta"}).status

            with ThreadPoolExecutor(max_workers=3) as pool:
                statuses = list(pool.map(wrong_login, range(3)))
            self.assertTrue(all(status in (401, 423) for status in statuses),
                            "Tentativas concorrentes devem ser recusadas: %r" % statuses)
            correct = Client().request("POST", "/api/login", {"email": email, "senha": PASSWORD})
            observations.append({"rodada": round_number, "erros_simultaneos": statuses, "senha_correta": correct.status})
            # Basta uma violacao para demonstrar perda de atualizacao. Nao exige tres 423.
            self.assertEqual(correct.status, 423,
                             "Conta nao bloqueada apos tres erros simultaneos: %r" % observations)

    def test_12_login_error_does_not_reveal_existing_email(self):
        """T07: e-mail inexistente e senha errada retornam a mesma resposta."""
        client, email = self.new_account("enumera")
        unknown = client.request("POST", "/api/login", {"email": email_for("ausente"), "senha": "incorreta"})
        wrong = client.request("POST", "/api/login", {"email": email, "senha": "incorreta"})
        self.assert_status(unknown, 401)
        self.assert_status(wrong, 401)
        self.assertEqual(unknown.json, wrong.json)

    def test_13_successful_login_resets_failure_counter(self):
        """Login correto zera duas falhas anteriores; nova falha nao bloqueia."""
        client, email = self.new_account("zera")
        for _ in range(2):
            self.assert_status(client.request("POST", "/api/login", {"email": email, "senha": "incorreta"}), 401)
        self.assert_status(client.request("POST", "/api/login", {"email": email, "senha": PASSWORD}), 200)
        self.assert_status(client.request("POST", "/api/login", {"email": email, "senha": "incorreta"}), 401)
        self.assert_status(client.request("POST", "/api/login", {"email": email, "senha": PASSWORD}), 200)

    def test_14_session_cookie_and_identity(self):
        """T08/T09: cookie HttpOnly/SameSite/Lax com30dias e identidade correta."""
        client, email = self.new_account("cookie", "Caio Fernandes Soares")
        response = client.request("POST", "/api/login", {"email": email.upper(), "senha": PASSWORD})
        self.assert_status(response, 200)
        cookies = response.headers.get_all("Set-Cookie") or []
        cookie = next((value for value in cookies if value.startswith("ADXSESSAO=")), "")
        for attribute in ("HttpOnly", "SameSite=Lax", "Max-Age=2592000", "Path=/"):
            self.assertTrue(attribute.lower() in cookie.lower(), "Cookie sem atributo " + attribute)
        user = self.json_ok(client, "/api/me")["usuario"]
        self.assertEqual((user["email"], user["nomeExibicao"]), (email, "Caio Fernandes"))

    def test_15_relogin_rotates_and_invalidates_old_cookie(self):
        """Novo login no mesmo navegador troca a sessao e invalida o cookie antigo."""
        client, email = self.signed_account("rotacao")
        old_cookie = client.session_cookie()
        self.assertIsNotNone(old_cookie)
        self.assert_status(client.request("POST", "/api/login", {"email": email, "senha": PASSWORD}), 200)
        self.assertTrue(client.session_cookie() != old_cookie, "Novo login deve gerar outro id de sessao")
        self.assert_status(Client().request("GET", "/api/me", cookie=old_cookie), 401)
        self.assert_status(client.request("GET", "/api/me"), 200)

    def test_16_logout_and_independent_sessions(self):
        """T23: logout revoga sua sessao; outro navegador da mesma conta permanece."""
        first, email = self.signed_account("sessoes")
        second = Client()
        self.assert_status(second.request("POST", "/api/login", {"email": email, "senha": PASSWORD}), 200)
        old_cookie = first.session_cookie()
        self.assert_status(first.request("POST", "/api/logout", {}), 200)
        self.assert_status(first.request("GET", "/api/me"), 401)
        self.assert_status(first.request("GET", "/api/painel"), 401)
        self.assert_status(Client().request("GET", "/api/me", cookie=old_cookie), 401)
        self.assert_status(second.request("GET", "/api/me"), 200)

    def test_17_invalid_match_reports_all_fields(self):
        """T10: campos de partida invalidos retornam todos os erros sem gravar."""
        response = self.primary.request("POST", "/api/partidas", {"adversario": "", "modo": "XYZ", "resultado": ""})
        self.assertEqual(set(self.assert_status(response, 400)["erros"]), {"adversario", "modo", "resultado"})

    def test_18_twelve_valid_matches_have_server_order(self):
        """T11: doze partidas validas recebem IDs e numeracao exclusiva do jogador."""
        self.assertEqual([match["numero"] for match in self.primary_matches], list(range(1, 13)))
        self.assertEqual(len({match["id"] for match in self.primary_matches}), 12)
        for match in self.primary_matches:
            self.assertEqual(set(match), MATCH_KEYS)

    def test_19_match_length_boundaries_and_server_date(self):
        """T12 expandido: sem dataPartida, vale hoje em Brasília, inclusive com campos legados."""
        client, _ = self.signed_account("partida-limites")
        for name in ("AB", "N" * 60):
            before = dt.datetime.now(BRASILIA).strftime("%d/%m/%Y")
            response = client.request("POST", "/api/partidas", {"adversario": name, "modo": "Blitz",
                "resultado": "Vitória", "data": "01/01/2000", "data_hora": "2000-01-01", "numero": 99,
                "usuario_id": self.json_ok(self.primary, "/api/me")["usuario"]["id"]})
            match = self.assert_status(response, 201)["partida"]
            after = dt.datetime.now(BRASILIA).strftime("%d/%m/%Y")
            self.assertEqual(match["adversario"], name)
            self.assertIn(match["data"], {before, after})
        for name in ("A", "N" * 61):
            with self.subTest(tamanho=len(name)):
                response = client.request("POST", "/api/partidas", {"adversario": name, "modo": "Blitz", "resultado": "Vitória"})
                self.assertIn("adversario", self.assert_status(response, 400)["erros"])
        history = self.json_ok(client, "/api/partidas")
        self.assertEqual(history["total"], 2)
        self.assertEqual([match["numero"] for match in history["itens"]], [2, 1])
        self.assertEqual(self.json_ok(self.primary, "/api/partidas")["total"], 12)

    def test_20_matches_cannot_be_edited_or_deleted(self):
        """T13: PUT/DELETE recusados; detalhes e resultado permanecem intactos."""
        match = self.primary_matches[0]
        path = "/api/partidas/%s" % match["id"]
        for method in ("PUT", "DELETE"):
            with self.subTest(metodo=method):
                response = self.primary.request(method, path, {"resultado": "Derrota"})
                self.assert_status(response, 405)
                self.assertEqual(response.headers.get("Allow"), "GET, POST")
        self.assertEqual(self.json_ok(self.primary, path)["partida"], match)

    def test_21_default_pagination_and_latest_first(self):
        """T14: 12 partidas, paginas10+2, ordem descendente e faixas corretas."""
        first = self.json_ok(self.primary, "/api/partidas")
        second = self.json_ok(self.primary, "/api/partidas?pagina=2")
        self.assertEqual((first["total"], first["paginas"], first["porPagina"], first["de"], first["ate"]),
                         (12, 2, 10, 1, 10))
        self.assertEqual([match["numero"] for match in first["itens"]], list(range(12, 2, -1)))
        self.assertEqual([match["numero"] for match in second["itens"]], [2, 1])
        self.assertEqual((second["de"], second["ate"]), (11, 12))

    def test_22_pagination_normalization_and_bounds(self):
        """Paginas negativas, nao numericas e tamanhos fora1..50 sao normalizados."""
        cases = [({"pagina": 0}, 1, 10), ({"pagina": -1}, 1, 10),
                 ({"pagina": "abc"}, 1, 10), ({"pagina": 999}, 2, 10),
                 ({"porPagina": 0}, 1, 1), ({"porPagina": -2}, 1, 1),
                 ({"porPagina": 51}, 1, 50), ({"porPagina": "abc"}, 1, 10)]
        for parameters, expected_page, expected_size in cases:
            with self.subTest(parametros=parameters):
                result = self.json_ok(self.primary, query("/api/partidas", **parameters))
                self.assertEqual((result["pagina"], result["porPagina"]), (expected_page, expected_size))
                self.assertEqual(result["total"], 12)

    def test_23_maximum_integer_page_must_not_produce_500(self):
        """REGRESSAO: pagina2147483647 deve responder200 normalizado ou400, nunca500."""
        response = self.primary.request("GET", "/api/partidas?pagina=2147483647")
        self.assertIn(response.status, (200, 400), "Pagina extrema retornou HTTP %s: %s" %
                      (response.status, response.text[:300]))
        if response.status == 200:
            self.assertEqual((response.json["total"], response.json["pagina"]), (12, 2))

    def test_24_filters_individual_and_combined(self):
        """T15: modo, resultado, busca sem caixa e combinacao de filtros."""
        filters = [({"modo": "Blitz"}, 4), ({"resultado": "Empate"}, 3),
                   ({"busca": "rafa"}, 4), ({"busca": "RAFA"}, 4),
                   ({"modo": "Blitz", "resultado": "Vitória"}, 4),
                   ({"modo": "Rápido", "resultado": "Empate", "busca": "Juliana"}, 1)]
        for parameters, expected in filters:
            with self.subTest(filtros=parameters):
                self.assertEqual(self.json_ok(self.primary, query("/api/partidas", **parameters))["total"], expected)

    def test_25_invalid_filters_are_rejected(self):
        """Filtros fora dos dominios de modo/resultado devolvem400."""
        for parameters in ({"modo": "blitz"}, {"resultado": "Vitoria"}):
            with self.subTest(filtros=parameters):
                self.assert_status(self.primary.request("GET", query("/api/partidas", **parameters)), 400)

    def test_26_search_escapes_sql_and_literal_wildcards(self):
        """T16: %, _ e barra invertida sao literais; SQL nao amplia os resultados."""
        for term in ("' OR '1'='1", "%", "_", "\\"):
            with self.subTest(sem_correspondencia=term):
                self.assertEqual(self.json_ok(self.primary, query("/api/partidas", busca=term))["total"], 0)
        for term, expected in (("%", "Literal 100%"), ("_", "Literal nome_sobrenome"),
                               ("\\", "Literal pasta\\arquivo")):
            with self.subTest(busca_literal=term):
                result = self.json_ok(self.literal, query("/api/partidas", busca=term))
                self.assertEqual(result["total"], 1)
                self.assertEqual(result["itens"][0]["adversario"], expected)

    def test_27_empty_history_contract(self):
        """Historico vazio tem pagina1, faixas0/0 e lista vazia."""
        result = self.json_ok(self.profile, "/api/partidas?pagina=2")
        self.assertEqual({key: result[key] for key in ("total", "pagina", "paginas", "de", "ate", "itens")},
                         {"total": 0, "pagina": 1, "paginas": 1, "de": 0, "ate": 0, "itens": []})

    def test_28_details_and_opponent_retrospective(self):
        """T17: retrospecto Rafael4jogos,2V/1E/1D e detalhe da partida12."""
        result = self.json_ok(self.primary, "/api/partidas/%s" % self.primary_matches[-1]["id"])
        self.assertEqual(result["partida"], self.primary_matches[-1])
        self.assertEqual(result["totalPartidas"], 12)
        summary = result["retrospecto"]["resumo"]
        self.assertEqual(tuple(summary[key] for key in ("total", "vitorias", "empates", "derrotas")), (4, 2, 1, 1))
        self.assertEqual([match["numero"] for match in result["retrospecto"]["partidas"]], [12, 11, 7, 1])

    def test_29_isolation_for_multiple_match_ids(self):
        """T18: varios IDs de outro jogador nao vazam detalhes nem retrospectos."""
        for client, foreign in ((self.literal, self.primary_matches[:2]), (self.primary, self.literal_matches[:2])):
            for match in foreign:
                with self.subTest(partida=match["id"]):
                    self.assert_status(client.request("GET", "/api/partidas/%s" % match["id"]), 404)
        self.assertEqual(self.json_ok(self.primary, "/api/partidas")["total"], 12)
        self.assertEqual(self.json_ok(self.literal, "/api/partidas")["total"], 3)

    def test_30_invalid_or_missing_match_identifiers(self):
        """IDs inexistentes, negativos, texto e overflow devolvem404 sem erro500."""
        for match_id in ("0", "-1", "abc", "9223372036854775808"):
            with self.subTest(id=match_id):
                self.assert_status(self.primary.request("GET", "/api/partidas/" + match_id), 404)

    def test_31_dashboard_statistics_and_modes(self):
        """T19: painel calcula12jogos,7V/3E/2D,70.8% e estatisticas por modo."""
        result = self.json_ok(self.primary, "/api/painel")
        self.assertEqual(result["resumo"], {"total": 12, "vitorias": 7, "empates": 3, "derrotas": 2, "aproveitamento": 70.8})
        modes = {item["modo"]: (item["vitorias"], item["empates"], item["derrotas"]) for item in result["modos"]}
        self.assertEqual(modes, {"Clássico": (2, 1, 1), "Rápido": (1, 2, 1), "Blitz": (4, 0, 0)})
        self.assertEqual([match["numero"] for match in result["ultimas"]], [12, 11, 10, 9, 8])

    def test_32_ranking_order_self_marker_and_dashboard_position(self):
        """T20: ranking ordenado e destaque/posicao coerentes sem total global fixo."""
        rows = self.json_ok(self.primary, "/api/ranking")["jogadores"]
        panel = self.json_ok(self.primary, "/api/painel")
        self.assertEqual([row["posicao"] for row in rows], list(range(1, len(rows) + 1)))
        keys = [(row["vitorias"], row["aproveitamento"]) for row in rows]
        self.assertEqual(keys, sorted(keys, reverse=True))
        own = [row for row in rows if row["voce"]]
        self.assertEqual(len(own), 1)
        self.assertEqual((own[0]["posicao"], own[0]["vitorias"]), (panel["posicao"], 7))
        self.assertEqual(panel["totalJogadores"], len(rows))

    def test_33_profile_valid_update_and_immutable_fields(self):
        """T21: perfil persiste campos editaveis e ignora ID, nome e e-mail forjados."""
        before = self.json_ok(self.profile, "/api/perfil")["usuario"]
        data = {"nomeExibicao": "Jogador de Teste", "modoPreferido": "Blitz", "sobre": "Jogo desde 2019.",
                "id": 999999, "nome": "Nome Forjado", "email": email_for("forjado"), "senha_hash": "forjado"}
        saved = self.assert_status(self.profile.request("POST", "/api/perfil", data), 200)["usuario"]
        self.assertEqual(saved, self.json_ok(self.profile, "/api/perfil")["usuario"])
        self.assertEqual((saved["nomeExibicao"], saved["modoPreferido"], saved["sobre"]),
                         ("Jogador de Teste", "Blitz", "Jogo desde 2019."))
        for key in ("id", "nome", "email"):
            self.assertEqual(saved[key], before[key])

    def test_34_profile_boundaries_and_no_write_on_validation_failure(self):
        """Perfil: nome2/40 e sobre300 aceitos; nome1/41 e sobre301 rejeitados."""
        for name, about in (("AB", ""), ("N" * 40, "s" * 300)):
            with self.subTest(nome=len(name), sobre=len(about)):
                self.assert_status(self.profile.request("POST", "/api/perfil", {
                    "nomeExibicao": name, "modoPreferido": "Rápido", "sobre": about}), 200)
        before = self.json_ok(self.profile, "/api/perfil")["usuario"]
        for field, value in (("nomeExibicao", "A"), ("nomeExibicao", "N" * 41),
                             ("sobre", "s" * 301), ("modoPreferido", "blitz")):
            with self.subTest(campo=field, tamanho=len(value)):
                data = {"nomeExibicao": "Nome Valido", "modoPreferido": "Blitz", "sobre": "Valido"}
                data[field] = value
                self.assertIn(field, self.assert_status(self.profile.request("POST", "/api/perfil", data), 400)["erros"])
        self.assertEqual(self.json_ok(self.profile, "/api/perfil")["usuario"], before)

    def test_35_partial_profile_form_is_rejected_without_overwrite(self):
        """POST exige formulario completo: enviar so sobre falha sem alterar perfil."""
        self.assert_status(self.profile.request("POST", "/api/perfil", {
            "nomeExibicao": "Perfil Integral", "modoPreferido": "Clássico", "sobre": "Texto a preservar."}), 200)
        before = self.json_ok(self.profile, "/api/perfil")["usuario"]
        response = self.profile.request("POST", "/api/perfil", {"sobre": "Somente sobre"})
        self.assertEqual(set(self.assert_status(response, 400)["erros"]), {"nomeExibicao", "modoPreferido"})
        self.assertEqual(self.json_ok(self.profile, "/api/perfil")["usuario"], before)

    def test_36_omitted_optional_about_is_full_form_replacement(self):
        """Contrato atual: sobre omitido em POST valido substitui o texto por vazio."""
        self.assert_status(self.profile.request("POST", "/api/perfil", {
            "nomeExibicao": "Perfil Integral", "modoPreferido": "Blitz", "sobre": "Texto anterior"}), 200)
        response = self.profile.request("POST", "/api/perfil", {
            "nomeExibicao": "Perfil Integral", "modoPreferido": "Blitz"})
        self.assertEqual(self.assert_status(response, 200)["usuario"]["sobre"], "")
        self.assertEqual(self.json_ok(self.profile, "/api/perfil")["usuario"]["sobre"], "")

    def test_37_json_escapes_user_html(self):
        """T22: HTML permanece texto e e escapado no JSON de partida/perfil."""
        client, _ = self.signed_account("html")
        payload = "<img src=x onerror=alert(1)>"
        response = client.request("POST", "/api/partidas", {
            "adversario": payload, "modo": "Blitz", "resultado": "Vitória"})
        self.assertEqual(self.assert_status(response, 201)["partida"]["adversario"], payload)
        self.assertNotIn("<img", response.text)
        response = client.request("POST", "/api/perfil", {
            "nomeExibicao": "Pessoa HTML", "modoPreferido": "Blitz", "sobre": payload})
        self.assertEqual(self.assert_status(response, 200)["usuario"]["sobre"], payload)
        self.assertNotIn("<img", response.text)

    def test_38_user_schema_does_not_expose_credentials(self):
        """/me e /perfil expoem somente campos publicos, sem senha/hash/bloqueio."""
        for path in ("/api/me", "/api/perfil"):
            with self.subTest(route=path):
                user = self.json_ok(self.primary, path)["usuario"]
                self.assertEqual(set(user), USER_KEYS)
                self.assertNotIn("senha", json.dumps(user).lower())
        rows = self.json_ok(self.primary, "/api/ranking")["jogadores"]
        allowed = {"posicao", "nome", "total", "vitorias", "empates", "derrotas", "aproveitamento", "voce"}
        self.assertTrue(all(set(row) == allowed for row in rows), "Ranking nao deve expor credenciais ou e-mails")

    def test_39_security_headers_on_public_private_and_error_responses(self):
        """T24: cabecalhos de seguranca tambem aparecem em401/404 e JSON sem cache."""
        checks = [(Client(), "/index.html", 200), (Client(), "/api/painel", 401),
                  (self.primary, "/api/me", 200), (self.primary, "/api/partidas/0", 404)]
        for client, path, status in checks:
            with self.subTest(route=path, status=status):
                response = client.request("GET", path)
                self.assert_status(response, status)
                self.assertEqual(response.headers.get("X-Frame-Options"), "DENY")
                self.assertEqual(response.headers.get("X-Content-Type-Options"), "nosniff")
                self.assertEqual(response.headers.get("Referrer-Policy"), "same-origin")
                self.assertIn("frame-ancestors 'none'", response.headers.get("Content-Security-Policy", ""))
                if path.startswith("/api/") and status != 401:
                    self.assertEqual(response.headers.get("Cache-Control"), "no-store")

    def test_40_opponents_include_public_players_and_only_personal_history(self):
        """A lista inclui novatos e todo o histórico pessoal, usando somente nomes públicos."""
        path = "/api/partidas/adversarios"
        display_name = "Jogador " + uuid.uuid4().hex[:10]
        full_name = display_name + " Sobrenome"
        newcomer, email = self.signed_account("lista-adversarios", full_name)

        data = self.json_ok(self.primary, path)
        self.assertEqual(set(data), {"adversarios"})
        names = data["adversarios"]
        self.assertIsInstance(names, list)
        self.assertTrue(all(isinstance(name, str) for name in names))
        self.assertIn(display_name, names)
        self.assertNotIn(full_name, names)
        self.assertNotIn(email, names)
        self.assertNotIn(display_name, self.json_ok(newcomer, path)["adversarios"])
        self.assertTrue({match["adversario"] for match in self.primary_matches}.issubset(names))
        self.assertEqual([name.lower() for name in names].count("rafael moura"), 1)

        private_opponent = "Visitante " + uuid.uuid4().hex[:10]
        self.assert_status(newcomer.request("POST", "/api/partidas", {
            "adversario": private_opponent, "modo": "Blitz", "resultado": "Empate"}), 201)
        self.assertIn(private_opponent, self.json_ok(newcomer, path)["adversarios"])
        self.assertNotIn(private_opponent, self.json_ok(self.primary, path)["adversarios"])


    def test_41_calendar_date_persists_without_changing_registration_time_or_order(self):
        """Um dia escolhido no calendário aparece na partida e nas consultas após o registro."""
        client, _ = self.signed_account("calendario")
        payload = {"adversario": "Ana Souza", "modo": "Blitz", "resultado": "Empate"}
        today = dt.datetime.now(BRASILIA).date()
        first = self.assert_status(client.request("POST", "/api/partidas",
                                   {**payload, "dataPartida": today.isoformat()}), 201)["partida"]
        self.assertEqual(first["data"], today.strftime("%d/%m/%Y"))

        before = dt.datetime.now(BRASILIA).replace(second=0, microsecond=0)
        past = self.assert_status(client.request("POST", "/api/partidas",
                                  {**payload, "dataPartida": "2024-02-29"}), 201)["partida"]
        after = dt.datetime.now(BRASILIA)
        self.assertEqual(past["data"], "29/02/2024")
        self.assertEqual((first["numero"], past["numero"]), (1, 2))
        registered_at = dt.datetime.strptime(past["registradaEm"], "%d/%m/%Y às %H:%M").replace(tzinfo=BRASILIA)
        self.assertLessEqual(before, registered_at)
        self.assertLessEqual(registered_at, after)
        self.assertEqual(past["hora"], registered_at.strftime("%H:%M"))
        details = self.json_ok(client, "/api/partidas/%s" % past["id"])
        self.assertEqual(details["partida"], past)
        self.assertEqual(details["retrospecto"]["partidas"], [past, first])
        self.assertEqual(self.json_ok(client, "/api/partidas")["itens"], [past, first])
        self.assertEqual(self.json_ok(client, "/api/painel")["ultimas"], [past, first])

    def test_42_calendar_rejects_empty_malformed_impossible_and_future_dates(self):
        """Uma data inválida gera erro no campo e nenhuma partida é gravada."""
        client, _ = self.signed_account("calendario-invalido")
        future = dt.datetime.now(BRASILIA).date() + dt.timedelta(days=2)
        for value in ("", "29/02/2024", "2024-2-9", "2023-02-29", "2024-04-31", future.isoformat()):
            with self.subTest(data=value):
                response = client.request("POST", "/api/partidas", {
                    "adversario": "Ana Souza", "modo": "Blitz", "resultado": "Empate", "dataPartida": value})
                self.assertEqual(set(self.assert_status(response, 400)["erros"]), {"dataPartida"})
        self.assertEqual(self.json_ok(client, "/api/partidas")["total"], 0)


class JsonTestResult(unittest.TextTestResult):
    """Um registro por metodo unittest; detalhes de subtestes ficam no pai."""
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.cases = []
        self.started = {}
        self.outcomes = {}
        self.infrastructure_error = False

    def startTest(self, test):
        super().startTest(test)
        self.started[test.id()] = time.monotonic()
        self.outcomes[test.id()] = {"status": "pass", "details": []}

    def remember(self, test, status, detail):
        key = test.id()
        current = self.outcomes.setdefault(key, {"status": status, "details": []})
        priority = {"pass": 0, "skipped": 1, "fail": 2, "error": 3}
        if priority[status] > priority[current["status"]]:
            current["status"] = status
        if detail:
            current["details"].append(detail)

    def addFailure(self, test, err):
        super().addFailure(test, err)
        self.remember(test, "fail", self._exc_info_to_string(err, test))

    def addError(self, test, err):
        super().addError(test, err)
        if isinstance(err[1], InfrastructureError):
            self.infrastructure_error = True
        self.remember(test, "error", self._exc_info_to_string(err, test))
        if test.id() not in self.started:  # setUpClass tambem precisa aparecer no JSON
            self.cases.append({"id": test.id(), "description": str(test), "status": "error",
                               "duration_seconds": 0, "detail": self.outcomes[test.id()]["details"]})

    def addSkip(self, test, reason):
        super().addSkip(test, reason)
        self.remember(test, "skipped", reason)

    def addSubTest(self, test, subtest, err):
        super().addSubTest(test, subtest, err)
        if err is not None:
            status = "fail" if issubclass(err[0], test.failureException) else "error"
            if isinstance(err[1], InfrastructureError):
                self.infrastructure_error = True
            self.remember(test, status, str(subtest) + "\n" + self._exc_info_to_string(err, test))

    def stopTest(self, test):
        outcome = self.outcomes[test.id()]
        record = {"id": test.id(), "description": test.shortDescription() or test.id(),
                  "status": outcome["status"],
                  "duration_seconds": round(time.monotonic() - self.started[test.id()], 4)}
        if outcome["details"]:
            record["detail"] = outcome["details"]
        self.cases.append(record)
        super().stopTest(test)


def main(argv=None):
    global BASE_URL, TIMEOUT
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default=BASE_URL, help="Servidor ADX ja iniciado")
    parser.add_argument("--report", type=Path, default=Path("resultados_api.json"))
    parser.add_argument("--timeout", type=float, default=TIMEOUT, help="Timeout de cada requisicao, em segundos")
    parser.add_argument("--test", action="append", help="Metodo especifico, repetivel; padrao: todos")
    args = parser.parse_args(argv)
    BASE_URL = args.base_url.rstrip("/")
    TIMEOUT = args.timeout
    parsed = urllib.parse.urlsplit(BASE_URL)
    if parsed.scheme not in ("http", "https") or not parsed.netloc or parsed.query or parsed.fragment:
        parser.error("--base-url deve ser uma URL HTTP(S) sem query/fragmento")
    if TIMEOUT <= 0:
        parser.error("--timeout deve ser positivo")
    start = time.monotonic()
    report = {"suite": "ADX HTTP integration", "base_url": BASE_URL,
              "started_at": dt.datetime.now(dt.timezone.utc).isoformat(), "run_id": RUN_ID}
    try:
        response = Client().request("GET", "/index.html")
        if response.status != 200:
            raise InfrastructureError("Preflight /index.html respondeu HTTP %s, esperado200" % response.status)
    except InfrastructureError as error:
        report["cases"] = [{"id": "infrastructure.preflight", "description": "Servidor acessivel",
                            "status": "error", "duration_seconds": round(time.monotonic() - start, 4),
                            "detail": str(error)}]
        report["exit_code"] = 2
        print(str(error), file=sys.stderr)
    else:
        loader = unittest.TestLoader()
        if args.test:
            suite = unittest.TestSuite(loader.loadTestsFromName(name, AdxHttpTests) for name in args.test)
        else:
            suite = loader.loadTestsFromTestCase(AdxHttpTests)
        result = unittest.TextTestRunner(verbosity=2, resultclass=JsonTestResult).run(suite)
        report["cases"] = result.cases
        report["exit_code"] = 2 if result.infrastructure_error else (0 if result.wasSuccessful() else 1)
    cases = report["cases"]
    report["duration_seconds"] = round(time.monotonic() - start, 4)
    report["requests"] = REQUEST_COUNT
    report["summary"] = {"run": len(cases), "passed": sum(case["status"] == "pass" for case in cases),
                         "failed": sum(case["status"] == "fail" for case in cases),
                         "errors": sum(case["status"] == "error" for case in cases),
                         "skipped": sum(case["status"] == "skipped" for case in cases)}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print("Relatorio: %s; requisicoes: %s" % (args.report, REQUEST_COUNT))
    return report["exit_code"]


if __name__ == "__main__":
    sys.exit(main())
