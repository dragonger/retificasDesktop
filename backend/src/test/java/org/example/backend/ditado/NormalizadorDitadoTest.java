package org.example.backend.ditado;

import org.example.backend.ditado.CatalogoDitado.Cliente;
import org.example.backend.ditado.CatalogoDitado.Item;
import org.example.backend.ditado.CatalogoDitado.Modelo;
import org.example.backend.ditado.DitadoDados.Entendimento;
import org.example.backend.ditado.DitadoDados.ItemModelo;
import org.example.backend.ditado.DitadoDados.OpcaoModelo;
import org.example.backend.ditado.DitadoDados.PerguntaModelo;
import org.example.backend.ditado.DitadoDados.RefModelo;
import org.example.backend.ditado.DitadoDados.RespostaModelo;
import org.example.backend.ditado.DitadoDados.ValorModelo;
import org.example.backend.ditado.NormalizadorDitado.Normalizado;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O normalizador e a barreira de seguranca do ditado: nada que o modelo diga chega ao app sem passar por aqui.
 * Puro (sem Spring, sem banco). Os ids 1..3 pertencem a "empresa do token"; 900+ sao de OUTRA empresa ou inventados.
 */
class NormalizadorDitadoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 22);   // quinta-feira

    private static final CatalogoDitado CATALOGO = new CatalogoDitado(
            List.of(new Cliente(1, "Joao da Silva", "Campinas"), new Cliente(2, "Joao Pereira", "Valinhos"),
                    new Cliente(3, "Maria Souza", null)),
            List.of(new Modelo(10, "AP 1.8", "CABECOTE"), new Modelo(11, "Fire 1.0", "BLOCO")),
            List.of(new Item(20, "Plainar cabecote", "CABECOTE"), new Item(21, "Retificar bloco", "BLOCO")),
            List.of(new Item(30, "Valvula de admissao", "CABECOTE")));

    // ------------------------------------------------------------------ construtores de teste

    private static RefModelo ref(String estado, long id, String ouvido, Long... candidatos) {
        return new RefModelo(estado, id, List.of(candidatos), ouvido);
    }

    private static ItemModelo item(String estado, long id, String ouvido, int quantidade, String categoria) {
        return new ItemModelo(estado, id, List.of(), ouvido, quantidade, categoria);
    }

    private static Entendimento entendimento(RefModelo cliente, List<RefModelo> componentes, List<ItemModelo> servicos,
                                             List<ItemModelo> pecas, List<ValorModelo> valores) {
        return new Entendimento(cliente, componentes, servicos, pecas, valores, "", "", "", "", "", "", 0);
    }

    private static RespostaModelo resposta(Entendimento e, PerguntaModelo... perguntas) {
        return new RespostaModelo(e, List.of(perguntas));
    }

    private static Entendimento vazio() {
        return entendimento(ref("AUSENTE", 0, ""), List.of(), List.of(), List.of(), List.of());
    }

    private static Normalizado normalizar(RespostaModelo r) {
        return NormalizadorDitado.normalizar(r, CATALOGO, HOJE, 1, 4);
    }

    // ------------------------------------------------------------------ cliente e componentes

    @Test
    void idValidoViraOkComONomeDoCatalogoNaoOQueOModeloDisse() {
        var n = normalizar(resposta(entendimento(ref("OK", 1, "joao silva"), List.of(), List.of(), List.of(), List.of())));
        assertThat(n.entendimento().cliente().estado()).isEqualTo("OK");
        assertThat(n.entendimento().cliente().id()).isEqualTo(1L);
        assertThat(n.entendimento().cliente().nome()).isEqualTo("Joao da Silva");
    }

    @Test
    void idInventadoOuDeOutraEmpresaNuncaChegaAoApp() {
        var n = normalizar(resposta(entendimento(ref("OK", 900, "Carlos Estranho"), List.of(), List.of(), List.of(), List.of())));
        assertThat(n.entendimento().cliente().estado()).isEqualTo("NAO_ENCONTRADO");
        assertThat(n.entendimento().cliente().id()).isNull();
        assertThat(n.entendimento().cliente().nome()).isNull();
        assertThat(n.entendimento().cliente().ouvido()).isEqualTo("Carlos Estranho");

        var semOuvido = normalizar(resposta(entendimento(ref("OK", 900, ""), List.of(), List.of(), List.of(), List.of())));
        assertThat(semOuvido.entendimento().cliente().estado()).isEqualTo("AUSENTE");
    }

    @Test
    void duvidaComDoisCandidatosValidosViraOpcoesEComUmSoViraOk() {
        var duvida = normalizar(resposta(entendimento(ref("DUVIDA", 0, "Joao", 1L, 2L), List.of(), List.of(), List.of(), List.of())));
        assertThat(duvida.entendimento().cliente().estado()).isEqualTo("DUVIDA");
        assertThat(duvida.entendimento().cliente().opcoes()).extracting(DitadoDados.OpcaoRef::id).containsExactly(1L, 2L);
        assertThat(duvida.entendimento().cliente().opcoes().get(1).nome()).isEqualTo("Joao Pereira");

        var umValido = normalizar(resposta(entendimento(ref("DUVIDA", 0, "Joao", 1L, 777L), List.of(), List.of(), List.of(), List.of())));
        assertThat(umValido.entendimento().cliente().estado()).as("o candidato inventado cai e sobra um").isEqualTo("OK");
        assertThat(umValido.entendimento().cliente().id()).isEqualTo(1L);
    }

    @Test
    void candidatosSaoLimitadosEsemRepeticao() {
        List<Cliente> muitos = new ArrayList<>();
        for (long i = 1; i <= 8; i++) muitos.add(new Cliente(i, "Cliente " + i, null));
        var catalogo = new CatalogoDitado(muitos, List.of(), List.of(), List.of());
        var r = resposta(entendimento(ref("DUVIDA", 0, "x", 1L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L),
                List.of(), List.of(), List.of(), List.of()));
        var n = NormalizadorDitado.normalizar(r, catalogo, HOJE, 1, 4);
        assertThat(n.entendimento().cliente().opcoes()).hasSize(NormalizadorDitado.MAX_CANDIDATOS);
    }

    @Test
    void componenteSoAceitaIdDeModeloNaoDeClienteNemDeServico() {
        var n = normalizar(resposta(entendimento(ref("AUSENTE", 0, ""),
                List.of(ref("OK", 10, "ap"), ref("OK", 1, "joao"), ref("OK", 20, "plaina"), ref("OK", 10, "ap de novo")),
                List.of(), List.of(), List.of())));
        // o 10 repetido cai; os ids 1 (cliente) e 20 (servico) nao sao modelos: viram "nao encontrado" com o que foi ouvido
        assertThat(n.entendimento().componentes()).hasSize(3);
        assertThat(n.entendimento().componentes().get(0).id()).isEqualTo(10L);
        assertThat(n.entendimento().componentes().get(0).categoria()).isEqualTo("CABECOTE");
        assertThat(n.entendimento().componentes().get(1).estado()).as("id de cliente nao e modelo").isEqualTo("NAO_ENCONTRADO");
        assertThat(n.entendimento().componentes().get(1).id()).isNull();
        assertThat(n.entendimento().componentes().get(2).estado()).as("id de servico nao e modelo").isEqualTo("NAO_ENCONTRADO");
        assertThat(n.entendimento().componentes().get(2).id()).isNull();
    }

    // ------------------------------------------------------------------ servicos e pecas

    @Test
    void servicoDoCatalogoUsaNomeECategoriaDoCatalogoEServicoLivreVirouTexto() {
        var n = normalizar(resposta(entendimento(ref("AUSENTE", 0, ""), List.of(),
                List.of(item("OK", 20, "plaina", 0, ""), item("NAO_ENCONTRADO", 0, "trocar guias", 0, "CABECOTE"),
                        item("OK", 901, "servico de outra empresa", 0, "")),
                List.of(), List.of())));
        var servicos = n.entendimento().servicos();
        assertThat(servicos).hasSize(3);
        assertThat(servicos.get(0).nome()).isEqualTo("Plainar cabecote");
        assertThat(servicos.get(0).categoria()).isEqualTo("CABECOTE");
        assertThat(servicos.get(1).estado()).isEqualTo("LIVRE");
        assertThat(servicos.get(1).nome()).isEqualTo("trocar guias");
        assertThat(servicos.get(2).estado()).as("id de outra empresa vira texto livre, sem id").isEqualTo("LIVRE");
        assertThat(servicos.get(2).id()).isNull();
    }

    @Test
    void quantidadeDePecaFicaEntre1e999ESaoRepetidosDescartados() {
        var n = normalizar(resposta(entendimento(ref("AUSENTE", 0, ""), List.of(), List.of(),
                List.of(item("OK", 30, "valvula", 0, ""), item("OK", 30, "valvula", 4, ""),
                        item("LIVRE", 0, "junta", 5000, ""), item("LIVRE", 0, "retentor", -3, "")),
                List.of())));
        var pecas = n.entendimento().pecas();
        assertThat(pecas).hasSize(3);
        assertThat(pecas.get(0).quantidade()).isEqualTo(1);
        assertThat(pecas.get(1).quantidade()).isEqualTo(999);
        assertThat(pecas.get(2).quantidade()).isEqualTo(1);
    }

    @Test
    void servicoNaoTemQuantidadeEItensVaziosSaoIgnorados() {
        var n = normalizar(resposta(entendimento(ref("AUSENTE", 0, ""), List.of(),
                List.of(item("OK", 21, "", 7, ""), item("AUSENTE", 0, "", 0, "")), List.of(), List.of())));
        assertThat(n.entendimento().servicos()).hasSize(1);
        assertThat(n.entendimento().servicos().get(0).quantidade()).isNull();
    }

    @Test
    void listasSaoLimitadasATrintaItens() {
        List<ItemModelo> muitos = new ArrayList<>();
        for (int i = 0; i < 50; i++) muitos.add(item("LIVRE", 0, "servico " + i, 0, ""));
        var n = normalizar(resposta(entendimento(ref("AUSENTE", 0, ""), List.of(), muitos, List.of(), List.of())));
        assertThat(n.entendimento().servicos()).hasSize(NormalizadorDitado.MAX_ITENS);
    }

    // ------------------------------------------------------------------ valores, datas, desconto, textos

    @Test
    void valoresSomamPorCategoriaIgnoramInvalidosEAvisamDoForaDoLimite() {
        var n = normalizar(resposta(entendimento(ref("AUSENTE", 0, ""), List.of(), List.of(), List.of(), List.of(
                new ValorModelo("CABECOTE", 100, 0), new ValorModelo("CABECOTE", 50.555, 20),
                new ValorModelo("NAO_EXISTE", 999, 999), new ValorModelo("BLOCO", 0, 0),
                new ValorModelo("BIELA", -5, 10), new ValorModelo("MONTAGEM", 5_000_000, 0)))));
        assertThat(n.entendimento().valores()).hasSize(1);
        assertThat(n.entendimento().valores().get(0).categoria()).isEqualTo("CABECOTE");
        assertThat(n.entendimento().valores().get(0).valorServicos()).isEqualByComparingTo("150.56");
        assertThat(n.entendimento().valores().get(0).valorPecas()).isEqualByComparingTo("20");
        assertThat(n.avisos()).anyMatch(a -> a.contains("BIELA")).anyMatch(a -> a.contains("MONTAGEM"));
    }

    @Test
    void valorNaoNumericoOuInfinitoEDescartado() {
        assertThat(NormalizadorDitado.dinheiro(Double.NaN)).isNull();
        assertThat(NormalizadorDitado.dinheiro(Double.POSITIVE_INFINITY)).isNull();
        assertThat(NormalizadorDitado.dinheiro(-0.01)).isNull();
        assertThat(NormalizadorDitado.dinheiro(1_000_000)).isEqualByComparingTo("1000000");
        assertThat(NormalizadorDitado.dinheiro(1_000_000.01)).isNull();
    }

    private static Normalizado comEntrega(String data) {
        var e = new Entendimento(ref("AUSENTE", 0, ""), List.of(), List.of(), List.of(), List.of(), data, "sexta", "",
                "", "", "", 0);
        return normalizar(resposta(e));
    }

    @Test
    void dataDeEntregaPrecisaSerValidaNaoPassadaENaoDistante() {
        assertThat(comEntrega("2026-10-23").entendimento().entregaData()).isEqualTo("2026-10-23");
        assertThat(comEntrega("2026-10-22").entendimento().entregaData()).as("hoje vale").isEqualTo("2026-10-22");
        assertThat(comEntrega("2026-10-21").entendimento().entregaData()).isNull();
        assertThat(comEntrega("2026-10-21").avisos()).anyMatch(a -> a.contains("passado"));
        assertThat(comEntrega("2028-01-01").entendimento().entregaData()).isNull();
        assertThat(comEntrega("23/10/2026").entendimento().entregaData()).isNull();
        assertThat(comEntrega("23/10/2026").avisos()).anyMatch(a -> a.contains("nao entendida"));
        assertThat(comEntrega("").entendimento().entregaData()).isNull();
        assertThat(comEntrega("").avisos()).isEmpty();
    }

    @Test
    void statusDescricaoObservacaoEDescontoSaoValidados() {
        var e = new Entendimento(ref("AUSENTE", 0, ""), List.of(), List.of(), List.of(), List.of(), "", "",
                "FINALIZADO", "x".repeat(400), "  Obs\u0000 com\n  espacos  ", "PERCENTUAL", 150);
        var n = normalizar(resposta(e));
        assertThat(n.entendimento().status()).as("status fora da lista").isNull();
        assertThat(n.entendimento().descricao()).hasSize(255);
        assertThat(n.entendimento().observacao()).isEqualTo("Obs com espacos");
        assertThat(n.entendimento().descontoTipo()).as("percentual acima de 100").isNull();
        assertThat(n.avisos()).anyMatch(a -> a.contains("Desconto"));

        var ok = new Entendimento(ref("AUSENTE", 0, ""), List.of(), List.of(), List.of(), List.of(), "", "",
                "PRONTO", "", "", "VALOR", 25.5);
        var n2 = normalizar(resposta(ok));
        assertThat(n2.entendimento().status()).isEqualTo("PRONTO");
        assertThat(n2.entendimento().descontoTipo()).isEqualTo("VALOR");
        assertThat(n2.entendimento().descontoValor()).isEqualByComparingTo("25.50");
        assertThat(n2.entendimento().descricao()).isNull();
    }

    // ------------------------------------------------------------------ perguntas

    private static PerguntaModelo escolha(String texto, OpcaoModelo... opcoes) {
        return new PerguntaModelo("qualquer", "ESCOLHA", "CLIENTE", texto, List.of(opcoes));
    }

    private static OpcaoModelo opcao(String tipo, long id, String rotulo) {
        return new OpcaoModelo(rotulo, tipo, id);
    }

    @Test
    void perguntaDeEscolhaUsaNomesDoCatalogoEIdsFicamSoNoServidor() {
        var n = normalizar(resposta(vazio(), escolha("Qual Joao?", opcao("CLIENTE", 1, "texto do modelo"),
                opcao("CLIENTE", 2, "outro texto"))));
        assertThat(n.perguntas()).hasSize(1);
        assertThat(n.perguntas().get(0).id()).as("o id da pergunta e do servidor").isEqualTo("r1p1");
        assertThat(n.perguntas().get(0).tipo()).isEqualTo("ESCOLHA");
        assertThat(n.perguntas().get(0).opcoes()).extracting(DitadoDados.OpcaoDitado::rotulo)
                .containsExactly("Joao da Silva", "Joao Pereira");
        assertThat(n.guardadas().get(0).opcoes()).extracting(DitadoDados.OpcaoGuardada::refId).containsExactly(1L, 2L);
    }

    @Test
    void opcaoComIdInvalidoCaiEPerguntaSemDuasOpcoesVirouAberta() {
        var n = normalizar(resposta(vazio(), escolha("Qual?", opcao("CLIENTE", 1, "a"), opcao("CLIENTE", 900, "b"))));
        assertThat(n.perguntas().get(0).tipo()).isEqualTo("ABERTA");
        assertThat(n.perguntas().get(0).opcoes()).isEmpty();
        assertThat(n.guardadas().get(0).opcoes()).isEmpty();
    }

    @Test
    void opcoesDeTextoECategoriaPassamSemId() {
        var n = normalizar(resposta(vazio(), escolha("Os 150 sao de servico ou de pecas?", opcao("TEXTO", 0, "Servico"),
                opcao("TEXTO", 0, "Pecas"), opcao("OUTRO", 5, "invalida"), opcao("TEXTO", 0, "   "))));
        assertThat(n.perguntas().get(0).opcoes()).hasSize(2);
    }

    @Test
    void maximoDeTresPerguntasPorRodadaESemTextoEDescartada() {
        var n = normalizar(resposta(vazio(),
                new PerguntaModelo("a", "ABERTA", "ENTREGA", "Qual a entrega?", List.of()),
                new PerguntaModelo("b", "ABERTA", "OUTRO", "   ", List.of()),
                new PerguntaModelo("c", "ABERTA", "VALOR", "Qual o valor?", List.of()),
                new PerguntaModelo("d", "ABERTA", "CAMPO_INVENTADO", "Mais uma?", List.of()),
                new PerguntaModelo("e", "ABERTA", "OUTRO", "E mais outra?", List.of())));
        assertThat(n.perguntas()).hasSize(NormalizadorDitado.MAX_PERGUNTAS);
        assertThat(n.perguntas()).extracting(DitadoDados.PerguntaDitado::id).containsExactly("r1p1", "r1p2", "r1p3");
        assertThat(n.perguntas().get(2).campo()).as("campo desconhecido vira OUTRO").isEqualTo("OUTRO");
    }

    @Test
    void naUltimaRodadaNenhumaPerguntaEAceita() {
        var r = resposta(vazio(), new PerguntaModelo("a", "ABERTA", "ENTREGA", "Qual a entrega?", List.of()));
        var n = NormalizadorDitado.normalizar(r, CATALOGO, HOJE, 4, 4);
        assertThat(n.perguntas()).isEmpty();
        assertThat(n.guardadas()).isEmpty();
        var antes = NormalizadorDitado.normalizar(r, CATALOGO, HOJE, 3, 4);
        assertThat(antes.perguntas()).hasSize(1);
        assertThat(antes.perguntas().get(0).id()).isEqualTo("r3p1");
    }

    // ------------------------------------------------------------------ robustez

    @Test
    void respostaNulaOuComCamposNulosNaoQuebra() {
        assertThat(normalizar(null).entendimento().cliente().estado()).isEqualTo("AUSENTE");
        var e = new Entendimento(null, null, null, null, null, null, null, null, null, null, null, 0);
        var n = normalizar(new RespostaModelo(e, null));
        assertThat(n.entendimento().componentes()).isEmpty();
        assertThat(n.entendimento().servicos()).isEmpty();
        assertThat(n.entendimento().valores()).isEmpty();
        assertThat(n.perguntas()).isEmpty();
    }

    @Test
    void limparTiraControleJuntaEspacosECorta() {
        assertThat(NormalizadorDitado.limpar("  a\u0000b\u0007  c\t\td \n e ", 50)).isEqualTo("ab c d e");
        assertThat(NormalizadorDitado.limpar(null, 10)).isEmpty();
        assertThat(NormalizadorDitado.limpar("abcdefghij", 4)).isEqualTo("abcd");
    }
}
