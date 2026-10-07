package com.cliniva.tenancy;

/**
 * Perfis de acesso.
 *
 * <ul>
 * <li>ADMIN: dono da plataforma (manutenção e modo suporte), sem clínica.</li>
 * <li>OWNER: administrador da clínica; vê e configura tudo.</li>
 * <li>RECEPCAO: agenda, pacientes, atendimentos e estoque; não configura.</li>
 * <li>PROFISSIONAL: só a própria agenda e os próprios pacientes.</li>
 * </ul>
 */
public enum Papel {
    ADMIN,
    OWNER,
    RECEPCAO,
    PROFISSIONAL
}
