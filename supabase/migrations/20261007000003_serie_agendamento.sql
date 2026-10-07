-- Agendamento recorrente (Fase 3).
--
-- Uma série guarda a regra com que os atendimentos foram gerados; cada
-- sessão continua sendo um atendimento comum (com profissional, conflito e
-- estoque próprios) que aponta para a série em atendimento.serie_id.
--
-- Regras da geração (no serviço):
--   * domingo é sempre pulado; sábado só quando inclui_sabado;
--   * MENSAL usa o mesmo dia do mês e, quando o mês é mais curto (dia 31),
--     o último dia daquele mês;
--   * o fim é por data (data_fim) OU por quantidade de sessões, nunca os dois.
--
-- Idempotente: pode ser reaplicada sem erro.

CREATE TABLE IF NOT EXISTS serie_agendamento (
    id uuid NOT NULL,
    clinica_id uuid NOT NULL,
    cliente_id uuid NOT NULL,
    profissional_id uuid NOT NULL,
    frequencia varchar(20) NOT NULL,
    inclui_sabado boolean NOT NULL DEFAULT false,
    inicio timestamp(6) NOT NULL,
    data_fim date,
    quantidade integer,
    criado_em timestamp(6) NOT NULL DEFAULT now(),
    CONSTRAINT pk_serie_agendamento PRIMARY KEY (id),
    CONSTRAINT fk_serie_clinica FOREIGN KEY (clinica_id) REFERENCES clinica (id) ON DELETE CASCADE,
    CONSTRAINT fk_serie_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id),
    CONSTRAINT fk_serie_profissional FOREIGN KEY (profissional_id) REFERENCES profissional (id),
    CONSTRAINT ck_serie_frequencia CHECK (frequencia IN ('DIARIA', 'SEMANAL', 'QUINZENAL', 'MENSAL')),
    CONSTRAINT ck_serie_fim CHECK ((data_fim IS NULL) <> (quantidade IS NULL)),
    CONSTRAINT ck_serie_quantidade CHECK (quantidade IS NULL OR quantidade BETWEEN 1 AND 100),
    CONSTRAINT ck_serie_data_fim CHECK (data_fim IS NULL OR data_fim >= inicio::date)
);

CREATE INDEX IF NOT EXISTS idx_serie_clinica ON serie_agendamento (clinica_id);

-- Cliente e profissional da série precisam ser da clínica da série.
CREATE OR REPLACE FUNCTION ck_serie_mesma_clinica()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM cliente WHERE id = NEW.cliente_id AND clinica_id = NEW.clinica_id)
       OR NOT EXISTS (SELECT 1 FROM profissional WHERE id = NEW.profissional_id AND clinica_id = NEW.clinica_id)
    THEN
        RAISE EXCEPTION 'cliente e profissional da serie precisam ser da mesma clinica';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_serie_mesma_clinica ON serie_agendamento;
CREATE TRIGGER trg_serie_mesma_clinica
    BEFORE INSERT OR UPDATE ON serie_agendamento
    FOR EACH ROW EXECUTE FUNCTION ck_serie_mesma_clinica();

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'atendimento' AND column_name = 'serie_id'
    ) THEN
        ALTER TABLE atendimento ADD COLUMN serie_id uuid;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_atendimento_serie') THEN
        ALTER TABLE atendimento
            ADD CONSTRAINT fk_atendimento_serie FOREIGN KEY (serie_id)
            REFERENCES serie_agendamento (id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_atendimento_serie ON atendimento (serie_id);

-- Um atendimento só entra em série da própria clínica.
CREATE OR REPLACE FUNCTION ck_atendimento_serie_mesma_clinica()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.serie_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM serie_agendamento s WHERE s.id = NEW.serie_id AND s.clinica_id = NEW.clinica_id
    ) THEN
        RAISE EXCEPTION 'serie % nao pertence a clinica %', NEW.serie_id, NEW.clinica_id;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_atendimento_serie_mesma_clinica ON atendimento;
CREATE TRIGGER trg_atendimento_serie_mesma_clinica
    BEFORE INSERT OR UPDATE OF serie_id, clinica_id ON atendimento
    FOR EACH ROW EXECUTE FUNCTION ck_atendimento_serie_mesma_clinica();
