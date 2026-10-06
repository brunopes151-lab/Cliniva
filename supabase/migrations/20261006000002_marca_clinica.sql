-- =====================================================================
-- Marca configurável: logo da clínica e nome padrão neutro.
--
-- O nome já vive em clinica.nome; aqui entra a logo. Ela fica no próprio
-- banco como data URL (PNG, JPEG ou WebP, já reduzida pelo navegador),
-- para não depender de bucket de Storage nem de URL externa. SVG fica de
-- fora de propósito: pode carregar script.
--
-- A clínica padrão da migration 02 passa a se chamar "Minha Clínica",
-- só se ninguém a renomeou e se o nome estiver livre (nome é único).
--
-- Idempotente.
-- =====================================================================

ALTER TABLE clinica ADD COLUMN IF NOT EXISTS logo_data_url text;

ALTER TABLE clinica DROP CONSTRAINT IF EXISTS ck_clinica_logo;
ALTER TABLE clinica ADD CONSTRAINT ck_clinica_logo CHECK (
    logo_data_url IS NULL
    OR (length(logo_data_url) <= 400000
        AND logo_data_url ~ '^data:image/(png|jpeg|webp);base64,')
);

COMMENT ON COLUMN clinica.logo_data_url IS
    'Logo da clínica como data URL (png/jpeg/webp, até ~300 KB). NULL = símbolo neutro.';

UPDATE clinica
SET nome = 'Minha Clínica'
WHERE id = '00000000-0000-0000-0000-000000000001'
  AND nome = 'Clínica Padrão'
  AND NOT EXISTS (SELECT 1 FROM clinica WHERE nome = 'Minha Clínica');
