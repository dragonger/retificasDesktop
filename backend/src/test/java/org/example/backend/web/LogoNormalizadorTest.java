package org.example.backend.web;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Normalizacao da logo: so PNG/JPEG de verdade, tamanho maximo e saida sempre em PNG ate 400 px. */
class LogoNormalizadorTest {

    private static byte[] imagem(int largura, int altura, String formato) throws Exception {
        BufferedImage img = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(img, formato, saida);
        return saida.toByteArray();
    }

    private static BufferedImage ler(byte[] png) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    @Test
    void imagemPequenaNaoEhAmpliada() throws Exception {
        BufferedImage saida = ler(LogoNormalizador.normalizar(imagem(120, 80, "png")));
        assertThat(saida.getWidth()).isEqualTo(120);
        assertThat(saida.getHeight()).isEqualTo(80);
    }

    @Test
    void imagemGrandeFicaComOMaiorLadoEm400MantendoAProporcao() throws Exception {
        BufferedImage saida = ler(LogoNormalizador.normalizar(imagem(1600, 800, "png")));
        assertThat(saida.getWidth()).isEqualTo(400);
        assertThat(saida.getHeight()).isEqualTo(200);
        BufferedImage alta = ler(LogoNormalizador.normalizar(imagem(300, 1500, "png")));
        assertThat(alta.getHeight()).isEqualTo(400);
        assertThat(alta.getWidth()).isEqualTo(80);
    }

    @Test
    void jpegVirouPngNaSaida() throws Exception {
        byte[] saida = LogoNormalizador.normalizar(imagem(200, 200, "jpeg"));
        assertThat(saida[0]).isEqualTo((byte) 0x89);
        assertThat(new String(saida, 1, 3)).isEqualTo("PNG");
    }

    @Test
    void recusaVazioNuloTextoGifGiganteEAcimaDe5MB() throws Exception {
        assertThatThrownBy(() -> LogoNormalizador.normalizar(null)).isInstanceOf(LogoNormalizador.LogoInvalidaException.class);
        assertThatThrownBy(() -> LogoNormalizador.normalizar(new byte[0])).isInstanceOf(LogoNormalizador.LogoInvalidaException.class);
        assertThatThrownBy(() -> LogoNormalizador.normalizar("texto".getBytes())).isInstanceOf(LogoNormalizador.LogoInvalidaException.class);
        byte[] gif = imagem(10, 10, "gif");
        assertThatThrownBy(() -> LogoNormalizador.normalizar(gif)).isInstanceOf(LogoNormalizador.LogoInvalidaException.class);
        byte[] larga = imagem(8001, 1, "png");
        assertThatThrownBy(() -> LogoNormalizador.normalizar(larga)).isInstanceOf(LogoNormalizador.LogoInvalidaException.class);
        assertThatThrownBy(() -> LogoNormalizador.normalizar(new byte[LogoNormalizador.BYTES_MAXIMO + 1]))
                .isInstanceOf(LogoNormalizador.LogoInvalidaException.class);
    }
}
