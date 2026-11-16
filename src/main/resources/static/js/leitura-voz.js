// Leitura em voz alta (Web Speech API) para quem ENXERGA COM DIFICULDADE ou tem dificuldade de leitura
// (baixa visão, dislexia, pouca familiaridade com texto) e não usa leitor de tela.
// Quem é cego usa o próprio leitor de tela (NVDA, TalkBack…), para o qual as páginas já são preparadas.
//
// Duas formas, ambas por escolha do usuário:
//   - "Ouvir esta página" (botão no cabeçalho de todas as telas) e "Ouvir …" em pontos específicos;
//   - "Ler ao apontar" (preferência): fala o nome do botão, link ou campo ao passar o mouse ou chegar nele com Tab.
// Nunca começa sozinha, sempre pode ser interrompida (botão ou Esc). Sem suporte no navegador, nada aparece.
const LeituraVoz = {
  suportado: 'speechSynthesis' in window && 'SpeechSynthesisUtterance' in window,
  botaoAtivo: null,
  // A página pode oferecer uma leitura própria, mais detalhada (ex.: resultado da análise); null = leitura genérica
  leitorDaPagina: null,

  /**
   * Cria um botão "Ouvir…" em `alvo`. `obterTexto` é chamado na hora do clique (lê o conteúdo atual da tela).
   * opcoes.compacto: só o ícone (o nome continua disponível para o leitor de tela); opcoes.classe: classes do botão.
   */
  criarBotao(alvo, obterTexto, rotulo, opcoes = {}) {
    if (!this.suportado || !alvo) return null;
    const botao = document.createElement('button');
    botao.type = 'button';
    botao.className = opcoes.classe || 'btn-contorno btn-ouvir';
    botao.rotulo = rotulo;
    botao.compacto = !!opcoes.compacto;
    if (opcoes.id) botao.id = opcoes.id;
    this.desenhar(botao, false);
    botao.addEventListener('click', () => {
      if (this.botaoAtivo === botao) this.parar();
      else this.falar(obterTexto(), botao);
    });
    alvo.appendChild(botao);
    return botao;
  },

  desenhar(botao, falando) {
    const icone = falando
      ? '<rect x="6" y="6" width="12" height="12" rx="1" fill="currentColor"/>'
      : '<path d="M4 9v6h4l5 4V5L8 9z" fill="currentColor"/><path d="M16 8.5a5 5 0 0 1 0 7M18.5 6a8.5 8.5 0 0 1 0 12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>';
    const texto = falando ? 'Parar leitura' : botao.rotulo;
    botao.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="20" height="20" aria-hidden="true" focusable="false">${icone}</svg>`
      + `<span${botao.compacto ? ' class="visually-hidden"' : ''}>${texto}</span>`;
    if (botao.compacto) botao.title = texto;
  },

  /** Divide em frases (o Chrome interrompe falas muito longas) e fala em sequência. */
  falar(texto, botao) {
    this.parar();
    const frases = String(texto || '').split(/(?<=[.!?;:])\s+|\n+/).map((f) => f.trim()).filter(Boolean);
    if (!frases.length) return;
    this.botaoAtivo = botao;
    this.desenhar(botao, true);
    frases.forEach((frase, i) => {
      const fala = this.criarFala(frase);
      if (i === frases.length - 1) fala.onend = () => { if (this.botaoAtivo === botao) this.terminar(); };
      fala.onerror = () => { if (this.botaoAtivo === botao) this.terminar(); };
      speechSynthesis.speak(fala);
    });
  },

  /** Fala curta (nome de um botão, ao apontar). Não interrompe a leitura de uma página inteira. */
  falarCurto(texto) {
    if (this.botaoAtivo || !texto) return;
    speechSynthesis.cancel();
    speechSynthesis.speak(this.criarFala(texto));
  },

  criarFala(texto) {
    const fala = new SpeechSynthesisUtterance(texto);
    fala.lang = 'pt-BR';
    fala.rate = 0.95;
    const voz = speechSynthesis.getVoices().find((v) => (v.lang || '').toLowerCase().startsWith('pt'));
    if (voz) fala.voice = voz;
    return fala;
  },

  parar() {
    speechSynthesis.cancel();
    this.terminar();
  },

  terminar() {
    if (this.botaoAtivo) this.desenhar(this.botaoAtivo, false);
    this.botaoAtivo = null;
  },

  /** "23790.91016 ..." → "2 3 7 9 0, 9 1 0 1 6, ..." para conferir número a número */
  soletrarNumeros(texto) {
    return String(texto || '').split(/[\s.]+/).filter(Boolean).map((g) => g.split('').join(' ')).join(', ');
  },

  // ---------- Descrição falada de elementos ----------

  visivel(el) {
    if (el.closest('[aria-hidden="true"], [hidden], .visually-hidden')) return false;
    const estilo = getComputedStyle(el);
    return el.getClientRects().length > 0 && estilo.visibility !== 'hidden';
  },

  // Quebras de linha viram pausa (título e descrição de um cartão não saem grudados)
  limpar(texto, pausa = ' ') {
    return String(texto || '').replace(/\s*\n+\s*/g, pausa).replace(/\s+/g, ' ').trim();
  },

  rotuloDoCampo(campo) {
    const rotulo = campo.labels && campo.labels[0] ? campo.labels[0].innerText : campo.getAttribute('aria-label') || campo.placeholder || '';
    return this.limpar(rotulo).replace(/\s*\*\s*/g, ' ');
  },

  /** Como um elemento interativo deve ser falado: "Botão: Sair", "Campo Valor: 150,00", "Opção: Pagar Boleto…". */
  descrever(el) {
    const tag = el.tagName;
    if (tag === 'INPUT' && (el.type === 'checkbox' || el.type === 'radio')) {
      return `${el.type === 'checkbox' ? 'Caixa de seleção' : 'Opção'}: ${this.rotuloDoCampo(el)}, ${el.checked ? 'marcada' : 'não marcada'}.`;
    }
    if (tag === 'INPUT' || tag === 'TEXTAREA') {
      const valor = el.type === 'date' && el.value ? el.value.split('-').reverse().join('/') : el.value;
      return `Campo ${this.rotuloDoCampo(el)}: ${this.limpar(valor) || 'vazio'}.`;
    }
    if (tag === 'SELECT') {
      return `Lista ${this.rotuloDoCampo(el)}: ${this.limpar(el.options[el.selectedIndex]?.text)}.`;
    }
    const nome = this.limpar(el.getAttribute('aria-label') || el.innerText || el.title, ', ');
    if (!nome) return '';
    if (tag === 'A') return el.classList.contains('acao-card') ? `Opção: ${nome}.` : `Link: ${nome}.`;
    if (tag === 'BUTTON' || el.getAttribute('role') === 'button') {
      const estado = el.getAttribute('aria-pressed') === 'true' ? ', ativado' : '';
      return `Botão: ${nome}${estado}.`;
    }
    return /[.!?:]$/.test(nome) ? nome : nome + '.';
  },

  /** Lê uma área da página na ordem em que aparece, dizendo o que é botão, link e campo. */
  textoDaArea(raiz) {
    const partes = [];
    const interativo = 'a, button, input, select, textarea, [role="button"]';
    const bloco = /^(H[1-6]|P|LI|DT|DD|TD|TH|SUMMARY|LEGEND|LABEL|CAPTION)$/;
    // Texto com negrito/itálico no meio ("Ag: <b>0001</b>") é lido inteiro, sem perder as partes soltas
    const soTextoFormatado = (el) => [...el.children].every((c) => /^(B|STRONG|I|EM|SPAN|SMALL|KBD|CODE|BR|ABBR|MARK)$/.test(c.tagName));
    const percorrer = (no) => {
      for (const el of no.children) {
        if (!this.visivel(el) || el.tagName === 'SVG' || el.tagName === 'SCRIPT') continue;
        if (el.matches(interativo)) {
          if (!(el.tagName === 'INPUT' && el.type === 'file')) partes.push(this.descrever(el));
          continue;
        }
        if (el.tagName === 'LABEL' && el.control) continue; // o rótulo é dito junto com o campo
        if ((bloco.test(el.tagName) || soTextoFormatado(el)) && !el.querySelector(interativo)) {
          let texto = this.limpar(el.innerText, '. ').replace(/([.!?:])\. /g, '$1 ');
          if (texto && el.getAttribute('aria-current') === 'step') texto += ', etapa atual';
          if (texto) partes.push(/[.!?:]$/.test(texto) ? texto : texto + '.');
          continue;
        }
        percorrer(el);
      }
    };
    percorrer(raiz);
    return this.paraFala(partes.filter(Boolean).join(' '));
  },

  textoDaPagina() {
    const proprio = typeof this.leitorDaPagina === 'function' ? this.leitorDaPagina() : null;
    return proprio || this.textoDaArea(document.getElementById('conteudo') || document.body);
  },

  // ---------- Ler ao apontar ----------
  // O que é lido ao passar o mouse, em ordem de prioridade:
  //   1. botão, link ou campo → "Botão: Sair", "Campo Valor: 150,00"
  //   2. item de lista inteiro (um boleto do histórico, uma verificação, um alerta…)
  //   3. bloco de texto (parágrafo, rótulo, célula, título)
  //   4. qualquer outro elemento que tenha texto próprio (ex.: uma frase solta dentro de um cartão)
  INTERATIVO: 'a, button, input, select, textarea, [role="button"]',
  ITEM_INTEIRO: '.historico-item, .ultima-analise, .verificacao-item, .alerta-item, .resumo-numeros > div, .step, [data-ler-bloco]',
  BLOCO_TEXTO: 'h1, h2, h3, h4, p, li, dt, dd, td, th, label, legend, summary, caption',

  alvoAoApontar(no) {
    if (!no || !no.closest) return null;
    const alvo = no.closest(this.INTERATIVO) || no.closest(this.ITEM_INTEIRO) || no.closest(this.BLOCO_TEXTO);
    if (alvo) return alvo;
    // Elemento com texto próprio (não só o texto dos filhos), subindo até achar um
    for (let el = no; el && el !== document.body; el = el.parentElement) {
      if ([...el.childNodes].some((n) => n.nodeType === Node.TEXT_NODE && n.textContent.trim())) return el;
    }
    return null;
  },

  /** Ajustes para soar natural: linha digitável crua não vira um número gigante; "20/100" vira "20 de 100". */
  paraFala(texto) {
    return String(texto || '')
      .replace(/\b\d{40,}\b/g, (n) => `linha digitável terminada em ${n.slice(-4)}`)
      .replace(/\b(\d{1,3})\s*\/\s*100\b/g, '$1 de 100');
  },

  textoAoApontar(el) {
    if (el.matches(this.INTERATIVO)) return this.descrever(el);
    const texto = this.paraFala(this.limpar(el.innerText, '. ').replace(/([.!?:])\. /g, '$1 '));
    return texto.length > 600 ? texto.slice(0, 600) + '…' : texto; // nunca lê uma página inteira de uma vez
  },

  iniciarLerAoApontar() {
    let ultimo = null;
    let espera = null;
    const ler = (el) => {
      if (!window.Preferencias || !Preferencias.lerAoApontar() || !el || el === ultimo) return;
      if (!this.visivel(el)) return;
      ultimo = el;
      this.falarCurto(this.textoAoApontar(el));
    };
    document.addEventListener('focusin', (e) => {
      const el = e.target.closest && e.target.closest(this.INTERATIVO + ', h1, h2, h3');
      if (el) ler(el);
    });
    document.addEventListener('mouseover', (e) => {
      const el = this.alvoAoApontar(e.target);
      clearTimeout(espera);
      if (!el) { ultimo = null; return; }
      espera = setTimeout(() => ler(el), 250); // espera o mouse parar, para não falar tudo pelo caminho
    });
  },
};

if (LeituraVoz.suportado) {
  speechSynthesis.getVoices(); // alguns navegadores só carregam as vozes depois do primeiro pedido
  window.addEventListener('pagehide', () => speechSynthesis.cancel());
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && (LeituraVoz.botaoAtivo || speechSynthesis.speaking)) LeituraVoz.parar();
  });
  LeituraVoz.iniciarLerAoApontar();
}
