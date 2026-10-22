package org.example.backend.ditado;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.JsonOutputFormat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Schema fixo da saida estruturada do modelo (igual para todas as empresas, para nao pagar a compilacao de um
 * schema por catalogo). Todos os campos sao obrigatorios e nenhum e nulo: "sem valor" e texto vazio ou 0, que o
 * {@link NormalizadorDitado} converte. Limites de tamanho nao entram aqui (a saida estruturada nao os aceita);
 * eles sao aplicados pelo normalizador.
 */
public final class EsquemaDitado {

    private static final List<String> ESTADOS = List.of("OK", "DUVIDA", "NAO_ENCONTRADO", "AUSENTE");
    private static final List<String> CATEGORIAS = List.of("CABECOTE", "BLOCO", "BIELA", "VIRABREQUIM", "MONTAGEM", "OUTRO");

    private EsquemaDitado() {
    }

    /** O formato de saida para a chamada ao modelo. */
    public static JsonOutputFormat formato() {
        JsonOutputFormat.Schema.Builder schema = JsonOutputFormat.Schema.builder();
        schema(). forEach((chave, valor) -> schema.putAdditionalProperty(chave, JsonValue.from(valor)));
        return JsonOutputFormat.builder().schema(schema.build()).build();
    }

    /** O schema como mapa (tambem usado nos testes). */
    public static Map<String, Object> schema() {
        return objeto(
                "entendimento", objeto(
                        "cliente", ref(),
                        "componentes", lista(ref()),
                        "servicos", lista(item()),
                        "pecas", lista(item()),
                        "valores", lista(valor()),
                        "entregaData", texto(),
                        "entregaOuvido", texto(),
                        "status", enumerado(List.of("", "ABERTO", "EM_ANDAMENTO", "PRONTO")),
                        "descricao", texto(),
                        "observacao", texto(),
                        "descontoTipo", enumerado(List.of("", "VALOR", "PERCENTUAL")),
                        "descontoValor", numero()),
                "perguntas", lista(pergunta()));
    }

    private static Map<String, Object> ref() {
        return objeto(
                "estado", enumerado(ESTADOS),
                "id", inteiro(),
                "candidatos", lista(inteiro()),
                "ouvido", texto());
    }

    private static Map<String, Object> item() {
        return objeto(
                "estado", enumerado(ESTADOS),
                "id", inteiro(),
                "candidatos", lista(inteiro()),
                "ouvido", texto(),
                "quantidade", inteiro(),
                "categoria", enumerado(com(CATEGORIAS)));
    }

    private static Map<String, Object> valor() {
        return objeto(
                "categoria", enumerado(CATEGORIAS),
                "valorServicos", numero(),
                "valorPecas", numero());
    }

    private static Map<String, Object> pergunta() {
        return objeto(
                "id", texto(),
                "tipo", enumerado(List.of("ESCOLHA", "ABERTA")),
                "campo", enumerado(List.of("CLIENTE", "COMPONENTE", "SERVICO", "PECA", "VALOR", "ENTREGA", "DESCONTO",
                        "STATUS", "OUTRO")),
                "texto", texto(),
                "opcoes", lista(objeto(
                        "rotulo", texto(),
                        "refTipo", enumerado(List.of("CLIENTE", "COMPONENTE", "SERVICO", "PECA", "CATEGORIA", "TEXTO")),
                        "refId", inteiro())));
    }

    // ------------------------------------------------------------------ pecas do schema

    private static List<String> com(List<String> base) {
        List<String> r = new java.util.ArrayList<>();
        r.add("");
        r.addAll(base);
        return r;
    }

    /** Objeto com todas as propriedades obrigatorias e sem propriedades extras. */
    private static Map<String, Object> objeto(Object... paresChaveSchema) {
        Map<String, Object> propriedades = new LinkedHashMap<>();
        for (int i = 0; i < paresChaveSchema.length; i += 2) {
            propriedades.put((String) paresChaveSchema[i], paresChaveSchema[i + 1]);
        }
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("type", "object");
        o.put("properties", propriedades);
        o.put("required", List.copyOf(propriedades.keySet()));
        o.put("additionalProperties", false);
        return o;
    }

    private static Map<String, Object> lista(Map<String, Object> itens) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "array");
        m.put("items", itens);
        return m;
    }

    private static Map<String, Object> texto() {
        return Map.of("type", "string");
    }

    private static Map<String, Object> inteiro() {
        return Map.of("type", "integer");
    }

    private static Map<String, Object> numero() {
        return Map.of("type", "number");
    }

    private static Map<String, Object> enumerado(List<String> valores) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "string");
        m.put("enum", valores);
        return m;
    }
}
