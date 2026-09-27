package org.example.backend.whatsapp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Envio do orçamento em PDF pelo WhatsApp Business Platform (Cloud API da
 * Meta), direto pro número do cliente — sem abrir o WhatsApp no aparelho.
 *
 * Fica DESLIGADO enquanto WHATSAPP_TOKEN e WHATSAPP_PHONE_NUMBER_ID não
 * estiverem configurados (ver WHATSAPP-API.md na raiz do backend); aí o app
 * continua só com o link wa.me.
 *
 * Fluxo (2 chamadas):
 *   1. POST /{phone-number-id}/media — sobe o PDF e recebe um media id;
 *   2. POST /{phone-number-id}/messages — manda o modelo (template) aprovado
 *      com o PDF no cabeçalho e os parâmetros do corpo.
 * Mensagem iniciada pela empresa precisa ser de um modelo aprovado pela Meta
 * (categoria Utilidade), por isso não é texto livre.
 */
@Service
public class WhatsAppService {

    /** Falha no envio, com mensagem pronta pra mostrar no app. */
    public static class WhatsAppException extends Exception {
        public WhatsAppException(String mensagem) {
            super(mensagem);
        }
    }

    private final String graphUrl;
    private final String apiVersion;
    private final String token;
    private final String phoneNumberId;
    private final String templateNome;
    private final String templateIdioma;
    private final HttpClient http;
    private final JsonMapper json = JsonMapper.builder().build();

    public WhatsAppService(
            @Value("${WHATSAPP_TOKEN:}") String token,
            @Value("${WHATSAPP_PHONE_NUMBER_ID:}") String phoneNumberId,
            @Value("${WHATSAPP_TEMPLATE_NAME:orcamento}") String templateNome,
            @Value("${WHATSAPP_TEMPLATE_LANG:pt_BR}") String templateIdioma,
            @Value("${WHATSAPP_API_VERSION:v23.0}") String apiVersion,
            @Value("${WHATSAPP_GRAPH_URL:https://graph.facebook.com}") String graphUrl) {
        this.token = token.trim();
        this.phoneNumberId = phoneNumberId.trim();
        this.templateNome = templateNome.trim();
        this.templateIdioma = templateIdioma.trim();
        this.apiVersion = apiVersion.trim();
        this.graphUrl = graphUrl.trim().replaceAll("/+$", "");
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public boolean isHabilitado() {
        return !token.isEmpty() && !phoneNumberId.isEmpty();
    }

    /**
     * Número no formato da API (só dígitos, com DDI 55), ou null se não
     * parecer celular/fixo brasileiro com DDD. Mesma regra do link wa.me do app.
     */
    public static String numeroInternacional(String telefone) {
        String d = telefone == null ? "" : telefone.replaceAll("\\D", "");
        if (d.length() == 10 || d.length() == 11) {
            return "55" + d;
        }
        if (d.startsWith("55") && (d.length() == 12 || d.length() == 13)) {
            return d;
        }
        return null;
    }

    /**
     * Sobe o PDF e manda o modelo pro número informado.
     *
     * @param parametrosCorpo valores de {{1}}, {{2}}... do corpo do modelo, na ordem
     * @return id da mensagem na Meta (wamid...)
     */
    public String enviarOrcamento(String numeroDestino, byte[] pdf, String nomeArquivo,
                                  List<String> parametrosCorpo) throws WhatsAppException {
        if (!isHabilitado()) {
            throw new WhatsAppException("Envio pelo WhatsApp ainda não configurado.");
        }
        String mediaId = subirPdf(pdf, nomeArquivo);
        return enviarModelo(numeroDestino, mediaId, nomeArquivo, parametrosCorpo);
    }

    private String subirPdf(byte[] pdf, String nomeArquivo) throws WhatsAppException {
        String boundary = "----retifica" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream corpo = new ByteArrayOutputStream();
        escreverCampo(corpo, boundary, "messaging_product", "whatsapp");
        escreverCampo(corpo, boundary, "type", "application/pdf");
        escrever(corpo, "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + nomeArquivo.replace("\"", "") + "\"\r\n"
                + "Content-Type: application/pdf\r\n\r\n");
        corpo.writeBytes(pdf);
        escrever(corpo, "\r\n--" + boundary + "--\r\n");

        HttpRequest req = HttpRequest.newBuilder(URI.create(url("/media")))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(corpo.toByteArray()))
                .build();
        JsonNode resposta = chamar(req, "enviar o PDF");
        String id = resposta.path("id").asString("");
        if (id.isEmpty()) {
            throw new WhatsAppException("A Meta não devolveu o id do PDF enviado.");
        }
        return id;
    }

    private String enviarModelo(String numeroDestino, String mediaId, String nomeArquivo,
                                List<String> parametrosCorpo) throws WhatsAppException {
        ObjectNode raiz = json.createObjectNode();
        raiz.put("messaging_product", "whatsapp");
        raiz.put("recipient_type", "individual");
        raiz.put("to", numeroDestino);
        raiz.put("type", "template");
        ObjectNode template = raiz.putObject("template");
        template.put("name", templateNome);
        template.putObject("language").put("code", templateIdioma);
        ArrayNode componentes = template.putArray("components");

        ObjectNode cabecalho = componentes.addObject();
        cabecalho.put("type", "header");
        ObjectNode documento = cabecalho.putArray("parameters").addObject();
        documento.put("type", "document");
        documento.putObject("document").put("id", mediaId).put("filename", nomeArquivo);

        if (!parametrosCorpo.isEmpty()) {
            ObjectNode corpo = componentes.addObject();
            corpo.put("type", "body");
            ArrayNode parametros = corpo.putArray("parameters");
            for (String valor : parametrosCorpo) {
                parametros.addObject().put("type", "text").put("text", parametroSeguro(valor));
            }
        }

        HttpRequest req = HttpRequest.newBuilder(URI.create(url("/messages")))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(raiz), StandardCharsets.UTF_8))
                .build();
        JsonNode resposta = chamar(req, "enviar a mensagem");
        String id = resposta.path("messages").path(0).path("id").asString("");
        if (id.isEmpty()) {
            throw new WhatsAppException("A Meta não confirmou o envio da mensagem.");
        }
        return id;
    }

    /**
     * Parâmetro de modelo não pode ser vazio nem ter quebra de linha, tab ou
     * mais de 4 espaços seguidos (a Meta recusa a mensagem inteira).
     */
    static String parametroSeguro(String valor) {
        String v = valor == null ? "" : valor.replaceAll("[\\r\\n\\t]+", " ").replaceAll(" {4,}", "   ").trim();
        return v.isEmpty() ? "-" : v;
    }

    private JsonNode chamar(HttpRequest req, String acao) throws WhatsAppException {
        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new WhatsAppException("Sem conexão com o WhatsApp ao " + acao + ".");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WhatsAppException("Envio interrompido ao " + acao + ".");
        }
        JsonNode corpo;
        try {
            corpo = json.readTree(resp.body());
        } catch (RuntimeException e) {
            corpo = json.createObjectNode();
        }
        if (resp.statusCode() / 100 != 2) {
            throw new WhatsAppException("O WhatsApp recusou ao " + acao + ": " + mensagemDeErro(corpo, resp.statusCode()));
        }
        return corpo;
    }

    /** Mensagem legível a partir do erro padrão da Graph API ({"error":{"message","error_data":{"details"}}}). */
    static String mensagemDeErro(JsonNode corpo, int status) {
        JsonNode erro = corpo.path("error");
        String detalhe = erro.path("error_data").path("details").asString("");
        String mensagem = erro.path("message").asString("");
        String texto = !detalhe.isEmpty() ? detalhe : !mensagem.isEmpty() ? mensagem : "HTTP " + status;
        int codigo = erro.path("code").asInt(0);
        return codigo != 0 ? texto + " (código " + codigo + ")" : texto;
    }

    private String url(String caminho) {
        return graphUrl + "/" + apiVersion + "/" + phoneNumberId + caminho;
    }

    private static void escreverCampo(ByteArrayOutputStream saida, String boundary, String nome, String valor) {
        escrever(saida, "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + nome + "\"\r\n\r\n"
                + valor + "\r\n");
    }

    private static void escrever(ByteArrayOutputStream saida, String texto) {
        saida.writeBytes(texto.getBytes(StandardCharsets.UTF_8));
    }
}
