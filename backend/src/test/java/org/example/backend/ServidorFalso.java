package org.example.backend;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Servidor HTTP falso (so no localhost) que faz o papel da Meta e da funcao dihPagamento do Alto
 * Medidas. Guarda cada chamada recebida e responde com o que o teste mandar. Nenhum teste sai da maquina.
 */
final class ServidorFalso implements AutoCloseable {

    record Chamada(String metodo, String caminho, Map<String, String> cabecalhos, byte[] corpo) {
        String corpoTexto() {
            return new String(corpo, StandardCharsets.UTF_8);
        }
    }

    record Resposta(int status, String corpo) {
    }

    final List<Chamada> chamadas = new CopyOnWriteArrayList<>();
    volatile Function<Chamada, Resposta> comportamento = c -> new Resposta(200, "{}");
    private final HttpServer servidor;

    private ServidorFalso(HttpServer servidor) {
        this.servidor = servidor;
    }

    static ServidorFalso iniciar() {
        try {
            HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ServidorFalso falso = new ServidorFalso(http);
            http.createContext("/", troca -> {
                Map<String, String> cabecalhos = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                troca.getRequestHeaders().forEach((k, v) -> cabecalhos.put(k, String.join(",", v)));
                byte[] corpo = troca.getRequestBody().readAllBytes();
                Chamada chamada = new Chamada(troca.getRequestMethod(), troca.getRequestURI().getPath(), cabecalhos, corpo);
                falso.chamadas.add(chamada);
                Resposta r = falso.comportamento.apply(chamada);
                byte[] saida = r.corpo().getBytes(StandardCharsets.UTF_8);
                troca.getResponseHeaders().add("Content-Type", "application/json");
                troca.sendResponseHeaders(r.status(), saida.length == 0 ? -1 : saida.length);
                if (saida.length > 0) troca.getResponseBody().write(saida);
                troca.close();
            });
            http.start();
            return falso;
        } catch (IOException e) {
            throw new IllegalStateException("Nao foi possivel subir o servidor falso", e);
        }
    }

    String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    void limpar() {
        chamadas.clear();
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}
