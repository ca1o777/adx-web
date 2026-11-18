# -*- coding: utf-8 -*-
"""Executa as camadas Java, API e frontend e reúne os resultados.

Requisitos: JDK 17+, Python 3.9+ e Node.js 20+. Usa o Maven Wrapper do projeto.
Nenhum servidor precisa estar aberto. A API roda num banco temporário separado.
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import uuid
import xml.etree.ElementTree as ET

PROJECT = Path(__file__).resolve().parents[1]
DEPENDENCY_GOAL = "org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath"


def executable(value, label):
    found = shutil.which(value)
    if found:
        return found
    path = Path(value).expanduser()
    if path.is_file():
        return str(path.resolve())
    if value == "java" and os.environ.get("JAVA_HOME"):
        candidate = Path(os.environ["JAVA_HOME"]) / "bin" / ("java.exe" if os.name == "nt" else "java")
        if candidate.is_file():
            return str(candidate)
    raise RuntimeError(f"{label} não encontrado: {value}")


def command(exe, args):
    # cmd /s /c exige aspas externas ao comando inteiro. Passamos a linha
    # pronta ao CreateProcess para evitar um segundo escape de aspas pelo Python.
    if os.name == "nt" and Path(exe).suffix.lower() in (".cmd", ".bat"):
        values = [str(exe), *map(str, args)]
        if any(any(c in value for c in ('"', '\r', '\n')) for value in values):
            raise RuntimeError("Argumento batch contém aspas ou quebra de linha não permitidas.")
        quoted = " ".join('"' + value + '"' for value in values)
        prefix = subprocess.list2cmdline([os.environ.get("COMSPEC", "cmd.exe"), "/d", "/s", "/c"])
        return prefix + ' "' + quoted + '"'
    return [exe, *args]


def run(exe, args, log, timeout=300, env=None):
    with log.open("w", encoding="utf-8") as stream:
        result = subprocess.run(command(exe, args), cwd=PROJECT, stdout=stream,
                                stderr=subprocess.STDOUT, timeout=timeout, env=env)
    return result.returncode


def junit_results(folder):
    cases = []
    for file in sorted(folder.glob("TEST-*.xml")):
        root = ET.parse(file).getroot()
        for case in root.iter("testcase"):
            failure = case.find("failure")
            error = case.find("error")
            skipped = case.find("skipped")
            status = ("failed" if failure is not None else "error" if error is not None
                      else "skipped" if skipped is not None else "passed")
            item = {"id": case.get("classname", "") + "." + case.get("name", ""),
                    "status": status, "duration_seconds": float(case.get("time", 0))}
            problem = failure if failure is not None else error
            if problem is not None:
                item["detail"] = problem.get("message") or (problem.text or "")[:5000]
            cases.append(item)
    counts = {"run": len(cases), "passed": 0, "failed": 0, "errors": 0, "skipped": 0}
    for case in cases:
        counts[{"error": "errors"}.get(case["status"], case["status"])] += 1
    return {"suite": "java", "summary": counts, "cases": cases}


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def wait_server(proc, url, timeout=40):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        if proc.poll() is not None:
            raise RuntimeError("O servidor encerrou antes de ficar pronto; consulte servidor.log.")
        try:
            with urllib.request.urlopen(url + "/api/me", timeout=1):
                return
        except urllib.error.HTTPError as error:
            if error.code == 401:
                return
        except (OSError, urllib.error.URLError):
            pass
        time.sleep(0.15)
    raise RuntimeError("O servidor não iniciou a tempo; consulte servidor.log.")


def stop_server(proc):
    if proc.poll() is None:
        proc.terminate()
        try:
            proc.wait(timeout=10)
        except subprocess.TimeoutExpired:
            proc.kill()
            proc.wait(timeout=5)


def summarize(report):
    if "summary" in report:
        return report["summary"]
    # Aceita o formato do runner frontend sem supor nomes de contadores.
    cases = report.get("cases", [])
    counts = {"run": len(cases), "passed": 0, "failed": 0, "errors": 0, "skipped": 0}
    for case in cases:
        key = {"pass": "passed", "fail": "failed", "error": "errors",
               "skip": "skipped"}.get(case.get("status"), case.get("status"))
        if key in counts:
            counts[key] += 1
    return counts


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--suites", nargs="+", choices=("java", "api", "frontend"),
                        default=["java", "api", "frontend"])
    wrapper = PROJECT / ("mvnw.cmd" if os.name == "nt" else "mvnw")
    parser.add_argument("--maven", default=str(wrapper) if wrapper.is_file() else "mvn")
    parser.add_argument("--java", default="java")
    parser.add_argument("--node", default="node")
    parser.add_argument("--maven-repo", type=Path)
    parser.add_argument("--report-dir", type=Path)
    parser.add_argument("--api-runs", type=int, default=1,
                        help="Repetições HTTP no mesmo banco temporário (1 a 10).")
    args = parser.parse_args()
    if not 1 <= args.api_runs <= 10:
        parser.error("--api-runs deve estar entre 1 e 10")

    tag = dt.datetime.now().strftime("%Y%m%d-%H%M%S") + "-" + uuid.uuid4().hex[:6]
    report_dir = (args.report_dir or PROJECT / "resultados-testes" / tag).resolve()
    report_dir.mkdir(parents=True, exist_ok=False)
    result = {"started_at": dt.datetime.now().astimezone().isoformat(),
              "suites": [], "infrastructure_errors": []}
    env = os.environ.copy()
    env["PYTHONIOENCODING"] = "utf-8"
    env["MAVEN_SKIP_RC"] = "true"
    env.setdefault("MAVEN_USER_HOME", str(PROJECT / ".ferramentas" / "maven"))
    java = maven = node = None
    build_ready = False
    if any(s in args.suites for s in ("java", "api")):
        try:
            maven = executable(args.maven, "Maven")
            java = executable(args.java, "Java")
            common = ["-B", "-ntp", "-Dstyle.color=never"]
            repository = args.maven_repo or PROJECT / ".ferramentas" / "repositorio"
            common.append("-Dmaven.repo.local=" + str(repository.resolve()))
            rc = run(maven, common + ["compile", DEPENDENCY_GOAL,
                     "-Dmdep.outputFile=target/test-runtime-classpath.txt", "-DincludeScope=runtime"],
                     report_dir / "maven-build.log", timeout=600, env=env)
            if rc != 0:
                raise RuntimeError(f"Preparação Maven falhou ({rc}); consulte maven-build.log.")
            build_ready = True
        except (RuntimeError, OSError, subprocess.TimeoutExpired) as error:
            result["infrastructure_errors"].append(str(error))

    if "java" in args.suites and build_ready:
        try:
            xml_dir = report_dir / "java-xml"
            rc = run(maven, common + ["test", "-Dadx.testReportsDirectory=" + str(xml_dir)],
                     report_dir / "java.log", env=env)
            java_result = junit_results(xml_dir)
            java_result["exit_code"] = rc
            if not java_result["cases"]:
                raise RuntimeError("Maven não gerou casos JUnit; consulte java.log.")
            if rc and not (java_result["summary"]["failed"] or java_result["summary"]["errors"]):
                raise RuntimeError(f"Maven encerrou com código {rc} sem falha JUnit; consulte java.log.")
            result["suites"].append(java_result)
        except (RuntimeError, OSError, ET.ParseError, subprocess.TimeoutExpired) as error:
            result["infrastructure_errors"].append(str(error))

    if "api" in args.suites and build_ready:
        try:
            classpath = str(PROJECT / "target" / "classes") + os.pathsep + (
                PROJECT / "target" / "test-runtime-classpath.txt").read_text(encoding="utf-8").strip()
            # Usa a própria pasta de resultados para respeitar ambientes restritos
            # e remover somente o banco temporário criado por esta execução.
            with tempfile.TemporaryDirectory(prefix="banco-temporario-", dir=report_dir) as temp:
                port = free_port()
                url = f"http://127.0.0.1:{port}"
                database = (Path(temp) / "banco" / "adx").as_posix()
                with (report_dir / "servidor.log").open("w", encoding="utf-8") as log:
                    proc = subprocess.Popen([java, "-Dfile.encoding=UTF-8", "-Djava.io.tmpdir=" + temp, f"-Dadx.porta={port}",
                        "-Dadx.db=jdbc:h2:file:" + database, "-cp", classpath, "br.edu.adx.Main"],
                        cwd=PROJECT, stdout=log, stderr=subprocess.STDOUT)
                    try:
                        wait_server(proc, url)
                        for index in range(1, args.api_runs + 1):
                            output = report_dir / f"api-{index}.json"
                            rc = run(sys.executable, [str(PROJECT / "testes" / "api" / "main.py"),
                                "--base-url", url, "--report", str(output)],
                                report_dir / f"api-{index}.log", timeout=180, env=env)
                            if not output.is_file():
                                raise RuntimeError(f"API não produziu relatório; consulte api-{index}.log.")
                            api_result = json.loads(output.read_text(encoding="utf-8"))
                            api_result["exit_code"] = rc
                            api_result["run_index"] = index
                            result["suites"].append(api_result)
                            if rc == 2:
                                raise RuntimeError(f"Falha de infraestrutura HTTP; consulte api-{index}.log.")
                    finally:
                        stop_server(proc)
        except (RuntimeError, OSError, ValueError, subprocess.TimeoutExpired) as error:
            result["infrastructure_errors"].append(str(error))

    if "frontend" in args.suites:
        try:
            node = executable(args.node, "Node.js")
            output_dir = report_dir / "frontend"
            rc = run(node, [str(PROJECT / "testes" / "frontend" / "executar.cjs"),
                           "--report-dir", str(output_dir)], report_dir / "frontend.log", env=env)
            frontend_file = output_dir / "summary.json"
            if not frontend_file.is_file():
                raise RuntimeError("Frontend não produziu summary.json; consulte frontend.log.")
            frontend = json.loads(frontend_file.read_text(encoding="utf-8"))
            frontend["suite"] = "frontend"
            frontend["exit_code"] = rc
            result["suites"].append(frontend)
            if rc == 2:
                raise RuntimeError("Erro de execução do frontend; consulte frontend.log.")
        except (RuntimeError, OSError, ValueError, subprocess.TimeoutExpired) as error:
            result["infrastructure_errors"].append(str(error))

    counts = {"run": 0, "passed": 0, "failed": 0, "errors": 0, "skipped": 0}
    for suite in result["suites"]:
        summary = summarize(suite)
        for key in counts:
            counts[key] += summary.get(key, 0)
    result["summary"] = counts
    result["finished_at"] = dt.datetime.now().astimezone().isoformat()
    (report_dir / "resumo.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    lines = ["# Resultado dos testes do ADX", "", "| Camada | Casos | Aprovados | Falhas | Erros | Ignorados |",
             "|---|---:|---:|---:|---:|---:|"]
    for suite in result["suites"]:
        s = summarize(suite)
        label = suite.get("suite", "?") + (f" #{suite['run_index']}" if "run_index" in suite else "")
        lines.append(f"| {label} | {s.get('run', 0)} | {s.get('passed', 0)} | {s.get('failed', 0)} | {s.get('errors', 0)} | {s.get('skipped', 0)} |")
    if result["infrastructure_errors"]:
        lines += ["", "Falhas de infraestrutura:"] + ["- " + e for e in result["infrastructure_errors"]]
    lines += ["", "Falhas de teste indicam comportamento da aplicação que precisa ser corrigido. Os testes não alteram o código de produção."]
    (report_dir / "resumo.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))
    print("\nRelatórios:", report_dir)
    if result["infrastructure_errors"]:
        return 2
    return 1 if counts["failed"] or counts["errors"] or any(s.get("exit_code") for s in result["suites"]) else 0


if __name__ == "__main__":
    raise SystemExit(main())
