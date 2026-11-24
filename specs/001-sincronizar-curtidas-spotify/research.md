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

---

## 2. Decisões Tecnológicas e Justificativas

### 2.1 Backend e Framework
- **Decisão**: Java 21 LTS com Spring Boot 3.3.x e Apache Maven.
- **Racional**: Cumpre a exigência constitucional (Princípio VII). O Java 21 oferece Virtual Threads, Records para DTOs imutáveis, Pattern Matching e `HttpClient` moderno. O Spring Boot fornece ecossistema maduro para REST, injeção de dependências e testes integrados.
- **Alternativas Rejeitadas**:
  - Java 17: Rejeitado por não ser a versão fixada na constituição (Java 21).
  - Frameworks reativos (WebFlux): Rejeitado pelo Princípio VII (simplicidade arquitetural; programação imperativa tradicional com Virtual Threads atende amplamente à volumetria de um único usuário).

### 2.2 Banco de Dados Local e Persistência
- **Decisão**: SQLite 3 gerenciado via `org.xerial:sqlite-jdbc` e Spring `JdbcClient` / `JdbcTemplate`, com migrações via Flyway (`flyway-core`).
- **Racional**:
  - O SQLite armazena todos os dados em um único arquivo no volume nomeado Docker (`playlistmaker_sqlite_data`).
  - O uso de `JdbcClient` (Spring 6) mantém o código enxuto, sem necessidade de dialetos JPA/Hibernate complexos que costumam apresentar incompatibilidades com recursos nativos do SQLite (como `UPSERT` e transações DDL).
  - Mantém o domínio 100% puro, sem anotações `@Entity` ou dependências de frameworks ORM.
- **Alternativas Rejeitadas**:
  - Spring Data JPA / Hibernate: Rejeitado para evitar sobrecarga de sessão, mapeamentos bidirecionais e inconsistências com SQLite.
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

### 2.5 Estratégia de Sincronização Incremental Idempotente
- **Decisão**:
  - A sincronização executa leitura paginada de 50 em 50 faixas até `offset + limit >= total`.
  - Conforme cada lote de 50 é lido, as faixas locais sem identificador Spotify são descartadas.
  - O sistema calcula o delta:
    - *Novas faixas*: presentes no lote do Spotify mas inexistentes na base local -> inseridas no banco.
    - *Faixas mantidas*: atualiza metadados e data de sincronização (`UPSERT`).
  - Ao final da leitura de todos os lotes do Spotify, qualquer faixa presente no banco local com `fonte = 'spotify'` cujo identificador não tenha sido encontrado na varredura remota é removida da tabela local (`DELETE FROM faixa_curtida WHERE id NOT IN (...)`).
  - Resumo de sincronização (`total`, `novas`, `removidas`) é gravado em `registro_sincronizacao` e retornado na API.

### 2.6 Estratégia de Testes por Camada e JaCoCo (Princípio VIII)
- **Decisão**:
  - **Unitários**: Testes de domínio (`ServicoSincronizacaoTest`, `ServicoAutenticacaoTest`, entidades) sem nenhum container ou mock de rede complexo. Cobertura: 100% de linhas e 100% de branches.
  - **Integração com WireMock**: `AdaptadorSpotifyClientTest` rodando contra servidor WireMock em porta dinâmica, usando fixtures JSON baseadas em payloads reais do Spotify gravadas em `src/test/resources/fixtures/spotify/`:
    - `saved-tracks-page1.json` (50 faixas)
    - `saved-tracks-page2.json` (com faixas locais e músicas normais)
    - `saved-tracks-empty.json`
    - `token-response.json`
    - `rate-limit-429.json`
  - **Integração com SQLite**: `AdaptadorRepositorioSqliteTest` rodando com SQLite real em arquivo temporário (isolado por teste).
  - **Ponta a Ponta (E2E)**: `@SpringBootTest` com todos os serviços externos apontando para WireMock, executando o ciclo completo: conectar conta -> sincronizar 700+ faixas simuladas -> consultar base local.
  - **JaCoCo Plugin**: Executado no ciclo `mvn verify`, com regras mínimas de falha de build:
    - Regra 1: Pacote `core.dominio` com 100% de linhas e branches.
    - Regra 2: Backend geral com no mínimo 90% de linhas e 85% de branches.
    - Exclusões justificadas: classes de configuração Spring pura (`@Configuration`) e DTOs de mapeamento JSON sem lógica de negócio.

### 2.7 Docker e Compose (Princípio IX)
- **Decisão**:
  - `Dockerfile` com multi-estágio:
    - Builder: `maven:3.9.9-eclipse-temurin-21`
    - Runtime: `eclipse-temurin:21-jre-jammy` executando como usuário não-root `appuser`.
  - `docker-compose.yml`:
    - Serviço: `playlist-maker-backend`.
    - Porta: `127.0.0.1:8888:8888`.
    - Volume nomeado: `playlistmaker_sqlite_data:/data`.
    - Sem Ollama, sem Vue, sem ReccoBeats, sem Last.fm (escopo restrito a esta funcionalidade).
