// Leitura em voz alta SOB DEMANDA (Web Speech API), para quem tem dificuldade de leitura e não usa leitor de tela.
// Nunca começa sozinha, sempre tem botão de parar (e Esc) e não substitui o leitor de tela.
// Sem suporte no navegador, o botão simplesmente não aparece.
const LeituraVoz = {
  suportado: 'speechSynthesis' in window && 'SpeechSynthesisUtterance' in window,
  botaoAtivo: null,

  /** Cria o botão "Ouvir…" em `alvo`. `obterTexto` é chamado na hora do clique (lê o conteúdo atual da tela). */
  criarBotao(alvo, obterTexto, rotulo) {
    if (!this.suportado || !alvo) return null;
    const botao = document.createElement('button');
    botao.type = 'button';
    botao.className = 'btn-contorno btn-ouvir';
    botao.rotulo = rotulo;
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
    botao.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="20" height="20" aria-hidden="true" focusable="false">${icone}</svg>`
      + `<span>${falando ? 'Parar leitura' : botao.rotulo}</span>`;
  },

  falar(texto, botao) {
    this.parar();
    // Frases curtas: o Chrome interrompe falas muito longas
    const frases = texto.split(/(?<=[.!?;:])\s+|\n+/).map((f) => f.trim()).filter(Boolean);
    if (!frases.length) return;
    const voz = speechSynthesis.getVoices().find((v) => (v.lang || '').toLowerCase().startsWith('pt'));
    this.botaoAtivo = botao;
    this.desenhar(botao, true);
    frases.forEach((frase, i) => {
      const fala = new SpeechSynthesisUtterance(frase);
      fala.lang = 'pt-BR';
      fala.rate = 0.95;
      if (voz) fala.voice = voz;
      if (i === frases.length - 1) fala.onend = () => { if (this.botaoAtivo === botao) this.terminar(); };
      fala.onerror = () => { if (this.botaoAtivo === botao) this.terminar(); };
      speechSynthesis.speak(fala);
    });
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
};

if (LeituraVoz.suportado) {
  speechSynthesis.getVoices(); // alguns navegadores só carregam as vozes depois do primeiro pedido
  window.addEventListener('pagehide', () => speechSynthesis.cancel());
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && LeituraVoz.botaoAtivo) LeituraVoz.parar(); });
}
