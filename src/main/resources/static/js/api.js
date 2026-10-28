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
