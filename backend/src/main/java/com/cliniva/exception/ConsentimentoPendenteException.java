package com.cliniva.exception;

/** Paciente sem termo de consentimento vigente: nada clínico novo é registrado. */
public class ConsentimentoPendenteException extends RuntimeException {
    public ConsentimentoPendenteException(String mensagem) {
        super(mensagem);
    }
}
