package br.edu.adx.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Regras de negócio da documentação (RN001 a RN005) que não dependem do banco de dados. */
public final class Regras {
    public static final List<String> MODOS = List.of("Clássico", "Rápido", "Blitz");
    public static final List<String> RESULTADOS = List.of("Vitória", "Empate", "Derrota");
    public static final int SENHA_MINIMA = 8;            // RN001
    public static final int TENTATIVAS_MAXIMAS = 3;      // RN003
    public static final int BLOQUEIO_MINUTOS = 15;       // RN003
    public static final int SESSAO_DIAS = 30;            // RN003

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");

    private Regras() { }

    public static boolean emailValido(String email) {
        return email != null && email.length() <= 120 && EMAIL.matcher(email).matches();
    }

    /** RN001: nome completo, e-mail válido, senha com no mínimo 8 caracteres e confirmação igual. */
    public static Map<String, String> validarCadastro(String nome, String email, String senha, String confirmar) {
        Map<String, String> erros = new LinkedHashMap<>();
        if (nome.isEmpty() || nome.split("\\s+").length < 2) {
            erros.put("nome", "Informe nome e sobrenome.");
        } else if (nome.length() > 100) {
            erros.put("nome", "O nome pode ter no máximo 100 caracteres.");
        }
        if (!emailValido(email)) {
            erros.put("email", "Informe um e-mail válido.");
        }
        if (senha.length() < SENHA_MINIMA) {
            erros.put("senha", "A senha precisa ter pelo menos 8 caracteres.");
        } else if (senha.length() > 72) {
            erros.put("senha", "A senha pode ter no máximo 72 caracteres.");
        }
        if (confirmar.isEmpty() || !confirmar.equals(senha)) {
            erros.put("confirmar", "As senhas não são iguais.");
        }
        return erros;
    }

    /** RN004: adversário, modo de jogo e resultado são obrigatórios (a data é gerada pelo servidor). */
    public static Map<String, String> validarPartida(String adversario, String modo, String resultado) {
        Map<String, String> erros = new LinkedHashMap<>();
        if (adversario.length() < 2) {
            erros.put("adversario", "Informe o nome do adversário.");
        } else if (adversario.length() > 60) {
            erros.put("adversario", "O nome do adversário pode ter no máximo 60 caracteres.");
        }
        if (!MODOS.contains(modo)) {
            erros.put("modo", "Escolha o modo de jogo.");
        }
        if (!RESULTADOS.contains(resultado)) {
            erros.put("resultado", "Escolha o resultado.");
        }
        return erros;
    }

    /** RN005: dados de perfil que o jogador pode atualizar. */
    public static Map<String, String> validarPerfil(String nomeExibicao, String modo, String sobre) {
        Map<String, String> erros = new LinkedHashMap<>();
        if (nomeExibicao.length() < 2) {
            erros.put("nomeExibicao", "Informe como você quer aparecer no ranking.");
        } else if (nomeExibicao.length() > 40) {
            erros.put("nomeExibicao", "O nome de exibição pode ter no máximo 40 caracteres.");
        }
        if (!MODOS.contains(modo)) {
            erros.put("modoPreferido", "Escolha um modo de jogo.");
        }
        if (sobre.length() > 300) {
            erros.put("sobre", "O texto pode ter no máximo 300 caracteres.");
        }
        return erros;
    }

    /** Nome de exibição inicial: primeiro nome e sobrenome. */
    public static String nomeExibicaoPadrao(String nome) {
        String[] p = nome.trim().split("\\s+");
        String r = p.length > 1 ? p[0] + " " + p[1] : p[0];
        return r.length() > 40 ? r.substring(0, 40) : r;
    }
}
