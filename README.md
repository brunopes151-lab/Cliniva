# Cliniva

Sistema de gestão para clínica de estética de pequeno porte —
agenda, clientes, serviços, controle de estoque e financeiro.

## Origem

Este repositório é uma adaptação do [Cliniva](https://github.com/paulovpinheiroo/cliniva),
de Paulo Victor Pinheiro, distribuído sob a licença MIT (ver [`LICENSE`](LICENSE)).

## Contexto

Desenvolvido para resolver um problema real (sistema de gestão
acessível pra pequenos negócios) e como projeto de evolução técnica
em backend (Java/Spring Boot) e frontend (React).

## Stack

- **Backend:** Java 21, Spring Boot 4.1, PostgreSQL, Maven
- **Frontend:** React 19, TypeScript, Vite, Tailwind CSS
- **Auth:** Supabase (Auth — JWT validado via JWKS na API)
- **Testes backend:** JUnit 5 + Mockito (H2 em memória)
- **Deploy:** Render (backend) · Vercel (frontend) · Supabase (banco + auth)

## Estrutura do repositório

```
cliniva/
├── backend/       API REST — Java / Spring Boot / PostgreSQL / Maven
│   ├── README.md        Guia completo do backend (PT)
│   └── README.en.md     Technical guide (EN)
├── frontend/      SPA — React + TypeScript + Vite + Tailwind
│   ├── README.md        Guia completo do frontend (PT)
│   └── README.en.md     Technical guide (EN)
├── README.md
└── EDR.png        Diagrama de entidade-relacionamento
```

> Guias detalhados: [backend/README.md](backend/README.md) ·
> [backend/README.en.md](backend/README.en.md) ·
> [frontend/README.md](frontend/README.md) ·
> [frontend/README.en.md](frontend/README.en.md)

## Como rodar localmente

### 1. Requisitos

- Java 21
- Maven 3.9+
- Node.js 20+ (npm)
- Supabase CLI ([instalação](https://supabase.com/docs/guides/cli)) + Docker

### 2. Banco de dados

O schema é versionado em `supabase/migrations/` (Supabase Migrations) e o
backend roda com `ddl-auto=validate` — quem cria o schema é a migração,
não o Hibernate.

Suba o ambiente local do Supabase (Postgres em `localhost:54322`), que
aplica as migrations automaticamente:

```bash
supabase start
```

As credenciais da API/Supabase local ficam em `supabase status`.
Para apontar o backend para o banco local, use:

```bash
SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:54322/postgres' \
SPRING_DATASOURCE_USER=postgres \
SPRING_DATASOURCE_PASSWORD=postgres
```

> Alternativa: `supabase link --project-ref <ref>` + `supabase db push`
> para aplicar as migrations no projeto remoto do Supabase.

### 3. Backend (API em `http://localhost:8080`)

```bash
cd backend
mvn spring-boot:run
```

A migration `20260909000003_seed_administracao.sql` cria a **Clínica
Padrão**. O ADMIN que ela também criava é desativado pela migration
`20261006000001_neutralizar_seed_admin.sql`: o administrador da plataforma
(painel `/admin`) é criado na subida do backend a partir de
`ADMIN_BOOTSTRAP_EMAIL`. Crie a conta com o mesmo e-mail no Supabase
(Authentication → Users) e entre por `/login-admin`.

Executar os testes (109 unit/integration tests, usa H2 em memória — não precisa de banco):

```bash
cd backend
mvn test
```

Detalhes completos (endpoints, regras de domínio, erros):
[`backend/README.md`](backend/README.md).

### 4. Frontend (UI em `http://localhost:5173`)

```bash
cd frontend
cp .env.example .env   # preencha VITE_SUPABASE_URL e VITE_SUPABASE_ANON_KEY
npm install
npm run dev
```

O Vite encaminha `/api/*` para o backend (`localhost:8080`) durante o
desenvolvimento. Com auth ativo, todas as rotas exigem login — crie uma
clínica e o responsável pelo painel `/admin` (o auto-cadastro em `/cadastro`
fica desligado, ver `ONBOARDING_PUBLICO_ATIVO`).

Detalhes completos (estrutura, tema, animações, como criar páginas):
[`frontend/README.md`](frontend/README.md).

### 4.5. Alternativa: tudo em containers (Docker Compose)

Sobe o Postgres local (as migrations de `supabase/migrations/` são aplicadas
automaticamente na primeira subida), o backend e o frontend:

```bash
docker compose up -d --build   # db :5433, api :8080, ui :5173
docker compose stop            # pausa sem perder os dados
docker compose down            # remove os containers (os dados ficam no volume)
```

A porta `5433` do Postgres do compose evita conflito com um Postgres já rodando
no host (5432).

Requisitos: `backend/.env.local` e `frontend/.env` preenchidos — o login/auth
continuam usando o Supabase remoto, só o banco de dados é local. Para o código
ser reavaliado depois de mudanças, use `docker compose up -d --build` de novo.

> Com LLM no resumo do dia: exporte `LLM_PROVIDER` e `LLM_GEMINI_API_KEY`
> (ou `LLM_GROQ_API_KEY`) no serviço `backend` do `docker-compose.yml`.

O administrador criado por `ADMIN_BOOTSTRAP_EMAIL` funciona sempre em
`/admin`, onde se cria a clínica e o responsável.

### 5. Variáveis de ambiente do backend

| Variável | Uso |
|----------|-----|
| `SPRING_DATASOURCE_URL` / `_USER` / `_PASSWORD` | Conexão com o PostgreSQL |
| `SUPABASE_URL` | URL base do projeto Supabase (JWKS + admin API) |
| `SUPABASE_SERVICE_ROLE_KEY` | Service role key (criar usuários/reset de senha) |
| `SUPABASE_JWT_SECRET` | Segredo do JWT (fallback HS256) |
| `CLINIVA_CORS_ORIGIN` | Origem permitida no CORS (default `http://localhost:5173`) |
| `ADMIN_BOOTSTRAP_EMAIL` | E-mail do administrador da plataforma, criado na subida se não existir |
| `ONBOARDING_PUBLICO_ATIVO` | `true` liga o auto-cadastro público de clínica. Padrão `false` |
| `LLM_PROVIDER` | `gemini` ou `groq`. **Vazio = nenhuma chamada externa** (resumo por template) |
| `LLM_GEMINI_API_KEY` | Chave da API Gemini (só se `LLM_PROVIDER=gemini`) |
| `LLM_GEMINI_MODEL` | Default `gemini-3.5-flash-lite` |
| `LLM_GROQ_API_KEY` | Chave da API Groq (só se `LLM_PROVIDER=groq`) |
| `LLM_GROQ_MODEL` | Default `openai/gpt-oss-20b` |

> Os defaults de modelo são conferidos contra a documentação oficial dos
> providers. Se trocar, confira a lista atual antes: um nome de modelo
> inválido faz **toda** chamada falhar, e o resumo cai no template sem
> erro visível na tela — [Gemini](https://ai.google.dev/gemini-api/docs/models) ·
> [Groq](https://console.groq.com/docs/models).

## API — visão geral

| Recurso | Endpoints |
|---------|-----------|
| Clientes | `GET/POST /api/clientes` · `GET/PUT/DELETE /api/clientes/{id}` · busca por `?nome=`, `?status=` |
| Cliente · CRM | `GET /api/clientes/{id}/historico` · `GET/POST /api/clientes/{id}/notas` · `DELETE /api/clientes/{id}/notas/{notaId}` · `GET /api/clientes/aniversariantes?mes=` |
| Serviços | `GET/POST /api/servicos` · `GET/PUT/DELETE /api/servicos/{id}` (campo `duracaoMinutos`, 1–1440) |
| Itens/Estoque | `GET/POST /api/items` · `GET/PUT/DELETE /api/items/{id}` · `PATCH /api/items/{id}/estoque` |
| Atendimentos | `GET/POST /api/atendimentos` · `GET/PUT /api/atendimentos/{id}` · `PATCH /{id}/status` |
| Agenda (autenticada) | `GET /api/agenda?data=` · `GET /api/agenda/link` · `GET /api/agenda/disponibilidade?data=&servicoId=` · `GET/PUT /api/agenda/horarios` |
| Booking público | `GET /api/public/booking/{slug}/servicos` · `GET /api/public/booking/{slug}/disponibilidade?data=&servicoId=` · `POST /api/public/booking/{slug}` |
| Resumo do dia | `GET /api/resumo-do-dia` (limite de 5 gerações/dia/clínica) |

Filtros de atendimento: `?status=`, `?clienteId=`, `?dataInicio=`, `?dataFim=`.

### Agenda — regras

- Todo agendamento (público ou interno) respeita o **expediente por clínica**
  (`horario_atendimento`) e a **janela de 30 dias** (hoje … hoje+30).
- Datas passadas são recusadas.
- Sobreposição de horários é bloqueada sob **lock pessimista por clínica**
  (`SELECT ... FOR UPDATE`); o banco também recusa estoque negativo.
- A duração do atendimento é a **soma das durações dos serviços** (snapshot).
- O booking público só funciona para clínicas **ativas** e tem **rate limit**
  (5 tentativas por clínica+telefone+IP a cada 10 min).
- O link público de uma clínica é `https://<frontend>/agendar/<slug>`.

Erros seguem o formato `{"status", "mensagem", "erros"}` (400/401/403/404/409/429/503).

## Deploy (v0.3.0)

### Supabase (banco + auth)

1. Crie um projeto no [Supabase](https://supabase.com).
   Para dados de saúde (LGPD), escolha a região **South America (São Paulo)**.
2. **Migrations em produção**: neste fork nada é aplicado automaticamente.
   Configure o secret `PRODUCTION_DATABASE_URL` no GitHub e rode o workflow
   `migrations` à mão (Actions → migrations → Run workflow): desmarcado ele
   só mostra o que seria aplicado; marcando "Aplicar" ele aplica.
   - ⚠️ Migrations são aplicadas **na ordem do timestamp** e **não devem ser
     editadas depois de aplicadas**: corrija sempre criando a próxima.
   - Banco novo por SQL manual: prefira aplicar `supabase/migrations/` em
     ordem. `supabase/schema.sql` consolida só até a 07.
3. Em **Project Settings → API** copie: URL do projeto, `anon key`,
   `service_role key` e **Project Settings → Database → Connection URI`.
4. Driver JDBC: `jdbc:postgresql://db.<ref>.supabase.co:5432/postgres?sslmode=require`
   (usuário `postgres` e a senha do banco).

> **Ordem obrigatória no deploy:** primeiro aplique as migrations (workflow
> `migrations`), **depois** o merge em `main` — o backend sobe com
> `ddl-auto=validate` e exige o schema já existente. Inverter a ordem derruba
> o backend.
>
> **Docker Compose com banco já existente:** as migrations só rodam com o
> volume vazio. Para um ambiente já iniciado, aplique na mão:
>
> ```bash
> docker compose exec -T db psql -U postgres -d cliniva \
>   < supabase/migrations/20260909000006_agenda_hardening.sql
> # ou recrie do zero (APAGA OS DADOS):
> docker compose down -v && docker compose up -d
> ```

### Backend (Render)

O deploy é declarado via `render.yaml` (Blueprint, raiz do repo). Push na
`main` → Render reconstrói o Docker (`backend/Dockerfile`) e publica.

```yaml
# render.yaml (versão resumida — ver arquivo real)
services:
  - type: web
    name: minha-clinica-backend  # URL: a que o Render gerar para o seu serviço
    runtime: docker
    rootDir: backend
    plan: free
    healthCheckPath: /actuator/health
```

Secrets preenchidos no painel do serviço (Environment), **fora do git**:
`SPRING_DATASOURCE_URL/USER/PASSWORD`, `SUPABASE_URL`,
`SUPABASE_JWT_SECRET`, `SUPABASE_SERVICE_ROLE_KEY`, `ADMIN_BOOTSTRAP_EMAIL` e
`CLINIVA_CORS_ORIGIN` (o domínio exato do seu frontend, nunca um curinga).
Fixas via render.yaml: `PORT=8080`, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=50`.

> **Pooler IPv4 (obrigatório no Render):** a conexão direta do Supabase é
> IPv6-only e o free do Render não tem IPv6. Use o Shared Pooler (session,
> porta 5432): `jdbc:postgresql://aws-0-us-west-2.pooler.supabase.com:5432/postgres`
> com usuário `postgres.<project-ref>` (troque a região pela do seu projeto).
>
> **Free tier dorme:** o pinger do UptimeRobot (health a cada 10 min)
> mantém a instância acordada.

Health check: `GET https://<seu-backend>/actuator/health`. Para conferir se
o commit no ar é o esperado: `API=https://<seu-backend> scripts/check-deploy.sh`.

> ⚠️ O nome do serviço no painel do Render e a URL gerada podem ser
> diferentes. O que vale é o **log do deploy**: se o processo morre, o Render continua
> servindo o container antigo e o health check continua respondendo 200.

### Frontend (Vercel)

1. Importe o repositório na Vercel (framework detectado: Vite), `dist` de saída.
2. Configure as variáveis `VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY` e
   `VITE_API_URL` (URL do seu backend terminando em `/api`).
3. Adicione o domínio da Vercel em `CLINIVA_CORS_ORIGIN` do backend.

O arquivo `vercel.json` faz o rewrite SPA para `index.html`.

Link público de booking de uma clínica: `https://<seu-frontend>/agendar/<slug>`.

### CI

GitHub Actions: `Maven test` (backend), `oxlint` + `vite build` (frontend);
o deploy do backend é do Render (Blueprint, push na `main`), o do frontend
da Vercel — nenhum segredo de deploy é necessário no GitHub.

## Status

🚀 **v0.4.0** — Agenda: expediente por clínica, duração de serviço com
snapshot no atendimento, validação de conflitos, agenda interna e booking
público por link.

### v0.4.0 · Agenda

- **Expediente por clínica** (dia da semana + abertura/fechamento), editável
  em `/agenda`; padrão seg–sáb 08:00–18:00 semeado em toda clínica nova.
- **Duração de serviço** (1–1440 min) propagada como snapshot no atendimento;
  o fim do atendimento é exibido na grade.
- **Validação de conflito** sob lock pessimista por clínica: dois
  agendamentos simultâneos no mesmo horário não passam. O banco também recusa
  estoque negativo.
- **Janela única** para os dois fluxos: só entre hoje e hoje+30, nunca no
  passado, sempre dentro do expediente.
- **Agenda interna** (`/agenda`): grade diária, criar, remarcar, concluir,
  cancelar, configurar expediente e copiar o link público.
- **Booking público** (`/agendar/<slug>`): sem login, escolhe serviço/dia/
  horário, cria ou reutiliza o cliente pelo telefone, origem `ONLINE`.
  clinics inativas saem do ar; rate limit por clínica+telefone+IP.
- **Provisioning único de clínica**: onboarding e painel admin geram slug
  público e expediente do mesmo jeito (antes o admin deixava a clínica sem
  link funcional).
- **168 testes backend verdes.**

### v0.3.0 · Auth, Admin & Deploy

- **Autenticação JWT** validada no backend via JWKS do Supabase
  (o ADMIN master do seed é vinculado pelo e-mail no primeiro login)
  (`/api/public` aberto, `/api/**` autenticado, `/api/admin/**` somente ADMIN).
- **Multi-tenant por clínica**: todos os recursos escopados pelo dono; o
  ADMIN acessa qualquer clínica em **modo suporte** (header `X-Clinica`).
- **Onboarding e admin**: `/api/public/onboarding` (auto-registro),
  `/api/admin/*` (listar/criar/detalhar/atualizar clínicas, responsáveis,
  reset de senha e métricas — clientes, atendimentos, receita).
- **Frontend**: `/login`, `/cadastro`, `/trocar-senha`, `/admin` com
  guardas de rota; token anexado automaticamente (`Bearer`) e modo suporte
  no painel admin.
- **Deploy**: backend no Render (free + pinger UptimeRobot), frontend na
  Vercel, banco/auth no Supabase, CI no GitHub Actions.
- 109 testes backend verdes.

### v0.2.0 · CRM

- Perfil completo do cliente: data de nascimento, origem
  (indicação/Instagram/Google/passou na rua), canal preferido,
  preferências e observações; status prospect/ativo/inativo.
- **Histórico financeiro** por cliente: atendimentos, gasto acumulado,
  ticket médio, última visita e frequência (`GET /clientes/{id}/historico`).
- **Anotações** por cliente (listar/adicionar/excluir) e bloqueio de
  exclusão de cliente com vínculos.
- **Aniversariantes do mês** (`GET /clientes/aniversariantes?mes=`) e
  selo de fidelidade na lista (novo / recorrente / frequente).
- **WhatsApp**: follow-up na ficha do cliente e lembretes de
  atendimento agendado (link `wa.me` pré-preenchido, sem custo).
- Página **/clientes/:id** com ficha completa, financeiro, histórico e
  anotações (responsiva).
- Dashboard: **novos vs recorrentes** no mês e aniversariantes (com
  botão de saudação via WhatsApp).
- 79 testes backend verdes (unidade + integração de repositório).

### v0.1.1 · responsividade mobile

- Navegação por **drawer** no mobile (rail carbon off-canvas, botão `[ menu ]`
  no cabeçalho; fecha ao navegar / `Escape` / clique no overlay).
- Listas de registro viram **cards** em telas < `md` (Clientes, Serviços,
  Estoque, Atendimentos) — tabelas preservadas no desktop.
- Áreas de toque maiores (botões e campos), `PageHeader`, modais e
  cabeçalho/rodapé adaptados por breakpoint.

## Roadmap

### v0.3.0 · Deploy & Auth ✅

- [x] Autenticação/login via Supabase (JWT validado via JWKS)
- [x] Multi-tenant por clínica + painel admin com modo suporte
- [x] Deploy backend (Render) + frontend (Vercel) + banco (Supabase)

### MVP Backend ✅

- [x] Modelagem de entidades (ERD)
- [x] Setup do projeto (Spring Initializr, Postgres)
- [x] Domínio Cliente (entity, repository, service, controller)
- [x] Domínio Serviço
- [x] Domínio Item (estoque)
- [x] Domínio Atendimento
- [x] AtendimentoServico / AtendimentoItem
- [x] Endpoints de leitura (listagem, busca por id, filtros por query param)
- [x] Validações (Bean Validation)
- [x] Tratamento global de exceptions
- [x] CRUD completo (update/delete) e mudança de status do Atendimento
- [x] Testes unitários (109 testes, suite verde)

### MVP Frontend ✅

- [x] Setup Vite + React + TypeScript + Tailwind (palette da marca)
- [x] Layout com sidebar e navegação
- [x] CRUD de Clientes (com busca por nome)
- [x] CRUD de Serviços
- [x] CRUD de Itens + movimentação de estoque
- [x] Atendimentos: filtros, agendamento (serviços + itens extras), mudança de status
- [x] Dashboard (contagens, próximos atendimentos, estoque baixo)

### Deploy

- [x] Auth/login (Supabase)
- [x] Hospedagem: backend Render + frontend Vercel + banco Supabase
- [x] Deploy backend + banco na nuvem
- [x] Agenda por clínica + booking público (v0.4.0)
- [ ] PWA / instalação em home screen

## Ideias futuras (fora do escopo do MVP)

- Integração com IA (a definir o caso de uso específico —
  sugestão de horários, previsão de estoque, resumo do dia, etc.)

## Modelagem

![alt text](EDR.png)