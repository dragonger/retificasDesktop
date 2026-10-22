package org.example.backend.ditado;

import org.example.backend.ditado.CatalogoDitado.Cliente;
import org.example.backend.ditado.CatalogoDitado.Item;
import org.example.backend.ditado.CatalogoDitado.Modelo;
import org.example.backend.ditado.DitadoDados.EntendimentoDitado;
import org.example.backend.ditado.DitadoDados.ItemDitado;
import org.example.backend.ditado.DitadoDados.ItemModelo;
import org.example.backend.ditado.DitadoDados.OpcaoDitado;
import org.example.backend.ditado.DitadoDados.OpcaoGuardada;
import org.example.backend.ditado.DitadoDados.OpcaoModelo;
import org.example.backend.ditado.DitadoDados.OpcaoRef;
import org.example.backend.ditado.DitadoDados.PerguntaDitado;
import org.example.backend.ditado.DitadoDados.PerguntaGuardada;
import org.example.backend.ditado.DitadoDados.PerguntaModelo;
import org.example.backend.ditado.DitadoDados.RefDitado;
import org.example.backend.ditado.DitadoDados.RefModelo;
import org.example.backend.ditado.DitadoDados.RespostaModelo;
import org.example.backend.ditado.DitadoDados.ValorDitado;
import org.example.backend.ditado.DitadoDados.ValorModelo;
import org.example.model.CategoriaProduto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * A barreira de seguranca do ditado. Recebe o que o modelo de IA disse e devolve so o que pode chegar ao app:
 * todo id e conferido contra o catalogo da empresa do token (id inventado, de outra empresa ou do tipo
 * errado e descartado), nome e categoria vem do catalogo e nunca do texto do modelo, e valores, datas,
 * quantidades e tamanhos passam por limites. Puro: sem Spring, sem banco, sem relogio (o "hoje" entra como parametro).
 */
public final class NormalizadorDitado {

    public static final int MAX_PERGUNTAS = 3;
    public static final int MAX_OPCOES = 6;
    public static final int MAX_CANDIDATOS = 5;
    public static final int MAX_ITENS = 30;
    public static final BigDecimal VALOR_MAXIMO = new BigDecimal("1000000");

    private static final Set<String> STATUS = Set.of("ABERTO", "EM_ANDAMENTO", "PRONTO");
    private static final Set<String> CAMPOS = Set.of("CLIENTE", "COMPONENTE", "SERVICO", "PECA", "VALOR", "ENTREGA",
            "DESCONTO", "STATUS", "OUTRO");

    /** Resultado da normalizacao, antes de a sessao e o limite entrarem. */
    public record Normalizado(EntendimentoDitado entendimento, List<PerguntaDitado> perguntas,
                              List<PerguntaGuardada> guardadas, List<String> avisos) {
    }

    private NormalizadorDitado() {
    }

    /**
     * @param rodada     rodada atual (1 = primeira chamada ao modelo)
     * @param maxRodadas limite de chamadas por ditado; na ultima nenhuma pergunta e aceita
     */
    public static Normalizado normalizar(RespostaModelo resposta, CatalogoDitado catalogo, LocalDate hoje,
                                         int rodada, int maxRodadas) {
        List<String> avisos = new ArrayList<>();
        DitadoDados.Entendimento e = resposta != null ? resposta.entendimento() : null;
        if (e == null) {
            e = new DitadoDados.Entendimento(null, List.of(), List.of(), List.of(), List.of(), "", "", "", "", "", "", 0);
        }

        RefDitado cliente = resolverRef(e.cliente(), catalogo.clientes().keySet(),
                id -> Optional.ofNullable(catalogo.clientes().get(id)).map(c -> new Info(c.nome(), null)), false);

        List<RefDitado> componentes = new ArrayList<>();
        Set<Long> componentesVistos = new LinkedHashSet<>();
        for (RefModelo m : limitar(e.componentes())) {
            RefDitado r = resolverRef(m, catalogo.modelos().keySet(),
                    id -> Optional.ofNullable(catalogo.modelos().get(id)).map(x -> new Info(x.nome(), x.categoria())), false);
            if ("AUSENTE".equals(r.estado())) continue;
            if (r.id() != null && !componentesVistos.add(r.id())) continue;
            componentes.add(r);
        }

        List<ItemDitado> servicos = itens(e.servicos(), catalogo.servicos(), false);
        List<ItemDitado> pecas = itens(e.pecas(), catalogo.pecas(), true);

        List<ValorDitado> valores = valores(e.valores(), avisos);

        String entregaData = null;
        String entregaOuvido = limpar(e.entregaOuvido(), 120);
        String dataTexto = limpar(e.entregaData(), 10);
        if (!dataTexto.isEmpty()) {
            try {
                LocalDate data = LocalDate.parse(dataTexto);
                if (data.isBefore(hoje)) {
                    avisos.add("Data de entrega no passado ignorada: " + dataTexto);
                } else if (data.isAfter(hoje.plusDays(366))) {
                    avisos.add("Data de entrega distante demais ignorada: " + dataTexto);
                } else {
                    entregaData = data.toString();
                }
            } catch (DateTimeParseException ex) {
                avisos.add("Data de entrega nao entendida: " + limpar(e.entregaData(), 30));
            }
        }

        String status = limpar(e.status(), 20);
        if (!STATUS.contains(status)) status = null;
        String descricao = vazioParaNulo(limpar(e.descricao(), 255));
        String observacao = vazioParaNulo(limpar(e.observacao(), 1000));

        String descontoTipo = limpar(e.descontoTipo(), 12);
        BigDecimal descontoValor = null;
        if ("VALOR".equals(descontoTipo) || "PERCENTUAL".equals(descontoTipo)) {
            BigDecimal valor = dinheiro(e.descontoValor());
            boolean percentual = "PERCENTUAL".equals(descontoTipo);
            if (valor == null || valor.signum() <= 0 || (percentual && valor.compareTo(BigDecimal.valueOf(100)) > 0)) {
                if (e.descontoValor() != 0) avisos.add("Desconto ignorado: valor invalido.");
                descontoTipo = null;
            } else {
                descontoValor = valor;
            }
        } else {
            descontoTipo = null;
        }

        EntendimentoDitado entendimento = new EntendimentoDitado(cliente, componentes, servicos, pecas, valores,
                entregaData, entregaOuvido.isEmpty() ? null : entregaOuvido, status, descricao, observacao,
                descontoTipo, descontoValor);

        // Na ultima chamada nenhuma pergunta e aceita: o que sobrou fica marcado como duvida/pendencia.
        List<PerguntaDitado> perguntas = new ArrayList<>();
        List<PerguntaGuardada> guardadas = new ArrayList<>();
        if (rodada < maxRodadas) {
            perguntas(resposta != null ? resposta.perguntas() : null, catalogo, rodada, perguntas, guardadas);
        }
        return new Normalizado(entendimento, perguntas, guardadas, avisos);
    }

    // ------------------------------------------------------------------ referencias

    private record Info(String nome, String categoria) {
    }

    private static RefDitado resolverRef(RefModelo m, Set<Long> validos, Function<Long, Optional<Info>> buscar,
                                         boolean aceitaLivre) {
        if (m == null) return new RefDitado("AUSENTE", null, null, null, List.of(), null);
        String ouvido = limpar(m.ouvido(), 120);
        Set<Long> ids = new LinkedHashSet<>();
        if (m.id() > 0 && validos.contains(m.id())) ids.add(m.id());
        if (m.candidatos() != null) {
            for (Long c : m.candidatos()) {
                if (c != null && c > 0 && validos.contains(c) && ids.size() < MAX_CANDIDATOS) ids.add(c);
            }
        }
        if (ids.size() == 1) {
            long id = ids.iterator().next();
            Info info = buscar.apply(id).orElse(new Info("", null));
            return new RefDitado("OK", id, info.nome(), info.categoria(), List.of(), ouvido.isEmpty() ? null : ouvido);
        }
        if (ids.size() > 1) {
            List<OpcaoRef> opcoes = new ArrayList<>();
            for (Long id : ids) buscar.apply(id).ifPresent(i -> opcoes.add(new OpcaoRef(id, i.nome())));
            return new RefDitado("DUVIDA", null, null, null, opcoes, ouvido.isEmpty() ? null : ouvido);
        }
        if (!ouvido.isEmpty()) {
            return new RefDitado(aceitaLivre ? "LIVRE" : "NAO_ENCONTRADO", null, null, null, List.of(), ouvido);
        }
        return new RefDitado("AUSENTE", null, null, null, List.of(), null);
    }

    private static List<ItemDitado> itens(List<ItemModelo> entrada, Map<Long, Item> catalogo, boolean peca) {
        List<ItemDitado> saida = new ArrayList<>();
        Set<Long> vistos = new LinkedHashSet<>();
        for (ItemModelo m : limitar(entrada)) {
            RefModelo ref = new RefModelo(m.estado(), m.id(), m.candidatos(), m.ouvido());
            RefDitado r = resolverRef(ref, catalogo.keySet(),
                    id -> Optional.ofNullable(catalogo.get(id)).map(i -> new Info(i.nome(), i.categoria())), true);
            if ("AUSENTE".equals(r.estado())) continue;
            if (r.id() != null && !vistos.add(r.id())) continue;
            String categoria = categoriaValida(m.categoria());
            if (categoria == null) categoria = categoriaValida(r.categoria());
            Integer quantidade = null;
            if (peca) quantidade = Math.max(1, Math.min(999, m.quantidade() > 0 ? m.quantidade() : 1));
            // item livre (sem catalogo): a descricao e o que foi ouvido
            String nome = r.nome() != null ? r.nome() : r.ouvido();
            saida.add(new ItemDitado(r.estado(), r.id(), nome, categoria, quantidade, r.opcoes(), r.ouvido()));
        }
        return saida;
    }

    // ------------------------------------------------------------------ valores

    private static List<ValorDitado> valores(List<ValorModelo> entrada, List<String> avisos) {
        Map<String, BigDecimal[]> porCategoria = new LinkedHashMap<>();
        for (ValorModelo v : limitar(entrada)) {
            String categoria = categoriaValida(v.categoria());
            if (categoria == null) continue;
            BigDecimal servicos = dinheiro(v.valorServicos());
            BigDecimal pecas = dinheiro(v.valorPecas());
            if (servicos == null || pecas == null) {
                avisos.add("Valor ignorado em " + categoria + ": fora do limite.");
                continue;
            }
            if (servicos.signum() == 0 && pecas.signum() == 0) continue;
            BigDecimal[] atual = porCategoria.computeIfAbsent(categoria, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            atual[0] = atual[0].add(servicos);
            atual[1] = atual[1].add(pecas);
        }
        List<ValorDitado> saida = new ArrayList<>();
        porCategoria.forEach((cat, v) -> saida.add(new ValorDitado(cat, v[0], v[1])));
        return saida;
    }

    /** Valor em reais com 2 casas, ou null se for negativo, infinito, NaN ou acima do limite. */
    static BigDecimal dinheiro(double valor) {
        if (Double.isNaN(valor) || Double.isInfinite(valor) || valor < 0) return null;
        BigDecimal v = BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP);
        return v.compareTo(VALOR_MAXIMO) > 0 ? null : v;
    }

    // ------------------------------------------------------------------ perguntas

    private static void perguntas(List<PerguntaModelo> entrada, CatalogoDitado catalogo, int rodada,
                                  List<PerguntaDitado> saida, List<PerguntaGuardada> guardadas) {
        if (entrada == null) return;
        for (PerguntaModelo p : entrada) {
            if (p == null || saida.size() >= MAX_PERGUNTAS) break;
            String texto = limpar(p.texto(), 200);
            if (texto.isEmpty()) continue;
            String campo = CAMPOS.contains(limpar(p.campo(), 20)) ? limpar(p.campo(), 20) : "OUTRO";
            List<OpcaoGuardada> opcoes = new ArrayList<>();
            if ("ESCOLHA".equals(limpar(p.tipo(), 10)) && p.opcoes() != null) {
                for (OpcaoModelo o : p.opcoes()) {
                    if (o == null || opcoes.size() >= MAX_OPCOES) break;
                    OpcaoGuardada g = opcao(o, catalogo);
                    if (g != null && opcoes.stream().noneMatch(x -> x.refTipo().equals(g.refTipo()) && x.refId() == g.refId()
                            && x.rotulo().equals(g.rotulo()))) {
                        opcoes.add(g);
                    }
                }
            }
            String tipo = opcoes.size() >= 2 ? "ESCOLHA" : "ABERTA";
            if (tipo.equals("ABERTA")) opcoes = List.of();
            String id = "r" + rodada + "p" + (saida.size() + 1);
            List<OpcaoDitado> paraOApp = new ArrayList<>();
            for (int i = 0; i < opcoes.size(); i++) paraOApp.add(new OpcaoDitado(i, opcoes.get(i).rotulo()));
            saida.add(new PerguntaDitado(id, tipo, campo, texto, paraOApp));
            guardadas.add(new PerguntaGuardada(id, tipo, campo, texto, opcoes));
        }
    }

    private static OpcaoGuardada opcao(OpcaoModelo o, CatalogoDitado catalogo) {
        String tipo = limpar(o.refTipo(), 12);
        String rotulo = limpar(o.rotulo(), 60);
        switch (tipo) {
            case "CLIENTE": {
                Cliente c = catalogo.clientes().get(o.refId());
                return c == null ? null : new OpcaoGuardada(c.nome(), tipo, c.id());
            }
            case "COMPONENTE": {
                Modelo m = catalogo.modelos().get(o.refId());
                return m == null ? null : new OpcaoGuardada(m.nome(), tipo, m.id());
            }
            case "SERVICO": {
                Item i = catalogo.servicos().get(o.refId());
                return i == null ? null : new OpcaoGuardada(i.nome(), tipo, i.id());
            }
            case "PECA": {
                Item i = catalogo.pecas().get(o.refId());
                return i == null ? null : new OpcaoGuardada(i.nome(), tipo, i.id());
            }
            case "CATEGORIA":
            case "TEXTO":
                return rotulo.isEmpty() ? null : new OpcaoGuardada(rotulo, tipo, 0);
            default:
                return null;
        }
    }

    // ------------------------------------------------------------------ utilitarios

    private static <T> List<T> limitar(List<T> lista) {
        if (lista == null) return List.of();
        List<T> saida = new ArrayList<>();
        for (T t : lista) {
            if (t != null && saida.size() < MAX_ITENS) saida.add(t);
        }
        return saida;
    }

    private static String categoriaValida(String nome) {
        if (nome == null) return null;
        try {
            return CategoriaProduto.valueOf(nome.trim()).name();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String vazioParaNulo(String s) {
        return s.isEmpty() ? null : s;
    }

    /** Tira caracteres de controle, junta espacos repetidos e corta no tamanho. */
    static String limpar(String texto, int max) {
        if (texto == null) return "";
        String t = texto.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").replaceAll("[\\s]+", " ").trim();
        return t.length() > max ? t.substring(0, max).trim() : t;
    }
}
