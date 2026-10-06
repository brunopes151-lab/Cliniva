-- =====================================================================
-- Neutraliza o ADMIN de plataforma semeado pela migration 03.
--
-- A 03 cria um usuário ADMIN com o e-mail pessoal do autor original. Como
-- o vínculo com o Supabase acontece pelo e-mail no primeiro login, quem
-- tiver uma conta com aquele e-mail no Supabase desta instalação vira dono
-- da plataforma, com acesso a todas as clínicas.
--
-- Migration existente não se edita: aqui o registro é desativado e o
-- e-mail é trocado por um endereço inválido (o que também tira o dado
-- pessoal do banco). O administrador desta instalação passa a ser criado
-- pela variável ADMIN_BOOTSTRAP_EMAIL na subida do backend.
--
-- Idempotente.
-- =====================================================================

UPDATE usuario
SET ativo = FALSE,
    email = 'admin-seed-desativado@invalid.local',
    nome = 'Administrador do seed (desativado)',
    supabase_user_id = NULL
WHERE id = '00000000-0000-0000-0000-000000000002';
