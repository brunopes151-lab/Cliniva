-- Fecha a dimensão profissional que a migration 08 deixou pela metade
-- (é a "09" prevista no comentário dela).
--
-- 1. Todo atendimento passa a ter profissional (NOT NULL). Os antigos vão
--    para o profissional "Geral" da clínica, como a 08 já fazia.
-- 2. O banco recusa dois atendimentos não cancelados do MESMO profissional
--    com horários sobrepostos (restrição de exclusão com btree_gist). O
--    serviço já checa isso sob lock; esta é a garantia final.
-- 3. Os vínculos serviço ↔ "Geral" que a 08 criou automaticamente são
--    removidos: sem vínculo, o serviço pode ser feito por qualquer
--    profissional ativo (regra documentada na própria tabela). Mantê-los
--    faria todo serviço antigo ficar restrito ao "Geral".
--
-- Idempotente: pode ser reaplicada sem erro.

-- ---------------------------------------------------------------------------
-- 1. Garante um "Geral" por clínica e preenche atendimento.profissional_id
-- ---------------------------------------------------------------------------
INSERT INTO profissional (id, clinica_id, nome, ativo, criado_em)
SELECT gen_random_uuid(), c.id, 'Geral', true, now()
FROM clinica c
ON CONFLICT (clinica_id, nome) DO NOTHING;

UPDATE atendimento a
SET profissional_id = p.id
FROM profissional p
WHERE p.clinica_id = a.clinica_id
  AND p.nome = 'Geral'
  AND a.profissional_id IS NULL;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM atendimento WHERE profissional_id IS NULL) THEN
        RAISE EXCEPTION 'atendimento sem profissional depois do backfill';
    END IF;
END $$;

ALTER TABLE atendimento ALTER COLUMN profissional_id SET NOT NULL;

-- O profissional precisa ser da mesma clínica do atendimento. Trigger e não
-- CHECK porque o Postgres não aceita subquery em CHECK (ver a 08).
CREATE OR REPLACE FUNCTION ck_atendimento_profissional_mesma_clinica()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM profissional p
        WHERE p.id = NEW.profissional_id AND p.clinica_id = NEW.clinica_id
    ) THEN
        RAISE EXCEPTION 'profissional % nao pertence a clinica %',
            NEW.profissional_id, NEW.clinica_id;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_atendimento_profissional_mesma_clinica ON atendimento;
CREATE TRIGGER trg_atendimento_profissional_mesma_clinica
    BEFORE INSERT OR UPDATE OF profissional_id, clinica_id ON atendimento
    FOR EACH ROW EXECUTE FUNCTION ck_atendimento_profissional_mesma_clinica();

-- ---------------------------------------------------------------------------
-- 2. Sem sobreposição por profissional
--
--    Intervalo semiaberto [início, fim): um atendimento que termina às 10:00
--    não conflita com outro que começa às 10:00. Cancelado não bloqueia.
-- ---------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS btree_gist;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ex_atendimento_profissional_sobreposto'
    ) THEN
        IF EXISTS (
            SELECT 1
            FROM atendimento a
            JOIN atendimento b
              ON a.profissional_id = b.profissional_id
             AND a.id < b.id
             AND a.status IS DISTINCT FROM 'CANCELADO'
             AND b.status IS DISTINCT FROM 'CANCELADO'
             AND a.data_atendimento < b.data_atendimento + b.duracao_minutos * interval '1 minute'
             AND b.data_atendimento < a.data_atendimento + a.duracao_minutos * interval '1 minute'
        ) THEN
            RAISE EXCEPTION
                'existem atendimentos sobrepostos do mesmo profissional; resolva antes de aplicar';
        END IF;

        ALTER TABLE atendimento
            ADD CONSTRAINT ex_atendimento_profissional_sobreposto
            EXCLUDE USING gist (
                profissional_id WITH =,
                tsrange(data_atendimento,
                        data_atendimento + duracao_minutos * interval '1 minute',
                        '[)') WITH &&
            ) WHERE (status IS DISTINCT FROM 'CANCELADO');
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 3. Remove os vínculos automáticos serviço ↔ "Geral" criados pela 08
-- ---------------------------------------------------------------------------
DELETE FROM servico_profissional sp
USING profissional p
WHERE sp.profissional_id = p.id
  AND p.nome = 'Geral';
