package org.example.backend.ditado;

/** Erro do ditado que vira resposta HTTP: {@code status} e a mensagem (ja em linguagem de usuario). */
public class DitadoException extends RuntimeException {

    private final int status;

    public DitadoException(int status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public DitadoException(int status, String mensagem, Throwable causa) {
        super(mensagem, causa);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
