-- Fase 4: anamnese e prontuário.
--
-- modelo_ficha: os formulários (campos em JSON). Editar um modelo cria uma
-- versão nova da mesma família; a versão antiga continua existindo porque os
-- registros já feitos apontam para ela.
--
-- registro_clinico: cada anamnese, avaliação, plano, evolução ou reavaliação
-- de um paciente. registro_clinico_versao: o conteúdo. Corrigir é inserir uma
-- versão nova com o motivo; o banco recusa alterar ou apagar versões e
-- recusa apagar registros. Prontuário não se apaga.

CREATE TABLE IF NOT EXISTS modelo_ficha (
    id           uuid         NOT NULL DEFAULT gen_random_uuid(),
    clinica_id   uuid         NOT NULL,
    familia_id   uuid         NOT NULL,
    versao       integer      NOT NULL,
    nome         varchar(120) NOT NULL,
    area         varchar(20)  NOT NULL,
    tipo         varchar(30)  NOT NULL,
    campos       jsonb        NOT NULL,
    ativo        boolean      NOT NULL DEFAULT true,
    criado_em    timestamp    NOT NULL DEFAULT now(),
    CONSTRAINT pk_modelo_ficha PRIMARY KEY (id),
    CONSTRAINT fk_modelo_ficha_clinica FOREIGN KEY (clinica_id) REFERENCES clinica (id) ON DELETE CASCADE,
    CONSTRAINT uk_modelo_ficha_versao UNIQUE (familia_id, versao),
    CONSTRAINT ck_modelo_ficha_versao CHECK (versao >= 1),
    CONSTRAINT ck_modelo_ficha_area CHECK (area IN ('ESTETICA', 'FISIOTERAPIA', 'GERAL')),
    CONSTRAINT ck_modelo_ficha_tipo CHECK (tipo IN
        ('ANAMNESE', 'AVALIACAO_INICIAL', 'PLANO_TERAPEUTICO', 'EVOLUCAO', 'REAVALIACAO')),
    CONSTRAINT ck_modelo_ficha_campos CHECK (jsonb_typeof(campos) = 'array')
);

CREATE INDEX IF NOT EXISTS idx_modelo_ficha_clinica ON modelo_ficha (clinica_id, familia_id);

CREATE TABLE IF NOT EXISTS registro_clinico (
    id               uuid        NOT NULL DEFAULT gen_random_uuid(),
    clinica_id       uuid        NOT NULL,
    cliente_id       uuid        NOT NULL,
    profissional_id  uuid        NOT NULL,
    atendimento_id   uuid,
    tipo             varchar(30) NOT NULL,
    criado_em        timestamp   NOT NULL DEFAULT now(),
    CONSTRAINT pk_registro_clinico PRIMARY KEY (id),
    CONSTRAINT fk_registro_clinica FOREIGN KEY (clinica_id) REFERENCES clinica (id),
    CONSTRAINT fk_registro_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id),
    CONSTRAINT fk_registro_profissional FOREIGN KEY (profissional_id) REFERENCES profissional (id),
    CONSTRAINT fk_registro_atendimento FOREIGN KEY (atendimento_id) REFERENCES atendimento (id),
    CONSTRAINT ck_registro_tipo CHECK (tipo IN
        ('ANAMNESE', 'AVALIACAO_INICIAL', 'PLANO_TERAPEUTICO', 'EVOLUCAO', 'REAVALIACAO'))
);

CREATE INDEX IF NOT EXISTS idx_registro_cliente ON registro_clinico (clinica_id, cliente_id, criado_em);

CREATE TABLE IF NOT EXISTS registro_clinico_versao (
    id               uuid         NOT NULL DEFAULT gen_random_uuid(),
    clinica_id       uuid         NOT NULL,
    registro_id      uuid         NOT NULL,
    numero           integer      NOT NULL,
    modelo_ficha_id  uuid         NOT NULL,
    conteudo         jsonb        NOT NULL,
    autor_id         uuid         NOT NULL,
    autor_nome       varchar(150) NOT NULL,
    motivo           varchar(500),
    criado_em        timestamp    NOT NULL DEFAULT now(),
    CONSTRAINT pk_registro_clinico_versao PRIMARY KEY (id),
    CONSTRAINT fk_rcv_clinica FOREIGN KEY (clinica_id) REFERENCES clinica (id),
    CONSTRAINT fk_rcv_registro FOREIGN KEY (registro_id) REFERENCES registro_clinico (id),
    CONSTRAINT fk_rcv_modelo FOREIGN KEY (modelo_ficha_id) REFERENCES modelo_ficha (id),
    CONSTRAINT fk_rcv_autor FOREIGN KEY (autor_id) REFERENCES usuario (id),
    CONSTRAINT uk_rcv_numero UNIQUE (registro_id, numero),
    CONSTRAINT ck_rcv_numero CHECK (numero >= 1),
    -- Toda correção diz por quê.
    CONSTRAINT ck_rcv_motivo CHECK (numero = 1 OR length(btrim(coalesce(motivo, ''))) > 0),
    CONSTRAINT ck_rcv_conteudo CHECK (jsonb_typeof(conteudo) = 'object')
);

CREATE INDEX IF NOT EXISTS idx_rcv_registro ON registro_clinico_versao (registro_id, numero);

-- Tudo da mesma clínica.
CREATE OR REPLACE FUNCTION ck_prontuario_mesma_clinica()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_TABLE_NAME = 'registro_clinico' THEN
        IF NOT EXISTS (SELECT 1 FROM cliente WHERE id = NEW.cliente_id AND clinica_id = NEW.clinica_id)
           OR NOT EXISTS (SELECT 1 FROM profissional WHERE id = NEW.profissional_id AND clinica_id = NEW.clinica_id)
           OR (NEW.atendimento_id IS NOT NULL AND NOT EXISTS (
                SELECT 1 FROM atendimento WHERE id = NEW.atendimento_id AND clinica_id = NEW.clinica_id
                AND cliente_id = NEW.cliente_id)) THEN
            RAISE EXCEPTION 'registro clinico precisa ser da mesma clinica do paciente, do profissional e do atendimento';
        END IF;
    ELSIF TG_TABLE_NAME = 'registro_clinico_versao' THEN
        IF NOT EXISTS (SELECT 1 FROM registro_clinico WHERE id = NEW.registro_id AND clinica_id = NEW.clinica_id)
           OR NOT EXISTS (SELECT 1 FROM modelo_ficha WHERE id = NEW.modelo_ficha_id AND clinica_id = NEW.clinica_id) THEN
            RAISE EXCEPTION 'versao precisa ser da mesma clinica do registro e do modelo';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_registro_clinico_mesma_clinica ON registro_clinico;
CREATE TRIGGER trg_registro_clinico_mesma_clinica
    BEFORE INSERT ON registro_clinico
    FOR EACH ROW EXECUTE FUNCTION ck_prontuario_mesma_clinica();

DROP TRIGGER IF EXISTS trg_rcv_mesma_clinica ON registro_clinico_versao;
CREATE TRIGGER trg_rcv_mesma_clinica
    BEFORE INSERT ON registro_clinico_versao
    FOR EACH ROW EXECUTE FUNCTION ck_prontuario_mesma_clinica();

-- Versões: só inserção.
CREATE OR REPLACE FUNCTION ck_prontuario_versao_imutavel()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'registro_clinico_versao so aceita insercao: para corrigir, adicione uma versao nova';
END;
$$;

DROP TRIGGER IF EXISTS trg_rcv_imutavel ON registro_clinico_versao;
CREATE TRIGGER trg_rcv_imutavel
    BEFORE UPDATE OR DELETE ON registro_clinico_versao
    FOR EACH ROW EXECUTE FUNCTION ck_prontuario_versao_imutavel();

-- Registro: não se apaga, e paciente, profissional, tipo e data não mudam.
CREATE OR REPLACE FUNCTION ck_registro_clinico_imutavel()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'registro clinico nao pode ser apagado';
    END IF;
    IF NEW.clinica_id <> OLD.clinica_id OR NEW.cliente_id <> OLD.cliente_id
       OR NEW.profissional_id <> OLD.profissional_id OR NEW.tipo <> OLD.tipo
       OR NEW.criado_em <> OLD.criado_em
       OR NEW.atendimento_id IS DISTINCT FROM OLD.atendimento_id THEN
        RAISE EXCEPTION 'registro clinico nao pode ser alterado: adicione uma versao nova';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_registro_clinico_imutavel ON registro_clinico;
CREATE TRIGGER trg_registro_clinico_imutavel
    BEFORE UPDATE OR DELETE ON registro_clinico
    FOR EACH ROW EXECUTE FUNCTION ck_registro_clinico_imutavel();

-- Modelo usado em registro: campos, área e tipo não mudam (editar = versão nova).
CREATE OR REPLACE FUNCTION ck_modelo_ficha_congelado()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' AND EXISTS (SELECT 1 FROM registro_clinico_versao WHERE modelo_ficha_id = OLD.id) THEN
        RAISE EXCEPTION 'modelo usado em prontuario nao pode ser apagado';
    END IF;
    IF TG_OP = 'UPDATE' AND (NEW.campos <> OLD.campos OR NEW.area <> OLD.area OR NEW.tipo <> OLD.tipo
       OR NEW.versao <> OLD.versao OR NEW.familia_id <> OLD.familia_id OR NEW.clinica_id <> OLD.clinica_id) THEN
        RAISE EXCEPTION 'modelo de ficha nao muda: salve uma versao nova';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_modelo_ficha_congelado ON modelo_ficha;
CREATE TRIGGER trg_modelo_ficha_congelado
    BEFORE UPDATE OR DELETE ON modelo_ficha
    FOR EACH ROW EXECUTE FUNCTION ck_modelo_ficha_congelado();
