package org.example.backend.web;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

/**
 * Confere e padroniza a logo enviada por uma empresa: só PNG ou JPEG de
 * verdade (pelo conteúdo, não pela extensão), reduzida para caber em
 * {@link #LADO_MAXIMO} px e regravada como PNG. Regravar descarta metadados
 * e qualquer coisa escondida no arquivo original.
 */
final class LogoNormalizador {

    static final int BYTES_MAXIMO = 5 * 1024 * 1024;
    /** Cobre a barra do app (84 px a 3x) e o PDF (63 pt de altura a ~300 dpi). */
    static final int LADO_MAXIMO = 400;
    private static final int LADO_ORIGINAL_MAXIMO = 8000;
    private static final long PIXELS_ORIGINAIS_MAXIMO = 40_000_000L;
    private static final Set<String> FORMATOS = Set.of("png", "jpeg");

    static final class LogoInvalidaException extends Exception {
        LogoInvalidaException(String mensagem) {
            super(mensagem);
        }
    }

    private LogoNormalizador() {
    }

    static byte[] normalizar(byte[] arquivo) throws LogoInvalidaException {
        if (arquivo == null || arquivo.length == 0) {
            throw new LogoInvalidaException("Escolha uma imagem.");
        }
        if (arquivo.length > BYTES_MAXIMO) {
            throw new LogoInvalidaException("A imagem passa de 5 MB.");
        }
        BufferedImage original = ler(arquivo);
        BufferedImage reduzida = reduzir(original);
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try {
            ImageIO.write(reduzida, "png", saida);
        } catch (IOException e) {
            throw new LogoInvalidaException("Não foi possível processar a imagem.");
        }
        return saida.toByteArray();
    }

    /** Lê as dimensões antes de decodificar, para recusar imagens gigantes sem abrir na memória. */
    private static BufferedImage ler(byte[] arquivo) throws LogoInvalidaException {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(arquivo))) {
            Iterator<ImageReader> leitores = entrada != null ? ImageIO.getImageReaders(entrada) : null;
            if (leitores == null || !leitores.hasNext()) {
                throw new LogoInvalidaException("Use uma imagem PNG ou JPG.");
            }
            ImageReader leitor = leitores.next();
            try {
                String formato = leitor.getFormatName().toLowerCase(java.util.Locale.ROOT);
                if (!FORMATOS.contains(formato.equals("jpg") ? "jpeg" : formato)) {
                    throw new LogoInvalidaException("Use uma imagem PNG ou JPG.");
                }
                leitor.setInput(entrada, true, true);
                int largura = leitor.getWidth(0);
                int altura = leitor.getHeight(0);
                if (largura <= 0 || altura <= 0 || largura > LADO_ORIGINAL_MAXIMO || altura > LADO_ORIGINAL_MAXIMO
                        || (long) largura * altura > PIXELS_ORIGINAIS_MAXIMO) {
                    throw new LogoInvalidaException("A imagem é grande demais (máximo 8000 px de lado).");
                }
                BufferedImage imagem = leitor.read(0);
                if (imagem == null) {
                    throw new LogoInvalidaException("Não foi possível ler a imagem.");
                }
                return imagem;
            } finally {
                leitor.dispose();
            }
        } catch (IOException e) {
            throw new LogoInvalidaException("Não foi possível ler a imagem.");
        }
    }

    private static BufferedImage reduzir(BufferedImage original) {
        int largura = original.getWidth();
        int altura = original.getHeight();
        double escala = Math.min(1.0, (double) LADO_MAXIMO / Math.max(largura, altura));
        int novaLargura = Math.max(1, (int) Math.round(largura * escala));
        int novaAltura = Math.max(1, (int) Math.round(altura * escala));
        Image fonte = escala < 1.0
                ? original.getScaledInstance(novaLargura, novaAltura, Image.SCALE_AREA_AVERAGING)
                : original;
        BufferedImage destino = new BufferedImage(novaLargura, novaAltura, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = destino.createGraphics();
        try {
            g.drawImage(fonte, 0, 0, null);
        } finally {
            g.dispose();
        }
        return destino;
    }
}
