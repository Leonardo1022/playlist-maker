# Feature Specification: Conectar Conta e Sincronizar Músicas Curtidas do Spotify

**Feature Branch**: `001-sincronizar-curtidas-spotify`

**Created**: 2026-11-24

**Status**: Draft

**Input**: User description: "Como único usuário da aplicação, quero conectar minha conta do Spotify e sincronizar minhas músicas curtidas para uma base local, para poder consultá-las depois sem depender do Spotify a cada pedido.

Comportamentos esperados:
- Conectar a conta pedindo apenas as permissões necessárias para ler as curtidas e criar playlists privadas.
- Manter a sessão entre execuções, sem pedir login toda vez, e avisar claramente quando a sessão expirar.
- Sincronizar sob demanda, de forma incremental: incluir as faixas novas e remover as que deixei de curtir.
- Ignorar arquivos locais, que não têm identificador no Spotify.
- Mostrar o resultado da sincronização: total de curtidas, novas e removidas.
- Se o Spotify limitar as requisições, aguardar o tempo indicado; se o tempo for longo demais, avisar em quanto tempo tentar de novo.
- Funciona para bibliotecas de 700 ou mais faixas."

## Clarifications

### Session 2026-11-24

- Q: Como o usuário deve acompanhar o andamento da sincronização na interface enquanto ela estiver sendo executada? (FR-010) → A: Indicador com contagem de faixas lidas em tempo real (ex.: "Processando X faixas...").
- Q: Caso a sincronização seja interrompida por falha de rede ou tempo de espera prolongado, como deve ser o comportamento da próxima tentativa de sincronização? (FR-008) → A: Executar uma nova sincronização do início, atualizando a base de forma idempotente sem salvar cursores intermediários.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Conexão inicial da conta Spotify e persistência de sessão (Priority: P1)

Como único usuário da aplicação, quero conectar minha conta do Spotify autorizando apenas as permissões necessárias para gerenciar playlists e ler curtidas, mantendo minha sessão ativa entre reinicializações da aplicação para não precisar efetuar login repetidamente.

**Why this priority**: É o requisito primordial e indispensável para qualquer operação com o Spotify. Sem a autorização com escopos mínimos e a retenção de sessão, nenhuma sincronização ou consulta é viável.

**Independent Test**: Pode ser testado de forma independente iniciando o fluxo de conexão da conta com os escopos restritos (`user-library-read` e `playlist-modify-private`), concluindo o consentimento e reiniciando a aplicação; o sistema deve reconhecer a sessão como ativa e autenticada sem solicitar credenciais novamente.

**Acceptance Scenarios**:

1. **Given** que a aplicação não possui uma conta conectada, **When** o usuário solicita conectar sua conta, **Then** o sistema direciona para autorização solicitando estritamente as permissões de leitura da biblioteca (`user-library-read`) e criação/edição de playlists privadas (`playlist-modify-private`).
2. **Given** que o usuário concluiu a autorização no serviço externo com sucesso, **When** retorna para a aplicação, **Then** o sistema registra a conexão ativa e exibe o estado conectado.
3. **Given** que a conta foi conectada em uma execução anterior e a permissão permanece válida, **When** a aplicação é reiniciada ou reaberta, **Then** o estado de conexão é restabelecido automaticamente sem exigir nova ação de login.

---

### User Story 2 - Sincronização incremental de músicas curtidas sob demanda (Priority: P1)

Como usuário conectado, quero solicitar sob demanda a sincronização das minhas músicas curtidas para uma base local, processando acervos grandes, ignorando arquivos locais sem identificador, adicionando músicas novas e excluindo músicas descurtidas, com exibição de um resumo claro do resultado.

**Why this priority**: É a proposta de valor central da funcionalidade: garantir autonomia e rapidez nas consultas locais das faixas curtidas sem dependência contínua da API externa a cada requisição.

**Independent Test**: Pode ser testado de forma independente disparando a sincronização em uma conta com acervo de 700 ou mais músicas curtidas (incluindo arquivos locais): o sistema deve ignorar os arquivos locais, salvar todas as faixas com identificador Spotify e exibir o resumo exato (total, novas e removidas). Posteriormente, ao adicionar uma nova curtida ou descurtir uma música existente no Spotify, uma nova sincronização deve refletir a adição ou a remoção com exatidão no resumo e na base local.

**Acceptance Scenarios**:

1. **Given** uma conta conectada contendo 700 ou mais músicas curtidas e arquivos locais sem identificador externo, **When** o usuário aciona a sincronização sob demanda, **Then** o sistema processa todo o catálogo, ignora silenciosamente os arquivos locais, armazena todas as faixas válidas na base local e apresenta o resumo informando o total de músicas curtidas, a quantidade de músicas novas adicionadas e zero removidas.
2. **Given** uma base local previamente sincronizada, **When** o usuário adiciona novas faixas às curtidas no Spotify e aciona a sincronização sob demanda, **Then** o sistema identifica e inclui apenas as faixas novas na base local e apresenta o resumo com a contagem de novas adições e o novo total.
3. **Given** uma base local previamente sincronizada, **When** o usuário remove faixas das suas curtidas no Spotify e aciona a sincronização sob demanda, **Then** o sistema identifica as ausências, remove as respectivas faixas da base local e exibe o total de faixas removidas e o total remanescente no resumo.
4. **Given** que uma sincronização foi concluída com sucesso, **When** o usuário consulta suas músicas curtidas na aplicação, **Then** os dados são obtidos diretamente da base local sem realizar chamadas externas ao Spotify.
5. **Given** uma sincronização em andamento, **When** o sistema processa os lotes de faixas, **Then** a interface apresenta um indicador visual com a contagem de faixas lidas em tempo real (ex.: "Processando X faixas...") e mantém novas ações bloqueadas até a finalização.

---

### User Story 3 - Tratamento de limitação de requisições (Rate Limit) (Priority: P2)

Como usuário executando a sincronização, quero que o sistema lide de maneira resiliente e transparente quando o Spotify limitar a taxa de requisições, aguardando de forma automática em intervalos curtos ou informando claramente o tempo de espera necessário quando o intervalo for prolongado.

**Why this priority**: Garante robustez durante o processamento de grandes volumes de faixas (700+ faixas) e transparência para o usuário caso os limites de cota da API sejam atingidos.

**Independent Test**: Pode ser testado de forma independente simulando limites de taxa: para esperas curtas (até 60 segundos), a sincronização aguarda e prossegue sem intervenção; para esperas longas (> 60 segundos), o sistema interrompe a sincronização preservando os dados já salvos e informa o tempo restante para nova tentativa.

**Acceptance Scenarios**:

1. **Given** uma sincronização em andamento, **When** o provedor indicar limitação de taxa com tempo de espera curto (até 60 segundos), **Then** o sistema pausa as requisições pelo período exato solicitado e retoma a sincronização a partir do ponto de interrupção, finalizando o processo com sucesso.
2. **Given** uma sincronização em andamento, **When** o provedor indicar limitação de taxa com tempo de espera longo (superior a 60 segundos), **Then** o sistema suspende a sincronização preservando os lotes de músicas já gravados e exibe um aviso em português informando em quantos minutos ou segundos o usuário deve tentar novamente.
3. **Given** uma sincronização anterior interrompida por cota prolongada ou erro de rede, **When** o usuário aciona uma nova sincronização após o tempo de espera, **Then** o sistema executa o processo completo do início de forma idempotente, atualizando a base local sem gerar duplicações.

---

### User Story 4 - Tratamento e recuperação de sessão expirada ou revogada (Priority: P2)

Como usuário, quero receber avisos compreensíveis e orientações acionáveis caso a sessão com o Spotify expire ou tenha suas permissões revogadas, mantendo meus dados locais intactos.

**Why this priority**: Evita frustração do usuário com falhas genéricas de autenticação, oferecendo um caminho direto para restabelecer a conexão sem perder o histórico já sincronizado.

**Independent Test**: Pode ser testado de forma independente invalidando ou revogando as credenciais da sessão; ao tentar sincronizar, a aplicação deve identificar a expiração, apresentar aviso em português solicitando nova conexão e preservar os dados locais previamente salvos.

**Acceptance Scenarios**:

1. **Given** uma sessão cujo token temporário expirou, **When** o usuário executa uma sincronização, **Then** o sistema renova a credencial automaticamente em segundo plano e realiza a sincronização normalmente sem interrupções.
2. **Given** uma sessão cuja autorização foi revogada ou expirou de modo não renovável, **When** o usuário tenta sincronizar ou abrir a aplicação, **Then** o sistema exibe mensagem clara em português informando que a sessão expirou e orientando a realizar novo login, mantendo intactas as músicas salvas na base local.

---

### Edge Cases

- **Presença de arquivos locais**: Músicas originadas de arquivos locais do usuário no aplicativo Spotify não possuem identificador de catálogo externo. O sistema deve ignorar essas entradas sem tratá-las como erros e sem inseri-las na base de dados local.
- **Biblioteca vazia**: Caso o usuário conecte uma conta sem nenhuma música curtida, a sincronização deve transcorrer normalmente e concluir com resumo indicando total 0, novas 0 e removidas 0.
- **Queda de conexão de internet e retomada**: Se a rede falhar durante o processamento, as faixas já salvas permanecem íntegras na base local e uma mensagem de erro é exibida. Na tentativa subsequente, a sincronização recomeça do início de forma idempotente, sem necessidade de cursores intermediários.
- **Alteração de curtidas simultânea à sincronização**: Caso o usuário curta ou descurta músicas enquanto a sincronização está paginando a biblioteca, o tratamento por identificador único deve garantir idempotência, evitando duplicidade de registros na base local.
- **Interrupção voluntária ou cancelamento da autorização**: Se o usuário fechar a janela de consentimento ou recusar as permissões solicitadas, a aplicação deve retornar ao estado não conectado sem inconsistências, exibindo aviso claro de que a conexão não foi concluída.
- **Conta sem plano Spotify Premium ativo**: Caso o usuário conecte uma conta que não possua Spotify Premium ativo (ou a API do Spotify retorne erro de restrição de produto/desenvolvedor), o sistema deve identificar a condição e apresentar mensagem clara em português informando que a aplicação opera em modo de desenvolvimento do Spotify, exigindo conta Premium ativa.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema DEVE permitir ao usuário conectar sua conta do Spotify via navegador requisitando unicamente as permissões `user-library-read` (leitura da biblioteca) e `playlist-modify-private` (criação e edição de playlists privadas).
- **FR-002**: O sistema DEVE persistir a sessão de autorização localmente de forma segura, garantindo que a aplicação permaneça conectada entre diferentes execuções sem requerer login a cada inicialização.
- **FR-003**: O sistema DEVE renovar a credencial de acesso automaticamente em segundo plano antes ou durante as operações com o provedor, sempre que o acesso permanecer válido.
- **FR-004**: O sistema DEVE apresentar mensagem clara, amigável e acionável em português quando a sessão expirar de forma definitiva ou for revogada, orientando o usuário a reconectar sua conta.
- **FR-005**: O sistema DEVE disponibilizar ação explícita para que o usuário execute a sincronização de suas músicas curtidas sob demanda.
- **FR-006**: O sistema DEVE ser capaz de sincronizar acervos de qualquer tamanho, operando de forma estável para bibliotecas com 700 ou mais músicas curtidas.
- **FR-007**: O sistema DEVE identificar e ignorar arquivos locais que não possuam identificador oficial do catálogo do Spotify durante a leitura da biblioteca.
- **FR-008**: O sistema DEVE realizar a sincronização de forma incremental: na primeira sincronização, incluir todas as faixas válidas; nas sincronizações subsequentes, incluir apenas novas faixas curtidas e remover da base local aquelas que deixaram de estar curtidas no Spotify.
- **FR-009**: O sistema NÃO DEVE realizar nenhuma operação de escrita, modificação ou remoção de faixas curtidas na conta do Spotify do usuário (leitura estrita da biblioteca).
- **FR-010**: O sistema DEVE apresentar ao final de cada sincronização um resumo contendo: o total de faixas curtidas na base local, a quantidade de faixas novas adicionadas e a quantidade de faixas removidas.
- **FR-011**: O sistema DEVE persistir um histórico estruturado de cada sincronização executada (contendo data/hora de início e fim, status, total de faixas, novas e removidas) e registrar para cada faixa a data/hora da coleta e a respectiva fonte de dados.
- **FR-012**: O sistema DEVE utilizar a base local como repositório para todas as consultas de músicas curtidas do usuário, sem emitir chamadas ao serviço externo durante tais consultas.
- **FR-013**: Em caso de limitação de taxa de requisições pelo Spotify com tempo de espera curto (até 60 segundos), o sistema DEVE aguardar automaticamente o tempo solicitado e retomar a sincronização sem intervenção manual.
- **FR-014**: Em caso de limitação de taxa de requisições pelo Spotify com tempo de espera prolongado (superior a 60 segundos), o sistema DEVE interromper a sincronização preservando os dados já gravados e exibir notificação em português informando em quanto tempo o usuário poderá tentar novamente.
- **FR-015**: O sistema DEVE executar a sincronização em segundo plano de forma assíncrona sob demanda e DEVE bloquear execuções simultâneas por meio de controle de concorrência exclusivo, rejeitando novas tentativas de disparo com aviso claro de que uma sincronização já está em andamento, enquanto exibe na interface um indicador de progresso com a contagem de faixas lidas em tempo real (ex.: "Processando X faixas...").
- **FR-016**: Em caso de interrupção anterior por falha de rede ou esgotamento de tempo de espera, o sistema DEVE reiniciar o processo a partir do início na sincronização seguinte, garantindo atualização idempotente da base local sem armazenar cursores parciais de paginação.
- **FR-017**: O sistema DEVE identificar contas sem assinatura Spotify Premium ativa (ou restrições de produto da API) e exibir mensagem explicativa em português orientando o usuário sobre a necessidade de plano Premium para a utilização da aplicação.

### Key Entities *(include if feature involves data)*

- **SessaoUsuario**: Representa a sessão de autenticação e autorização do usuário perante o Spotify. Contém atributos conceituais sobre validade do acesso, data/hora da última autorização e estado da conexão (ativa, expirada, revogada).
- **FaixaCurtida**: Representa uma música curtida do usuário armazenada na base local. Contém atributos conceituais como identificador único no serviço externo, título da música, artistas, álbum, duração, data em que foi curtida no serviço externo e data da última sincronização local.
- **RegistroSincronizacao**: Representa o histórico persistido de cada sincronização sob demanda (atendendo aos requisitos FR-010 e FR-011). Contém atributos conceituais de data/hora de início e término, estado final (sucesso, interrompido por cota, erro de rede), total final de músicas curtidas na base local, quantidade de faixas novas inseridas e quantidade de faixas removidas.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: O usuário consegue autorizar a conta e concluir a sincronização inicial completa de uma biblioteca com 700 faixas em menos de 60 segundos (em condições normais de rede e sem limitação de taxa imposta pelo provedor).
- **SC-002**: Em sincronizações subsequentes sem alterações nas curtidas no Spotify, a operação conclui em menos de 10 segundos para acervos de 700 ou mais músicas, reportando exatamente 0 faixas novas e 0 faixas removidas.
- **SC-003**: 100% das faixas com identificador Spotify presentes na biblioteca de curtidas são registradas com sucesso na base local, com 0% de arquivos locais sem identificador incluídos na base.
- **SC-004**: 100% das faixas descurtidas no serviço externo são identificadas e removidas da base local na sincronização subsequente.
- **SC-005**: Ao reiniciar a aplicação, a sessão do usuário é restaurada com sucesso em 100% dos testes sem solicitação de novo login (desde que a autorização não tenha sido revogada).
- **SC-006**: Ao receber resposta de restrição de taxa do provedor, o sistema respeita 100% do período de espera indicado antes de enviar qualquer nova requisição, fornecendo feedback claro sobre o tempo de espera.
- **SC-007**: Durante 100% do tempo de execução de uma sincronização sob demanda, a interface atualiza visualmente a contagem de faixas processadas e impede disparos simultâneos concorrentes.

## Assumptions

- O usuário possui uma conta ativa no Spotify com acesso à sua biblioteca de músicas curtidas.
- O tempo limite estipulado para espera automática em caso de restrição de requisições (Rate Limit) é de 60 segundos; esperas superiores são consideradas prolongadas e justificam suspender o processo com aviso claro ao usuário para não reter a aplicação indefinidamente.
- A sincronização das músicas curtidas é realizada sob demanda através de acionamento explícito pelo usuário na interface, sem rotinas autônomas ou periódicas em segundo plano.
- A aplicação é de uso estritamente pessoal e local, destinada a um único usuário em sua própria máquina.
- Consultas subsequentes sobre as músicas curtidas utilizam exclusivamente a base de dados local, preservando autonomia e evitando dependência contínua de requisições de rede ao Spotify.
