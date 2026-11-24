# Pesquisa Técnica e Decisões de Arquitetura: Sincronização de Curtidas do Spotify

**Data da Pesquisa**: 2026-11-24  
**Funcionalidade**: `001-sincronizar-curtidas-spotify`  
**Conformidade Constitucional**: Princípios II, IV, V, VII, VIII, IX, X, XI da Constituição v1.2.0.

---

## 1. Documentação Oficial da API do Spotify (Princípio V)

Conforme estabelecido no **Princípio V da Constituição**, todos os endpoints externos foram conferidos na documentação oficial vigente do Spotify em **2026-11-24**.

### 1.1 Endpoint de Autorização OAuth 2.0 (PKCE)
- **URL da Documentação**: `https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow`
- **Data da Conferência**: 2026-11-24
- **Endpoint**: `GET https://accounts.spotify.com/authorize`
- **Parâmetros Utilizados**:
  - `client_id`: Identificador da aplicação registrado no Spotify Developer Dashboard.
  - `response_type`: Valor fixo `code`.
  - `redirect_uri`: `http://127.0.0.1:8888/api/auth/callback` (estritamente loopback conforme Princípio II).
  - `code_challenge_method`: Valor fixo `S256`.
  - `code_challenge`: Hash SHA-256 do `code_verifier`, codificado em Base64 URL-safe sem padding.
  - `state`: Valor aleatório criptográfico (mínimo 16 caracteres) para prevenção de CSRF.
  - `scope`: `user-library-read playlist-modify-private` (estritamente os escopos mínimos autorizados).

### 1.2 Endpoint de Troca e Renovação de Token
- **URL da Documentação**: `https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow`
- **Data da Conferência**: 2026-11-24
- **Endpoint**: `POST https://accounts.spotify.com/api/token`
- **Cabeçalho**: `Content-Type: application/x-www-form-urlencoded`
- **Fluxo de Troca (Authorization Code)**:
  - `grant_type`: `authorization_code`
  - `client_id`: Identificador da aplicação.
  - `code`: Código retornado no callback após consentimento do usuário.
  - `redirect_uri`: `http://127.0.0.1:8888/api/auth/callback`.
  - `code_verifier`: O segredo PKCE original gerado pela aplicação.
- **Fluxo de Renovação (Refresh Token)**:
  - `grant_type`: `refresh_token`
  - `refresh_token`: O token de atualização recuperado da base local.
  - `client_id`: Identificador da aplicação.
- **Campos da Resposta Utilizados**:
  - `access_token`: Token de curta duração para autorizar chamadas da API Web.
  - `token_type`: `Bearer`.
  - `expires_in`: Tempo de vida em segundos (habitualmente 3600 segundos).
  - `refresh_token`: Token persistente para renovações futuras (emitido na primeira troca e opcionalmente rotacionado).
  - `scope`: Escopos confirmados pelo provedor.

### 1.3 Endpoint de Músicas Curtidas (Saved Tracks)
- **URL da Documentação**: `https://developer.spotify.com/documentation/web-api/reference/get-users-saved-tracks`
- **Data da Conferência**: 2026-11-24
- **Endpoint**: `GET https://api.spotify.com/v1/me/tracks`
- **Cabeçalho**: `Authorization: Bearer <access_token>`
- **Parâmetros de Consulta**:
  - `limit`: `50` (limite máximo permitido por requisição pela API para minimizar viagens de rede).
  - `offset`: Deslocamento para paginação (0, 50, 100, ...).
- **Campos Mapeados da Resposta**:
  - `total`: Quantidade total de músicas curtidas pelo usuário no Spotify.
  - `limit`, `offset`, `next`: Metadados de paginação.
  - `items[].added_at`: Timestamp UTC (`ISO 8601`) de quando a faixa foi curtida.
  - `items[].track.id`: Identificador Spotify exclusivo da música.
  - `items[].track.name`: Título da música.
  - `items[].track.artists[].name`: Nome(s) do(s) artista(s) principal(is) e participante(s).
  - `items[].track.album.name`: Nome do álbum correspondente.
  - `items[].track.duration_ms`: Duração da música em milissegundos.
  - `items[].track.is_local`: Booleano indicando se a faixa é um arquivo local.
- **Regra de Filtro de Arquivos Locais**:
  - Se `items[].track.id == null` ou `items[].track.is_local == true`, a entrada é classificada como arquivo local e descartada silenciosamente sem persistência nem contabilização no resumo.

### 1.4 Tratamento de Limitação de Requisições (Rate Limits / HTTP 429)
- **URL da Documentação**: `https://developer.spotify.com/documentation/web-api/concepts/rate-limits`
- **Data da Conferência**: 2026-11-24
- **Comportamento Documentado**: Quando a cota de requisições por janela é atingida, a API retorna status `429 Too Many Requests` e o cabeçalho `Retry-After` informando a quantidade de segundos de espera obrigatória.
- **Implementação no Cliente**:
  - Leitura do cabeçalho `Retry-After`.
  - Se `tempo_espera <= 60 segundos`: Pausar a thread com `Thread.sleep` (ou backoff assíncrono) pelo período indicado e retentar a requisição até o limite de 3 tentativas consecutivas.
  - Se `tempo_espera > 60 segundos`: Suspender a sincronização, registrar o status no histórico e notificar a interface informando os minutos/segundos necessários antes de nova tentativa.

### 1.5 Tratamento de Conta Sem Spotify Premium (Princípio I)
- **URL da Documentação**: `https://developer.spotify.com/documentation/web-api/concepts/developer-terms-and-policies` e `https://developer.spotify.com/documentation/web-api/reference/get-current-users-profile`
- **Data da Conferência**: 2026-11-24
- **Endpoint**: `GET https://api.spotify.com/v1/me`
- **Regra e Comportamento**: Conforme o Princípio I da Constituição, o modo de desenvolvimento do Spotify impõe restrições em que a conta do desenvolvedor/dono do app exige assinatura Premium ativa. Caso o perfil retorne `product != "premium"` ou a API retorne `403 Forbidden` com restrições de produto/desenvolvedor:
  - O sistema captura o erro especificamente e retorna o status estruturado `PREMIUM_REQUERIDO`.
  - Mensagem acionável ao usuário em português: "Sua conta do Spotify não possui plano Premium ativo. A aplicação opera em modo de desenvolvimento do Spotify, exigindo uma conta Premium para acesso e sincronização da biblioteca."

---

## 2. Decisões Tecnológicas e Justificativas

### 2.1 Backend e Framework
- **Decisão**: Java 21 LTS (`21.0.5`), Spring Boot `3.4.0` e Apache Maven `3.9.9`.
- **Status de Suporte do Spring Boot 3.4.0**:
  - Lançamento oficial: 21 de novembro de 2024.
  - Suporte Comunitário (OSS): Ativo até 24 de novembro de 2025.
  - Suporte Comercial estendido: Até novembro de 2027.
  - Compatibilidade: Suporta nativamente Java 21 LTS e Spring Framework 6.2, sendo a versão moderna estável recomendada para novas aplicações baseadas no JDK 21.
- **Racional**: Cumpre a exigência constitucional (Princípio VII). O Java 21 oferece Virtual Threads, Records para DTOs imutáveis, Pattern Matching e `HttpClient` moderno. O Spring Boot fornece ecossistema maduro para REST, injeção de dependências e testes integrados.
- **Alternativas Rejeitadas**:
  - Java 17: Rejeitado por não ser a versão fixada na constituição (Java 21).
  - Spring Boot 3.3.x: Embora estável, sua janela de suporte OSS encerra-se antes da linha 3.4.
  - Frameworks reativos (WebFlux): Rejeitado pelo Princípio VII (simplicidade arquitetural; programação imperativa tradicional com Virtual Threads atende amplamente à volumetria de um único usuário).

### 2.2 Banco de Dados Local, Persistência e Controle de Esquema (Flyway)
- **Decisão**: SQLite 3 gerenciado via `org.xerial:sqlite-jdbc` e Spring `JdbcClient` / `JdbcTemplate`, com migrações gerenciadas pelo Flyway (`org.flywaydb:flyway-core`).
- **Justificativa do Flyway (Complexity Tracking)**:
  - *Problema*: O SQLite armazena o banco em arquivo persistente no volume Docker. A criação manual de tabelas via scripts ad-hoc em Java ou SQL não versionado impede evolução segura e idempotente do esquema (criação de tabelas, índices e futuras colunas), gerando risco de corrupção ou inconsistência entre ambientes de desenvolvimento e teste.
  - *Solução com Flyway*: Versionamento declarativo com migrações SQL imutáveis (`V1__...`, `V2__...`), executadas automaticamente no bootstrap da aplicação.
  - *Alternativa mais simples rejeitada*: Execução de script imperativo no startup via `schema.sql` do Spring Boot foi rejeitada porque não possui controle de versão de migrações (`schema_version`), falha ao lidar com alterações incrementais em bancos já existentes e não oferece rastreabilidade de migrações aplicadas.
- **Racional do `JdbcClient`**:
  - O SQLite armazena todos os dados em um único arquivo no volume nomeado Docker (`playlistmaker_sqlite_data`).
  - O uso de `JdbcClient` (Spring 6) mantém o código enxuto, sem necessidade de dialetos JPA/Hibernate complexos que costumam apresentar incompatibilidades com recursos nativos do SQLite (como `UPSERT` e transações DDL).
  - Mantém o domínio 100% puro, sem anotações `@Entity` ou dependências de frameworks ORM.
- **Alternativas Rejeitadas**:
  - Spring Data JPA / Hibernate: Rejeitado para evitar sobrecarga de sessão, mapeamentos bidirecionais e inconsistências de dialeto com SQLite.
  - Arquivos JSON planos: Rejeitado por falta de transacionalidade e suporte a consultas estruturadas eficientes para 700+ músicas.

### 2.3 Arquitetura Hexagonal Leve
- **Decisão**: Estrutura de pacotes estritamente desacoplada:
  - `core.dominio`: Entidades puras (`FaixaCurtida`, `SessaoUsuario`, `RegistroSincronizacao`, `ResumoSincronizacao`), regras de negócio e exceções do domínio. Zero dependências externas (sem Spring, sem SQL, sem HTTP).
  - `core.porta`: Interfaces de entrada (`CasoDeUsoSincronizacao`, `CasoDeUsoAutenticacao`) e portas de saída (`PortaSpotifyClient`, `PortaRepositorioFaixas`, `PortaRepositorioSessao`, `PortaRepositorioHistorico`).
  - `adaptadores.entrada.rest`: Controllers Spring Boot expondo endpoints REST locais para autenticação e sincronização.
  - `adaptadores.saida.spotify`: Cliente HTTP utilizando `RestClient` do Spring configurado para falar com a API do Spotify, interceptar 429 e mapear DTOs.
  - `adaptadores.saida.persistencia.sqlite`: Repositórios implementando as portas de persistência com SQL nativo e `JdbcClient`.
- **Racional**: Permite testar todo o domínio e as regras de sincronização com testes unitários puros ultrarrápidos, além de isolar o Spotify e o SQLite atrás de contratos estáveis.

### 2.4 Fluxo de Autorização PKCE e Gerenciamento de Sessão
- **Decisão**: Fluxo PKCE puro executado entre o backend local e o Spotify:
  1. Frontend requisita `/api/auth/login-url`.
  2. Backend gera `code_verifier` criptográfico e `code_challenge` (SHA-256), guarda temporariamente o par `state`/`code_verifier` em cache local temporário com expiração e retorna a URL de autorização.
  3. Usuário autoriza no navegador e o Spotify redireciona para `http://127.0.0.1:8888/api/auth/callback?code=...&state=...`.
  4. Backend valida o `state`, recupera o `code_verifier`, envia requisição `POST /api/token` com PKCE e persiste `refresh_token` e `access_token` no SQLite.
  5. Assegura que nenhum token ou segredo seja exibido em logs estruturados (Princípio X).

### 2.5 Estratégia de Sincronização Incremental, Execução em Segundo Plano e Idempotência
- **Por que a sincronização "incremental" ainda percorre toda a biblioteca remota**:
  - A API do Spotify não disponibiliza endpoints de delta, changelog, webhooks ou parâmetros de data de modificação (`since` ou `modified_after`) para a biblioteca de músicas curtidas (`/v1/me/tracks`).
  - Para detectar músicas que o usuário *deixou de curtir* (removidas), é impossível saber quais faixas foram excluídas sem comparar a totalidade atual das curtidas do Spotify com a base local.
  - Portanto, a sincronização é **incremental no processamento e na persistência local**, e não no consumo da API remota:
    1. *Persistência eficiente*: Faixas já presentes localmente sofrem apenas `UPSERT` sem disparar escrita desnecessária;
    2. *Isolamento de enriquecimento futuro (Princípio IV)*: As faixas existentes que já possuírem metadados de áudio e tags NÃO sofrerão chamadas de enriquecimento em features futuras; somente faixas classificadas como *novas* serão encaminhadas para enriquecimento;
    3. *Remoção determinística*: As faixas locais ausentes na varredura remota são excluídas do SQLite, mantendo a base estritamente espelhada às curtidas ativas.
- **Execução em Segundo Plano (Assíncrona) e Prevenção de Execuções Simultâneas**:
  - A sincronização é **assíncrona (segundo plano)**. O endpoint `POST /api/sincronizacao` dispara a tarefa em thread de segundo plano e retorna imediatamente `202 Accepted`.
  - *Prevenção de execuções simultâneas*:
    - Na JVM: Uso de lock explícito de exclusão mútua (`ReentrantLock` com `tryLock()` ou `AtomicBoolean`). Se uma sincronização já estiver em execução, qualquer nova requisição recebe status `409 Conflict` imediatamente com a mensagem `"Já existe uma sincronização em andamento"`.
    - No Banco de Dados: O registro corrente em `registro_sincronizacao` mantém o status `EM_ANDAMENTO`.
  - A interface acompanha o progresso em tempo real consultando `GET /api/sincronizacao/status` (polling).
- **Manutenção do Histórico de Sincronizações (Requisitos FR-010 e FR-011 do spec.md)**:
  - Para cumprir rigorosamente o `FR-010` (apresentar total de curtidas, novas e removidas) e o `FR-011` (registrar data/hora da coleta e fonte de dados), cada execução gera um registro persistido na tabela `registro_sincronizacao`, permitindo consultar o resultado da última sincronização mesmo após reiniciar a aplicação.

### 2.6 Estratégia de Testes por Camada, JaCoCo e Segurança de Logs (Princípio VIII e X)
- **Decisão de Testes**:
  - **Unitários**: Testes de domínio (`ServicoSincronizacaoTest`, `ServicoAutenticacaoTest`, entidades) cobrindo 100% de linhas e branches do domínio.
  - **Integração com WireMock**: `AdaptadorSpotifyClientWireMockTest` rodando contra servidor WireMock com fixtures JSON baseadas em payloads reais do Spotify:
    - `saved-tracks-page1.json` (50 faixas)
    - `saved-tracks-page2.json` (faixas normais e locais)
    - `saved-tracks-empty.json`
    - `token-response.json`
    - `rate-limit-429.json`
  - **Integração com SQLite**: `RepositoriosSqliteTest` rodando com SQLite em arquivo temporário isolado e migrações Flyway.
  - **Ponta a Ponta (E2E)**: `@SpringBootTest` com WireMock simulando o fluxo completo de autenticação e sincronização.
- **Teste de Não Vazamento de Tokens em Log (Princípio II e X)**:
  - Criação do teste automatizado `SegurancaLogsTest` utilizando `OutputCaptureExtension` do Spring Boot com nível de log em DEBUG, assegurando via asserções explícitas (`assertThat(logs).doesNotContain(accessToken).doesNotContain(refreshToken)`) que nenhuma credencial é emitida nos logs.
- **Exclusões Explícitas da Medição do JaCoCo e Justificativas (Princípio VIII)**:
  Conforme o Princípio VIII da Constituição, todas as exclusões são restritas a configurações puras e DTOs sem lógica:
  1. `br.com.playlistmaker.PlaylistMakerApplication`: Classe principal de bootstrap do Spring Boot, contendo apenas a invocação estática padrão `SpringApplication.run`.
  2. `br.com.playlistmaker.adaptadores.saida.persistencia.sqlite.ConfiguracaoBancoDados`: Classe `@Configuration` pura do Spring responsável por instanciar os beans de `DataSource` SQLite e `JdbcClient`.
  3. `br.com.playlistmaker.adaptadores.saida.spotify.ConfiguracaoSpotifyClient`: Classe `@Configuration` pura do Spring responsável por configurar a URL base e o client HTTP do `RestClient`.
  4. `br.com.playlistmaker.adaptadores.entrada.rest.dto.*`: Records/DTOs imutáveis de entrada e saída da API REST sem nenhuma lógica de negócio ou ramificação condicional.
  5. `br.com.playlistmaker.adaptadores.saida.spotify.dto.*`: Records/DTOs imutáveis de desserialização JSON das respostas do Spotify sem regras de negócio.

### 2.7 Docker, Compose e .dockerignore (Princípio IX)
- **Decisão**:
  - `.dockerignore` obrigatório na raiz do projeto para impedir vazamento de segredos, binários ou arquivos temporários para o contexto de build. Contém:
    - `.git/`, `.gitignore`
    - `target/`
    - `.env*`
    - `*.db`, `*.db-journal`
    - `.specify/`, `.agents/`
    - `specs/`
  - `Dockerfile` multi-estágio com tags exatas fixadas:
    - Builder: `maven:3.9.9-eclipse-temurin-21`
    - Runtime: `eclipse-temurin:21.0.5_11-jre-jammy` executando com usuário não-root `appuser`.
  - `docker-compose.yml`:
    - Serviço: `playlist-maker-backend`.
    - Porta: `127.0.0.1:8888:8888` (estritamente loopback).
    - Volume nomeado: `playlistmaker_sqlite_data:/data`.
    - Sem Ollama, sem Vue, sem ReccoBeats, sem Last.fm (escopo restrito a esta funcionalidade).
