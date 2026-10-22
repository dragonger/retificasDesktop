package org.example.backend.ditado;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Relogio dos testes: o tempo so anda quando o teste manda. */
final class RelogioFalso extends Clock {

    private Instant agora;
    private final ZoneId zona;

    RelogioFalso(Instant inicio, ZoneId zona) {
        this.agora = inicio;
        this.zona = zona;
    }

    static RelogioFalso em(String instanteUtc) {
        return new RelogioFalso(Instant.parse(instanteUtc), ZoneId.of("UTC"));
    }

    void avancar(Duration d) {
        agora = agora.plus(d);
    }

    @Override
    public ZoneId getZone() {
        return zona;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        RelogioFalso outro = new RelogioFalso(agora, zone);
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return zone;
            }

            @Override
            public Clock withZone(ZoneId z) {
                return outro.withZone(z);
            }

            @Override
            public Instant instant() {
                return RelogioFalso.this.instant();
            }
        };
    }

    @Override
    public Instant instant() {
        return agora;
    }
}
