package org.example.backend.ditado;

import org.example.backend.ditado.LimiteDitado.Resultado;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Limite diario (trava de custo) e sessoes em memoria, com relogio controlado. Sem Spring e sem banco. */
class LimiteESessoesDitadoTest {

    private static final CatalogoDitado VAZIO = new CatalogoDitado(List.of(), List.of(), List.of(), List.of());

    // ------------------------------------------------------------------ limite diario

    @Test
    void cadaEmpresaTemOSeuLimiteEOGlobalTambemValeEdevolverLiberaUso() {
        var relogio = RelogioFalso.em("2026-10-22T15:00:00Z");   // 12:00 em Brasilia
        var limite = new LimiteDitado(relogio, 3, 5);

        assertThat(limite.reservar(1)).isEqualTo(Resultado.OK);
        assertThat(limite.reservar(1)).isEqualTo(Resultado.OK);
        assertThat(limite.reservar(1)).isEqualTo(Resultado.OK);
        assertThat(limite.reservar(1)).as("4o da empresa 1").isEqualTo(Resultado.LIMITE_EMPRESA);
        assertThat(limite.restantes(1)).isZero();
        assertThat(limite.restantes(2)).as("a empresa 2 so enxerga o teto global restante (2)").isEqualTo(2);

        assertThat(limite.reservar(2)).isEqualTo(Resultado.OK);
        assertThat(limite.reservar(2)).isEqualTo(Resultado.OK);
        assertThat(limite.reservar(2)).as("global de 5 estourado").isEqualTo(Resultado.LIMITE_GLOBAL);
        assertThat(limite.restantes(2)).isZero();

        limite.liberar(1);
        assertThat(limite.restantes(1)).as("devolver um uso libera 1").isEqualTo(1);
        assertThat(limite.reservar(1)).isEqualTo(Resultado.OK);
    }

    @Test
    void liberarSemTerReservadoNaoCriaUsoNegativo() {
        var limite = new LimiteDitado(RelogioFalso.em("2026-10-22T15:00:00Z"), 2, 10);
        limite.liberar(7);
        assertThat(limite.restantes(7)).isEqualTo(2);
        limite.reservar(7);
        limite.reservar(7);
        assertThat(limite.reservar(7)).isEqualTo(Resultado.LIMITE_EMPRESA);
    }

    @Test
    void oDiaViraAMeiaNoiteDeBrasiliaENaoDoUtc() {
        var relogio = RelogioFalso.em("2026-10-22T20:00:00Z");   // 17:00 em Brasilia
        var limite = new LimiteDitado(relogio, 1, 10);
        assertThat(limite.reservar(1)).isEqualTo(Resultado.OK);
        assertThat(limite.reservar(1)).isEqualTo(Resultado.LIMITE_EMPRESA);

        relogio.avancar(Duration.ofHours(6));   // 23:00 de Brasilia, 02:00 UTC do dia 23: ainda o mesmo dia local
        assertThat(limite.reservar(1)).as("ainda e dia 22 em Brasilia").isEqualTo(Resultado.LIMITE_EMPRESA);

        relogio.avancar(Duration.ofHours(1));   // 00:00 de Brasilia do dia 23
        assertThat(limite.reservar(1)).as("virou o dia em Brasilia").isEqualTo(Resultado.OK);
    }

    // ------------------------------------------------------------------ sessoes

    @Test
    void sessaoSoEDevolvidaParaAMesmaEmpresaEOMesmoUsuario() {
        var sessoes = new SessoesDitado(RelogioFalso.em("2026-10-22T15:00:00Z"), Duration.ofMinutes(20), 200);
        var s = sessoes.criar(5, 50, VAZIO, "sistema");
        assertThat(sessoes.buscar(s.id(), 5, 50)).isPresent();
        assertThat(sessoes.buscar(s.id(), 6, 50)).as("outra empresa").isEmpty();
        assertThat(sessoes.buscar(s.id(), 5, 51)).as("outro usuario").isEmpty();
        assertThat(sessoes.buscar("nao-existe", 5, 50)).isEmpty();
        assertThat(sessoes.buscar(null, 5, 50)).isEmpty();
    }

    @Test
    void sessaoExpiraSemUsoMasUsarRenovaAValidade() {
        var relogio = RelogioFalso.em("2026-10-22T15:00:00Z");
        var sessoes = new SessoesDitado(relogio, Duration.ofMinutes(20), 200);
        var s = sessoes.criar(5, 50, VAZIO, "sistema");

        relogio.avancar(Duration.ofMinutes(15));
        assertThat(sessoes.buscar(s.id(), 5, 50)).as("usada aos 15 min").isPresent();
        relogio.avancar(Duration.ofMinutes(15));
        assertThat(sessoes.buscar(s.id(), 5, 50)).as("30 min desde o inicio, mas 15 desde o ultimo uso").isPresent();
        relogio.avancar(Duration.ofMinutes(21));
        assertThat(sessoes.buscar(s.id(), 5, 50)).as("21 min sem uso").isEmpty();
    }

    @Test
    void tetoDeSessoesDescartaAMaisAntigaMasNuncaUmaEmAndamento() {
        var relogio = RelogioFalso.em("2026-10-22T15:00:00Z");
        var sessoes = new SessoesDitado(relogio, Duration.ofMinutes(20), 3);
        var primeira = sessoes.criar(1, 1, VAZIO, "a");
        relogio.avancar(Duration.ofSeconds(1));
        var segunda = sessoes.criar(1, 1, VAZIO, "b");
        relogio.avancar(Duration.ofSeconds(1));
        var terceira = sessoes.criar(1, 1, VAZIO, "c");
        assertThat(primeira.tentarIniciar()).as("a mais antiga esta em andamento").isTrue();
        relogio.avancar(Duration.ofSeconds(1));

        var quarta = sessoes.criar(1, 1, VAZIO, "d");
        assertThat(sessoes.buscar(primeira.id(), 1, 1)).as("em andamento nao e descartada").isPresent();
        assertThat(sessoes.buscar(segunda.id(), 1, 1)).as("a mais antiga livre foi descartada").isEmpty();
        assertThat(sessoes.buscar(terceira.id(), 1, 1)).isPresent();
        assertThat(sessoes.buscar(quarta.id(), 1, 1)).isPresent();
    }

    @Test
    void trancaDeChamadaEmAndamentoEmeRodadasSoAvancamQuandoConcluidas() {
        var sessoes = new SessoesDitado(RelogioFalso.em("2026-10-22T15:00:00Z"), Duration.ofMinutes(20), 200);
        var s = sessoes.criar(1, 1, VAZIO, "x");
        assertThat(s.rodada()).isZero();
        assertThat(s.tentarIniciar()).isTrue();
        assertThat(s.tentarIniciar()).as("segundo toque enquanto a primeira chamada roda").isFalse();
        s.terminar();
        assertThat(s.rodada()).as("terminar sem concluir nao avanca a rodada").isZero();
        assertThat(s.tentarIniciar()).isTrue();

        s.concluirRodada(List.of(new DitadoDados.PerguntaGuardada("r1p1", "ABERTA", "OUTRO", "Qual?", List.of())));
        assertThat(s.rodada()).isEqualTo(1);
        assertThat(s.perguntas()).hasSize(1);
    }
}
