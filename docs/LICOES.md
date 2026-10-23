# Lições e cuidados

Armadilhas já encontradas, para não repetir.

## Dados reais em produção

O banco de produção tem dados reais: um cliente real (o próprio dono), o **pedido #129**, 12 componentes técnicos Fiat (com faixas de medida móvel e fixo), cerca de 28 serviços e 14 peças de catálogo (a maioria com valor R$ 0,00 até o dono preencher), além das OS do Alto Medidas.

- **Nunca rode testes, scripts nem experimentos contra a produção.** Use o Postgres local ou o descartável de teste (`docker-compose.test.yml`); os testes automatizados têm uma trava que aborta se o banco não for local.
- O pedido #129 já foi finalizado por engano mais de uma vez por testes que tocaram o id errado. Se algo desse tipo acontecer, desfaça pelo banco: `UPDATE PEDIDO SET DATENTREGA = NULL WHERE ID = 129` (usando `psql`, não mais o H2).
- **Ao extrair o `id` de uma resposta JSON de `POST /api/pedidos`**, lembre que o objeto `cliente` aninhado também tem `"id"`, e ele aparece **antes** do `id` do pedido. Um `grep` ingênuo pega o id errado; parseie o JSON e confira a identidade antes de qualquer `PUT` ou `DELETE`.
- Dados de teste criados à mão devem ser apagados pela API logo depois.

## Spring Boot e Hibernate

- **`@PathVariable Long id` sem nome** quebra em runtime no Spring 7 (`Name for argument ... not specified`): é preciso `-parameters` (`maven.compiler.parameters=true`). Isso não aparece em `mvn compile`; só chamando `PUT`/`DELETE .../{id}`. Teste esses caminhos depois de qualquer upgrade grande.
- **Coluna nova com `hbm2ddl=update`** deixa as linhas antigas com `NULL`. Use getters defensivos e backfill ao adicionar colunas.
- **Pool de conexões do Hibernate** (o interno) não valida conexões e deu 500 depois que o Neon suspendeu o compute; por isso o HikariCP.
- Arquivos de `core/` tiveram caracteres corrompidos (UTF-8 lido como Windows-1252 e salvo de novo). Já foi corrigido; ao editar arquivos `.java` no Windows, mantenha UTF-8 sem BOM.

## PWA e navegador

- O service worker cacheia agressivamente; ao testar, desregistre-o e limpe os caches.
- **`window.open()` depois de um `await`** é bloqueado como pop-up. Mais grave: `window.open()` consome a "ativação transitória" do toque, e o `navigator.share()` chamado em seguida rejeita com `NotAllowedError`. Como `navigator.canShare()` é síncrono, decida **antes** de abrir qualquer aba; só reserve a aba em branco quando o compartilhamento nativo nem vai ser tentado.
- `navigator.share()` com arquivo no iOS/Safari exige estar bem perto do gesto do usuário (por isso o PDF é pré-buscado).
- Compartilhar com `files` junto de `text` ou `title` faz o WhatsApp descartar o PDF e mandar só o texto; mande apenas `files`.
- Duplo toque no compartilhar dá `InvalidStateError`; há uma trava (`compartilhamentoEmAndamento`).
- Na automação de navegador, o clique por coordenada às vezes não registra; disparar `elemento.click()` por JS é mais confiável.

## Railway

- **Mudar variável ou "redeploy" refaz o build a partir do Git** (remoto `SAS-retifica`), não do último `railway up`. Envie para os dois remotos antes (ver [PRODUCAO.md](PRODUCAO.md#como-publicar)).
- A porta em produção é a `8080` (variável `PORT` injetada pelo Railway), não a `8443`. Perdeu-se muito tempo "depurando a rede privada" quando era só a porta errada.
- Já existiu um serviço duplicado (`SAS-retifica`, criado ao conectar o repositório direto pelo painel, sem variáveis, que ficou crashado). Antes de criar outro serviço, confirme com `railway service list` que só existe um.
- O `railway` CLI não vem no PATH da sessão de shell.

## Integração com o Alto Medidas

O gatilho `enviarOsParaDih` do Alto Medidas roda também quando a própria função grava `dihEnviadaEm` e quando `dihPagamento` grava. Ele sai cedo porque nenhum dos dois altera `finalizado` nem `pagtoValidadoPor`: **não mude isso sem reavaliar o laço**.

## Processo

- Comandos encadeados com `&&` escondem erros: já aconteceu de um `git fetch` com argumento errado interromper a cadeia enquanto o `railway up` seguinte publicou código antigo.
- No PowerShell 5.1, `Set-Content -Encoding utf8` grava BOM, e o `javac` quebra com `illegal character: '﻿'`.
- O prefixo `!` para rodar comando no chat é uma instrução do Claude Code; ao colar um comando em um terminal PowerShell separado, não inclua o `!`.

- **Reconhecimento de voz repete o texto no Android.** No Chrome do Android o `SpeechRecognition` contínuo reenvia resultados finais e o `resultIndex` não é confiável; acumular por evento duplicava a fala. O `ditadoVoz` (app.js) monta o texto da sessão do zero a cada evento (e, como o Android entrega cada hipótese parcial como um resultado novo, um resultado que continua o anterior o substitui em vez de somar), fixa a base só em edição de teclado ou reinício da escuta e ignora o `input` que ele mesmo gera. Só dá para ver o defeito num aparelho real.
