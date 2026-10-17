# Contexto do projeto — leia primeiro

Ponto de partida para quem retoma o projeto (pessoa ou assistente). Este arquivo é curto de propósito: o conteúdo detalhado está em `docs/`. Atualizado em 2026-10-17, conferido contra o código desta branch, que já inclui os testes dos PRs #18 e #19 (ainda não mesclados).

**Retífica** é um sistema de gestão para uma retífica de motores (hoje a Retífica Dih Soluções), já com isolamento por empresa e em produção no Railway + Neon. O uso diário é pelo PWA; a API também integra com o app Alto Medidas.

## Mapa da documentação

| Quero saber… | Leia |
|---|---|
| O que é e como começar | [README.md](README.md) |
| Módulos, stack, modelo de dados, API, autenticação, riscos | [docs/ARQUITETURA.md](docs/ARQUITETURA.md) |
| Rodar, compilar e testar localmente | [docs/DESENVOLVIMENTO.md](docs/DESENVOLVIMENTO.md) |
| Railway, Neon, variáveis, como publicar | [docs/PRODUCAO.md](docs/PRODUCAO.md) |
| O que o app faz e como o `app.js` funciona | [docs/PWA.md](docs/PWA.md) |
| Multiempresa: estado, decisões, o que falta, login automático | [docs/SAAS.md](docs/SAAS.md) |
| Armadilhas já encontradas e cuidados com os dados reais | [docs/LICOES.md](docs/LICOES.md) |
| Alto Medidas, WhatsApp, `hbm2ddl`, testes | [backend/ALTO-MEDIDAS.md](backend/ALTO-MEDIDAS.md), [backend/WHATSAPP-API.md](backend/WHATSAPP-API.md), [backend/SCHEMA-STARTUP.md](backend/SCHEMA-STARTUP.md), [backend/TESTES.md](backend/TESTES.md) |

## Estado em uma olhada

- **Em produção**: backend + PWA no Railway (`retifica-backend`), proxy que acorda o backend fora do horário, cron que o liga e desliga, banco no Neon. Fases 1a e 1b do SaaS (login e isolamento por empresa) prontas.
- **Integração com o Alto Medidas**: ligada. O lado do Firebase (PR #2 do `alto-medidas`) ainda espera aprovação e publicação; enquanto isso nenhuma OS chega.
- **WhatsApp**: código pronto, desligado (faltam as credenciais da Meta).
- **Login automático**: ativo (decisão do dono). Ver [docs/SAAS.md](docs/SAAS.md#login-automático).
- **Desktop JavaFX**: não compila desde a Fase 1b; futuro indefinido.
- **Testes**: testes automatizados de isolamento, dinheiro, JWT e Alto Medidas, em revisão nos PRs #18 e #19 (ver [backend/TESTES.md](backend/TESTES.md)). O resto da matriz de testes ainda não foi feito.

## Regras de trabalho

1. **Dados reais em produção**: nunca testar contra o banco de produção; conferir o pedido #129 no fim de qualquer sessão de testes. Detalhes em [docs/LICOES.md](docs/LICOES.md).
2. **Nenhum segredo no repositório** (ele é público). Chaves, tokens e senhas só nas variáveis do Railway ou do Firebase.
3. **Publicar**: `git push origin main` **e** `git push saas main`, conferir as refs e só então `railway up --ci --service retifica-backend`. O Railway constrói do `saas`. Ver [docs/PRODUCAO.md](docs/PRODUCAO.md#como-publicar).
4. **Compilar sem o desktop**: `mvn -pl core,backend -am ...`.
5. **Commits e PRs sem linha de co-autoria nem "gerado por"** de ferramentas.
6. **Documentação em dia**: mudou comportamento, variável, rota ou fluxo de publicação? Atualize o documento correspondente na mesma mudança.

## Pendências conhecidas

- Aprovar e publicar o PR do Alto Medidas (segredos do Firebase `DIH_ENTRADA_KEY` e `DIH_SAIDA_KEY`, `firebase deploy --only functions`, nova versão do app do Daniel) e testar com uma OS de teste.
- Decidir o login automático, o futuro do desktop e as integrações por empresa (ver [docs/SAAS.md](docs/SAAS.md)).
- Fase 2 do SaaS: onboarding de outras oficinas.
- Restante da matriz de testes (pedido, PDF, WhatsApp, sincronizador, contratos de DTO).
- Conferir no celular real: fontes, busca, logo, rascunhos e a tela do Daniel.
