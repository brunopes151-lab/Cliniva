-- Sessão paga com pacote não soma de novo no faturamento: o dinheiro entra
-- na venda do pacote (pacote_cliente.valor_pago, na data da compra) e o
-- serviço do atendimento passa a valer zero na baixa. O movimento guarda qual
-- serviço foi zerado e o valor que ele tinha, para devolver no estorno.

ALTER TABLE pacote_movimento
    ADD COLUMN IF NOT EXISTS servico_id uuid,
    ADD COLUMN IF NOT EXISTS valor_cobrado numeric(10, 2);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_pm_servico') THEN
        ALTER TABLE pacote_movimento
            ADD CONSTRAINT fk_pm_servico FOREIGN KEY (servico_id) REFERENCES servico (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_pm_valor_cobrado') THEN
        ALTER TABLE pacote_movimento
            ADD CONSTRAINT ck_pm_valor_cobrado CHECK (valor_cobrado IS NULL OR valor_cobrado >= 0);
    END IF;
END;
$$;

-- Faturamento por dia: vendas de pacote pela data da compra.
CREATE INDEX IF NOT EXISTS idx_pacote_cliente_compra ON pacote_cliente (clinica_id, data_compra);

CREATE OR REPLACE FUNCTION ck_pacote_movimento_servico_mesma_clinica()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.servico_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM servico WHERE id = NEW.servico_id AND clinica_id = NEW.clinica_id) THEN
        RAISE EXCEPTION 'servico do movimento precisa ser da mesma clinica';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_pacote_movimento_servico_mesma_clinica ON pacote_movimento;
CREATE TRIGGER trg_pacote_movimento_servico_mesma_clinica
    BEFORE INSERT ON pacote_movimento
    FOR EACH ROW EXECUTE FUNCTION ck_pacote_movimento_servico_mesma_clinica();
