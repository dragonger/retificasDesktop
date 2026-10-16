package org.example.backend.altomedidas;

import org.example.model.OsAltoMedidasModel;
import org.example.repository.OsAltoMedidasRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Leva ao Alto Medidas o "cliente pagou" marcado pela DIH. Tenta na hora (ao
 * marcar) e repete o que falhou: a cada 10 minutos enquanto o servidor está
 * acordado e quando alguém abre a tela.
 */
@Component
public class AltoMedidasSincronizador {

    private final AltoMedidasCliente cliente;
    private final OsAltoMedidasRepository repositorio = new OsAltoMedidasRepository();
    private final AtomicBoolean rodando = new AtomicBoolean(false);

    public AltoMedidasSincronizador(AltoMedidasCliente cliente) {
        this.cliente = cliente;
    }

    public boolean isHabilitado() {
        return cliente.isHabilitado();
    }

    /** Envia o estado atual de uma OS e anota o resultado. */
    public void sincronizar(OsAltoMedidasModel os) {
        boolean pago = os.isPago();
        AltoMedidasCliente.Resposta r = cliente.enviarPagamento(os.getOsId(), pago, os.getPagoEm());
        boolean ok = r.resultado() != AltoMedidasCliente.Resultado.FALHA;
        repositorio.registrarSincronia(os.getId(), pago, ok, r.erro());
    }

    /** Repete tudo o que ficou pendente; só uma execução por vez. */
    @Scheduled(fixedDelayString = "${ALTO_MEDIDAS_RETRY_MS:600000}", initialDelayString = "120000")
    public void sincronizarPendentes() {
        if (!cliente.isHabilitado() || !rodando.compareAndSet(false, true)) return;
        try {
            for (OsAltoMedidasModel os : repositorio.naoSincronizadas()) {
                try {
                    sincronizar(os);
                } catch (RuntimeException e) {
                    // uma OS com problema não trava as outras
                }
            }
        } finally {
            rodando.set(false);
        }
    }
}
