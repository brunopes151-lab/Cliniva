-- Fase 5: LGPD.
--
-- termo_consentimento: o texto que o paciente aceita, por versão. Mudar o
-- texto publica a versão seguinte; uma versão publicada não muda.
--
-- consentimento_paciente: quem aceitou qual versão, quando e quem registrou.
-- Revogar preenche os campos de revogação uma única vez; o resto não muda.
-- Excluir o paciente leva junto os consentimentos dele.
--
-- auditoria_acesso: quem leu, escreveu ou exportou dados clínicos de qual
-- paciente. Só se insere. cliente_id e usuario_id não têm chave estrangeira
-- de propósito: o rastro continua mesmo se o cadastro sair.

CREATE TABLE IF NOT EXISTS termo_consentimento (
    id              uuid         NOT NULL DEFAULT gen_random_uuid(),
    clinica_id      uuid         NOT NULL,
    versao          integer      NOT NULL,
    texto           text         NOT NULL,
    vigente_desde   timestamp    NOT NULL DEFAULT now(),
    publicado_por   varchar(150),
    CONSTRAINT pk_termo_consentimento PRIMARY KEY (id),
    CONSTRAINT fk_termo_clinica FOREIGN KEY (clinica_id) REFERENCES clinica (id) ON DELETE CASCADE,
    CONSTRAINT uk_termo_versao UNIQUE (clinica_id, versao),
    CONSTRAINT ck_termo_versao CHECK (versao >= 1),
    CONSTRAINT ck_termo_texto CHECK (length(btrim(texto)) > 0)
);

CREATE TABLE IF NOT EXISTS consentimento_paciente (
    id                    uuid         NOT NULL DEFAULT gen_random_uuid(),
    clinica_id            uuid         NOT NULL,
    cliente_id            uuid         NOT NULL,
    termo_id              uuid         NOT NULL,
    aceito_em             timestamp    NOT NULL DEFAULT now(),
    registrado_por_id     uuid,
    registrado_por_nome   varchar(150) NOT NULL,
    revogado_em           timestamp,
    revogado_por_nome     varchar(150),
    motivo_revogacao      varchar(500),
    CONSTRAINT pk_consentimento_paciente PRIMARY KEY (id),
    CONSTRAINT fk_consentimento_clinica FOREIGN KEY (clinica_id) REFERENCES clinica (id) ON DELETE CASCADE,
    CONSTRAINT fk_consentimento_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id) ON DELETE CASCADE,
    CONSTRAINT fk_consentimento_termo FOREIGN KEY (termo_id) REFERENCES termo_consentimento (id) ON DELETE CASCADE,
    CONSTRAINT ck_consentimento_revogacao CHECK (
        (revogado_em IS NULL AND revogado_por_nome IS NULL AND motivo_revogacao IS NULL)
        OR (revogado_em IS NOT NULL AND revogado_por_nome IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS idx_consentimento_cliente ON consentimento_paciente (clinica_id, cliente_id, aceito_em);

CREATE TABLE IF NOT EXISTS auditoria_acesso (
    id             uuid         NOT NULL DEFAULT gen_random_uuid(),
    clinica_id     uuid         NOT NULL,
    cliente_id     uuid         NOT NULL,
    registro_id    uuid,
    usuario_id     uuid,
    usuario_nome   varchar(150) NOT NULL,
    papel          varchar(20)  NOT NULL,
    modo_suporte   boolean      NOT NULL DEFAULT false,
    acao           varchar(40)  NOT NULL,
    ip             varchar(64),
    criado_em      timestamp    NOT NULL DEFAULT now(),
    CONSTRAINT pk_auditoria_acesso PRIMARY KEY (id),
    CONSTRAINT fk_auditoria_clinica FOREIGN KEY (clinica_id) REFERENCES clinica (id) ON DELETE CASCADE,
    CONSTRAINT ck_auditoria_acao CHECK (acao IN ('VER_PRONTUARIO', 'VER_HISTORICO', 'CRIAR_REGISTRO',
        'CORRIGIR_REGISTRO', 'EXPORTAR_DADOS', 'REGISTRAR_CONSENTIMENTO', 'REVOGAR_CONSENTIMENTO'))
);

CREATE INDEX IF NOT EXISTS idx_auditoria_cliente ON auditoria_acesso (clinica_id, cliente_id, criado_em);

-- Tudo da mesma clínica.
CREATE OR REPLACE FUNCTION ck_lgpd_mesma_clinica()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM cliente WHERE id = NEW.cliente_id AND clinica_id = NEW.clinica_id)
       OR NOT EXISTS (SELECT 1 FROM termo_consentimento WHERE id = NEW.termo_id AND clinica_id = NEW.clinica_id) THEN
        RAISE EXCEPTION 'consentimento precisa ser da mesma clinica do paciente e do termo';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_consentimento_mesma_clinica ON consentimento_paciente;
CREATE TRIGGER trg_consentimento_mesma_clinica
    BEFORE INSERT ON consentimento_paciente
    FOR EACH ROW EXECUTE FUNCTION ck_lgpd_mesma_clinica();

-- Apagar só em cascata (paciente ou clínica excluídos): pg_trigger_depth() > 1
-- quando quem apaga é a chave estrangeira, e = 1 num DELETE direto.

-- Termo publicado não muda.
CREATE OR REPLACE FUNCTION ck_termo_imutavel()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF pg_trigger_depth() > 1 THEN
            RETURN OLD;
        END IF;
        RAISE EXCEPTION 'termo de consentimento nao pode ser apagado';
    END IF;
    RAISE EXCEPTION 'termo de consentimento nao muda: publique uma versao nova';
END;
$$;

DROP TRIGGER IF EXISTS trg_termo_imutavel ON termo_consentimento;
CREATE TRIGGER trg_termo_imutavel
    BEFORE UPDATE OR DELETE ON termo_consentimento
    FOR EACH ROW EXECUTE FUNCTION ck_termo_imutavel();

-- Consentimento: só a revogação, uma vez.
CREATE OR REPLACE FUNCTION ck_consentimento_imutavel()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF pg_trigger_depth() > 1 THEN
            RETURN OLD;
        END IF;
        RAISE EXCEPTION 'consentimento nao pode ser apagado: registre a revogacao';
    END IF;
    IF OLD.revogado_em IS NOT NULL THEN
        RAISE EXCEPTION 'consentimento ja revogado';
    END IF;
    IF NEW.clinica_id <> OLD.clinica_id OR NEW.cliente_id <> OLD.cliente_id OR NEW.termo_id <> OLD.termo_id
       OR NEW.aceito_em <> OLD.aceito_em OR NEW.registrado_por_nome <> OLD.registrado_por_nome
       OR NEW.registrado_por_id IS DISTINCT FROM OLD.registrado_por_id THEN
        RAISE EXCEPTION 'consentimento nao muda: so pode ser revogado';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_consentimento_imutavel ON consentimento_paciente;
CREATE TRIGGER trg_consentimento_imutavel
    BEFORE UPDATE OR DELETE ON consentimento_paciente
    FOR EACH ROW EXECUTE FUNCTION ck_consentimento_imutavel();

-- Auditoria: só inserção.
CREATE OR REPLACE FUNCTION ck_auditoria_imutavel()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' AND pg_trigger_depth() > 1 THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'auditoria_acesso so aceita insercao';
END;
$$;

DROP TRIGGER IF EXISTS trg_auditoria_imutavel ON auditoria_acesso;
CREATE TRIGGER trg_auditoria_imutavel
    BEFORE UPDATE OR DELETE ON auditoria_acesso
    FOR EACH ROW EXECUTE FUNCTION ck_auditoria_imutavel();
