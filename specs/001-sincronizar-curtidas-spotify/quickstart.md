# Guia Rápido de Execução e Validação (Quickstart)

**Funcionalidade**: `001-sincronizar-curtidas-spotify`  
**Data**: 2026-11-24  
**Ambiente**: Dev Container com Java 21, Maven 3.9 e Docker

---

## 1. Pré-Requisitos e Configuração

Certifique-se de definir as variáveis de ambiente necessárias no arquivo `.env` ou no ambiente do Dev Container:

```bash
# Configurações do Spotify Developer Dashboard
export SPOTIFY_CLIENT_ID="seu_client_id_aqui"
export APP_REDIRECT_URI="http://127.0.0.1:8888/api/auth/callback"
export APP_DATABASE_URL="jdbc:sqlite:/data/playlistmaker.db"
```

> [!NOTE]
> Conforme o Princípio II da Constituição, a aplicação utiliza **OAuth 2.0 PKCE sem Client Secret**, e a porta/redirect operam estritamente em loopback (`127.0.0.1:8888`).

---

## 2. Validação por Testes Automatizados e Cobertura (JaCoCo)

Execute a suíte de testes com validação dos limites constitucionais de cobertura de código (Princípio VIII):

```bash
mvn clean verify
```

O comando executa:
1. **Testes Unitários de Domínio**: Entidades, regras de sincronização incremental, detecção de deltas e filtros de arquivos locais (100% de cobertura de linhas e branches).
2. **Testes de Integração com WireMock**: `AdaptadorSpotifyClientTest` simulando paginação de 700+ faixas, respostas `429 Too Many Requests` com cabeçalho `Retry-After` e renovação de token PKCE usando fixtures gravadas em `src/test/resources/fixtures/spotify/`.
3. **Testes de Integração com SQLite**: `AdaptadorRepositorioSqliteTest` executando migrações Flyway e operações de banco em arquivo temporário.
4. **Verificação JaCoCo**: Falha imediata de build se o pacote de domínio estiver abaixo de 100% ou se o backend estiver abaixo de 90% de linhas e 85% de branches.

---

## 3. Execução Local com Docker Compose

Suba a aplicação com isolamento de rede e volume nomeado para o SQLite:

```bash
docker compose up --build -d
```

Verifique se a aplicação iniciou corretamente e escuta em loopback:

```bash
docker compose logs -f playlist-maker-backend
```

Saída esperada:
```text
Started PlaylistMakerApplication in ... seconds (process running in loopback 127.0.0.1:8888)
```

---

## 4. Cenários de Validação Manual dos Endpoints

### Cenário 1: Verificar Status Inicial da Sessão
Antes de conectar:
```bash
curl -s http://127.0.0.1:8888/api/auth/status
```
Resposta esperada:
```json
{
  "conectado": false,
  "status": "DESCONECTADO",
  "usuarioId": null,
  "mensagem": "Nenhuma conta do Spotify conectada"
}
```

### Cenário 2: Conectar Conta via OAuth 2.0 PKCE
1. Obtenha a URL de autorização:
   ```bash
   curl -s http://127.0.0.1:8888/api/auth/login-url
   ```
2. Abra a URL retornada no navegador do host (`http://127.0.0.1:8888/...`).
3. Faça login no Spotify e autorize os escopos mínimos (`user-library-read`, `playlist-modify-private`).
4. O navegador será redirecionado para `http://127.0.0.1:8888/api/auth/callback?code=...&state=...`.
5. Confirme a conexão:
   ```bash
   curl -s http://127.0.0.1:8888/api/auth/status
   ```
   Resposta esperada:
   ```json
   {
     "conectado": true,
     "status": "ATIVO",
     "usuarioId": "seu_id",
     "mensagem": "Conta conectada e sessão ativa"
   }
   ```

### Cenário 3: Disparar Sincronização Sob Demanda
Inicie a sincronização das músicas curtidas:
```bash
curl -s -X POST http://127.0.0.1:8888/api/sincronizacao
```
Resposta esperada (HTTP 202 Accepted):
```json
{
  "status": "EM_ANDAMENTO",
  "mensagem": "Sincronização iniciada"
}
```

### Cenário 4: Acompanhar Progresso e Resumo Final
Consulte o progresso em tempo real:
```bash
curl -s http://127.0.0.1:8888/api/sincronizacao/status
```
Enquanto estiver rodando:
```json
{
  "status": "EM_ANDAMENTO",
  "faixasProcessadas": 350,
  "totalEstimado": 742,
  "mensagem": "Processando 350 faixas..."
}
```
Após a conclusão:
```json
{
  "status": "CONCLUIDO",
  "faixasProcessadas": 742,
  "totalCurtidasLocal": 742,
  "novasFaixas": 742,
  "removidasFaixas": 0,
  "mensagem": "Sincronização concluída com sucesso",
  "finalizadoEm": "2026-11-24T07:35:10Z"
}
```

### Cenário 5: Consultar Músicas da Base Local (Sem Rede Externa)
Consulte as faixas salvas no SQLite:
```bash
curl -s "http://127.0.0.1:8888/api/curtidas?limite=10&deslocamento=0"
```
Resposta esperada contendo a listagem paginada obtida exclusivamente do banco local.

### Cenário 6: Persistência entre Reinicializações
Reinicie o container da aplicação:
```bash
docker compose restart playlist-maker-backend
```
Consulte novamente:
```bash
curl -s http://127.0.0.1:8888/api/auth/status
curl -s "http://127.0.0.1:8888/api/curtidas?limite=5"
```
A sessão permanece ativa (`conectado: true`) e as músicas continuam disponíveis, comprovando a integridade do volume nomeado do SQLite.
