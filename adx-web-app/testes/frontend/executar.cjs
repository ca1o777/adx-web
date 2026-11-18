'use strict';

// Node >= 20, sem npm install. Código de saída 1 quando qualquer teste falha.
const fs = require('node:fs');
const path = require('node:path');
const { run } = require('node:test');

const args = process.argv.slice(2);
let reportDir = path.join(__dirname, 'relatorios');
let namePattern;
for (let index = 0; index < args.length; index++) {
  if (args[index] === '--report-dir' && args[index + 1]) reportDir = path.resolve(args[++index]);
  else if (args[index] === '--name' && args[index + 1]) namePattern = args[++index];
  else { process.stderr.write('Uso: node testes/frontend/executar.cjs [--report-dir PASTA] [--name REGEX]\n'); process.exit(2); }
}
fs.mkdirSync(reportDir, { recursive: true });
const eventsPath = path.join(reportDir, 'resultados.ndjson');
const summaryPath = path.join(reportDir, 'summary.json');
const eventsFile = fs.openSync(eventsPath, 'w');
const started = new Date().toISOString();
const report = { suite: 'frontend', started, node: process.version, summary: { run: 0, passed: 0, failed: 0, errors: 0, skipped: 0 }, cases: [] };
const summary = report.summary;
function serialize(value) {
  if (Object.prototype.toString.call(value) === '[object RegExp]') return String(value);
  if (value instanceof Error || (value && typeof value === 'object' && value.message && value.stack)) {
    return { name: value.name, message: value.message, code: value.code, stack: value.stack, expected: value.expected, actual: value.actual, cause: value.cause && serialize(value.cause) };
  }
  if (Array.isArray(value)) return value.map(serialize);
  if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([key, child]) => [key, serialize(child)]));
  return value;
}

(async () => {
  const files = fs.readdirSync(__dirname).filter(file => file.endsWith('.test.cjs')).sort()
    .map(file => path.join(__dirname, file));
  const stream = run({ files, ...(namePattern ? { testNamePatterns: [namePattern] } : {}) });
  for await (const event of stream) {
    fs.writeSync(eventsFile, JSON.stringify(serialize(event)) + '\n');
    if (event.type !== 'test:pass' && event.type !== 'test:fail') continue;
    const data = event.data;
    if (data.nesting !== 0) continue;
    summary.run++;
    const error = data.details.error;
    const assertion = error && (error.code === 'ERR_ASSERTION' || error.cause?.code === 'ERR_ASSERTION');
    const status = data.skip ? 'skipped' : event.type === 'test:pass' ? 'passed' : assertion ? 'failed' : 'error';
    if (status === 'skipped') summary.skipped++;
    else if (status === 'passed') summary.passed++;
    else if (status === 'failed') summary.failed++;
    else summary.errors++;
    report.cases.push({
      id: `FE-${String(summary.run).padStart(3, '0')}`,
      description: data.name,
      status,
      duration_seconds: (data.details.duration_ms || 0) / 1000,
      regression_case: data.name.startsWith('regressão:'),
      file: data.file,
      line: data.line,
      ...(error ? { detail: (error.cause || error).message, error: serialize(error) } : {})
    });
    process.stdout.write(`${status.toUpperCase()} ${data.name}\n`);
  }
  report.finished = new Date().toISOString();
  report.status = summary.errors ? 'error' : summary.failed ? 'failed' : 'passed';
  fs.closeSync(eventsFile);
  fs.writeFileSync(summaryPath, JSON.stringify(report, null, 2) + '\n', 'utf8');
  process.stdout.write(`\n${summary.passed}/${summary.run} passaram; ${summary.failed} falharam; ${summary.errors} erros de execução; ${summary.skipped} ignorados.\nRelatório JSON: ${summaryPath}\n`);
  process.exitCode = summary.errors ? 2 : summary.failed ? 1 : 0;
})().catch(error => {
  fs.closeSync(eventsFile);
  process.stderr.write(error.stack + '\n');
  process.exitCode = 2;
});
