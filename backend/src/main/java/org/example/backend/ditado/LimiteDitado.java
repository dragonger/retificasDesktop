package org.example.backend.ditado;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * Trava de custo do ditado: quantos ditados por dia cada empresa pode abrir e quantos no total. O dia vira a
 * meia-noite de Brasilia. Fica em memoria, entao zera quando o backend reinicia (acontece no deploy e no
 * desligamento da noite); basta como trava de custo, e o limite de gasto do Console da Anthropic e a rede de
 * seguranca de verdade (ver backend/DITADO.md).
 */
public final class LimiteDitado {

    public static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

    public enum Resultado { OK, LIMITE_EMPRESA, LIMITE_GLOBAL }

    private final Clock relogio;
    private final int limiteEmpresa;
    private final int limiteGlobal;
    private LocalDate dia;
    private final Map<Long, Integer> porEmpresa = new HashMap<>();
    private int global;

    public LimiteDitado(Clock relogio, int limiteEmpresa, int limiteGlobal) {
        this.relogio = relogio;
        this.limiteEmpresa = limiteEmpresa;
        this.limiteGlobal = limiteGlobal;
    }

    public synchronized Resultado reservar(long empresaId) {
        virarSeNovoDia();
        int usados = porEmpresa.getOrDefault(empresaId, 0);
        if (usados >= limiteEmpresa) return Resultado.LIMITE_EMPRESA;
        if (global >= limiteGlobal) return Resultado.LIMITE_GLOBAL;
        porEmpresa.put(empresaId, usados + 1);
        global++;
        return Resultado.OK;
    }

    /** Devolve um uso reservado (a primeira chamada ao modelo falhou, entao o ditado nao aconteceu). */
    public synchronized void liberar(long empresaId) {
        virarSeNovoDia();
        int usados = porEmpresa.getOrDefault(empresaId, 0);
        if (usados > 0) {
            porEmpresa.put(empresaId, usados - 1);
            if (global > 0) global--;
        }
    }

    public synchronized int restantes(long empresaId) {
        virarSeNovoDia();
        int daEmpresa = limiteEmpresa - porEmpresa.getOrDefault(empresaId, 0);
        int doGlobal = limiteGlobal - global;
        return Math.max(0, Math.min(daEmpresa, doGlobal));
    }

    public int limiteEmpresa() {
        return limiteEmpresa;
    }

    private void virarSeNovoDia() {
        LocalDate hoje = LocalDate.now(relogio.withZone(BRASILIA));
        if (!hoje.equals(dia)) {
            dia = hoje;
            porEmpresa.clear();
            global = 0;
        }
    }
}
