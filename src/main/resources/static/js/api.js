// Camada de comunicação com a API do backend
const API = {
  BASE: '/api',

  getToken() {
    return sessionStorage.getItem('bs_token');
  },

  getUsuario() {
    const raw = sessionStorage.getItem('bs_usuario');
    return raw ? JSON.parse(raw) : null;
  },

  headers(extra = {}) {
    const token = this.getToken();
    return {
      'Content-Type': 'application/json',
      ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
      ...extra
    };
  },

  async fetch(path, options = {}) {
    const resp = await fetch(this.BASE + path, {
      headers: this.headers(),
      ...options
    });
    if (resp.status === 401 || resp.status === 403) {
      this.logout();
      return null;
    }
    return resp;
  },

  async login(agencia, conta, senha) {
    const resp = await fetch(`${this.BASE}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ agencia, conta, senha })
    });
    const data = await resp.json();
    if (resp.ok) {
      sessionStorage.setItem('bs_token', data.token);
      sessionStorage.setItem('bs_usuario', JSON.stringify({
        nome: data.nome, agencia: data.agencia, conta: data.conta
      }));
    }
    return { ok: resp.ok, data };
  },

  async analisarBoleto(payload) {
    const resp = await this.fetch('/boletos/analisar', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
    if (!resp) return { ok: false, data: { erro: 'Sessão expirada.' } };
    const data = await resp.json();
    return { ok: resp.ok, data };
  },

  async confirmarBoleto(boletoId) {
    const resp = await this.fetch(`/boletos/${boletoId}/confirmar`, { method: 'POST' });
    if (!resp) return { ok: false, data: {} };
    const data = await resp.json();
    return { ok: resp.ok, data };
  },

  async historico() {
    const resp = await this.fetch('/historico');
    if (!resp) return { ok: false, data: [] };
    const data = await resp.json();
    return { ok: resp.ok, data };
  },

  logout() {
    sessionStorage.removeItem('bs_token');
    sessionStorage.removeItem('bs_usuario');
    window.location.href = '/login.html';
  },

  requireAuth() {
    if (!this.getToken()) {
      window.location.href = '/login.html';
      return false;
    }
    return true;
  }
};

// Máscaras de digitação: o usuário digita só números e o campo formata sozinho.
// Em cada padrão, "0" é a posição de um dígito; o resto (. - / espaço) é inserido automaticamente.
const Mascara = {
  PADROES: {
    linhaDigitavel: '00000.00000 00000.000000 00000.000000 0 00000000000000',
    cpf: '000.000.000-00',
    cnpj: '00.000.000/0000-00',
    conta: '00000-0',
    agencia: '0000',
  },

  formatar(digitos, padrao) {
    let saida = '';
    let i = 0;
    for (const c of padrao) {
      if (i >= digitos.length) break;
      saida += c === '0' ? digitos[i++] : c;
    }
    return saida;
  },

  // CPF até 11 dígitos; a partir do 12º vira CNPJ
  cpfCnpj(digitos) {
    return Mascara.formatar(digitos, digitos.length <= 11 ? Mascara.PADROES.cpf : Mascara.PADROES.cnpj);
  },

  // Valor em reais digitado como no app do banco: 15000 → 150,00
  moeda(digitos) {
    if (!digitos) return '';
    const centavos = digitos.replace(/^0+/, '').padStart(3, '0');
    const inteiro = centavos.slice(0, -2).replace(/\B(?=(\d{3})+(?!\d))/g, '.');
    return `${inteiro},${centavos.slice(-2)}`;
  },

  /**
   * Liga a máscara a um input. `formato` é o nome de um padrão de PADROES ou uma função (digitos) => texto.
   * Mantém o cursor no lugar certo mesmo editando no meio do campo.
   */
  aplicar(input, formato, maxDigitos) {
    const formatador = typeof formato === 'function'
      ? formato
      : (d) => Mascara.formatar(d, Mascara.PADROES[formato]);
    const limite = maxDigitos || (typeof formato === 'string'
      ? Mascara.PADROES[formato].replace(/[^0]/g, '').length : Infinity);

    let anterior = input.value;
    input.addEventListener('input', (e) => {
      let valor = input.value;
      let cursor = input.selectionStart ?? valor.length;

      // Backspace em cima de um separador: apaga o dígito anterior a ele
      if (e.inputType === 'deleteContentBackward'
          && valor.replace(/\D/g, '') === anterior.replace(/\D/g, '')) {
        const antes = valor.slice(0, cursor).replace(/\d(?=\D*$)/, '');
        valor = antes + valor.slice(cursor);
        cursor = antes.length;
      }

      const digitosAntesDoCursor = valor.slice(0, cursor).replace(/\D/g, '').length;
      const digitos = valor.replace(/\D/g, '').slice(0, limite);
      const formatado = formatador(digitos);
      input.value = formatado;
      anterior = formatado;

      // Recoloca o cursor logo depois do mesmo número de dígitos
      let pos = 0;
      for (let vistos = 0; pos < formatado.length && vistos < digitosAntesDoCursor; pos++) {
        if (/\d/.test(formatado[pos])) vistos++;
      }
      if (formatador === Mascara.moeda) pos = formatado.length;
      if (document.activeElement === input) input.setSelectionRange(pos, pos);
    });

    // Formata também valores preenchidos por código (ex.: linha lida do boleto)
    input.reformatar = () => {
      input.value = formatador(input.value.replace(/\D/g, '').slice(0, limite));
      anterior = input.value;
    };
  }
};

// Validação de CPF/CNPJ no navegador (mesmo cálculo do backend) para avisar antes de enviar
const Documento = {
  valido(doc) {
    const d = (doc || '').replace(/\D/g, '');
    if (!(d.length === 11 || d.length === 14) || /^(\d)\1+$/.test(d)) return false;
    const digito = (base, pesos) => {
      const soma = pesos.reduce((s, p, i) => s + Number(base[i]) * p, 0);
      const resto = soma % 11;
      return resto < 2 ? 0 : 11 - resto;
    };
    if (d.length === 11) {
      const d1 = digito(d, [10, 9, 8, 7, 6, 5, 4, 3, 2]);
      const d2 = digito(d, [11, 10, 9, 8, 7, 6, 5, 4, 3, 2]);
      return d1 === Number(d[9]) && d2 === Number(d[10]);
    }
    const d1 = digito(d, [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]);
    const d2 = digito(d, [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]);
    return d1 === Number(d[12]) && d2 === Number(d[13]);
  }
};

// Exibe nome do usuário no navbar se existir #usuario-nome
document.addEventListener('DOMContentLoaded', () => {
  const el = document.getElementById('usuario-nome');
  if (el) {
    const u = API.getUsuario();
    if (u) el.textContent = u.nome.split(' ')[0];
  }
  const btnLogout = document.getElementById('btn-logout');
  if (btnLogout) btnLogout.addEventListener('click', () => API.logout());
});
