package org.example.backend.ditado;

import java.util.List;

/**
 * O que o modelo de IA devolve a cada rodada (schema fixo em {@link EsquemaDitado}), e o que o servidor
 * devolve ao app depois de validar tudo ({@link NormalizadorDitado}).
 *
 * No que o modelo devolve, "sem valor" e texto vazio ou 0 (em vez de null): o schema de saida estruturada
 * fica simples e todos os campos sao obrigatorios. O normalizador converte para null.
 */
public final class DitadoDados {

    private DitadoDados() {
    }

    // ---------------------------------------------------------------- do modelo

    public record RespostaModelo(Entendimento entendimento, List<PerguntaModelo> perguntas) {
    }

    public record Entendimento(RefModelo cliente, List<RefModelo> componentes, List<ItemModelo> servicos,
                               List<ItemModelo> pecas, List<ValorModelo> valores, String entregaData,
                               String entregaOuvido, String status, String descricao, String observacao,
                               String descontoTipo, double descontoValor) {
    }

    /** estado: OK | DUVIDA | NAO_ENCONTRADO | AUSENTE. id 0 = nenhum. */
    public record RefModelo(String estado, long id, List<Long> candidatos, String ouvido) {
    }

    public record ItemModelo(String estado, long id, List<Long> candidatos, String ouvido, int quantidade,
                             String categoria) {
    }

    public record ValorModelo(String categoria, double valorServicos, double valorPecas) {
    }

    /** tipo: ESCOLHA | ABERTA. */
    public record PerguntaModelo(String id, String tipo, String campo, String texto, List<OpcaoModelo> opcoes) {
    }

    /** refTipo: CLIENTE | COMPONENTE | SERVICO | PECA | CATEGORIA | TEXTO. */
    public record OpcaoModelo(String rotulo, String refTipo, long refId) {
    }

    // ---------------------------------------------------------------- para o app

    public record ResultadoDitado(String sessaoId, int rodada, int maxRodadas, String situacao,
                                  List<PerguntaDitado> perguntas, EntendimentoDitado entendimento,
                                  List<String> avisos, int usosRestantesHoje) {
    }

    public record PerguntaDitado(String id, String tipo, String campo, String texto, List<OpcaoDitado> opcoes) {
    }

    public record OpcaoDitado(int indice, String rotulo) {
    }

    /** estado: OK | DUVIDA | NAO_ENCONTRADO | AUSENTE | LIVRE (so itens: texto livre, sem catalogo). */
    public record RefDitado(String estado, Long id, String nome, String categoria, List<OpcaoRef> opcoes,
                            String ouvido) {
    }

    public record OpcaoRef(long id, String nome) {
    }

    public record ItemDitado(String estado, Long id, String nome, String categoria, Integer quantidade,
                             List<OpcaoRef> opcoes, String ouvido) {
    }

    public record ValorDitado(String categoria, java.math.BigDecimal valorServicos, java.math.BigDecimal valorPecas) {
    }

    public record EntendimentoDitado(RefDitado cliente, List<RefDitado> componentes, List<ItemDitado> servicos,
                                     List<ItemDitado> pecas, List<ValorDitado> valores, String entregaData,
                                     String entregaOuvido, String status, String descricao, String observacao,
                                     String descontoTipo, java.math.BigDecimal descontoValor) {
    }

    // ---------------------------------------------------- guardado na sessao

    /** A pergunta como o servidor a guardou (com as referencias), para montar a resposta sem confiar no app. */
    public record PerguntaGuardada(String id, String tipo, String campo, String texto, List<OpcaoGuardada> opcoes) {
    }

    public record OpcaoGuardada(String rotulo, String refTipo, long refId) {
    }
}
