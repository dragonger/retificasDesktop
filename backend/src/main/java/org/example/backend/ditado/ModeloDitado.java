package org.example.backend.ditado;

import org.example.backend.ditado.DitadoDados.RespostaModelo;
import org.example.backend.ditado.SessoesDitado.SessaoDitado;

/**
 * Quem "entende" o texto ditado. Em producao e o Claude ({@link AnthropicModeloDitado}); nos testes do servico,
 * uma implementacao falsa. O historico da conversa fica na sessao e so cresce (acrescentar, nunca editar).
 */
public interface ModeloDitado {

    /** False enquanto nao houver chave configurada: o ditado inteiro fica desligado. */
    boolean disponivel();

    /**
     * Envia a mensagem do usuario junto com o historico da sessao e devolve o que o modelo entendeu. So quando a
     * chamada da certo o historico da sessao e atualizado; falhar nao deixa a conversa pela metade.
     *
     * @throws DitadoException 502 (falha ou recusa da IA) ou 503 (IA mal configurada)
     */
    RespostaModelo interpretar(SessaoDitado sessao, String mensagemUsuario);
}
