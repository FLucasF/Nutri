# -*- coding: utf-8 -*-
"""Exercita os fluxos principais e anota todo 500.

Existe porque a suite de testes roda sobre H2 em modo de compatibilidade, e o
modo de compatibilidade nao e o PostgreSQL: ele aceita SQL que o Postgres
recusa. Cada divergencia so aparece quando alguem usa o sistema no banco de
verdade — que ate agora tem sido o cliente, uma tela por vez.
"""
import io
import json
import sys
import urllib.parse
import urllib.request
import urllib.error

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8090"
TOKEN = {"v": None}
FALHAS = []
out = io.open("varredura.txt", "w", encoding="utf-8")


def call(metodo, caminho, corpo=None, rotulo=None, aceita=None):
    dados = None
    # `aceita` existe por causa dos PDFs: pedir application/json a uma rota que
    # produz application/pdf faz o Spring recusar com "No acceptable
    # representation", e a varredura acusaria defeito onde nao ha.
    h = {"Accept": aceita or "application/json", "User-Agent": "varredura"}
    if corpo is not None:
        dados = json.dumps(corpo, ensure_ascii=False).encode("utf-8")
        h["Content-Type"] = "application/json; charset=utf-8"
    if TOKEN["v"]:
        h["Authorization"] = "Bearer " + TOKEN["v"]
    req = urllib.request.Request(BASE + caminho, data=dados, headers=h, method=metodo)
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            corpo = r.read()
            # PDF e binario: decodificar como texto estouraria e a varredura
            # acusaria defeito numa folha que saiu certa.
            if r.headers.get("Content-Type", "").startswith("application/pdf"):
                return r.status, {"pdf": len(corpo), "assinatura": corpo[:4].decode("latin-1")}
            bruto = corpo.decode("utf-8")
            return r.status, (json.loads(bruto) if bruto.strip() else None)
    except urllib.error.HTTPError as e:
        bruto = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(bruto)
        except Exception:
            return e.code, bruto
    except Exception as e:
        return "ERRO", str(e)[:150]


def testa(rotulo, metodo, caminho, corpo=None, espera=(200, 201, 204), aceita=None):
    s, b = call(metodo, caminho, corpo, aceita=aceita)
    ok = s in espera
    if not ok:
        FALHAS.append((rotulo, metodo, caminho, s,
                       json.dumps(b, ensure_ascii=False)[:200] if b else ""))
    out.write("%-4s %-46s %s\n" % ("" if ok else "!!", rotulo, s))
    out.flush()
    return b if ok else None


# -------------------------------------------------------------------- acesso
s, b = call("POST", "/api/auth/signup", {
    "name": "Varredura", "email": "varredura%d@exemplo.com" % __import__("time").time(),
    "password": "senhaSegura123", "crn": "CRN-3 00000"})
if s != 201:
    out.write("FALHOU no cadastro de conta: %s %s\n" % (s, b))
    out.close()
    raise SystemExit(1)
TOKEN["v"] = b["token"]
out.write("conta criada\n")

# ------------------------------------------------------------------ paciente
p = testa("criar paciente", "POST", "/api/patients", {
    "name": "Paciente da Varredura", "sex": "FEMALE", "dateBirth": "1990-05-20",
    "email": "pac@exemplo.com", "phone": "(11) 90000-0000",
    "occupation": "Professora", "goal": "Teste", "notes": "Observação inicial."})
pid = p["id"] if p else None

if pid:
    testa("listar pacientes", "GET", "/api/patients?size=20")
    testa("abrir paciente", "GET", "/api/patients/%d" % pid)
    # A data de nascimento vai junto: o PUT substitui o cadastro inteiro, e
    # omiti-la apagaria o campo — as equacoes que dependem de idade passariam
    # a recusar, e a varredura acusaria defeito onde ha regra funcionando.
    testa("alterar paciente", "PUT", "/api/patients/%d" % pid,
          {"name": "Paciente da Varredura", "sex": "FEMALE",
           "dateBirth": "1990-05-20", "notes": "Alterada."})
    testa("TAGs do catalogo", "GET", "/api/patient-tags")
    testa("TAGs do paciente", "GET", "/api/patients/%d/tags" % pid)
    testa("anotacao", "POST", "/api/patients/%d/notes" % pid, {"body": "Anotação."})
    testa("listar anotacoes", "GET", "/api/patients/%d/notes" % pid)
    testa("anexos", "GET", "/api/patients/%d/attachments" % pid)

# -------------------------------------------------------------------- exames
testa("paineis de exames", "GET", "/api/labtests/panels")
testa("parametros de exame", "GET", "/api/labtests/parameters")
if pid:
    testa("exames do paciente", "GET", "/api/patients/%d/labtests" % pid)

# ------------------------------------------------------------- antropometria
testa("protocolos", "GET", "/api/anthropometry/protocols")
if pid:
    av = testa("criar avaliacao", "POST", "/api/patients/%d/assessments" % pid, {
        "date": "2026-06-10", "weightKg": 72.5, "heightCm": 165,
        "skinfolds": {"TRICEPS": 20, "SUBSCAPULAR": 18, "SUPRAILIAC": 22, "ABDOMINAL": 25},
        "circumferences": [{"site": "WAIST", "side": "SINGLE", "valueCm": 80}],
        "protocolComposition": "FAULKNER",
        "equationExpenditure": "MIFFLIN_ST_JEOR", "factorActivity": 1.375})
    testa("listar avaliacoes", "GET", "/api/patients/%d/assessments" % pid)
    testa("evolucao", "GET", "/api/patients/%d/progress" % pid)
    if av:
        testa("corrigir avaliacao", "PUT", "/api/assessments/%d" % av["id"], {
            "date": "2026-06-10", "weightKg": 71.0, "heightCm": 165,
            "circumferences": [{"site": "WAIST", "side": "SINGLE", "valueCm": 79}]})

# ---------------------------------------------------------- calculo energetico
testa("opcoes de equacao", "GET", "/api/energy-plans/options")
if pid:
    testa("criar calculo", "POST", "/api/energy-plans", {
        "patientId": pid, "name": "Meta", "date": "2026-09-01",
        "weightKg": 72.5, "heightCm": 165, "activityLevel": "LOW_ACTIVE",
        "equations": ["MIFFLIN_ST_JEOR", "EER_2023"]})
    testa("listar calculos", "GET", "/api/patients/%d/energy-plans" % pid)

# -------------------------------------------------------------------- anamnese
testa("campos de anamnese", "GET", "/api/anamnesis-fields")
if pid:
    an = testa("criar anamnese", "POST", "/api/anamneses", {
        "patientId": pid, "name": "Inicial", "date": "2026-06-10",
        "body": json.dumps({"type": "doc", "content": [
            {"type": "paragraph", "content": [{"type": "text", "text": "Texto."}]}]})})
    testa("listar anamneses", "GET", "/api/patients/%d/anamneses" % pid)

# ------------------------------------------------------------------- alimentos
comida = testa("buscar alimento", "GET",
               "/api/foods?term=%s&size=5" % urllib.parse.quote("arroz"))
testa("grupos de alimento", "GET", "/api/foods/groups")
fid = comida["content"][0]["id"] if comida and comida.get("content") else None

# ------------------------------------------------------------------- cardapio
if pid and fid:
    plano = testa("criar plano", "POST", "/api/prescriptions", {
        "title": "Plano da varredura", "patientId": pid, "method": "FOODS",
        "handouts": "Beber água.",
        "meals": [{"name": "Café da Manhã", "items": [
            {"foodId": fid, "quantity": 100, "notes": "Observação.",
             "substitutions": [{"foodId": fid, "quantity": 80,
                                "description": "Metade"}]}]}]})
    if plano:
        testa("abrir plano", "GET", "/api/prescriptions/%d" % plano["id"])
        testa("listar planos", "GET", "/api/prescriptions?size=10")
        testa("publicar", "POST", "/api/prescriptions/%d/publish" % plano["id"])
        testa("duplicar", "POST", "/api/prescriptions/%d/duplicate" % plano["id"], None,
              (200, 201))
        ident = (call("GET", "/api/prescriptions/%d" % plano["id"])[1] or {}).get(
            "publicIdentifier")
        if ident:
            testa("plano publico", "GET", "/api/public/plans/%s" % ident)
testa("refeicoes favoritas", "GET", "/api/meal-favorites")

# ---------------------------------------------------------------------- agenda
testa("tipos de atendimento", "GET", "/api/schedule/types")
testa("agenda do dia", "GET", "/api/schedule/day?date=2026-09-24")
if pid:
    testa("agendar", "POST", "/api/schedule", {
        "patientId": pid, "start": "2026-09-24T09:00:00",
        "durationMinutes": 60, "type": "FIRST_CONSULTATION"})

# ------------------------------------------------------------ outros catalogos
testa("orientacoes", "GET", "/api/handouts")
testa("questionarios", "GET", "/api/questionnaires")
testa("financeiro", "GET", "/api/finance/transactions?size=10")
testa("receitas", "GET", "/api/recipes?size=5")
testa("equipe", "GET", "/api/users")

# ----------------------------------------------------------------- exames
# Lancar resultado e pedir a serie historica: e onde ja apareceu um 500 por
# parametro sem unidade padrao, e o catalogo tem varios assim.
if pid:
    params = call("GET", "/api/labtests/parameters")[1]
    alvo = None
    for p_ in (params or []):
        if not p_.get("unitStandard"):
            alvo = p_
            break
    alvo = alvo or (params[0] if params else None)
    if alvo:
        testa("lancar resultado", "POST", "/api/patients/%d/labtests" % pid, {
            "parameterId": alvo["id"], "dateCollection": "2026-06-01", "value": 95})
        testa("lancar segundo", "POST", "/api/patients/%d/labtests" % pid, {
            "parameterId": alvo["id"], "dateCollection": "2026-09-01", "value": 88})
        testa("serie historica", "GET",
              "/api/patients/%d/labtests/series/%d" % (pid, alvo["id"]))
        testa("solicitar exames", "POST", "/api/patients/%d/requests-from-labtest" % pid,
              {"date": "2026-09-01", "parameterIds": [alvo["id"]]}, (200, 201))
        testa("listar solicitacoes", "GET",
              "/api/patients/%d/requests-from-labtest" % pid)

# -------------------------------------------------------------------- PDFs
# Sao o que o paciente recebe na mao. Uma consulta quebrada aqui so aparece na
# hora de imprimir, que e a pior hora.
if pid:
    testa("PDF do relatorio de evolucao", "GET",
          "/api/patients/%d/anthropometry-report" % pid, None, (200, 422),
          aceita="application/pdf")
if pid and 'an' in dir() and an:
    testa("PDF da anamnese", "GET", "/api/anamneses/%d/pdf" % an["id"],
          None, (200,), aceita="application/pdf")
if pid and fid and 'plano' in dir() and plano:
    testa("PDF do cardapio", "GET", "/api/prescriptions/%d/pdf" % plano["id"],
          None, (200,), aceita="application/pdf")

# ------------------------------------------------------------- financeiro
testa("criar lancamento", "POST", "/api/finance/transactions", {
    "type": "INCOME", "value": 200.0, "accrual": "2026-09-01",
    "category": "Consulta", "description": "Primeira consulta"}, (200, 201))
testa("resumo financeiro", "GET", "/api/finance/summary?from=2026-09-01&to=2026-09-30")
testa("vencidos", "GET", "/api/finance/overdue")

# ------------------------------------------------------------- questionario
# O fluxo inteiro: criar, enviar ao paciente, responder pelo link publico e
# ler a resposta. E o unico caminho do sistema em que alguem sem conta escreve
# no banco.
q = testa("criar questionario", "POST", "/api/questionnaires", {
    "name": "Pre-consulta da varredura", "scorable": False,
    "questions": [
        {"statement": "Como se alimenta hoje?", "type": "TEXT", "required": True},
        {"statement": "Quantas refeicoes por dia?", "type": "NUMBER", "required": False},
    ]})
if q and pid:
    envio = testa("enviar ao paciente", "POST", "/api/patients/%d/questionnaires" % pid,
                  {"questionnaireId": q["id"]})
    if envio and envio.get("publicIdentifier"):
        ident_q = envio["publicIdentifier"]
        formulario = testa("abrir formulario publico", "GET",
                           "/api/public/questionnaires/%s" % ident_q)
        if formulario:
            perguntas = formulario.get("questions") or []
            testa("responder formulario", "POST",
                  "/api/public/questionnaires/%s" % ident_q,
                  {"answers": [{"questionId": x["id"], "value": "Resposta"}
                               for x in perguntas]}, (200, 204))
        testa("ler respostas", "GET", "/api/patients/%d/questionnaires" % pid)

# ---------------------------------------------------------------------- saida
out.write("\n")
if FALHAS:
    out.write("FALHAS (%d):\n" % len(FALHAS))
    for rotulo, metodo, caminho, s, corpo in FALHAS:
        out.write("  %-40s %-6s %-46s %s\n     %s\n" % (rotulo, metodo, caminho, s, corpo))
else:
    out.write("nenhuma falha\n")
out.close()
print(io.open("varredura.txt", encoding="utf-8").read())
