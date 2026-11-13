// Cabeçalho e menu lateral comuns às telas logadas.
// A página marca <body data-pagina="..."> e contém #app-topo, nav#app-menu e main#conteudo.
// No desktop o menu fica fixo ao lado; em telas estreitas vira uma gaveta aberta pelo botão do cabeçalho.
const Layout = {
  PAGINAS: [
    { id: 'home', href: '/home.html', texto: 'Início',
      icone: '<path d="M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"/>' },
    { id: 'pagar', href: '/pagar-boleto.html', texto: 'Pagar boleto',
      icone: '<path d="M4 5v14M7 5v14M10 5v14M14 5v14M17 5v14M20 5v14"/>' },
    { id: 'historico', href: '/historico.html', texto: 'Histórico',
      icone: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>' },
    { id: 'acessibilidade', href: '/acessibilidade.html', texto: 'Acessibilidade',
      icone: '<circle cx="12" cy="4.5" r="1.8"/><path d="M5 8.5l7 1.5 7-1.5M12 10v4.5M12 14.5l-3.5 6M12 14.5l3.5 6"/>' },
  ],

  icone(conteudo) {
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor"
      stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">${conteudo}</svg>`;
  },

  marca() {
    if (Tema.atual() === 'academico') {
      return `<img src="/marca-academica/logo-horizontal.png" class="logo-bradesco" alt="Bradesco">
        <span class="app-topo__divisor" aria-hidden="true"></span>
        <span class="app-topo__produto">Boleto Seguro</span>`;
    }
    return '<img src="/img/logo-boleto-seguro-negativo.svg" class="logo-proprio" alt="Boleto Seguro">';
  },

  renderizarTopo() {
    const topo = document.getElementById('app-topo');
    if (!topo) return;
    const usuario = API.getToken() ? API.getUsuario() : null;
    const nome = usuario ? Html.escapar(usuario.nome.split(' ')[0]) : '';
    topo.innerHTML = `
      <div class="app-topo__barra">
        <button type="button" class="btn-menu" id="btn-menu" aria-controls="app-menu" aria-expanded="false">
          ${Layout.icone('<path d="M4 6h16M4 12h16M4 18h16"/>').replace('<svg', '<svg width="24" height="24"')}
          <span class="visually-hidden">Abrir menu</span>
        </button>
        <a class="app-topo__marca" href="/home.html" title="Ir para o início">${Layout.marca()}</a>
        <span class="aviso-prototipo app-topo__aviso">Protótipo acadêmico — sem vínculo com contas reais</span>
        <div class="app-topo__acess" id="topo-acess"></div>
        <div class="app-topo__usuario">
          ${nome ? `<span class="app-topo__ola">Olá, <b>${nome}</b></span>` : ''}
          ${usuario
            ? '<button type="button" class="btn-topo" id="btn-logout">Sair</button>'
            : '<a class="btn-topo" href="/login.html">Entrar</a>'}
        </div>
      </div>
      <p class="faixa-prototipo mb-0">Protótipo acadêmico — nenhuma transação financeira real</p>`;
    const sair = document.getElementById('btn-logout');
    if (sair) sair.addEventListener('click', () => API.logout());
    document.getElementById('btn-menu').addEventListener('click', () => Layout.abrirMenu());
    Preferencias.montarControles(document.getElementById('topo-acess'));
  },

  renderizarMenu() {
    const menu = document.getElementById('app-menu');
    if (!menu) return;
    const atual = document.body.dataset.pagina;
    menu.innerHTML = `
      <button type="button" class="app-menu__fechar" id="btn-fechar-menu">
        <span aria-hidden="true">&times;</span><span class="visually-hidden">Fechar menu</span>
      </button>
      <ul class="app-menu__lista">
        ${Layout.PAGINAS.map((p) => `
          <li><a class="app-menu__link" href="${p.href}"${p.id === atual ? ' aria-current="page"' : ''}>
            ${Layout.icone(p.icone)}<span>${p.texto}</span></a></li>`).join('')}
      </ul>
      <div class="app-menu__acess">
        <p class="app-menu__titulo">Preferências de leitura</p>
        <div id="menu-acess"></div>
      </div>
      <p class="app-menu__rodape mb-0">Protótipo acadêmico. Nenhuma transação financeira real é realizada.</p>`;
    Preferencias.montarControles(document.getElementById('menu-acess'));

    const fundo = document.createElement('div');
    fundo.className = 'menu-fundo';
    fundo.addEventListener('click', () => Layout.fecharMenu());
    menu.after(fundo);
    document.getElementById('btn-fechar-menu').addEventListener('click', () => Layout.fecharMenu());
  },

  // Gaveta do menu (telas estreitas). Enquanto aberta, o resto da página fica inerte para o teclado e leitores de tela.
  abrirMenu() {
    const menu = document.getElementById('app-menu');
    menu.classList.add('aberto');
    document.getElementById('btn-menu').setAttribute('aria-expanded', 'true');
    document.getElementById('app-topo').inert = true;
    document.getElementById('conteudo').inert = true;
    menu.querySelector('.app-menu__link').focus();
  },

  fecharMenu() {
    const menu = document.getElementById('app-menu');
    if (!menu.classList.contains('aberto')) return;
    menu.classList.remove('aberto');
    document.getElementById('app-topo').inert = false;
    document.getElementById('conteudo').inert = false;
    const botao = document.getElementById('btn-menu');
    botao.setAttribute('aria-expanded', 'false');
    if (getComputedStyle(botao).display !== 'none') botao.focus();
  },

  iniciar() {
    Layout.renderizarTopo();
    Layout.renderizarMenu();
    document.addEventListener('keydown', (e) => { if (e.key === 'Escape') Layout.fecharMenu(); });
    // Ao alargar a janela o menu volta a ser lateral fixo
    matchMedia('(min-width: 992px)').addEventListener('change', (e) => { if (e.matches) Layout.fecharMenu(); });
    // O servidor pode trocar o tema depois do carregamento (ex.: logotipos acadêmicos indisponíveis)
    document.addEventListener('temachange', () => Layout.renderizarTopo());
  },
};

Layout.iniciar();
