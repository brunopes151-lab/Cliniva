-- Fase 5: fecha as tabelas para a API pública do Supabase.
--
-- O Supabase expõe o schema public pela API REST para quem tem a chave
-- anon, que vai no frontend. Sem RLS, essa chave lê e altera qualquer
-- tabela, inclusive cliente e prontuário. O sistema não usa essa API: o
-- frontend só usa o Supabase para login, e os dados passam pelo backend,
-- que conecta como dono das tabelas e por isso não é afetado pelo RLS.
--
-- Então: RLS ligado em todas as tabelas, sem nenhuma política (ninguém
-- passa pela API), e os papéis anon e authenticated sem permissão nelas.
-- Tabela nova precisa ligar o RLS também; o scripts/check-migrations.sh
-- confere.

DO $$
DECLARE
    tabela record;
BEGIN
    FOR tabela IN SELECT tablename FROM pg_tables WHERE schemaname = 'public' LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', tabela.tablename);
    END LOOP;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        EXECUTE 'REVOKE ALL ON ALL TABLES IN SCHEMA public FROM anon';
        EXECUTE 'REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM anon';
        EXECUTE 'REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA public FROM anon';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        EXECUTE 'REVOKE ALL ON ALL TABLES IN SCHEMA public FROM authenticated';
        EXECUTE 'REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM authenticated';
        EXECUTE 'REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA public FROM authenticated';
    END IF;
END;
$$;
