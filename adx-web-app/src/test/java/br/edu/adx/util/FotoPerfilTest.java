package br.edu.adx.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class FotoPerfilTest {
    @Test
    void normalizaJpegParaPngComTamanhoDeAvatar() throws IOException {
        byte[] original = imagem("jpeg", 1200, 600);
        byte[] png = FotoPerfil.normalizar(original, "image/jpeg");
        try (var entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(png))) {
            var leitor = ImageIO.getImageReaders(entrada).next();
            try {
                assertEquals("png", leitor.getFormatName());
                leitor.setInput(entrada);
                assertEquals(512, leitor.getWidth(0));
                assertEquals(256, leitor.getHeight(0));
                assertNotNull(leitor.read(0));
            } finally {
                leitor.dispose();
            }
        }
    }

    @Test
    void conservaTransparenciaDePng() throws IOException {
        BufferedImage entrada = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        entrada.setRGB(20, 20, Color.BLUE.getRGB());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(entrada, "png", bytes);
        byte[] png = FotoPerfil.normalizar(bytes.toByteArray(), "image/png");
        BufferedImage saida = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(0, saida.getRGB(0, 0));
        assertEquals(Color.BLUE.getRGB(), saida.getRGB(20, 20));
    }

    @Test
    void rejeitaTipoDeclaradoDiferenteDaImagem() throws IOException {
        byte[] jpeg = imagem("jpeg", 30, 30);
        assertThrows(IOException.class, () -> FotoPerfil.normalizar(jpeg, "image/png"));
    }

    @Test
    void rejeitaArquivoQueNaoPodeSerLidoComoImagem() {
        assertThrows(IOException.class, () -> FotoPerfil.normalizar(new byte[] { 5, 7, 9 }, "image/png"));
    }

    @Test
    void rejeitaDimensoesAcimaDoLimiteMesmoEmArquivoPequeno() throws IOException {
        byte[] largaDemais = imagem("png", 2049, 8);
        assertThrows(IOException.class, () -> FotoPerfil.normalizar(largaDemais, "image/png"));
    }

    private static byte[] imagem(String formato, int largura, int altura) throws IOException {
        BufferedImage imagem = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            ImageIO.write(imagem, formato, bytes);
            return bytes.toByteArray();
        }
    }
}
