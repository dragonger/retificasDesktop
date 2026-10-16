# Integração com o Alto Medidas (virabrequins do Daniel)

O Daniel finaliza os virabrequins no app **Alto Medidas** (`usabrl/alto-medidas`, Firebase). Cada OS finalizada chega aqui sozinha; a DIH marca **"Cliente pagou"** na tela *Catálogo → Virabrequins do Daniel*; quando a DIH repassa ao Daniel, ele toca em **"Confirmar Pagamento / Confirmar recebimento"** no Alto Medidas e a OS vira *Já repassado* nos dois apps. O topo da tela mostra **quanto a DIH deve ao Daniel** (clientes que já pagaram, ainda não repassado).

A integração fica **desligada** até as variáveis abaixo existirem: sem elas o servidor sobe normal, a entrada responde 503 e a tela não aparece.

## Como as duas pontas conversam

```
Alto Medidas (Cloud Functions)                    DIH (este backend)
 OS finalizada / Daniel confirma ──POST /api/integracoes/alto-medidas/os──▶ guarda a OS
   (header X-Api-Key = chave A)
 dihPagamento  ◀──────POST {osId, pago}────────── "Cliente pagou" / "Desfazer"
   (header X-Api-Key = chave B)
```

Duas chaves aleatórias (nunca no código, nunca no GitHub):

| Chave | Quem usa | Railway (backend da DIH) | Firebase (Alto Medidas) |
|---|---|---|---|
| **A** (entrada) | Alto Medidas → DIH | `ALTO_MEDIDAS_ENTRADA_KEY` | segredo `DIH_ENTRADA_KEY` |
| **B** (saída) | DIH → Alto Medidas | `ALTO_MEDIDAS_SAIDA_KEY` | segredo `DIH_SAIDA_KEY` |

Cada par precisa ter **o mesmo valor** dos dois lados. Para gerar (PowerShell): `[Convert]::ToBase64String((1..32 | % { Get-Random -Max 256 }) -as [byte[]])` — gere duas vezes.

## 1. Railway (serviço `retifica-backend`, aba Variables — pelo painel)

| Variável | Valor |
|---|---|
| `ALTO_MEDIDAS_ENTRADA_KEY` | chave A |
| `ALTO_MEDIDAS_SAIDA_KEY` | chave B |
| `ALTO_MEDIDAS_PAGAMENTO_URL` | URL da função `dihPagamento` (aparece no deploy do Firebase, algo como `https://us-central1-alto-medidas-49512.cloudfunctions.net/dihPagamento`) |
| `ALTO_MEDIDAS_EMPRESA_ID` | opcional; empresa que recebe as OS (padrão `1` = DIH) |
| `ALTO_MEDIDAS_RETRY_MS` | opcional; de quanto em quanto tempo repete o que não foi avisado (padrão 600000 = 10 min) |

Cole as chaves **pelo painel** (não pelo terminal, para não deixar no histórico).

## 2. Firebase (projeto `alto-medidas-49512`, feito por quem tem acesso — Daniel ou o dono do projeto)

```bash
firebase functions:secrets:set DIH_ENTRADA_KEY    # cola a chave A
firebase functions:secrets:set DIH_SAIDA_KEY      # cola a chave B
firebase deploy --only functions                  # na pasta do alto-medidas
```
- A URL da DIH vai no parâmetro `DIH_BASE_URL` (o padrão já é `https://retifica-backend-production.up.railway.app`).
- As funções novas: `enviarOsParaDih` (ao finalizar / ao Daniel confirmar), `reenviarOsParaDih` (de hora em hora, reenvia o que não chegou **e na primeira execução manda as OS já finalizadas dos últimos 30 dias**) e `dihPagamento`.
- Publique também a nova versão do app do Daniel (mostra o botão "Confirmar recebimento" nas OS finalizadas e mantém visíveis as que aguardam repasse).

## 3. Conferir com uma OS de teste
1. No Alto Medidas, finalize uma OS de teste → em até alguns segundos ela aparece em *Aguardando o cliente pagar*.
2. Toque em **Cliente pagou** → no Alto Medidas a OS ganha o selo **PAGO**.
3. No Alto Medidas, o Daniel toca em **Confirmar recebimento** → na DIH ela vai para *Já repassado* e sai do total devido.

## Como o total é calculado
`valor` + `valorPolir` (só se a OS tem polimento) de cada OS **paga pelo cliente e ainda não repassada**. `valor` negativo (abatimento) entra subtraindo, como no PDF do Alto Medidas. A conta é feita no servidor.

## O que acontece quando algo falha
- **DIH fora do ar quando o Daniel finaliza:** a função tenta de novo sozinha e, se não der, o reenvio de hora em hora entrega depois. Nada se perde.
- **Alto Medidas fora do ar quando a DIH marca pago:** a marcação vale na hora (e entra no total); o app mostra *"Ainda não avisado ao Alto Medidas — vai tentar de novo sozinho"* e o servidor repete a cada 10 min enquanto estiver acordado, e quando alguém abre a tela. O servidor da DIH dorme fora do horário (wake-proxy); a repetição retoma quando ele acorda.
- **OS já apagada no Alto Medidas (30 dias)** ou **já confirmada pelo Daniel:** respostas 404/409 encerram a repetição.
- A DIH guarda a **cópia permanente** de cada OS; o Alto Medidas apaga as finalizadas em 30 dias.
- **Depois que o Daniel confirma o recebimento**, a DIH não consegue mais desfazer o "cliente pagou" daquela OS (409).

## Regras de segurança
- A entrada compara a chave em tempo constante; sem `ALTO_MEDIDAS_ENTRADA_KEY` responde 503; chave errada, 401.
- A rota `/api/integracoes/**` é a única fora do login; todo o resto da tela exige o login da DIH.
- Se uma chave vazar, gere outra e troque nos dois lados.

## Aviso sobre o repositório `alto-medidas`
Ele guarda segredos dentro do código (conta de serviço do Firebase com chave privada, chave secreta do Supabase em `sync-supabase.js`, senhas da keystore Android em `android/app/build.gradle`), e as regras do Firestore liberam tudo para qualquer usuário logado. A integração **não usa nenhum desses**, mas vale trocar essas chaves e apertar as regras.
