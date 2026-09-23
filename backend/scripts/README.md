# Varredura contra PostgreSQL

```bash
# 1. um PostgreSQL vazio
docker run -d --name pg-teste -e POSTGRES_PASSWORD=t -e POSTGRES_USER=n \
  -e POSTGRES_DB=n -p 5433:5432 postgres:16-alpine

# 2. a aplicação, com o mesmo limite de memória do plano gratuito
docker build -t nutriplan:teste .
docker run -d --name nutri-pg -m 512m -p 8090:8080 \
  -e DB_URL="jdbc:postgresql://host.docker.internal:5433/n" \
  -e DB_USER=n -e DB_PASSWORD=t \
  -e NUTRIPLAN_JWT_SECRET="qualquer-coisa-com-32-caracteres-ou-mais" \
  nutriplan:teste

# 3. a varredura
python backend/scripts/varredura-postgres.py http://localhost:8090
```

Também serve contra um ambiente já publicado:

```bash
python backend/scripts/varredura-postgres.py https://seu-servico.onrender.com
```

Ela cria a própria conta a cada execução, então não suja nada que já exista.

## Por que isto existe

A suíte de testes roda sobre **H2 em modo de compatibilidade com PostgreSQL**,
e modo de compatibilidade não é PostgreSQL: ele aceita SQL que o banco real
recusa. A suíte inteira passava verde enquanto cinco consultas estavam
quebradas em produção.

O que apareceu só aqui:

| Sintoma | Causa |
|---|---|
| `function lower(bytea) does not exist` | termo de busca nulo dentro de `concat` — o Postgres não infere o tipo e assume `bytea`. Quatro listagens paginadas caíam com 500 |
| `for SELECT DISTINCT, ORDER BY expressions must appear in select list` | os painéis de exames ordenavam por um `case when` fora da lista de seleção |
| `syntax error at or near "||"` | `CHAR(10)` é função no H2 e declaração de tipo no Postgres. As migrações V12 e V13 não rodavam |

Nenhum dos três é detectável com H2. Rode isto antes de cada publicação.

## O que ela não cobre

Só o servidor — é o que diverge entre os bancos. A tela tem os testes de ponta
a ponta em `frontend/e2e`.
