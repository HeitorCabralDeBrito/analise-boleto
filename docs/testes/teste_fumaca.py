"""Teste rápido: confere se cada funcionalidade principal responde.

Uso (com o app rodando):  python docs/testes/teste_fumaca.py  [endereço]
Endereço padrão: http://localhost:8080. Só usa a biblioteca padrão do Python (nada para instalar).
"""
import json
import sys
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

BASE = sys.argv[1] if len(sys.argv) > 1 else 'http://localhost:8080'
# Boletos fictícios ao lado deste script (docs/testes/amostras)
AMOSTRAS = Path(__file__).resolve().parent / 'amostras'
LINHA = '23790910162345678901723456000001316260000015000'


def chamar(caminho, token=None, corpo=None, bruto=None, tipo='application/json'):
    cab = {'Content-Type': tipo}
    if token:
        cab['Authorization'] = 'Bearer ' + token
    dados = bruto if bruto is not None else (json.dumps(corpo).encode() if corpo is not None else None)
    req = urllib.request.Request(BASE + caminho, data=dados, headers=cab, method='POST' if dados is not None else 'GET')
    inicio = time.perf_counter()
    try:
        r = urllib.request.urlopen(req, timeout=180)
        corpo_resp = r.read()
        return r.status, (json.loads(corpo_resp) if corpo_resp[:1] in (b'{', b'[') else corpo_resp[:60]), round((time.perf_counter() - inicio) * 1000)
    except urllib.error.HTTPError as e:
        return e.code, e.read()[:200], round((time.perf_counter() - inicio) * 1000)


for _ in range(150):
    try:
        urllib.request.urlopen(BASE + '/api/config/tema', timeout=3)
        break
    except Exception:
        time.sleep(2)

resultados = []
def ok(nome, condicao, extra=''):
    resultados.append(bool(condicao))
    print(('OK   ' if condicao else 'FALHA') + f' {nome} {extra}')

s, d, ms = chamar('/login.html'); ok('página de login', s == 200, f'{ms}ms')
s, d, ms = chamar('/api/config/tema'); ok('config do tema', s == 200, str(d))
s, d, _ = chamar('/api/auth/login', corpo={'agencia': '0001', 'conta': '12345-6', 'senha': 'senha123'})
token = d.get('token') if isinstance(d, dict) else None
ok('login', s == 200 and token)
s, d, _ = chamar('/api/auth/login', corpo={'agencia': '0001', 'conta': '12345-6', 'senha': 'errada'}); ok('senha errada recusada', s in (400, 401))
s, d, _ = chamar('/api/historico'); ok('histórico sem login recusado', s in (401, 403))

for amostra in ['1_boleto_textual.pdf', '2_boleto_nitido.png']:
    fronteira = uuid.uuid4().hex
    arq = AMOSTRAS / amostra
    tipo = 'application/pdf' if amostra.endswith('.pdf') else 'image/png'
    corpo = (f'--{fronteira}\r\nContent-Disposition: form-data; name="arquivo"; filename="{arq.name}"\r\nContent-Type: {tipo}\r\n\r\n').encode() \
        + arq.read_bytes() + f'\r\n--{fronteira}--\r\n'.encode()
    s, d, ms = chamar('/api/boletos/ocr', token, bruto=corpo, tipo=f'multipart/form-data; boundary={fronteira}')
    lidos = sum(1 for c in (d.get('campos') or {}).values() if c and c.get('valor')) if isinstance(d, dict) else 0
    ok(f'leitura {amostra}', s == 200 and d.get('linhaDigitavel') == LINHA, f'{ms}ms, {lidos}/4 campos impressos')

s, a, ms = chamar('/api/boletos/analisar', token, {'linhaDigitavel': LINHA, 'tipoEntrada': 'MANUAL', 'origem': 'SITE',
    'nomeBeneficiarioDeclarado': 'Banco Bradesco S.A.', 'documentoBeneficiarioDeclarado': '60.746.948/0001-12',
    'valorDeclarado': '150,00', 'vencimentoDeclarado': '2026-11-10', 'dadosConferidos': True})
ok('análise com CNPJ (BrasilAPI)', s == 200 and a['consultaCnpj'] and a['consultaCnpj']['estado'] in ('ENCONTRADO', 'DESATUALIZADO', 'INDISPONIVEL'),
   f"{ms}ms, nota {a.get('score')}, cnpj {a.get('consultaCnpj', {}).get('estado')}, instituição {a.get('instituicao', {}).get('nome')}")
s, d, ms = chamar(f"/api/boletos/{a['boletoId']}/explicacao-ia", token, {})
ok('explicação (IA ou regras)', s == 200, f"{ms}ms, geradaPorIa={d.get('geradaPorIa') if isinstance(d, dict) else '?'}")
s, d, _ = chamar(f"/api/boletos/{a['boletoId']}/confirmar", token, {}); ok('confirmação simulada', s == 200)
s, d, _ = chamar('/api/historico', token); ok('histórico', s == 200 and isinstance(d, list) and len(d) > 0, f'{len(d) if isinstance(d, list) else 0} itens')
s, d, _ = chamar('/api/instituicoes', token); ok('instituições', s == 200 and len(d) > 30)
print(f'\n{sum(resultados)}/{len(resultados)} verificações OK')
