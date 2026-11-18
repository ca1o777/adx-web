package br.edu.adx.util;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Armazenamento seguro de senhas: PBKDF2 com HMAC-SHA256, sal aleatório por usuário (RNF03 e RNF05). */
public final class Seguranca {
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final int ITERACOES = 120_000;

    /** Hash de uma senha que não existe, usado para gastar o mesmo tempo quando o e-mail não está cadastrado. */
    public static final String HASH_FALSO = gerarHash("senha-inexistente-adx");

    private Seguranca() { }

    public static String gerarHash(String senha) {
        byte[] sal = new byte[16];
        ALEATORIO.nextBytes(sal);
        byte[] hash = derivar(senha, sal, ITERACOES);
        Base64.Encoder b64 = Base64.getEncoder();
        return "pbkdf2$" + ITERACOES + "$" + b64.encodeToString(sal) + "$" + b64.encodeToString(hash);
    }

    public static boolean conferir(String senha, String armazenado) {
        try {
            String[] partes = armazenado.split("\\$");
            int iteracoes = Integer.parseInt(partes[1]);
            byte[] sal = Base64.getDecoder().decode(partes[2]);
            byte[] esperado = Base64.getDecoder().decode(partes[3]);
            return MessageDigest.isEqual(esperado, derivar(senha, sal, iteracoes));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static byte[] derivar(String senha, byte[] sal, int iteracoes) {
        try {
            PBEKeySpec spec = new PBEKeySpec(senha.toCharArray(), sal, iteracoes, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 indisponível", e);
        }
    }
}
