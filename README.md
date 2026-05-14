# GrupoLink — Backend

API REST da plataforma **GrupoLink**: SaaS para gerenciamento inteligente de grupos WhatsApp voltado a afiliados e vendedores de e-commerce.

Construído com **Kotlin**, **Spring Boot 3.2.5** e arquitetura **Clean Architecture**.

---

## Índice

- [Visão geral](#visão-geral)
- [Stack técnica](#stack-técnica)
- [Arquitetura](#arquitetura)
- [Pré-requisitos](#pré-requisitos)
- [Clone e configuração](#clone-e-configuração)
- [Variáveis de ambiente](#variáveis-de-ambiente)
- [Subindo a infraestrutura local](#subindo-a-infraestrutura-local)
- [Rodando a aplicação](#rodando-a-aplicação)
- [Rodando os testes](#rodando-os-testes)
- [Documentação da API](#documentação-da-api)
- [Estrutura de pastas](#estrutura-de-pastas)
- [Migrações de banco](#migrações-de-banco)
- [CI/CD](#cicd)
- [Deploy na AWS](#deploy-na-aws)

---

## Visão geral

O backend do GrupoLink expõe uma API REST que gerencia:

- **Autenticação** JWT + Google OAuth2
- **Estruturas e grupos** com algoritmo round-robin de 3 níveis para distribuição de membros
- **Redirecionamento inteligente** com cookies de sessão de 90 dias e rastreamento UTM
- **Criação automática de grupos** via RabbitMQ quando o threshold de capacidade é atingido
- **Mensagens agendadas** via WhatsApp Cloud API (Meta)
- **Assinaturas recorrentes** via Mercado Pago Preapproval
- **Analytics** de cliques, membros, churn e UTM por estrutura
- **Links curtos** rastreáveis
- **Rate limiting** via Redis para endpoints de redirecionamento
- **Exportação CSV** de membros e logs de redirecionamento

---

## Stack técnica

| Tecnologia | Versão | Uso |
|---|---|---|
| Kotlin | 1.9.23 | Linguagem principal |
| Spring Boot | 3.2.5 | Framework web |
| Spring Security | 6.2.x | Autenticação e autorização |
| Spring Data JPA | 3.2.x | ORM |
| PostgreSQL | 16 | Banco de dados principal |
| Flyway | 9.22.x | Migrações de schema |
| Redis | 7 | Sessões de cookie, round-robin atômico, rate limiting |
| RabbitMQ | 3.13 | Fila assíncrona de criação de grupos |
| Spring WebFlux | 6.1.x | Cliente HTTP reativo (WhatsApp Cloud API) |
| jjwt | 0.12.6 | Geração e validação de JWT (HMAC-SHA256) |
| springdoc-openapi | 2.5.0 | Swagger UI / OpenAPI 3 |
| Mercado Pago SDK | 2.1.22 | Assinaturas recorrentes |
| Gradle | 8.8 | Build tool |

---

## Arquitetura

O projeto segue os princípios de **Clean Architecture** com 4 camadas:

```
┌─────────────────────────────────────────────────────┐
│  interfaces/api         (Controllers, DTOs de entrada)│
├─────────────────────────────────────────────────────┤
│  application/usecase    (Casos de uso, lógica de negócio) │
├─────────────────────────────────────────────────────┤
│  domain/model + repository  (Entidades, interfaces)  │
├─────────────────────────────────────────────────────┤
│  infrastructure         (JPA, Redis, RabbitMQ, JWT)  │
└─────────────────────────────────────────────────────┘
```

### Algoritmo round-robin de 3 níveis

O coração do produto é o `ProcessRedirectUseCase`:

1. **Nível 1 — Retorno de visitante:** cookie Redis verifica se o visitante já tem grupo atribuído
2. **Nível 2 — Distribuição ativa:** enquanto os grupos não atingem o `fillThreshold` (padrão 80%), um grupo recebe o tráfego sequencialmente
3. **Nível 3 — Ativação automática:** ao atingir 80%, os 2 próximos grupos são ativados simultaneamente e o tráfego é distribuído entre 3 grupos via contador atômico Redis

---

## Pré-requisitos

| Ferramenta | Versão mínima | Verificação |
|---|---|---|
| Java JDK | 17 (Temurin) | `java --version` |
| Docker Desktop | 4.x | `docker --version` |
| Docker Compose | 2.x | `docker compose version` |
| Git | 2.x | `git --version` |

> O Gradle Wrapper (`./gradlew`) já está incluído — **não é necessário instalar o Gradle** manualmente.

---

## Clone e configuração

### 1. Clone o repositório

```bash
git clone https://github.com/achadostreinofofo/grupolink-backend.git
cd grupolink-backend
```

### 2. Copie o arquivo de variáveis de ambiente

```bash
cp .env.example .env
```

Edite o `.env` com suas configurações locais (veja a seção [Variáveis de ambiente](#variáveis-de-ambiente)).

---

## Variáveis de ambiente

O arquivo `.env.example` contém todas as variáveis com valores padrão para desenvolvimento local:

```env
# ── Banco de dados ──────────────────────────────────────────
DB_HOST=localhost
DB_PORT=5432
DB_NAME=whatsapp_saas
DB_USER=postgres
DB_PASSWORD=postgres

# ── Redis ───────────────────────────────────────────────────
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=          # vazio em desenvolvimento

# ── RabbitMQ ────────────────────────────────────────────────
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USER=guest
RABBITMQ_PASS=guest

# ── JWT (mínimo 256 bits — troque em produção) ──────────────
JWT_SECRET=mude-esse-secret-em-producao-use-256-bits-no-minimo

# ── URLs ────────────────────────────────────────────────────
BASE_URL=http://localhost:8080
FRONTEND_URL=http://localhost:3000

# ── WhatsApp Cloud API (Meta) ───────────────────────────────
WHATSAPP_API_VERSION=v19.0
WHATSAPP_APP_SECRET=               # App Secret do Meta Developer Portal
WHATSAPP_WEBHOOK_VERIFY_TOKEN=mude-este-token

# ── Mercado Pago ────────────────────────────────────────────
MP_ACCESS_TOKEN=                   # Access Token do Mercado Pago
MP_WEBHOOK_SECRET=

# ── Google OAuth2 (opcional — deixe vazio para desativar) ───
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
```

> **Atenção em produção:** nunca comite o arquivo `.env` com credenciais reais. Use AWS SSM Parameter Store (veja `.aws/task-definition.json`).

---

## Subindo a infraestrutura local

O `docker-compose.yml` sobe **PostgreSQL**, **Redis** e **RabbitMQ** sem subir a aplicação (que roda direto pelo Gradle, facilitando hot-reload):

```bash
docker-compose up -d
```

Aguarde os health checks passarem (~15s) e verifique:

```bash
docker-compose ps
```

Todos os serviços devem estar com status `healthy`.

### Serviços e portas

| Serviço | Porta | Credenciais padrão |
|---|---|---|
| PostgreSQL 16 | `5432` | `postgres / postgres` |
| Redis 7 | `6379` | sem senha |
| RabbitMQ 3.13 | `5672` (AMQP) | `guest / guest` |
| RabbitMQ Management UI | `15672` | `guest / guest` |

---

## Rodando a aplicação

### Opção A — Gradle (recomendado para desenvolvimento)

```bash
./gradlew bootRun
```

A aplicação inicia em `http://localhost:8080`.

O Flyway executa as migrações automaticamente na primeira inicialização (criando as 12 tabelas).

### Opção B — JAR compilado

```bash
# Compila o JAR
./gradlew bootJar

# Executa
java -jar build/libs/whatsapp-groups-saas-*.jar
```

### Opção C — Docker (imagem completa)

```bash
# Build da imagem
docker build -t grupolink-backend .

# Executa (requer a infraestrutura já rodando)
docker run \
  --network host \
  --env-file .env \
  -p 8080:8080 \
  grupolink-backend
```

### Verificando a inicialização

Após subir, acesse:

- **Health check:** http://localhost:8080/actuator/health → deve retornar `{"status":"UP"}`
- **Swagger UI:** http://localhost:8080/swagger-ui.html → documentação completa da API
- **OpenAPI JSON:** http://localhost:8080/v3/api-docs

---

## Rodando os testes

### Todos os testes

```bash
./gradlew test
```

### Com relatório HTML

```bash
./gradlew test jacocoTestReport
# Relatório em: build/reports/tests/test/index.html
```

### Configuração de testes

Os testes usam **H2 in-memory** (não precisam do Docker) configurado em `src/test/resources/application-test.yml`:

- Flyway desabilitado (schema criado pelo Hibernate DDL)
- RabbitMQ excluído do autoconfigure
- JWT secret e URLs fixos para CI

### Testes existentes

| Classe | O que testa |
|---|---|
| `ProcessRedirectUseCaseTest` | Algoritmo round-robin: novo visitante, retorno, sem grupos, threshold, cookie expirado |
| `WhatsappWebhookControllerTest` | Verificação de webhook, eventos de entrada/saída de membros |
| `RateLimitFilterTest` | Limite de requisições, TTL Redis, header X-RateLimit |

---

## Documentação da API

Com a aplicação rodando, acesse o **Swagger UI**:

```
http://localhost:8080/swagger-ui.html
```

Para autenticar os endpoints protegidos:
1. Clique em **Authorize** (cadeado no topo)
2. Faça POST em `/api/auth/signup` ou `/api/auth/login`
3. Cole o token retornado no campo **BearerAuth** → `Bearer {token}`

### Principais endpoints

| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/api/auth/signup` | Cadastrar usuário (nome, e-mail, CPF, senha) |
| POST | `/api/auth/login` | Login e obtenção do JWT |
| GET | `/api/auth/me` | Dados do usuário autenticado |
| GET | `/r/{slug}` | Redirecionamento inteligente (público) |
| GET | `/s/{code}` | Link curto (público) |
| GET/POST | `/api/structures` | Listar e criar estruturas |
| POST | `/api/structures/{id}/groups` | Adicionar grupo a uma estrutura |
| GET | `/api/analytics/overview` | Métricas gerais |
| GET | `/api/analytics/structures/{id}/utm` | Relatório UTM por estrutura |
| POST | `/api/subscriptions/checkout` | Iniciar checkout Mercado Pago |
| POST | `/api/webhooks/mercadopago` | Webhook de pagamento (público) |

---

## Estrutura de pastas

```
src/main/kotlin/com/whatsappgroups/
├── domain/
│   ├── model/              # Entidades JPA
│   │   ├── User.kt         # Plan enum: FREE, SMART, DIAMOND, BLACK
│   │   ├── Structure.kt    # slug único, fillThreshold
│   │   ├── WhatsappGroup.kt # GroupStatus: ACTIVE, FULL, INACTIVE, CREATING
│   │   ├── RedirectLog.kt  # cookieId + campos UTM
│   │   ├── ShortLink.kt    # código único, contador de cliques
│   │   └── ...
│   └── repository/         # Interfaces Spring Data JPA
│
├── application/
│   ├── dto/                # Data Transfer Objects (request/response)
│   └── usecase/
│       ├── auth/           # AuthUseCase, UserSettingsUseCase
│       ├── structure/      # StructureUseCase, GroupUpdateUseCase
│       ├── redirect/       # ProcessRedirectUseCase (round-robin)
│       ├── group/          # AutoCreateGroupUseCase
│       ├── analytics/      # AnalyticsUseCase
│       ├── message/        # ScheduledMessageUseCase, MessageTemplateUseCase
│       ├── shortlink/      # ShortLinkUseCase
│       ├── payment/        # CheckoutUseCase, PaymentWebhookUseCase
│       ├── whatsapp/       # WhatsappAccountUseCase, SendScheduledMessagesUseCase
│       └── export/         # ExportMembersUseCase (CSV)
│
├── infrastructure/
│   ├── security/           # JwtTokenProvider, JwtAuthenticationFilter, SecurityConfig
│   ├── redis/              # RedisSessionService (cookies + round-robin atômico)
│   ├── messaging/          # RabbitMQ config, publisher, consumer
│   ├── payment/            # MercadoPagoService, MercadoPagoConfig
│   ├── whatsapp/           # WhatsappCloudApiClient (WebClient)
│   ├── oauth2/             # GoogleOAuth2Config, OAuth2SuccessHandler
│   ├── ratelimit/          # RateLimitFilter (@Order(1))
│   └── config/             # OpenApiConfig, CorsConfig
│
└── interfaces/
    └── api/                # REST Controllers + GlobalExceptionHandler
```

---

## Migrações de banco

As migrações Flyway estão em `src/main/resources/db/migration/` e são aplicadas automaticamente:

| Versão | Descrição |
|---|---|
| V1 | Tabela `users` (plan, whatsapp_integrated) |
| V2 | Tabela `structures` (slug único, fill_threshold) |
| V3 | Tabela `whatsapp_groups` (status, sort_order) |
| V4 | Tabela `redirect_logs` (cookie_id, UTM fields, índices) |
| V5 | Tabela `scheduled_messages` (status, scheduled_at) |
| V6 | Tabela `whatsapp_accounts` |
| V7 | Tabela `subscriptions` (mercado_pago_id único) |
| V8 | Tabela `blacklist` (índice único owner+phone) |
| V9 | Tabela `group_members` (source, joined_at, left_at) |
| V10 | Tabela `short_links` (código único) |
| V11 | Tabela `message_templates` |
| V12 | Coluna `cpf` em `users` (nullable, índice único parcial) |

---

## CI/CD

O pipeline é executado automaticamente em **push** e **pull requests** para `master` e `develop`.

### Jobs obrigatórios (bloqueiam merge se falharem)

| Job | O que faz |
|---|---|
| `test` | Testes unitários e de integração com PostgreSQL 16 e Redis 7 reais (via Docker services do GitHub Actions) |
| `static-analysis` | Análise estática CodeQL para Kotlin/Java (queries `security-and-quality`) |
| `security` | Trivy filesystem scan para CVEs críticos/altos + geração de SBOM (CycloneDX) |

### Deploy automático (CD)

Acionado em push para `master`:

1. Compila o JAR com `./gradlew bootJar`
2. Faz login no Amazon ECR
3. Build e push da imagem Docker com a tag do commit SHA
4. Atualiza a task definition do ECS com a nova imagem
5. Deploya no ECS Fargate aguardando estabilização

Configure os secrets no repositório (veja `SECRETS.md`).

---

## Deploy na AWS

A infraestrutura é provisionada via **AWS CloudFormation** em `.aws/cloudformation/`:

```bash
# 1. VPC, subnets, security groups
aws cloudformation deploy \
  --template-file .aws/cloudformation/01-vpc.yml \
  --stack-name grupolink-vpc \
  --capabilities CAPABILITY_NAMED_IAM

# 2. RDS PostgreSQL 16 (Multi-AZ em prod)
aws cloudformation deploy \
  --template-file .aws/cloudformation/02-rds.yml \
  --stack-name grupolink-rds \
  --parameter-overrides DBPassword=<senha-segura>

# 3. ElastiCache Redis 7
aws cloudformation deploy \
  --template-file .aws/cloudformation/03-elasticache.yml \
  --stack-name grupolink-cache

# 4. Amazon MQ RabbitMQ 3.13
aws cloudformation deploy \
  --template-file .aws/cloudformation/04-amazonmq.yml \
  --stack-name grupolink-mq \
  --parameter-overrides RabbitMQPassword=<senha-segura>

# 5. ECS Cluster, ALB, ECR, IAM roles, Task Definition e Service
aws cloudformation deploy \
  --template-file .aws/cloudformation/05-ecs.yml \
  --stack-name grupolink-ecs \
  --capabilities CAPABILITY_NAMED_IAM
```

Após o provisionamento, carregue os secrets no SSM Parameter Store:

```bash
aws ssm put-parameter --name "/grupolink/DB_HOST"       --value "<endpoint-rds>"     --type SecureString
aws ssm put-parameter --name "/grupolink/DB_PASSWORD"   --value "<senha>"             --type SecureString
aws ssm put-parameter --name "/grupolink/JWT_SECRET"    --value "<secret-256-bits>"   --type SecureString
aws ssm put-parameter --name "/grupolink/MP_ACCESS_TOKEN" --value "<token-mp>"         --type SecureString
# ... (demais parâmetros listados em SECRETS.md)
```

---

## Licença

Projeto acadêmico — PosTech FIAP. Todos os direitos reservados.
