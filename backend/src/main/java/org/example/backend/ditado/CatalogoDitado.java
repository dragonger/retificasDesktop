package org.example.backend.ditado;

import org.example.model.CabecoteModel;
import org.example.model.CategoriaProduto;
import org.example.model.ClienteModel;
import org.example.model.PecaCatalogoModel;
import org.example.model.ServicoCatalogoModel;
import org.example.repository.CabecoteRepository;
import org.example.repository.ClienteRepository;
import org.example.repository.PecaCatalogoRepository;
import org.example.repository.ServicoCatalogoRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * Foto do catalogo DE UMA EMPRESA no momento em que o ditado comeca: o que vai no prompt e, principalmente,
 * o conjunto de ids que o normalizador aceita. Um id que nao esta aqui (inventado pelo modelo ou de outra
 * empresa) nunca chega ao app.
 */
public final class CatalogoDitado {

    public record Cliente(long id, String nome, String municipio) {
    }

    /** Componente tecnico (cabecote, bloco, biela, virabrequim...). */
    public record Modelo(long id, String nome, String categoria) {
    }

    /** Servico ou peca do catalogo. */
    public record Item(long id, String nome, String categoria) {
    }

    private final Map<Long, Cliente> clientes = new LinkedHashMap<>();
    private final Map<Long, Modelo> modelos = new LinkedHashMap<>();
    private final Map<Long, Item> servicos = new LinkedHashMap<>();
    private final Map<Long, Item> pecas = new LinkedHashMap<>();

    public CatalogoDitado(List<Cliente> clientes, List<Modelo> modelos, List<Item> servicos, List<Item> pecas) {
        ordenados(clientes, Cliente::id).forEach(c -> this.clientes.put(c.id(), c));
        ordenados(modelos, Modelo::id).forEach(m -> this.modelos.put(m.id(), m));
        ordenados(servicos, Item::id).forEach(i -> this.servicos.put(i.id(), i));
        ordenados(pecas, Item::id).forEach(i -> this.pecas.put(i.id(), i));
    }

    /** Carrega do banco, sempre filtrado pela empresa do token. */
    public static CatalogoDitado carregar(Long empresaId) {
        List<Cliente> clientes = new ArrayList<>();
        for (ClienteModel c : new ClienteRepository().listarTodos(empresaId)) {
            clientes.add(new Cliente(c.getId(), c.getNome(), c.getMunicipio()));
        }
        List<Modelo> modelos = new ArrayList<>();
        for (CabecoteModel m : new CabecoteRepository().listarTodos(empresaId)) {
            modelos.add(new Modelo(m.getId(), m.getNome(), nome(m.getCategoria())));
        }
        List<Item> servicos = new ArrayList<>();
        for (ServicoCatalogoModel s : new ServicoCatalogoRepository().listarTodos(empresaId)) {
            servicos.add(new Item(s.getId(), s.getNome(), nome(s.getCategoria())));
        }
        List<Item> pecas = new ArrayList<>();
        for (PecaCatalogoModel p : new PecaCatalogoRepository().listarTodos(empresaId)) {
            pecas.add(new Item(p.getId(), p.getNome(), nome(p.getCategoria())));
        }
        return new CatalogoDitado(clientes, modelos, servicos, pecas);
    }

    private static String nome(CategoriaProduto categoria) {
        return categoria != null ? categoria.name() : null;
    }

    private static <T> List<T> ordenados(List<T> itens, ToLongFunction<T> id) {
        List<T> copia = new ArrayList<>(itens != null ? itens : List.of());
        copia.sort(Comparator.comparingLong(id));
        return copia;
    }

    public Map<Long, Cliente> clientes() {
        return clientes;
    }

    public Map<Long, Modelo> modelos() {
        return modelos;
    }

    public Map<Long, Item> servicos() {
        return servicos;
    }

    public Map<Long, Item> pecas() {
        return pecas;
    }
}
