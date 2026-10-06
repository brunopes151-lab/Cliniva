package com.cliniva.tenancy;

import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria o administrador da plataforma a partir de {@code ADMIN_BOOTSTRAP_EMAIL}.
 *
 * <p>Substitui o ADMIN fixo da migration 03 (neutralizado pela migration
 * {@code 20261006000001}): nenhum e-mail de administrador fica no git. O
 * vínculo com o Supabase acontece no primeiro login, pelo e-mail do JWT,
 * como já era com o seed.
 *
 * <p>Só cria; nunca promove. Se o e-mail já pertence a um usuário de
 * clínica, a subida segue e o caso fica no log para alguém decidir.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UsuarioRepository usuarioRepository;
    private final String email;

    public AdminBootstrap(UsuarioRepository usuarioRepository,
            @Value("${cliniva.admin.bootstrap-email:}") String email) {
        this.usuarioRepository = usuarioRepository;
        this.email = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isEmpty()) {
            return;
        }
        Optional<Usuario> existente = usuarioRepository.findByEmailIgnoreCase(email);
        if (existente.isPresent()) {
            if (existente.get().getPapel() != Papel.ADMIN) {
                log.warn("ADMIN_BOOTSTRAP_EMAIL pertence a um usuário que não é ADMIN; nada foi alterado");
            }
            return;
        }
        Usuario admin = new Usuario();
        admin.setEmail(email);
        admin.setNome("Administrador");
        admin.setPapel(Papel.ADMIN);
        admin.setAtivo(true);
        usuarioRepository.save(admin);
        log.info("Administrador da plataforma criado a partir de ADMIN_BOOTSTRAP_EMAIL");
    }
}
