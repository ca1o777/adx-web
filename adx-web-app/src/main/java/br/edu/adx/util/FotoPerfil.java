package br.edu.adx.util;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/** Normaliza uma imagem para PNG sem preservar metadados do arquivo enviado. */
public final class FotoPerfil {
    public static final int LIMITE_BYTES = 2 * 1024 * 1024;
    private static final int LIMITE_LADO = 2048;
    private static final int LADO_AVATAR = 512;

    private FotoPerfil() { }

    public static byte[] normalizar(byte[] entrada, String mime) throws IOException {
        String formato = switch (mime.toLowerCase(java.util.Locale.ROOT)) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpeg";
            default -> throw new IOException("Tipo de imagem inválido.");
        };
        if (entrada.length > LIMITE_BYTES) {
            throw new IOException("Arquivo acima do limite.");
        }
        try (var origem = new MemoryCacheImageInputStream(new ByteArrayInputStream(entrada))) {
            var leitores = ImageIO.getImageReaders(origem);
            if (!leitores.hasNext()) {
                throw new IOException("A imagem não pôde ser lida.");
            }
            ImageReader leitor = leitores.next();
            try {
                if (!formato.equalsIgnoreCase(leitor.getFormatName())) {
                    throw new IOException("Conteúdo diferente do tipo informado.");
                }
                leitor.setInput(origem, true, true);
                int largura = leitor.getWidth(0);
                int altura = leitor.getHeight(0);
                if (largura < 1 || altura < 1 || largura > LIMITE_LADO || altura > LIMITE_LADO) {
                    throw new IOException("Dimensões de imagem inválidas.");
                }
                return codificar(leitor.read(0), largura, altura);
            } finally {
                leitor.dispose();
            }
        }
    }

    private static byte[] codificar(BufferedImage imagem, int largura, int altura) throws IOException {
        double escala = Math.min(1.0, (double) LADO_AVATAR / Math.max(largura, altura));
        int novaLargura = Math.max(1, (int) Math.round(largura * escala));
        int novaAltura = Math.max(1, (int) Math.round(altura * escala));
        BufferedImage avatar = new BufferedImage(novaLargura, novaAltura, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = avatar.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(imagem, 0, 0, novaLargura, novaAltura, null);
        } finally {
            g.dispose();
            imagem.flush();
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        var escritor = ImageIO.getImageWritersByFormatName("png").next();
        try (var saida = new MemoryCacheImageOutputStream(bytes)) {
            escritor.setOutput(saida);
            escritor.write(avatar);
        } finally {
            escritor.dispose();
            avatar.flush();
        }
        return bytes.toByteArray();
    }
}
