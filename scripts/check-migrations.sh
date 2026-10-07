#!/usr/bin/env bash
# =====================================================================
# Verifica as migrations contra um Postgres real subindo a aplicação
# com `ddl-auto=validate`.
#
# Por que isso existe: os testes unitários rodam em H2 com
# `ddl-auto=create-drop`, ou seja, o schema vem do Hibernate e as
# migrations SQL NUNCA são exercitadas. Divergência de tipo entre
# migration e entidade (ex.: smallint vs Integer) só aparece no
# deploy de produção — e o processo morre em silêncio, enquanto o
# health check continua respondendo 200 do container antigo.
#
# Uso (local):  scripts/check-migrations.sh
# Uso (CI):     postgres:16 como service + este script
# =====================================================================
set -euo pipefail

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-cliniva}"
DB_USER="${DB_USER:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-postgres}"
PORT_APP="${PORT_APP:-8099}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MIGRATIONS_DIR="$REPO_ROOT/supabase/migrations"
LOG_FILE="${LOG_FILE:-$REPO_ROOT/target/check-migrations.log}"

cd "$REPO_ROOT"

export PGPASSWORD="$DB_PASSWORD"
PSQL=(psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -q)

echo "==> Aguardando Postgres em $DB_HOST:$DB_PORT ..."
for _ in $(seq 1 60); do
    if "${PSQL[@]}" -c 'SELECT 1' >/dev/null 2>&1; then
        break
    fi
    sleep 1
done
"${PSQL[@]}" -c 'SELECT 1' >/dev/null 2>&1 || {
    echo "ERRO: Postgres não respondeu em $DB_HOST:$DB_PORT"
    exit 1
}
echo "    Postgres ok"

echo "==> Aplicando migrations em ordem"
count=0
for file in "$MIGRATIONS_DIR"/*.sql; do
    name="$(basename "$file")"
    echo "    -> $name"
    "${PSQL[@]}" -f "$file" >/dev/null
    count=$((count + 1))
done
echo "    $count migration(s) aplicada(s)"

# As migrations 01..05 são de criação (tabelas, seeds) e não são meant
# para rodar de novo. Só as de hardening (a partir da 06) se declaram
# idempotentes, porque existe o caminho de aplicação manual documentado
# no vault. Reaplicar só as últimas, e ignore o erro se não houver.
echo "==> Verificando idempotência das migrations de hardening"
mapfile -t hardening < <(printf '%s\n' "$MIGRATIONS_DIR"/*.sql | tail -n +6)
if [ "${#hardening[@]}" -gt 0 ]; then
    for file in "${hardening[@]}"; do
        "${PSQL[@]}" -f "$file" >/dev/null 2>&1 || {
            echo "ERRO: migration de hardening NÃO é idempotente: $(basename "$file")"
            exit 1
        }
        echo "    ok: $(basename "$file")"
    done
else
    echo "    (nenhuma migration de hardening)"
fi

echo "==> Conferindo as regras do banco (dentro de uma transação desfeita no fim)"
"${PSQL[@]}" <<'SQL' >/dev/null
BEGIN;
DO $$
DECLARE
    c uuid := gen_random_uuid();
    outra uuid := gen_random_uuid();
    ana uuid := gen_random_uuid();
    bia uuid := gen_random_uuid();
    vizinha uuid := gen_random_uuid();
    paciente uuid := gen_random_uuid();
    servico_regra uuid;
    pacote_regra uuid;
    pc_regra uuid;
    modelo_regra uuid := gen_random_uuid();
    registro_regra uuid := gen_random_uuid();
    autor_regra uuid := gen_random_uuid();
    termo_regra uuid := gen_random_uuid();
    termo_vizinho uuid := gen_random_uuid();
    consentimento_regra uuid := gen_random_uuid();
    sem_cadastro uuid := gen_random_uuid();
    recusou boolean;
BEGIN
    INSERT INTO clinica (id, nome, slug, ativa, criada_em) VALUES
        (c, 'Regras ' || c, 'regras-' || c, true, now()),
        (outra, 'Regras ' || outra, 'regras-' || outra, true, now());
    INSERT INTO profissional (id, clinica_id, nome) VALUES
        (ana, c, 'Ana'), (bia, c, 'Bia'), (vizinha, outra, 'Vizinha');
    INSERT INTO cliente (id, clinica_id, nome, telefone) VALUES (paciente, c, 'Paciente', '5511900000000');

    -- Ana 10:00-11:00; Bia no mesmo horário é permitido.
    INSERT INTO atendimento (id, clinica_id, cliente_id, profissional_id, data_criacao, data_atendimento, duracao_minutos, status)
    VALUES (gen_random_uuid(), c, paciente, ana, current_date, '2030-01-07 10:00', 60, 'AGENDADO'),
           (gen_random_uuid(), c, paciente, bia, current_date, '2030-01-07 10:00', 60, 'AGENDADO');
    -- Encostado (11:00) e cancelado sobreposto também são permitidos.
    INSERT INTO atendimento (id, clinica_id, cliente_id, profissional_id, data_criacao, data_atendimento, duracao_minutos, status)
    VALUES (gen_random_uuid(), c, paciente, ana, current_date, '2030-01-07 11:00', 30, 'AGENDADO'),
           (gen_random_uuid(), c, paciente, ana, current_date, '2030-01-07 10:30', 30, 'CANCELADO');

    recusou := false;
    BEGIN
        INSERT INTO atendimento (id, clinica_id, cliente_id, profissional_id, data_criacao, data_atendimento, duracao_minutos, status)
        VALUES (gen_random_uuid(), c, paciente, ana, current_date, '2030-01-07 10:30', 30, 'AGENDADO');
    EXCEPTION WHEN exclusion_violation THEN recusou := true;
    END;
    IF NOT recusou THEN RAISE EXCEPTION 'sobreposição do mesmo profissional foi aceita'; END IF;

    recusou := false;
    BEGIN
        INSERT INTO atendimento (id, clinica_id, cliente_id, profissional_id, data_criacao, data_atendimento, duracao_minutos, status)
        VALUES (gen_random_uuid(), c, paciente, vizinha, current_date, '2030-01-08 10:00', 30, 'AGENDADO');
    EXCEPTION WHEN raise_exception THEN recusou := true;
    END;
    IF NOT recusou THEN RAISE EXCEPTION 'atendimento com profissional de outra clínica foi aceito'; END IF;

    recusou := false;
    BEGIN
        INSERT INTO usuario (id, clinica_id, papel, ativo, email, criado_em)
        VALUES (gen_random_uuid(), c, 'PROFISSIONAL', true, 'sem-prof-' || c || '@exemplo.test', now());
    EXCEPTION WHEN check_violation THEN recusou := true;
    END;
    IF NOT recusou THEN RAISE EXCEPTION 'PROFISSIONAL sem profissional foi aceito'; END IF;

    recusou := false;
    BEGIN
        INSERT INTO usuario (id, clinica_id, papel, ativo, email, criado_em, profissional_id)
        VALUES (gen_random_uuid(), c, 'PROFISSIONAL', true, 'cruzado-' || c || '@exemplo.test', now(), vizinha);
    EXCEPTION WHEN raise_exception THEN recusou := true;
    END;
    IF NOT recusou THEN RAISE EXCEPTION 'usuário ligado a profissional de outra clínica foi aceito'; END IF;

    recusou := false;
    BEGIN
        INSERT INTO usuario (id, clinica_id, papel, ativo, email, criado_em)
        VALUES (gen_random_uuid(), c, 'GERENTE', true, 'papel-' || c || '@exemplo.test', now());
    EXCEPTION WHEN check_violation THEN recusou := true;
    END;
    IF NOT recusou THEN RAISE EXCEPTION 'papel desconhecido foi aceito'; END IF;

    -- Série: fim por data OU por quantidade, nunca os dois.
    recusou := false;
    BEGIN
        INSERT INTO serie_agendamento (id, clinica_id, cliente_id, profissional_id, frequencia, inicio, data_fim, quantidade)
        VALUES (gen_random_uuid(), c, paciente, ana, 'SEMANAL', '2030-01-07 10:00', '2030-03-01', 4);
    EXCEPTION WHEN check_violation THEN recusou := true;
    END;
    IF NOT recusou THEN RAISE EXCEPTION 'serie com data final e quantidade foi aceita'; END IF;

    -- Pacote: saldo nunca negativo e movimento só de inserção.
    INSERT INTO servico (id, clinica_id, nome, valor, duracao_minutos)
    VALUES (gen_random_uuid(), c, 'Servico regras ' || c, 100, 50) RETURNING id INTO servico_regra;
    INSERT INTO pacote (id, clinica_id, servico_id, nome, sessoes, validade_dias, preco)
    VALUES (gen_random_uuid(), c, servico_regra, 'Pacote regras', 2, 30, 200) RETURNING id INTO pacote_regra;
    INSERT INTO pacote_cliente (id, clinica_id, cliente_id, pacote_id, servico_id, nome, sessoes_total, saldo,
                                data_compra, data_validade, valor_pago)
    VALUES (gen_random_uuid(), c, paciente, pacote_regra, servico_regra, 'Pacote regras', 2, 0,
            current_date, current_date + 30, 200) RETURNING id INTO pc_regra;

    recusou := false;
    BEGIN
        UPDATE pacote_cliente SET saldo = saldo - 1 WHERE id = pc_regra;
    EXCEPTION WHEN check_violation THEN recusou := true;
    END;
    IF NOT recusou THEN RAISE EXCEPTION 'saldo negativo foi aceito'; END IF;

    INSERT INTO pacote_movimento (id, clinica_id, pacote_cliente_id, tipo) VALUES (gen_random_uuid(), c, pc_regra, 'BAIXA');
    recusou := false;
    BEGIN
        DELETE FROM pacote_movimento WHERE pacote_cliente_id = pc_regra;
    EXCEPTION WHEN raise_exception THEN recusou := true;
    END;
    IF NOT recusou THEN RAISE EXCEPTION 'movimento de pacote foi apagado'; END IF;

    -- Prontuário: versão não se altera nem se apaga; registro não se apaga.
    INSERT INTO usuario (id, papel, ativo, nome, email, criado_em, clinica_id)
    VALUES (autor_regra, 'OWNER', true, 'Autor', 'autor-' || autor_regra || '@exemplo.test', now(), c);
    INSERT INTO modelo_ficha (id, clinica_id, familia_id, versao, nome, area, tipo, campos)
    VALUES (modelo_regra, c, gen_random_uuid(), 1, 'Evolução', 'GERAL', 'EVOLUCAO',
            '[{"id":"descricao","rotulo":"Descrição","tipo":"TEXTO_LONGO","obrigatorio":true}]');
    INSERT INTO registro_clinico (id, clinica_id, cliente_id, profissional_id, tipo)
    VALUES (registro_regra, c, paciente, ana, 'EVOLUCAO');
    INSERT INTO registro_clinico_versao (clinica_id, registro_id, numero, modelo_ficha_id, conteudo, autor_id, autor_nome)
    VALUES (c, registro_regra, 1, modelo_regra, '{"descricao":"Sessão"}', autor_regra, 'Autor');

    recusou := false;
    BEGIN
        UPDATE registro_clinico_versao SET conteudo = '{"descricao":"Outra"}' WHERE registro_id = registro_regra;
    EXCEPTION WHEN raise_exception THEN recusou := true;
    END;
    IF NOT recusou THEN RAISE EXCEPTION 'versao de prontuario foi alterada'; END IF;

    recusou := false;
    BEGIN
        DELETE FROM registro_clinico_versao WHERE registro_id = registro_regra;
    EXCEPTION WHEN raise_exception THEN recusou := true;
    END;
    IF NOT recusou THEN RAISE EXCEPTION 'versao de prontuario foi apagada'; END IF;

    recusou := false;
    BEGIN
        DELETE FROM registro_clinico WHERE id = registro_regra;
    EXCEPTION WHEN raise_exception THEN recusou := true;
    END;
    IF NOT recusou THEN RAISE EXCEPTION 'registro de prontuario foi apagado'; END IF;

    recusou := false;
    BEGIN
        UPDATE registro_clinico SET cliente_id = cliente_id, profissional_id = bia WHERE id = registro_regra;
    EXCEPTION WHEN raise_exception THEN recusou := true;
    END;
    IF NOT recusou THEN RAISE EXCEPTION 'registro de prontuario mudou de profissional'; END IF;

    -- Correção sem motivo é recusada.
    recusou := false;
    BEGIN
        INSERT INTO registro_clinico_versao (clinica_id, registro_id, numero, modelo_ficha_id, conteudo, autor_id, autor_nome)
        VALUES (c, registro_regra, 2, modelo_regra, '{"descricao":"Sessão"}', autor_regra, 'Autor');
    EXCEPTION WHEN check_violation THEN recusou := true;
    END;
    IF NOT recusou THEN RAISE EXCEPTION 'correcao sem motivo foi aceita'; END IF;

    -- Modelo usado não muda os campos.
    recusou := false;
    BEGIN
        UPDATE modelo_ficha SET campos = '[]' WHERE id = modelo_regra;
    EXCEPTION WHEN raise_exception THEN recusou := true;
    END;
    IF NOT recusou THEN RAISE EXCEPTION 'campos de modelo de ficha foram alterados'; END IF;

    -- Registro de outra clínica é recusado.
    recusou := false;
    BEGIN
        INSERT INTO registro_clinico (clinica_id, cliente_id, profissional_id, tipo)
        VALUES (c, paciente, vizinha, 'EVOLUCAO');
    EXCEPTION WHEN raise_exception THEN recusou := true;
    END;
    IF NOT recusou THEN RAISE EXCEPTION 'registro com profissional de outra clinica foi aceito'; END IF;

    -- LGPD: termo publicado não muda; consentimento só se revoga, uma vez;
    -- auditoria só se insere.
    INSERT INTO termo_consentimento (id, clinica_id, versao, texto) VALUES
        (termo_regra, c, 1, 'Termo de teste'), (termo_vizinho, outra, 1, 'Termo da vizinha');
    INSERT INTO consentimento_paciente (id, clinica_id, cliente_id, termo_id, registrado_por_nome)
    VALUES (consentimento_regra, c, paciente, termo_regra, 'Recepção');
    INSERT INTO auditoria_acesso (clinica_id, cliente_id, usuario_nome, papel, acao)
    VALUES (c, paciente, 'Autor', 'OWNER', 'VER_PRONTUARIO');

    recusou := false;
    BEGIN
        UPDATE termo_consentimento SET texto = 'Outro' WHERE id = termo_regra;
    EXCEPTION WHEN raise_exception THEN recusou := true;
    END;
    IF NOT recusou THEN RAISE EXCEPTION 'termo publicado foi alterado'; END IF;

    recusou := false;
    BEGIN
        DELETE FROM termo_consentimento WHERE id = termo_regra;
    EXCEPTION WHEN raise_exception THEN recusou := true;
    END;
    IF NOT recusou THEN RAISE EXCEPTION 'termo publicado foi apagado'; END IF;

    recusou := false;
    BEGIN
        UPDATE consentimento_paciente SET aceito_em = now() - interval '1 day' WHERE id = consentimento_regra;
    EXCEPTION WHEN raise_exception THEN recusou := true;
    END;
    IF NOT recusou THEN RAISE EXCEPTION 'data do consentimento foi alterada'; END IF;

    recusou := false;
    BEGIN
        DELETE FROM consentimento_paciente WHERE id = consentimento_regra;
    EXCEPTION WHEN raise_exception THEN recusou := true;
    END;
    IF NOT recusou THEN RAISE EXCEPTION 'consentimento foi apagado'; END IF;

    UPDATE consentimento_paciente SET revogado_em = now(), revogado_por_nome = 'Recepção',
        motivo_revogacao = 'Pedido do paciente' WHERE id = consentimento_regra;
    recusou := false;
    BEGIN
        UPDATE consentimento_paciente SET motivo_revogacao = 'Outro' WHERE id = consentimento_regra;
    EXCEPTION WHEN raise_exception THEN recusou := true;
    END;
    IF NOT recusou THEN RAISE EXCEPTION 'revogacao foi alterada'; END IF;

    recusou := false;
    BEGIN
        INSERT INTO consentimento_paciente (clinica_id, cliente_id, termo_id, registrado_por_nome)
        VALUES (c, paciente, termo_vizinho, 'Recepção');
    EXCEPTION WHEN raise_exception THEN recusou := true;
    END;
    IF NOT recusou THEN RAISE EXCEPTION 'consentimento com termo de outra clinica foi aceito'; END IF;

    recusou := false;
    BEGIN
        UPDATE auditoria_acesso SET acao = 'CRIAR_REGISTRO' WHERE cliente_id = paciente;
    EXCEPTION WHEN raise_exception THEN recusou := true;
    END;
    IF NOT recusou THEN RAISE EXCEPTION 'auditoria foi alterada'; END IF;

    recusou := false;
    BEGIN
        DELETE FROM auditoria_acesso WHERE cliente_id = paciente;
    EXCEPTION WHEN raise_exception THEN recusou := true;
    END;
    IF NOT recusou THEN RAISE EXCEPTION 'auditoria foi apagada'; END IF;

    -- Excluir um paciente sem prontuário leva os consentimentos e deixa a auditoria.
    INSERT INTO cliente (id, clinica_id, nome, telefone) VALUES (sem_cadastro, c, 'Sai', '5511900000009');
    INSERT INTO consentimento_paciente (clinica_id, cliente_id, termo_id, registrado_por_nome)
    VALUES (c, sem_cadastro, termo_regra, 'Recepção');
    INSERT INTO auditoria_acesso (clinica_id, cliente_id, usuario_nome, papel, acao)
    VALUES (c, sem_cadastro, 'Autor', 'OWNER', 'EXPORTAR_DADOS');
    DELETE FROM cliente WHERE id = sem_cadastro;
    IF EXISTS (SELECT 1 FROM consentimento_paciente WHERE cliente_id = sem_cadastro) THEN
        RAISE EXCEPTION 'consentimento ficou sem paciente';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM auditoria_acesso WHERE cliente_id = sem_cadastro) THEN
        RAISE EXCEPTION 'auditoria sumiu com o paciente';
    END IF;
END $$;
ROLLBACK;
SQL
echo "    ok: sobreposição por profissional, mesma clínica, perfis, séries, saldo de pacote, prontuário imutável e LGPD"

echo "==> Conferindo que nenhuma tabela fica aberta para a API pública do Supabase"
sem_rls="$("${PSQL[@]}" -At -c "SELECT string_agg(c.relname, ', ') FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = 'public' AND c.relkind = 'r' AND NOT c.relrowsecurity")"
if [ -n "$sem_rls" ]; then
    echo "ERRO: tabela(s) sem RLS (ligue o RLS numa migration nova): $sem_rls"
    exit 1
fi
echo "    ok: RLS ligado em todas as tabelas"

echo "==> Buildando a aplicação"
mkdir -p "$(dirname "$LOG_FILE")"
(cd backend && ./mvnw -q -B -DskipTests package)

echo "==> Subindo a app com ddl-auto=validate contra o schema migrado"
SPRING_DATASOURCE_URL="jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_NAME" \
SPRING_DATASOURCE_USER="$DB_USER" \
SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
SERVER_PORT="$PORT_APP" \
    java -jar backend/target/app.jar >"$LOG_FILE" 2>&1 &
APP_PID=$!

cleanup() {
    if kill -0 "$APP_PID" 2>/dev/null; then
        kill "$APP_PID" 2>/dev/null || true
        wait "$APP_PID" 2>/dev/null || true
    fi
}
trap cleanup EXIT

echo "    aguardando a aplicação subir (timeout 180s) ..."
started=0
for _ in $(seq 1 180); do
    if ! kill -0 "$APP_PID" 2>/dev/null; then
        echo ""
        echo "ERRO: a aplicação MORREU durante o startup."
        echo "---- últimas linhas do log ----"
        tail -40 "$LOG_FILE"
        exit 1
    fi
    code="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$PORT_APP/actuator/health" 2>/dev/null || true)"
    if [ "$code" = "200" ]; then
        started=1
        break
    fi
    sleep 1
done

if [ "$started" != "1" ]; then
    echo ""
    echo "ERRO: a aplicação não respondeu 200 em /actuator/health no tempo esperado."
    echo "---- últimas linhas do log ----"
    tail -40 "$LOG_FILE"
    exit 1
fi

echo "    aplicação no ar"

echo "==> Conferindo rotas (o contrato do frontend)"
assert_status() {
    local method="$1" path="$2" expected="$3" desc="$4"
    local actual
    actual="$(curl -s -o /dev/null -w '%{http_code}' \
        -X "$method" "http://localhost:$PORT_APP$path" 2>/dev/null || echo 000)"
    if [ "$actual" != "$expected" ]; then
        echo "    ERRO: $method $path -> $actual (esperado $expected) — $desc"
        exit 1
    fi
    echo "    ok: $method $path -> $actual"
}

# 404 = controller existe, recurso não encontrado (rota pública funcionando)
assert_status GET  /api/public/booking/slug-inexistente/servicos 404 "booking público lê slug"
# 400 = controller existe e valida o corpo
assert_status POST /api/public/booking/slug-inexistente            400 "booking público valida payload"
# 401 = rota existe e exige autenticação
assert_status GET  /api/agenda/horarios                            401 "agenda exige auth"
assert_status GET  /api/profissionais                              401 "equipe exige auth"
assert_status GET  /api/usuarios                                   401 "usuários exigem auth"
assert_status GET  /api/pacotes                                    401 "pacotes exigem auth"
assert_status GET  /api/fichas/modelos                             401 "modelos de ficha exigem auth"
assert_status GET  /api/agenda/link                                401 "link público exige auth"
assert_status GET  /api/me                                         401 "perfil exige auth"
# 404 = onboarding público desligado por padrão (ONBOARDING_PUBLICO_ATIVO)
assert_status POST /api/public/onboarding                           404 "onboarding público desligado"
# 200 = marca pública (nome e logo da tela de login)
assert_status GET  /api/public/marca                                200 "marca pública"
# 200 = rota de build pública (é o que revela deploy falho)
assert_status GET  /api/saude/build                                200 "build info pública"

echo ""
echo "==> TUDO OK: migrations, schema e rotas conferidos."
