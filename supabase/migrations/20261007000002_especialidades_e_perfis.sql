-- Especialidades dos profissionais e perfis de acesso da clínica.
--
-- Perfis (coluna usuario.papel, varchar):
--   ADMIN        dono da plataforma (manutenção, modo suporte), sem clínica
--   OWNER        administrador da clínica (vê e configura tudo)
--   RECEPCAO     agenda, pacientes, atendimentos e estoque; não configura
--   PROFISSIONAL só a própria agenda e os próprios pacientes
--
-- usuario.profissional_id liga o login à pessoa que atende. Obrigatório
-- para PROFISSIONAL, opcional para OWNER (o dono que também atende) e
-- proibido para ADMIN.
--
-- Idempotente: pode ser reaplicada sem erro.

-- ---------------------------------------------------------------------------
-- 1. Especialidades (por clínica)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS especialidade (
    id uuid NOT NULL,
    clinica_id uuid NOT NULL,
    nome varchar(80) NOT NULL,
    criado_em timestamp(6) NOT NULL DEFAULT now(),
    CONSTRAINT pk_especialidade PRIMARY KEY (id),
    CONSTRAINT fk_especialidade_clinica FOREIGN KEY (clinica_id)
        REFERENCES clinica (id) ON DELETE CASCADE,
    CONSTRAINT uk_especialidade_nome UNIQUE (clinica_id, nome),
    CONSTRAINT ck_especialidade_nome CHECK (length(btrim(nome)) > 0)
);

CREATE TABLE IF NOT EXISTS profissional_especialidade (
    profissional_id uuid NOT NULL,
    especialidade_id uuid NOT NULL,
    CONSTRAINT pk_profissional_especialidade PRIMARY KEY (profissional_id, especialidade_id),
    CONSTRAINT fk_pe_profissional FOREIGN KEY (profissional_id)
        REFERENCES profissional (id) ON DELETE CASCADE,
    CONSTRAINT fk_pe_especialidade FOREIGN KEY (especialidade_id)
        REFERENCES especialidade (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pe_especialidade ON profissional_especialidade (especialidade_id);

CREATE OR REPLACE FUNCTION ck_profissional_especialidade_mesma_clinica()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM profissional p
        JOIN especialidade e ON e.clinica_id = p.clinica_id
        WHERE p.id = NEW.profissional_id AND e.id = NEW.especialidade_id
    ) THEN
        RAISE EXCEPTION 'profissional e especialidade precisam ser da mesma clinica';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_pe_mesma_clinica ON profissional_especialidade;
CREATE TRIGGER trg_pe_mesma_clinica
    BEFORE INSERT OR UPDATE ON profissional_especialidade
    FOR EACH ROW EXECUTE FUNCTION ck_profissional_especialidade_mesma_clinica();

-- As duas áreas da clínica já vêm cadastradas; dá para renomear ou apagar.
INSERT INTO especialidade (id, clinica_id, nome, criado_em)
SELECT gen_random_uuid(), c.id, e.nome, now()
FROM clinica c
CROSS JOIN (VALUES ('Estética'), ('Fisioterapia')) AS e (nome)
ON CONFLICT (clinica_id, nome) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 2. Perfis de acesso
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'usuario' AND column_name = 'profissional_id'
    ) THEN
        ALTER TABLE usuario ADD COLUMN profissional_id uuid;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_usuario_profissional') THEN
        ALTER TABLE usuario
            ADD CONSTRAINT fk_usuario_profissional FOREIGN KEY (profissional_id)
            REFERENCES profissional (id) ON DELETE RESTRICT;
    END IF;

    -- Um login por profissional.
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_usuario_profissional') THEN
        ALTER TABLE usuario
            ADD CONSTRAINT uk_usuario_profissional UNIQUE (profissional_id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_usuario_papel') THEN
        ALTER TABLE usuario
            ADD CONSTRAINT ck_usuario_papel
            CHECK (papel IN ('ADMIN', 'OWNER', 'RECEPCAO', 'PROFISSIONAL'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_usuario_papel_profissional') THEN
        ALTER TABLE usuario
            ADD CONSTRAINT ck_usuario_papel_profissional
            CHECK ((papel <> 'PROFISSIONAL' OR profissional_id IS NOT NULL)
               AND (papel <> 'ADMIN' OR profissional_id IS NULL));
    END IF;
END $$;

CREATE OR REPLACE FUNCTION ck_usuario_profissional_mesma_clinica()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.profissional_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM profissional p
        WHERE p.id = NEW.profissional_id AND p.clinica_id = NEW.clinica_id
    ) THEN
        RAISE EXCEPTION 'usuario e profissional precisam ser da mesma clinica';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_usuario_profissional_mesma_clinica ON usuario;
CREATE TRIGGER trg_usuario_profissional_mesma_clinica
    BEFORE INSERT OR UPDATE OF profissional_id, clinica_id ON usuario
    FOR EACH ROW EXECUTE FUNCTION ck_usuario_profissional_mesma_clinica();
