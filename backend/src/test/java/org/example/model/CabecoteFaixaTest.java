package org.example.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Faixas de medida (movel/fixo) do componente tecnico: valor unico ou faixa, com virgula ou ponto. */
class CabecoteFaixaTest {
    // A faixa sai com travessao (U+2013) entre o minimo e o maximo; a entrada aceita hifen, travessao ou travessao longo.


    private static CabecoteModel comMovel(String texto) {
        CabecoteModel c = new CabecoteModel();
        c.setMovelFaixa(texto);
        return c;
    }

    @Test
    void valorUnicoViraFaixaDeUmPontoSo() {
        assertThat(comMovel("25,10").getMovelFaixa()).isEqualTo("25,10");
        assertThat(comMovel("25.10").getMovelFaixa()).as("ponto tambem vale").isEqualTo("25,10");
    }

    @Test
    void faixaComHifenOuTravessaoMantemMinimoEMaximo() {
        assertThat(comMovel("25,045-27,070").getMovelFaixa()).isEqualTo("25,045–27,070");
        assertThat(comMovel("25,045 – 27,070").getMovelFaixa()).as("travessao e espacos").isEqualTo("25,045–27,070");
        assertThat(comMovel("25,045—27,070").getMovelFaixa()).isEqualTo("25,045–27,070");
    }

    @Test
    void vazioOuNuloLimpaAFaixa() {
        assertThat(comMovel(null).getMovelFaixa()).isEmpty();
        assertThat(comMovel("   ").getMovelFaixa()).isEmpty();
        CabecoteModel c = comMovel("25,10");
        c.setMovelFaixa("");
        assertThat(c.getMovelFaixa()).as("limpa o que existia").isEmpty();
    }

    @Test
    void textoNaoNumericoLancaNumberFormatException() {
        assertThatThrownBy(() -> comMovel("abc")).isInstanceOf(NumberFormatException.class);
        assertThatThrownBy(() -> comMovel("25,10-xyz")).isInstanceOf(NumberFormatException.class);
    }

    @Test
    void movelEFixoSaoIndependentes() {
        CabecoteModel c = new CabecoteModel();
        c.setMovelFaixa("25,10");
        c.setFixoFaixa("30,000-30,050");
        assertThat(c.getMovelFaixa()).isEqualTo("25,10");
        assertThat(c.getFixoFaixa()).isEqualTo("30,000–30,050");
    }
}
