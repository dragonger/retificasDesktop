# Testes do backend

Primeiros testes automatizados do projeto: auditoria do isolamento entre empresas.
Eles rodam contra um **Postgres descartavel local**, nunca contra o banco de producao.

## Como rodar

```bash
docker compose -f docker-compose.test.yml up -d --wait   # Postgres em localhost:5544, banco retificas_test, sem volume
mvn -pl core,backend -am test
docker compose -f docker-compose.test.yml down
```

Cada `up` comeca de um banco vazio. O Maven do IntelliJ serve (ver CONTEXTO.md).
O build da imagem Docker continua com `-DskipTests`.

## O que protege o banco de producao

- O `maven-surefire-plugin` (backend/pom.xml) define `DATABASE_URL`, `JWT_SECRET` etc. **sinteticos** para o
  processo de teste, sobrescrevendo o ambiente da sua maquina, e zera `WHATSAPP_*` e `ALTO_MEDIDAS_PAGAMENTO_URL`.
- `GuardaBancoLocal` aborta **antes de abrir qualquer conexao** se `DATABASE_URL` nao for
  `jdbc:postgresql://localhost|127.0.0.1:PORTA/retificas_test`, ou se alguma integracao externa estiver ligada.
  Ele proprio e testado em `GuardaBancoLocalTest`.
- `user.home` dos testes aponta para `backend/target/test-home` (o bootstrap nao escreve credenciais no seu home).
- Os testes usam e-mails `@example.test` e dados com sufixo aleatorio; nao usam ids reais (nada de pedido #129).

## Os testes

| Classe | O que garante |
|---|---|
| `IsolamentoEntreEmpresasTest` | Duas empresas (A = empresa 1, dona da integracao Alto Medidas; B = outra). O token de B nao le, altera, finaliza, apaga nem gera PDF de nada de A, e as listas e agregados (pedidos, encerrados, dashboard, clientes, cabecotes, catalogos, logo, Alto Medidas) nao misturam empresas. O recurso de A continua intacto depois de cada ataque. |
| `MatrizDeEndpointsTest` | Todo endpoint `/api/**` e `/health` esta classificado como `PUBLICO` ou `ISOLADO_POR_EMPRESA`; **endpoint novo sem classificacao quebra o teste**. Todo `/api/**` nao publico responde 401 sem token (ou com token malformado). Integracao do Alto Medidas recusa chave errada/vazia. |
| `GuardaBancoLocalTest` | A trava anti-producao recusa banco remoto, outro nome de banco e integracoes ligadas. |

Verificado por mutacao: remover o filtro de empresa de `Cliente` (buscar, listar, deletar), `Cabecote.deletar`,
`ServicoCatalogo.deletar`, `PecaCatalogo.deletar`, `Pedido` (carregar com itens, deletar) e `OsAltoMedidas.listar`
deixa pelo menos um teste vermelho. `PedidoRepository.buscarPorId(id, empresaId)` nao e chamado por nenhum
controller do backend (so pelo desktop), por isso nenhum teste o exercita.

## Comportamento atual registrado (nao e aprovacao)

- `autoLoginPublicoEstaAtivoHoje`: `POST /api/auth/auto-login` responde 200 sem senha. Foi um risco aceito pelo dono
  em 12/09; quando for desligado, o teste deve passar a esperar 404.
- `pedidoNovoNaoVinculaClienteNemComponenteDeOutraEmpresa` aceita 200 (pedido criado **sem** o cliente de A)
  ou 400/404. Hoje a API responde 200 e ignora o cliente de outra empresa; se o comportamento passar a ser 400,
  o teste continua valendo.

## Fora desta etapa

Regras de pedido (total, desconto), JWT (expiracao/renovacao), Alto Medidas (upsert, devido, mes), WhatsApp, PDF e
build do desktop: ver a matriz de testes P0/P1/P2 do relatorio de analise. O desktop continua fora do build.
