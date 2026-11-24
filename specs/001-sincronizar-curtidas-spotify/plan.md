# Implementation Plan: Conectar Conta e Sincronizar Músicas Curtidas do Spotify

**Branch**: `001-sincronizar-curtidas-spotify` | **Date**: 2026-11-24 | **Spec**: [`spec.md`](spec.md)

**Input**: Feature specification from `specs/001-sincronizar-curtidas-spotify/spec.md`

---

## Summary

Implementação do backend da aplicação para conexão com a conta do Spotify via OAuth 2.0 Authorization Code com PKCE (sem client secret) e sincronização incremental sob demanda das músicas curtidas para banco SQLite local em arquitetura hexagonal leve. O sistema consome a API do Spotify (`/v1/me/tracks`) com paginação resiliente, ignorando arquivos locais, tratando respostas HTTP 429 de acordo com o cabeçalho `Retry-After`, e mantendo a sessão do usuário persistida no SQLite entre reinicializações. As consultas posteriores às faixas operam estritamente sobre a base local sem chamadas ao Spotify.

---

## Technical Context

**Language/Version**: Java 21 LTS (`21.0.5`)  
**Primary Dependencies**: Spring Boot `3.4.0` (`spring-boot-starter-web`, `spring-boot-starter-jdbc`), Flyway Core (`flyway-core` 10.x), SQLite JDBC (`sqlite-jdbc` 3.47.x).  
**Status de Suporte do Spring Boot 3.4.0**:
- Lançamento: 21 de novembro de 2024.
- Suporte Comunitário (OSS): Ativo até 24 de novembro de 2025.
- Suporte Comercial estendido: Até novembro de 2027.
- Justificativa da versão: Linha moderna e estável com suporte oficial nativo a Java 21 LTS e Spring Framework 6.2.
**Build Tool**: Apache Maven `3.9.9`.  
**Imagens Docker Fixadas**:
- Builder: `maven:3.9.9-eclipse-temurin-21`
- Runtime: `eclipse-temurin:21.0.5_11-jre-jammy`
**Storage**: SQLite 3 persistido em arquivo local dentro do volume Docker nomeado (`playlistmaker_sqlite_data`), com migrações gerenciadas pelo Flyway e acesso a dados via `JdbcClient`.  
**Testing**: JUnit 5, AssertJ, Mockito, WireMock (para simulação dos endpoints do Spotify com fixtures reais), `OutputCaptureExtension` para validação de segurança de logs (`SegurancaLogsTest`) e JaCoCo para controle estrito de cobertura.  
**Exclusões Explícitas da Medição do JaCoCo (Princípio VIII)**:
1. `br.com.playlistmaker.PlaylistMakerApplication`: Classe principal de bootstrap do Spring Boot (invocação estática `SpringApplication.run`).
2. `br.com.playlistmaker.adaptadores.saida.persistencia.sqlite.ConfiguracaoBancoDados`: Classe `@Configuration` pura do Spring responsável pela instanciação de beans do SQLite.
3. `br.com.playlistmaker.adaptadores.saida.spotify.ConfiguracaoSpotifyClient`: Classe `@Configuration` pura do Spring configurando a URL base e o client HTTP do `RestClient`.
4. `br.com.playlistmaker.adaptadores.entrada.rest.dto.*`: Records/DTOs imutáveis de entrada e saída da API REST sem nenhuma lógica de negócio ou ramificação condicional.
5. `br.com.playlistmaker.adaptadores.saida.spotify.dto.*`: Records/DTOs imutáveis de desserialização JSON das respostas do Spotify sem regras de negócio.
**Target Platform**: Linux Container via Docker Compose, com publicação restrita ao loopback do host (`127.0.0.1:8888:8888`).  
**Project Type**: Backend Web Service (API REST local em arquitetura hexagonal leve).  
**Modelo de Execução da Sincronização**: Assíncrona em segundo plano via `POST /api/sincronizacao` (HTTP 202 Accepted). Concorrência controlada por lock exclusivo na JVM (`ReentrantLock` com `tryLock()`) e status `EM_ANDAMENTO` na tabela `registro_sincronizacao`, rejeitando chamadas simultâneas com HTTP 409 Conflict. A interface consulta o progresso em tempo real via polling em `GET /api/sincronizacao/status`.  
**Histórico de Sincronizações**: Persistido de forma estruturada na tabela `registro_sincronizacao` em estrito atendimento aos requisitos **FR-010** e **FR-011** do `spec.md`.  
**Performance Goals**: Sincronização inicial de biblioteca com 700 faixas em menos de 60 segundos; sincronização incremental subsequente sem alterações em menos de 10 segundos.  
**Constraints**: Escopos mínimos (`user-library-read`, `playlist-modify-private`), sem client secret, redirect obrigatório em `127.0.0.1`, isolamento de chamadas externas atrás de interfaces do domínio, logs sem tokens ou segredos, tratamento de conta sem Spotify Premium (`FR-017`), zero chamadas externas em consultas de faixas.  
**Scale/Scope**: Usuário único, bibliotecas com 700 ou mais músicas curtidas.

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] **I. Uso pessoal e local**:
  - *Evidência*: Aplicação monousuário rodando localmente sem login próprio, telemetria ou multi-inquilino (`plan.md` Technical Context: "Scale/Scope: Usuário único"; `spec.md` "Como único usuário da aplicação..."; `data-model.md` seção 2: tabela `sessao_usuario` com restrição `CHECK (id = 1)`). Tratamento de conta sem plano Spotify Premium ativo adicionado especificamente conforme restrições do modo de desenvolvimento da API (`spec.md` FR-017; `research.md` seção 1.5).
- [x] **II. Segurança e privacidade por padrão**:
  - *Evidência de Loopback*: Porta publicada no host estritamente em loopback (`docker-compose.yml`: `127.0.0.1:8888:8888`; `contracts/auth-api.yaml`: `url: http://127.0.0.1:8888`; `quickstart.md` seção 3).
  - *Evidência de OAuth PKCE sem client secret*: `spec.md` FR-001; `research.md` seções 1.1 e 2.4; `quickstart.md` seção 1 (uso apenas de `code_verifier` e `code_challenge` S256).
  - *Evidência de Redirect URI*: `research.md` seção 1.1 e `plan.md` Technical Context com `http://127.0.0.1:8888/api/auth/callback` (nunca localhost).
  - *Evidência de Escopos Mínimos*: Estritamente `user-library-read` e `playlist-modify-private` (`spec.md` FR-001; `research.md` seção 1.1; `contracts/auth-api.yaml`).
  - *Evidência de Segredos e Tokens*: `SPOTIFY_CLIENT_ID` via variável de ambiente; `.dockerignore` excluindo `.env*` e chaves; tokens persistidos no SQLite sem exibição em logs (`data-model.md` seção 1.2; teste automatizado `SegurancaLogsTest.java` em `plan.md` Project Structure e `research.md` seção 2.6).
- [x] **III. Nada é criado sem confirmação**:
  - *Evidência*: A biblioteca de curtidas do usuário opera estritamente em modo de leitura, sem qualquer escrita ou modificação no Spotify nesta feature (`spec.md` FR-009: "O sistema NÃO DEVE realizar nenhuma operação de escrita, modificação ou remoção de faixas curtidas na conta do Spotify do usuário").
- [x] **IV. Cache local é a fonte de verdade para consultas**:
  - *Evidência*: As músicas curtidas são sincronizadas para o SQLite e todas as consultas são atendidas a partir da base local sem requisições externas ao Spotify (`spec.md` FR-012; `contracts/sync-api.yaml` endpoint `GET /api/curtidas`). Registro explícito da fonte (`'spotify'`) e data da coleta (`sincronizada_em`) em cada faixa (`spec.md` FR-011; `data-model.md` seções 1.1 e 2).
- [x] **V. Dependências externas são falíveis**:
  - *Evidência de Isolamento*: Spotify isolado atrás da interface de domínio `PortaSpotifyClient` (`plan.md` Project Structure; `research.md` seção 2.3).
  - *Evidência de Faixas sem Dados e Arquivos Locais*: Descarte explícito de arquivos locais sem identificador oficial (`spec.md` FR-007; `research.md` seção 1.3: filtro `track.id == null || track.is_local == true`).
  - *Evidência de Rate Limiting (429)*: Tratamento com leitura do cabeçalho `Retry-After` (espera se <= 60s; suspensão segura se > 60s) (`spec.md` FR-013 e FR-014; `research.md` seção 1.4; `contracts/sync-api.yaml`).
  - *Evidência de Documentação Oficial Vigente*: Endpoints conferidos e registrados com data 2026-11-24 e URLs oficiais (`research.md` seções 1.1 a 1.5).
- [x] **VI. O LLM traduz pedidos, não é fonte de verdade**:
  - *Evidência*: Nenhuma integração com Ollama ou LLM é utilizada nesta funcionalidade, permanecendo fora do escopo desta feature (`plan.md` Summary e Technical Context; `docker-compose.yml` sem serviço ollama).
- [x] **VII. Simplicidade arquitetural**:
  - *Evidência*: Backend em Java 21 LTS (`21.0.5`) com Spring Boot `3.4.0` e Maven `3.9.9` em arquitetura hexagonal leve (`plan.md` Technical Context e Project Structure). Sem ORM pesado, sem filas, sem microserviços (`research.md` seção 2.2: persistência direta via `JdbcClient`).
- [x] **VIII. Qualidade e testes (INEGOCIÁVEL)**:
  - *Evidência de Testes por Camada*: Unitários de domínio cobrindo 100% de linhas e branches (`FaixaCurtidaTest`, `SessaoUsuarioTest`, `ServicoSincronizacaoTest`, `ServicoAutenticacaoTest`); Integração do adaptador Spotify contra WireMock com fixtures de respostas reais (`AdaptadorSpotifyClientWireMockTest`, fixtures em `src/test/resources/fixtures/spotify/`); Integração de persistência com SQLite em arquivo temporário (`RepositoriosSqliteTest`); Teste de ponta a ponta simulado (`FluxoConexaoESincronizacaoE2ETest`) (`plan.md` Project Structure e `research.md` seção 2.6).
  - *Evidência de Limites JaCoCo e Exclusões*: Build configurado para falhar abaixo de 100% no domínio e 90% linhas / 85% branches no backend geral; 5 exclusões explicitamente listadas e justificadas (`plan.md` Technical Context e `research.md` seção 2.6).
  - *Evidência de Teste de Segurança de Logs*: `SegurancaLogsTest` assegurando que tokens não vazam em logs estruturados (`plan.md` Project Structure e `research.md` seção 2.6).
- [x] **IX. Docker e reprodutibilidade**:
  - *Evidência de Build Multi-estágio*: Imagens com tags fixadas: builder `maven:3.9.9-eclipse-temurin-21` e runtime `eclipse-temurin:21.0.5_11-jre-jammy` não-root (`research.md` seção 2.7; `plan.md` Technical Context).
  - *Evidência de `.dockerignore`*: Arquivo `.dockerignore` na raiz do projeto excluindo `.git/`, `.env*`, `target/`, bancos e arquivos de especificação (`plan.md` Project Structure).
  - *Evidência de Volume Nomeado*: SQLite persistido em volume nomeado exclusivo `playlistmaker_sqlite_data:/data` (`plan.md` Technical Context; `quickstart.md` seção 3).
- [x] **X. Erros acionáveis e logs seguros**:
  - *Evidência de Mensagens*: Todas as mensagens voltadas ao usuário são em português ("sessão expirada, faça login de novo", "cota do Spotify excedida, tente em N minutos", "conta Premium ativa necessária") (`spec.md` FR-004, FR-014, FR-017; `contracts/auth-api.yaml` e `contracts/sync-api.yaml`).
  - *Evidência de Logs*: Ausência de segredos ou tokens nos logs estruturados, validada por teste automatizado `SegurancaLogsTest.java` (`plan.md` Project Structure; `research.md` seção 2.6).
- [x] **XI. Idioma**:
  - *Evidência*: Toda a documentação, comentários, DTOs e identificadores de classes, métodos e tabelas em português sem acentos (`FaixaCurtida`, `SessaoUsuario`, `RegistroSincronizacao`, `ControladorAutenticacao`, `sincronizadaEm`, etc., conforme `plan.md` Project Structure e `data-model.md`).
- [x] **XII. Ambiente de desenvolvimento reproduzível**:
  - *Evidência*: Dev Container versionado em `.devcontainer/devcontainer.json` com Java 21, Maven 3.9 e Docker daemon compartilhado (`quickstart.md` seção 1).

**Resultado do Gate**: APROVADO com evidências completas e verificáveis para cada princípio.

---

## Project Structure

### Documentation (this feature)

```text
specs/001-sincronizar-curtidas-spotify/
├── spec.md              # Especificação de requisitos e histórias de usuário
├── checklists/
│   └── requirements.md  # Checklist de validação da especificação
├── plan.md              # Este plano de implementação
├── research.md          # Pesquisa técnica e conferência dos endpoints do Spotify (Princípio V)
├── data-model.md        # Modelo de dados conceitual e esquema SQLite com DDL
├── quickstart.md        # Guia rápido de execução e cenários de validação
└── contracts/           # Contratos OpenAPI das interfaces expostas
    ├── auth-api.yaml    # Contrato dos endpoints de autenticação PKCE
    └── sync-api.yaml    # Contrato dos endpoints de sincronização e consulta
```

### Source Code (repository root)

```text
.dockerignore
pom.xml
Dockerfile
docker-compose.yml
src/
├── main/
│   ├── java/br/com/playlistmaker/
│   │   ├── PlaylistMakerApplication.java
│   │   ├── core/
│   │   │   ├── dominio/
│   │   │   │   ├── FaixaCurtida.java
│   │   │   │   ├── SessaoUsuario.java
│   │   │   │   ├── EstadoSessao.java
│   │   │   │   ├── RegistroSincronizacao.java
│   │   │   │   ├── StatusSincronizacao.java
│   │   │   │   ├── ResumoSincronizacao.java
│   │   │   │   └── excecoes/
│   │   │   │       ├── SessaoInvalidaException.java
│   │   │   │       ├── RateLimitExcedidoException.java
│   │   │   │       ├── SpotifyPremiumRequeridoException.java
│   │   │   │       └── FalhaComunicacaoException.java
│   │   │   ├── porta/
│   │   │   │   ├── entrada/
│   │   │   │   │   ├── CasoDeUsoAutenticacao.java
│   │   │   │   │   └── CasoDeUsoSincronizacao.java
│   │   │   │   └── saida/
│   │   │   │       ├── PortaSpotifyClient.java
│   │   │   │       ├── PortaRepositorioFaixas.java
│   │   │   │       ├── PortaRepositorioSessao.java
│   │   │   │       └── PortaRepositorioHistorico.java
│   │   │   └── servico/
│   │   │       ├── ServicoAutenticacao.java
│   │   │       └── ServicoSincronizacao.java
│   │   └── adaptadores/
│   │       ├── entrada/rest/
│   │       │   ├── ControladorAutenticacao.java
│   │       │   ├── ControladorSincronizacao.java
│   │       │   ├── ControladorCurtidas.java
│   │       │   ├── dto/
│   │       │   │   ├── RespostaStatusAuthDTO.java
│   │       │   │   ├── RespostaLoginUrlDTO.java
│   │       │   │   ├── RespostaProgressoSyncDTO.java
│   │       │   │   └── RespostaFaixaLocalDTO.java
│   │       │   └── TratadorGlobalExcecoes.java
│   │       └── saida/
│   │           ├── spotify/
│   │           │   ├── AdaptadorSpotifyClient.java
│   │           │   ├── ConfiguracaoSpotifyClient.java
│   │           │   └── dto/
│   │           │       ├── SpotifyTokenResponseDTO.java
│   │           │       ├── SpotifySavedTracksResponseDTO.java
│   │           │       └── SpotifySavedTrackItemDTO.java
│   │           └── persistencia/sqlite/
│   │               ├── AdaptadorRepositorioFaixasSqlite.java
│   │               ├── AdaptadorRepositorioSessaoSqlite.java
│   │               ├── AdaptadorRepositorioHistoricoSqlite.java
│   │               └── ConfiguracaoBancoDados.java
│   └── resources/
│       ├── application.properties
│       └── db/migration/
│           └── V1__criar_esquema_inicial.sql
└── test/
    ├── java/br/com/playlistmaker/
    │   ├── core/
    │   │   ├── dominio/
    │   │   │   ├── FaixaCurtidaTest.java
    │   │   │   └── SessaoUsuarioTest.java
    │   │   └── servico/
    │   │       ├── ServicoAutenticacaoTest.java
    │   │       └── ServicoSincronizacaoTest.java
    │   ├── adaptadores/
    │   │   ├── saida/spotify/
    │   │   │   └── AdaptadorSpotifyClientWireMockTest.java
    │   │   └── saida/persistencia/sqlite/
    │   │       └── RepositoriosSqliteTest.java
    │   ├── seguranca/
    │   │   └── SegurancaLogsTest.java
    │   └── e2e/
    │       └── FluxoConexaoESincronizacaoE2ETest.java
    └── resources/
        ├── application-test.properties
        └── fixtures/spotify/
            ├── token-response.json
            ├── saved-tracks-page1.json
            ├── saved-tracks-page2.json
            ├── saved-tracks-com-arquivos-locais.json
            └── rate-limit-429.json
```

---

## Complexity Tracking

> Justificativa de dependências e escolhas arquiteturais conforme os Princípios VII e XII da Constituição.

| Dependência / Escolha | Justificativa | Alternativa Mais Simples Rejeitada Porque |
|---|---|---|
| **Flyway Core** (`org.flywaydb:flyway-core`) | Gerenciamento e versionamento automatizado e declarativo de migrações DDL (`V1__...`) no SQLite persistido em volume Docker (`playlistmaker_sqlite_data`), garantindo integridade e evolução reprodutível do esquema entre testes e produção. | Execução de script imperativo avulso (`schema.sql` ou comandos no startup Java) foi rejeitada porque não possui controle de versão de migrações (`schema_version`), falha ao lidar com alterações incrementais em bancos já existentes com dados e não oferece rastreabilidade das migrações aplicadas. |
