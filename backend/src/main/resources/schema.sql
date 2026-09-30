-- Tabelas do ADX no SQLite. Este script roda a cada inicialização; o IF NOT EXISTS preserva os dados.
-- Datas são gravadas como texto ISO-8601 em UTC, sempre no mesmo formato (ex.: 2026-09-27T21:14:05Z),
-- para que a ordem do texto seja a ordem cronológica (ver DataHoraConverter).

CREATE TABLE IF NOT EXISTS usuario (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    nome_completo     TEXT    NOT NULL,
    nome_exibicao     TEXT    NOT NULL,
    email             TEXT    NOT NULL UNIQUE,       -- RN001: e-mail único, gravado em minúsculas
    senha_hash        TEXT    NOT NULL,              -- RN001: hash BCrypt da senha
    modo_preferido    TEXT    CHECK (modo_preferido IN ('CLASSICO', 'RAPIDO', 'BLITZ')),
    sobre             TEXT,
    tentativas_falhas INTEGER NOT NULL DEFAULT 0,    -- RN003: senhas erradas seguidas
    bloqueado_ate     TEXT,                          -- RN003: fim do bloqueio de 15 minutos
    criado_em         TEXT    NOT NULL
);
