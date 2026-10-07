-- Pacotes de sessões (Fase 3).
--
--   pacote            modelo vendido pela clínica: serviço, nº de sessões,
--                     validade em dias e preço.
--   pacote_cliente    um pacote comprado por um paciente: saldo de sessões,
--                     data de validade e status (ATIVO ou CANCELADO; esgotado
--                     e vencido são derivados de saldo e validade).
--   pacote_movimento  histórico do saldo: BAIXA quando um atendimento do
--                     serviço é concluído, ESTORNO quando deixa de estar
--                     concluído. Só inserção.
--
-- O saldo nunca fica negativo nem passa do total (CHECK).
--
-- Idempotente: pode ser reaplicada sem erro.

CREATE TABLE IF NOT EXISTS pacote (
    id uuid NOT NULL,
    clinica_id uuid NOT NULL,
    servico_id uuid NOT NULL,
    nome varchar(120) NOT NULL,
    sessoes integer NOT NULL,
    validade_dias integer NOT NULL,
    preco numeric(10,2) NOT NULL,
    ativo boolean NOT NULL DEFAULT true,
    criado_em timestamp(6) NOT NULL DEFAULT now(),
    CONSTRAINT pk_pacote PRIMARY KEY (id),
    CONSTRAINT fk_pacote_clinica FOREIGN KEY (clinica_id) REFERENCES clinica (id) ON DELETE CASCADE,
    CONSTRAINT fk_pacote_servico FOREIGN KEY (servico_id) REFERENCES servico (id),
    CONSTRAINT uk_pacote_nome UNIQUE (clinica_id, nome),
    CONSTRAINT ck_pacote_sessoes CHECK (sessoes BETWEEN 1 AND 100),
    CONSTRAINT ck_pacote_validade CHECK (validade_dias BETWEEN 1 AND 1095),
    CONSTRAINT ck_pacote_preco CHECK (preco >= 0)
);

CREATE TABLE IF NOT EXISTS pacote_cliente (
    id uuid NOT NULL,
    clinica_id uuid NOT NULL,
    cliente_id uuid NOT NULL,
    pacote_id uuid NOT NULL,
    servico_id uuid NOT NULL,
    nome varchar(120) NOT NULL,
    sessoes_total integer NOT NULL,
    saldo integer NOT NULL,
    data_compra date NOT NULL,
    data_validade date NOT NULL,
    valor_pago numeric(10,2) NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'ATIVO',
    criado_em timestamp(6) NOT NULL DEFAULT now(),
    CONSTRAINT pk_pacote_cliente PRIMARY KEY (id),
    CONSTRAINT fk_pc_clinica FOREIGN KEY (clinica_id) REFERENCES clinica (id) ON DELETE CASCADE,
    CONSTRAINT fk_pc_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id),
    CONSTRAINT fk_pc_pacote FOREIGN KEY (pacote_id) REFERENCES pacote (id),
    CONSTRAINT fk_pc_servico FOREIGN KEY (servico_id) REFERENCES servico (id),
    CONSTRAINT ck_pc_status CHECK (status IN ('ATIVO', 'CANCELADO')),
    CONSTRAINT ck_pc_saldo CHECK (saldo >= 0 AND saldo <= sessoes_total),
    CONSTRAINT ck_pc_validade CHECK (data_validade >= data_compra),
    CONSTRAINT ck_pc_valor CHECK (valor_pago >= 0)
);

CREATE INDEX IF NOT EXISTS idx_pc_cliente ON pacote_cliente (cliente_id);

CREATE TABLE IF NOT EXISTS pacote_movimento (
    id uuid NOT NULL,
    clinica_id uuid NOT NULL,
    pacote_cliente_id uuid NOT NULL,
    atendimento_id uuid,
    tipo varchar(20) NOT NULL,
    criado_em timestamp(6) NOT NULL DEFAULT now(),
    CONSTRAINT pk_pacote_movimento PRIMARY KEY (id),
    CONSTRAINT fk_pm_clinica FOREIGN KEY (clinica_id) REFERENCES clinica (id) ON DELETE CASCADE,
    CONSTRAINT fk_pm_pacote_cliente FOREIGN KEY (pacote_cliente_id) REFERENCES pacote_cliente (id),
    CONSTRAINT fk_pm_atendimento FOREIGN KEY (atendimento_id) REFERENCES atendimento (id),
    CONSTRAINT ck_pm_tipo CHECK (tipo IN ('BAIXA', 'ESTORNO'))
);

CREATE INDEX IF NOT EXISTS idx_pm_pacote_cliente ON pacote_movimento (pacote_cliente_id);
CREATE INDEX IF NOT EXISTS idx_pm_atendimento ON pacote_movimento (atendimento_id);

-- Tudo do pacote precisa ser da mesma clínica.
CREATE OR REPLACE FUNCTION ck_pacote_mesma_clinica()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_TABLE_NAME = 'pacote' THEN
        IF NOT EXISTS (SELECT 1 FROM servico WHERE id = NEW.servico_id AND clinica_id = NEW.clinica_id) THEN
            RAISE EXCEPTION 'servico do pacote precisa ser da mesma clinica';
        END IF;
    ELSIF TG_TABLE_NAME = 'pacote_cliente' THEN
        IF NOT EXISTS (SELECT 1 FROM cliente WHERE id = NEW.cliente_id AND clinica_id = NEW.clinica_id)
           OR NOT EXISTS (SELECT 1 FROM pacote WHERE id = NEW.pacote_id AND clinica_id = NEW.clinica_id) THEN
            RAISE EXCEPTION 'cliente e pacote precisam ser da mesma clinica';
        END IF;
    ELSIF TG_TABLE_NAME = 'pacote_movimento' THEN
        IF NOT EXISTS (SELECT 1 FROM pacote_cliente WHERE id = NEW.pacote_cliente_id AND clinica_id = NEW.clinica_id)
           OR (NEW.atendimento_id IS NOT NULL AND NOT EXISTS (
                SELECT 1 FROM atendimento WHERE id = NEW.atendimento_id AND clinica_id = NEW.clinica_id)) THEN
            RAISE EXCEPTION 'movimento precisa ser da mesma clinica do pacote e do atendimento';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_pacote_mesma_clinica ON pacote;
CREATE TRIGGER trg_pacote_mesma_clinica
    BEFORE INSERT OR UPDATE ON pacote
    FOR EACH ROW EXECUTE FUNCTION ck_pacote_mesma_clinica();

DROP TRIGGER IF EXISTS trg_pacote_cliente_mesma_clinica ON pacote_cliente;
CREATE TRIGGER trg_pacote_cliente_mesma_clinica
    BEFORE INSERT OR UPDATE ON pacote_cliente
    FOR EACH ROW EXECUTE FUNCTION ck_pacote_mesma_clinica();

DROP TRIGGER IF EXISTS trg_pacote_movimento_mesma_clinica ON pacote_movimento;
CREATE TRIGGER trg_pacote_movimento_mesma_clinica
    BEFORE INSERT ON pacote_movimento
    FOR EACH ROW EXECUTE FUNCTION ck_pacote_mesma_clinica();

-- Movimento é histórico: não se altera nem se apaga.
CREATE OR REPLACE FUNCTION ck_pacote_movimento_imutavel()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'pacote_movimento so aceita insercao';
END;
$$;

DROP TRIGGER IF EXISTS trg_pacote_movimento_imutavel ON pacote_movimento;
CREATE TRIGGER trg_pacote_movimento_imutavel
    BEFORE UPDATE OR DELETE ON pacote_movimento
    FOR EACH ROW EXECUTE FUNCTION ck_pacote_movimento_imutavel();
