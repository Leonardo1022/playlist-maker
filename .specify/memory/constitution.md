# Constituição do Playlist Maker

Aplicação de uso pessoal que cria playlists no Spotify a partir das músicas curtidas do usuário, interpretando pedidos em texto livre. Este documento é a autoridade máxima do projeto: specs, planos e tarefas que o contrariem estão errados, não a constituição.

## Princípios Fundamentais

### I. Uso pessoal e local
A aplicação é de um único usuário e roda na máquina dele. NÃO DEVE haver multiusuário, hospedagem em nuvem, contas próprias ou telemetria. Os limites do modo de desenvolvimento do Spotify (Premium obrigatório para o dono do app, cota por conta) são restrições aceitas, e nenhuma funcionalidade pode depender de cota estendida.

### II. Segurança e privacidade por padrão
- Dentro do container a aplicação escuta em `0.0.0.0`; no host a porta DEVE ser publicada somente em loopback (`127.0.0.1:8888:8888`). Nunca expor na rede.
- OAuth com Authorization Code + PKCE, sem client secret. O redirect URI DEVE usar `127.0.0.1`, nunca `localhost`.
- Escopos mínimos: `user-library-read` e `playlist-modify-private`. Qualquer escopo adicional exige emenda desta constituição.
- Client ID, chave do Last.fm e qualquer outro segredo vêm apenas de variáveis de ambiente ou secrets do Docker. NÃO DEVEM aparecer no repositório, na imagem, em logs ou em mensagens de erro.
- O refresh token fica no banco local, dentro do volume, e NUNCA é registrado em log.
- Nenhum dado pessoal (curtidas, histórico, pedidos) sai da máquina, exceto as chamadas estritamente necessárias aos serviços listados no Princípio V.

### III. Nada é criado sem confirmação do usuário
- Toda playlist passa por uma prévia, em que o usuário pode remover faixas, e só é criada após confirmação explícita.
- Playlists são privadas por padrão. Não há limite de faixas imposto pela aplicação, que DEVE respeitar apenas os limites por requisição da API (adição em lotes).
- A aplicação NÃO DEVE alterar nem remover curtidas, nem alterar playlists que não criou. A biblioteca é somente leitura.

### IV. Cache local é a fonte de verdade para consultas
- Curtidas, features de áudio, tags e gêneros são sincronizados para o SQLite e consultados de lá.
- Um pedido do usuário NÃO DEVE disparar chamadas de enriquecimento a APIs externas. As únicas chamadas externas em tempo de pedido são a criação da playlist e a chamada ao LLM local.
- A sincronização é explícita e incremental (só faixas novas ou removidas).
- Todo dado enriquecido guarda a fonte e a data da coleta.

### V. Dependências externas são falíveis
- Spotify, ReccoBeats e Last.fm podem mudar, limitar ou sumir. Cada um DEVE ficar atrás de uma interface própria do domínio, para ser substituído sem tocar na regra de negócio.
- A ausência de dado é um estado de primeira classe: faixa sem features é "desconhecida" e NUNCA recebe valor padrão inventado. A spec de cada funcionalidade DEVE dizer o que acontece com faixas sem dados.
- Respostas 429 DEVEM respeitar `Retry-After`, com limite de tentativas e mensagem clara ao usuário quando a cota se esgotar.
- A API do Spotify mudou de forma relevante em 2026. Antes de usar qualquer endpoint, a fase de plano DEVE conferir a documentação oficial vigente e registrar o endpoint, os campos usados e a data da conferência.
- É proibido obter dados do Spotify por scraping, captura de áudio ou meios que violem os termos de uso. Os termos de cada serviço DEVEM ser revisados e resumidos na spec correspondente. O uso de dados do Spotify com modelos de IA DEVE ser conferido nos Termos de Desenvolvedor vigentes; na dúvida, restringir a inferência local, sem treinamento e sem persistir embeddings.

### VI. O LLM traduz pedidos, não é fonte de verdade
- O modelo (padrão `gemma3:4b`, via Ollama, local) apenas converte o texto do usuário em filtros estruturados: JSON validado por esquema, usando um vocabulário fixo e versionado de moods e atividades. O nome do modelo é lido de variável de ambiente, e a troca de modelo exige reexecutar o conjunto de casos de avaliação dos prompts antes de ser adotada.
- O modelo NUNCA recebe a biblioteca inteira, NUNCA é consultado sobre o que uma música "soa" e NUNCA seleciona faixas. A seleção é feita por código determinístico sobre o cache.
- Saída inválida do modelo resulta em erro claro ou em número limitado de novas tentativas, sem fallback silencioso.
- Prompts, esquema do JSON e vocabulário de moods ficam no repositório, versionados.
- O ambiente de execução não tem GPU: os timeouts devem ser dimensionados para inferência em CPU.

### VII. Simplicidade arquitetural
- Backend em Spring Boot com Java 21 e Maven, em arquitetura hexagonal leve: o domínio (filtros, seleção, regras de moods) NÃO DEVE depender de Spring, HTTP ou SQL. Adaptadores isolam Spotify, ReccoBeats, Last.fm, Ollama e SQLite.
- O Vue 3 é somente camada de apresentação: sem regra de negócio, falando apenas com a API do próprio backend, nunca com serviços externos.
- NÃO DEVEM ser introduzidos, sem justificativa registrada em "Complexity Tracking" do plano: banco vetorial, filas, microserviços, autenticação própria ou novos frameworks.
- Toda dependência nova (biblioteca ou serviço) exige justificativa na spec ou no plano.

### VIII. Qualidade e testes (INEGOCIÁVEL)
Cobertura completa é parte da definição de pronto: nenhuma tarefa está concluída sem seus testes.

- **Unitários:** domínio (filtros, seleção, mapeamento de moods), parsing de respostas, validação do JSON do LLM.
- **Integração:** adaptadores contra HTTP simulado (WireMock) e SQLite real em arquivo temporário. Respostas simuladas DEVEM vir de fixtures baseadas em respostas reais, versionadas, para detectar mudanças de contrato.
- **Ponta a ponta do fluxo principal** (login → sincronizar → pedido → prévia → criar), com todos os serviços externos simulados.
- **Frontend:** Vitest com Vue Test Utils, cobrindo componentes e o cliente da API do backend.
- Nenhum teste automatizado PODE chamar Spotify, ReccoBeats, Last.fm ou o Ollama real. A qualidade dos prompts é avaliada por um conjunto de casos fixos, executado manualmente e fora da pipeline.
- **Limites de cobertura (JaCoCo no backend, v8/Istanbul no frontend), com falha de build abaixo deles:**
  - domínio: 100% de linhas e de branches;
  - backend como um todo: no mínimo 90% de linhas e 85% de branches;
  - frontend: no mínimo 90% de linhas.
- Exclusões da medição (classe `main`, configuração pura, DTOs sem lógica) DEVEM ser explícitas e justificadas no plano.
- Cobertura não prova a qualidade das asserções. Teste de mutação (PIT) no domínio é recomendado, não bloqueante.

### IX. Docker e reprodutibilidade
- O projeto sobe com `docker compose up`. Builds multi-estágio: Node (Vue) → JDK 21 com Maven → JRE 21 enxuto, rodando como usuário não-root.
- Ollama roda como serviço separado, usando a imagem oficial `ollama/ollama` com tag fixada (hoje `0.33.3`). Os modelos ficam em um volume nomeado externo e exclusivo do projeto (`playlistmaker_ollama_data`, declarado com `external: true`), populado uma única vez por um passo manual e documentado. NÃO DEVE haver download de modelo em tempo de execução nem no build, e o projeto NÃO DEVE compartilhar volumes com outros projetos.
- O SQLite DEVE ficar em volume nomeado. Sem isso, o refresh token e o cache se perdem a cada rebuild.
- Versões de imagens e dependências são fixadas (tag ou digest, nunca `latest`).
- O compose define limites de memória explícitos. O host tem 16 GB de RAM e pouco disco livre: o tamanho das imagens e do modelo DEVE ser registrado no plano de infraestrutura como orçamento.
- Nenhum segredo entra na imagem nem no contexto de build (`.dockerignore` obrigatório).
- Testes rodam com `mvn verify` e `npm test` no mesmo ambiente de build, sem depender da máquina do desenvolvedor.

### X. Erros acionáveis e logs seguros
- Mensagens de erro ao usuário são em português, dizem o que aconteceu e o que fazer (por exemplo, "sessão expirada, faça login de novo" ou "cota do Spotify excedida, tente em N minutos").
- Logs são estruturados e NÃO DEVEM conter tokens, chaves ou o texto integral dos pedidos do usuário além do necessário para depuração.

### XI. Idioma
Tudo em português: documentos de SDD (constituição, specs, planos, tarefas), README, comentários, mensagens de commit, interface e identificadores do código (classes, métodos, variáveis, tabelas e colunas, sem acentos).
- Exceções: nomes impostos por frameworks e APIs (campos JSON do Spotify, convenções como `Controller`/`Repository`) e termos técnicos consagrados sem tradução usual.
- O projeto DEVE manter um `glossario.md` com os termos do domínio e seu nome no código, para evitar variações.

### XII. Ambiente de desenvolvimento reproduzível
- O desenvolvimento ocorre em um Dev Container, com o `devcontainer.json` versionado no repositório. Ninguém DEVE precisar instalar JDK, Maven ou Node no host.
- O ambiente DEVE fornecer Java 21, Maven e Node, com versões fixadas, e permitir rodar `mvn verify` e `npm test` sem passos manuais além dos documentados no README.
- O repositório DEVE ficar fora do sistema de arquivos do Windows (dentro do WSL2 ou em volume do Docker), porque o acesso via montagem torna Maven e npm muito lentos.
- O acesso ao Docker a partir do Dev Container DEVE usar o daemon do host. Docker-in-Docker só com justificativa registrada, porque duplica o armazenamento de imagens num host com pouco disco.
- Nenhum segredo pode constar no `devcontainer.json`, nos Dockerfiles do ambiente ou no repositório: as variáveis vêm do ambiente do desenvolvedor.
- Imagens e features do ambiente seguem a regra de versões fixadas do Princípio IX.

## Restrições Tecnológicas

- **Backend:** Java 21, Spring Boot 3.x (versão fixada), Maven, SQLite.
- **Frontend:** Vue 3 (Composition API), servido pelo backend.
- **LLM:** Ollama local, com `gemma3:4b` como modelo padrão (configurável).
- **Fontes de dados:** Spotify (curtidas e criação de playlist), ReccoBeats (features de áudio), Last.fm (tags).
- **Empacotamento:** Docker e Docker Compose.
- **Ambiente de desenvolvimento:** Dev Container com Java 21, Maven e Node (versões fixadas), repositório fora do sistema de arquivos do Windows.

## Fluxo de Desenvolvimento (SDD)

1. Nenhuma implementação começa sem spec aprovada. Mudança de comportamento começa pela alteração da spec.
2. A fase de plano executa o "Constitution Check" abaixo. Violações só passam com justificativa registrada em "Complexity Tracking".
3. Funcionalidades são fatiadas em partes testáveis e entregues de forma incremental, na ordem: autenticação → sincronização das curtidas → enriquecimento → tradução de pedido em filtros → prévia e criação → interface.

### Constitution Check (portões do plano)
- [ ] Rede: porta publicada só em loopback; redirect URI em `127.0.0.1`.
- [ ] Segredos fora de repositório, imagem e logs.
- [ ] Escopos do Spotify dentro dos permitidos.
- [ ] Endpoints conferidos na documentação oficial vigente, com data registrada.
- [ ] Comportamento definido para faixas sem dados e para erro 429.
- [ ] LLM limitado a gerar filtros validados; seleção feita por código.
- [ ] Prévia e confirmação antes de qualquer escrita no Spotify.
- [ ] Plano de testes por camada, com os limites de cobertura aplicáveis.
- [ ] Impacto em memória e disco do compose avaliado.
- [ ] Ambiente reproduzível pelo Dev Container versionado, sem depender de ferramentas instaladas no host.
- [ ] Nenhuma tecnologia fora da lista sem justificativa.

## Governança

- Esta constituição prevalece sobre qualquer outra prática, spec ou plano do projeto.
- Emendas exigem justificativa escrita, atualização da versão e revisão das specs afetadas.
- Versionamento semântico: **MAJOR** para remoção ou redefinição incompatível de princípio; **MINOR** para novo princípio ou ampliação relevante; **PATCH** para esclarecimentos de redação.
- Toda revisão de spec ou plano DEVE verificar conformidade com os princípios acima.

**Versão**: 1.2.0 | **Ratificada em**: 2026-11-19 | **Última alteração**: 2026-11-19

**Histórico:**
- 1.2.0 — Novo Princípio XII: ambiente de desenvolvimento em Dev Container, com regras de localização do repositório, acesso ao Docker e segredos.
- 1.1.0 — Princípio IX passa a usar a imagem oficial do Ollama com modelo em volume externo exclusivo; Princípio VI torna o modelo configurável por variável de ambiente.