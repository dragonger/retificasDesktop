package br.com.dih.retifica;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.util.ArrayList;

/**
 * Reconhecimento de voz nativo para ditar o pedido.
 *
 * O Chrome encerra a escuta sozinho e o que se fala no reinicio se perde. Aqui, no Android 13 ou mais novo,
 * usamos a sessao segmentada (EXTRA_SEGMENTED_SESSION): o microfone fica aberto entre as pausas e cada frase
 * chega como um segmento. Nos mais antigos, ou se a sessao encerrar, religamos na hora.
 *
 * Eventos para o JS:
 *   "estado"    { pronto: boolean }       false enquanto liga, true quando de fato voltou a ouvir
 *   "resultado" { texto: string }         texto da escuta inteira (frases ja finalizadas + a parcial)
 *   "erro"      { codigo: string }        so erros que param a escuta (permissao, rede, microfone)
 */
@CapacitorPlugin(
    name = "DitadoVoz",
    permissions = { @Permission(strings = { Manifest.permission.RECORD_AUDIO }, alias = "microfone") }
)
public class DitadoVozPlugin extends Plugin {

    private SpeechRecognizer reconhecedor;
    private boolean ativo = false;
    private boolean segmentada = false;
    private String idioma = "pt-BR";
    // frases ja finalizadas nesta escuta (sobrevivem aos reinicios do reconhecedor)
    private final StringBuilder finalizado = new StringBuilder();
    private int falhasSeguidas = 0;

    @PluginMethod
    public void disponivel(PluginCall call) {
        JSObject r = new JSObject();
        r.put("disponivel", SpeechRecognizer.isRecognitionAvailable(getContext()));
        r.put("segmentada", Build.VERSION.SDK_INT >= 33);
        r.put("android", Build.VERSION.SDK_INT);
        call.resolve(r);
    }

    @PluginMethod
    public void iniciar(PluginCall call) {
        if (getPermissionState("microfone") != com.getcapacitor.PermissionState.GRANTED) {
            requestPermissionForAlias("microfone", call, "aoPedirPermissao");
            return;
        }
        comecar(call);
    }

    @PermissionCallback
    private void aoPedirPermissao(PluginCall call) {
        if (getPermissionState("microfone") == com.getcapacitor.PermissionState.GRANTED) {
            comecar(call);
        } else {
            call.reject("permissao");
        }
    }

    private void comecar(PluginCall call) {
        if (!SpeechRecognizer.isRecognitionAvailable(getContext())) {
            call.reject("indisponivel");
            return;
        }
        String lang = call.getString("idioma");
        if (lang != null && !lang.isEmpty()) idioma = lang;
        getActivity().runOnUiThread(() -> {
            try {
                destruir();
                finalizado.setLength(0);
                falhasSeguidas = 0;
                ativo = true;
                reconhecedor = SpeechRecognizer.createSpeechRecognizer(getContext());
                reconhecedor.setRecognitionListener(new Ouvinte());
                escutar();
                call.resolve();
            } catch (Exception e) {
                ativo = false;
                call.reject("falha: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void parar(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            ativo = false;
            if (reconhecedor != null) {
                try { reconhecedor.stopListening(); } catch (Exception e) { /* ja parado */ }
            }
            call.resolve();
        });
    }

    @PluginMethod
    public void encerrar(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            ativo = false;
            destruir();
            call.resolve();
        });
    }

    @Override
    protected void handleOnDestroy() {
        ativo = false;
        destruir();
    }

    // ---- interno (sempre na thread principal) ----

    private void destruir() {
        if (reconhecedor != null) {
            try { reconhecedor.cancel(); } catch (Exception e) { /* ja parado */ }
            try { reconhecedor.destroy(); } catch (Exception e) { /* ja destruido */ }
            reconhecedor = null;
        }
    }

    private void escutar() {
        if (reconhecedor == null) return;
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, idioma);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getContext().getPackageName());
        // pausas longas nao encerram a escuta (quem fala pensa no que vai dizer)
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L);
        if (Build.VERSION.SDK_INT >= 33) {
            segmentada = true;
            i.putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, true);
        }
        estado(false);
        reconhecedor.startListening(i);
    }

    private void religar() {
        if (!ativo) return;
        // pequeno respiro: religar no mesmo instante da erro ERROR_RECOGNIZER_BUSY em alguns aparelhos
        getActivity().getWindow().getDecorView().postDelayed(() -> {
            if (ativo) {
                try { escutar(); } catch (Exception e) { falhar("falha"); }
            }
        }, 60);
    }

    private void falhar(String codigo) {
        ativo = false;
        JSObject r = new JSObject();
        r.put("codigo", codigo);
        notifyListeners("erro", r);
        estado(false);
    }

    private void estado(boolean pronto) {
        JSObject r = new JSObject();
        r.put("pronto", pronto);
        notifyListeners("estado", r);
    }

    private void emitir(String parcial) {
        String texto = finalizado.toString();
        if (parcial != null && !parcial.isEmpty()) {
            texto = texto.isEmpty() ? parcial : texto + " " + parcial;
        }
        JSObject r = new JSObject();
        r.put("texto", texto);
        notifyListeners("resultado", r);
    }

    private void acrescentar(String frase) {
        if (frase == null) return;
        frase = frase.trim();
        if (frase.isEmpty()) return;
        if (finalizado.length() > 0) finalizado.append(' ');
        finalizado.append(frase);
    }

    private static String primeiro(Bundle b, String chave) {
        ArrayList<String> l = b == null ? null : b.getStringArrayList(chave);
        return (l == null || l.isEmpty()) ? "" : l.get(0);
    }

    private class Ouvinte implements RecognitionListener {
        @Override public void onReadyForSpeech(Bundle params) { falhasSeguidas = 0; estado(true); }
        @Override public void onBeginningOfSpeech() { }
        @Override public void onRmsChanged(float rmsdB) { }
        @Override public void onBufferReceived(byte[] buffer) { }
        @Override public void onEndOfSpeech() { }
        @Override public void onEvent(int eventType, Bundle params) { }

        @Override
        public void onPartialResults(Bundle partialResults) {
            emitir(primeiro(partialResults, SpeechRecognizer.RESULTS_RECOGNITION));
        }

        // Android 13+: cada frase finalizada chega aqui e a sessao segue aberta
        @Override
        public void onSegmentResults(Bundle segmentResults) {
            acrescentar(primeiro(segmentResults, SpeechRecognizer.RESULTS_RECOGNITION));
            emitir("");
        }

        @Override
        public void onEndOfSegmentedSession() {
            emitir("");
            religar();
        }

        // sem sessao segmentada: o resultado final vem aqui e a escuta acaba, entao religamos
        @Override
        public void onResults(Bundle results) {
            if (!segmentada) acrescentar(primeiro(results, SpeechRecognizer.RESULTS_RECOGNITION));
            emitir("");
            if (ativo) religar();
        }

        @Override
        public void onError(int erro) {
            switch (erro) {
                case SpeechRecognizer.ERROR_NO_MATCH:
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                case SpeechRecognizer.ERROR_CLIENT:
                    // silencio ou escuta cancelada: normal, so religa
                    if (ativo) religar();
                    break;
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    if (ativo && ++falhasSeguidas < 5) religar(); else falhar("ocupado");
                    break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    falhar("permissao");
                    break;
                case SpeechRecognizer.ERROR_NETWORK:
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                    falhar("rede");
                    break;
                case SpeechRecognizer.ERROR_AUDIO:
                    falhar("microfone");
                    break;
                default:
                    if (ativo && ++falhasSeguidas < 5) religar(); else falhar("falha");
            }
        }
    }
}
