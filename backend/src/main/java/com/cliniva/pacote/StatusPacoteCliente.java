package com.cliniva.pacote;

/**
 * Status gravado do pacote do paciente. Esgotado e vencido não são gravados:
 * saem do saldo e da validade (ver {@link PacoteCliente#situacao}).
 */
public enum StatusPacoteCliente {
    ATIVO,
    CANCELADO
}
