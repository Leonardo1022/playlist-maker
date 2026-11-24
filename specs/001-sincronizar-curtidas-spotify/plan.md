# Implementation Plan: Conectar Conta e Sincronizar Músicas Curtidas do Spotify

**Branch**: `001-sincronizar-curtidas-spotify` | **Date**: 2026-11-24 | **Spec**: [`spec.md`](spec.md)

**Input**: Feature specification from `specs/001-sincronizar-curtidas-spotify/spec.md`

---

## Summary

Implementação do backend da aplicação para conexão com a conta do Spotify via OAuth 2.0 Authorization Code com PKCE (sem client secret) e sincronização incremental sob demanda das músicas curtidas para banco SQLite local em arquitetura hexagonal leve. O sistema consome a API do Spotify (`/v1/me/tracks`) com paginação resiliente, ignorando arquivos locais, tratando respostas HTTP 429 de acordo com o cabeçalho `Retry-After`, e mantendo a sessão do usuário persistida no SQLite entre reinicializações. As consultas posteriores às faixas operam estritamente sobre a base local sem chamadas ao Spotify.

---

## Technical Context

**Language/Version**: Java 21 LTS  
**Primary Dependencies**: Spring Boot 3.3.x (`spring-boot-starter-web`, `spring-boot-starter-jdbc`), Flyway Core (`flyway-core`), SQLite JDBC (`sqlite-jdbc`).  
**Storage**: SQLite 3 armazenado em volume Docker nomeado (`playlistmaker_sqlite_data`), com migrações via Flyway e persistência direta via `JdbcClient`.  
**Testing**: JUnit 5, AssertJ, Mockito, WireMock (para simulação de endpoints do Spotify com fixtures de respostas reais) e JaCoCo para controle estrito de cobertura.  
**Target Platform**: Linux Container via Docker Compose, com publicação restrita ao loopback do host (`127.0.0.1:8888:8888`).  
**Project Type**: Backend Web Service (API REST local em arquitetura hexagonal).  
**Performance Goals**: Sincronização inicial de biblioteca com 700 faixas em menos de 60 segundos; sincronização incremental subsequente sem alterações em menos de 10 segundos.  
**Constraints**: Escopos mínimos (`user-library-read`, `playlist-modify-private`), sem client secret, redirect obrigatório em `127.0.0.1`, isolamento de chamadas externas atrás de interfaces do domínio, logs sem tokens ou segredos, zero chamadas externas em consultas de faixas.  
**Scale/Scope**: Usuário único, bibliotecas com 700 ou mais músicas curtidas.

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] **I. Uso pessoal e local**: Backend monousuário, sem contas próprias, sem telemetria, rodando localmente.
- [x] **II. Segurança e privacidade por padrão**:
  - Porta publicada no host estritamente em loopback (`127.0.0.1:8888:8888`).
  - OAuth 2.0 Authorization Code com PKCE, sem client secret. Redirect URI em `http://127.0.0.1:8888/api/auth/callback`.
  - Escopos mínimos fixados: `user-library-read` e `playlist-modify-private`.
  - `SPOTIFY_CLIENT_ID` via variável de ambiente, nunca commitado.
  - Refresh token salvo no SQLite dentro de volume nomeado e NUNCA registrado em logs.
- [x] **III. Nada é criado sem confirmação**: Biblioteca de curtidas é estritamente somente leitura (nenhuma escrita no Spotify nesta feature).
- [x] **IV. Cache local é a fonte de verdade**: Faixas sincronizadas para SQLite. Consultas leem unicamente do SQLite local, sem chamadas externas. Sincronização explícita sob demanda e incremental. Registro da data e fonte (`spotify`) em cada faixa.
- [x] **V. Dependências externas são falíveis**:
  - Spotify isolado atrás da porta do domínio `PortaSpotifyClient`.
  - Arquivos locais sem ID são ignorados explicitamente.
  - HTTP 429 respeita `Retry-After`: aguarda se <= 60s; suspende com notificação amigável se > 60s.
  - Endpoints vigentes do Spotify conferidos na documentação oficial em 2026-11-24 e registrados no `research.md`.
- [x] **VI. LLM**: Não aplicável a esta funcionalidade (Ollama fica fora desta feature).
- [x] **VII. Simplicidade arquitetural**: Backend em Spring Boot com Java 21 e Maven. Arquitetura hexagonal leve (domínio puro sem Spring, SQL ou HTTP). Sem ORM pesado, sem filas, sem microserviços.
- [x] **VIII. Qualidade e testes (Inegociável)**:
  - Testes unitários cobrindo 100% de linhas e branches do domínio.
  - Testes de integração com WireMock e fixtures reais versionadas em `src/test/resources/fixtures/spotify/`.
  - Teste de integração do repositório SQLite em arquivo temporário com migrações Flyway.
  - Teste ponta a ponta (E2E) simulando login -> sincronizar -> consultar.
  - Nenhuma chamada real externa durante testes automatizados.
  - Limites de cobertura JaCoCo configurados para quebrar o build (`mvn verify`) se abaixo das metas (100% domínio, 90% backend linhas, 85% branches).
- [x] **IX. Docker e reprodutibilidade**: Build multi-estágio (Maven 3.9 + Temurin 21 -> Temurin 21 JRE Jammy não-root). Compose apenas com a aplicação e volume nomeado `playlistmaker_sqlite_data`.
- [x] **X. Erros acionáveis e logs seguros**: Mensagens em português ("sessão expirada, faça login de novo", "cota do Spotify excedida, tente em N minutos"). Logs sem tokens.
- [x] **XI. Idioma**: Especificações, planos, comentários e identificadores do código em português sem acentos.
- [x] **XII. Dev Container**: Projeto preparado para execução reproduzível via Dev Container com Java 21, Maven e Docker.

**Resultado do Gate**: APROVADO sem nenhuma violação constitucional.

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

> Nenhuma violação constitucional detectada. Todos os princípios e diretrizes foram atendidos integralmente.

| Violação | Justificativa | Alternativa Mais Simples Rejeitada Porque |
|---|---|---|
| *Nenhuma* | *N/A* | *N/A* |
