# Modelo de Dados: Sincronização de Curtidas do Spotify

**Funcionalidade**: `001-sincronizar-curtidas-spotify`  
**Data**: 2026-11-24  
**Banco de Dados**: SQLite 3 (armazenado em `/data/playlistmaker.db` no volume nomeado `playlistmaker_sqlite_data`)

---

## 1. Entidades de Domínio (Arquitetura Hexagonal)

As entidades de domínio residem no pacote `core.dominio` e são totalmente puras (sem dependências de frameworks, anotações JPA ou bibliotecas de terceiros).

### 1.1 `FaixaCurtida`
Representa uma faixa presente nas músicas curtidas do usuário no Spotify e persistida localmente.

- **Campos**:
  - `id` (`String`): Identificador único do catálogo do Spotify (ex.: `"7ouMYWpwJ422jRcDASZB7P"`). Não nulo, chave de unicidade.
  - `titulo` (`String`): Nome da música. Não nulo.
  - `artistas` (`String`): Nome do(s) artista(s) concatenados por vírgula (ex.: `"Muse"` ou `"Queen, David Bowie"`). Não nulo.
  - `album` (`String`): Nome do álbum da faixa. Não nulo.
  - `duracaoMs` (`long`): Duração da faixa em milissegundos. Maior que zero.
  - `curtidaEm` (`Instant`): Timestamp UTC em que a faixa foi curtida no Spotify (`added_at`). Não nulo.
  - `sincronizadaEm` (`Instant`): Timestamp UTC da última atualização da faixa na base local. Não nulo.
  - `fonte` (`String`): Origem da coleta do dado (valor padrão `"spotify"`). Não nulo.

- **Regras de Negócio**:
  - Músicas sem identificador ou marcadas como arquivos locais (`is_local = true`) são rejeitadas na camada de adaptação e nunca instanciadas como `FaixaCurtida`.
  - Duas instâncias são iguais se possuírem o mesmo `id`.

### 1.2 `SessaoUsuario`
Representa o estado de autorização e credenciais de acesso da conta Spotify do usuário.

- **Campos**:
  - `id` (`Long`): Identificador interno do registro de sessão (registro único `1` para o único usuário da aplicação).
  - `usuarioId` (`String`): Identificador do usuário no Spotify (opcional/recuperado após conexão).
  - `accessToken` (`String`): Token Bearer de curta duração para chamadas na Web API.
  - `refreshToken` (`String`): Token duradouro utilizado para renovar o `accessToken`. Persistido no banco, nunca exibido em logs.
  - `expiraEm` (`Instant`): Timestamp UTC de expiração do `accessToken` atual.
  - `atualizadoEm` (`Instant`): Timestamp da última alteração de credenciais.
  - `estado` (`EstadoSessao`): Enum (`ATIVO`, `EXPIRADO`, `REVOGADO`, `DESCONECTADO`).

- **Regras de Negócio e Transições de Estado**:
  - `DESCONECTADO` → `ATIVO`: Conexão inicial com troca de código PKCE bem-sucedida.
  - `ATIVO` → `EXPIRADO`: Tentativa de refresh token falha com `400/401` ou credencial externa invalidada.
  - `EXPIRADO` → `ATIVO`: Novo fluxo de login concluído pelo usuário.
  - O sistema renova o `accessToken` automaticamente se faltarem menos de 60 segundos para `expiraEm`.

### 1.3 `RegistroSincronizacao`
Registra o histórico e o status de cada sincronização sob demanda disparada.

- **Campos**:
  - `id` (`Long`): Identificador sequencial da execução.
  - `iniciadoEm` (`Instant`): Data/hora de início da varredura.
  - `finalizadoEm` (`Instant`): Data/hora de conclusão (ou `null` enquanto em andamento).
  - `status` (`StatusSincronizacao`): Enum (`EM_ANDAMENTO`, `CONCLUIDO`, `LIMITADO`, `ERRO`).
  - `totalCurtidas` (`int`): Total de faixas na base local após a operação.
  - `novasFaixas` (`int`): Quantidade de faixas inseridas nesta sincronização.
  - `removidasFaixas` (`int`): Quantidade de faixas descurtidas excluídas da base local.
  - `mensagem` (`String`): Detalhamento do status ou mensagem de erro em português.
  - `tempoEsperaSegundos` (`Integer`): Tempo indicado no cabeçalho `Retry-After` caso status seja `LIMITADO`.

---

## 2. Esquema Relacional SQLite (DDL)

O banco é versionado pelo Flyway com a migração inicial em `src/main/resources/db/migration/V1__criar_esquema_inicial.sql`.

```sql
-- Tabela de Sessão do Usuário (Princípio I: Usuário único)
CREATE TABLE IF NOT EXISTS sessao_usuario (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    usuario_id TEXT,
    access_token TEXT NOT NULL,
    refresh_token TEXT NOT NULL,
    expira_em TEXT NOT NULL,
    atualizado_em TEXT NOT NULL,
    estado TEXT NOT NULL CHECK (estado IN ('ATIVO', 'EXPIRADO', 'REVOGADO', 'DESCONECTADO'))
);

-- Tabela de Músicas Curtidas Locais
CREATE TABLE IF NOT EXISTS faixa_curtida (
    id TEXT PRIMARY KEY,
    titulo TEXT NOT NULL,
    artistas TEXT NOT NULL,
    album TEXT NOT NULL,
    duracao_ms INTEGER NOT NULL,
    curtida_em TEXT NOT NULL,
    sincronizada_em TEXT NOT NULL,
    fonte TEXT NOT NULL DEFAULT 'spotify'
);

CREATE INDEX IF NOT EXISTS idx_faixa_curtida_curtida_em ON faixa_curtida (curtida_em DESC);
CREATE INDEX IF NOT EXISTS idx_faixa_curtida_titulo ON faixa_curtida (titulo);

-- Tabela de Registro/Histórico de Sincronizações
CREATE TABLE IF NOT EXISTS registro_sincronizacao (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    iniciado_em TEXT NOT NULL,
    finalizado_em TEXT,
    status TEXT NOT NULL CHECK (status IN ('EM_ANDAMENTO', 'CONCLUIDO', 'LIMITADO', 'ERRO')),
    total_curtidas INTEGER DEFAULT 0,
    novas_faixas INTEGER DEFAULT 0,
    removidas_faixas INTEGER DEFAULT 0,
    mensagem TEXT,
    tempo_espera_segundos INTEGER
);

CREATE INDEX IF NOT EXISTS idx_registro_sincronizacao_iniciado_em ON registro_sincronizacao (iniciado_em DESC);
```

---

## 3. Estratégia de Atualização Incremental e Idempotência

1. **Inserção/Atualização (Upsert)**:
   Para cada faixa válida obtida da API do Spotify:
   ```sql
   INSERT INTO faixa_curtida (id, titulo, artistas, album, duracao_ms, curtida_em, sincronizada_em, fonte)
   VALUES (:id, :titulo, :artistas, :album, :duracaoMs, :curtidaEm, :sincronizadaEm, :fonte)
   ON CONFLICT(id) DO UPDATE SET
       titulo = excluded.titulo,
       artistas = excluded.artistas,
       album = excluded.album,
       duracao_ms = excluded.duracao_ms,
       curtida_em = excluded.curtida_em,
       sincronizada_em = excluded.sincronizada_em;
   ```
2. **Remoção de Descurtidas**:
   Ao concluir a varredura completa das faixas do Spotify na sincronização:
   As faixas presentes no banco cuja data de `sincronizada_em` for anterior à data de início da sincronização atual (ou que não constem no conjunto de IDs processados) são removidas:
   ```sql
   DELETE FROM faixa_curtida 
   WHERE fonte = 'spotify' AND sincronizada_em < :inicioSincronizacao;
   ```
   A contagem de linhas afetadas por essa instrução `DELETE` fornece exatamente a quantidade de `removidasFaixas`.
3. **Idempotência**:
   Se a sincronização for interrompida e reiniciada, como a identificação é chave primária fixa pelo `id` do Spotify e o `DELETE` baseia-se no timestamp da execução concluída, não há duplicações nem estados inconsistentes.
