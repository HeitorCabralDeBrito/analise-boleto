// Preferências de leitura: tamanho do texto e alto contraste.
// Carregado no <head> (como tema.js) para aplicar antes de a página aparecer.
// São opcionais: a acessibilidade essencial (teclado, leitor de tela, contraste AA) não depende delas.
(function () {
  const NIVEIS = [87.5, 100, 112.5, 125, 150]; // % do tamanho do texto
  const PADRAO = 1;
  const raiz = document.documentElement;

  function ler(chave) {
    try { return localStorage.getItem(chave); } catch { return null; }
  }
  function gravar(chave, valor) {
    try { localStorage.setItem(chave, valor); } catch { /* navegação privada: vale só nesta página */ }
  }

  let nivel = Number(ler('bs_fonte'));
  if (!Number.isInteger(nivel) || nivel < 0 || nivel >= NIVEIS.length || ler('bs_fonte') === null) nivel = PADRAO;
  let contraste = ler('bs_contraste') === 'alto';

  function aplicar() {
    // Tudo no CSS (e no Bootstrap) é medido em rem, então mudar o tamanho da raiz amplia a página inteira
    raiz.style.fontSize = NIVEIS[nivel] + '%';
    raiz.dataset.contraste = contraste ? 'alto' : 'normal';
  }
  aplicar();

  // Região anunciada pelo leitor de tela quando uma preferência muda
  function anunciar(texto) {
    let regiao = document.getElementById('anuncio-preferencias');
    if (!regiao) {
      regiao = document.createElement('p');
      regiao.id = 'anuncio-preferencias';
      regiao.className = 'visually-hidden';
      regiao.setAttribute('aria-live', 'polite');
      document.body.appendChild(regiao);
    }
    regiao.textContent = texto;
  }

  function mudou(texto) {
    aplicar();
    anunciar(texto);
    document.dispatchEvent(new CustomEvent('preferenciaschange'));
  }

  const Preferencias = {
    percentual: () => NIVEIS[nivel],
    altoContraste: () => contraste,

    mudarFonte(passo) {
      nivel = passo === 0 ? PADRAO : Math.min(Math.max(nivel + passo, 0), NIVEIS.length - 1);
      gravar('bs_fonte', String(nivel));
      mudou(`Tamanho do texto: ${String(NIVEIS[nivel]).replace('.', ',')}%`);
    },

    alternarContraste() {
      contraste = !contraste;
      gravar('bs_contraste', contraste ? 'alto' : 'normal');
      mudou(contraste ? 'Alto contraste ativado' : 'Alto contraste desativado');
    },

    /**
     * Monta os botões A− / A / A+ / Alto contraste dentro de `container`.
     * O texto visível faz parte do nome acessível (WCAG 2.5.3): "A+ aumentar texto".
     */
    montarControles(container) {
      if (!container) return;
      container.innerHTML = `
        <div class="controles-acess" role="group" aria-label="Preferências de leitura">
          <button type="button" class="btn-acess" data-acao="menos">A−<span class="visually-hidden"> diminuir texto</span></button>
          <button type="button" class="btn-acess" data-acao="padrao">A<span class="visually-hidden"> tamanho padrão do texto</span></button>
          <button type="button" class="btn-acess" data-acao="mais">A+<span class="visually-hidden"> aumentar texto</span></button>
          <button type="button" class="btn-acess btn-acess--contraste" data-acao="contraste" aria-pressed="false">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true" focusable="false">
              <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="2"/><path d="M12 3a9 9 0 0 1 0 18z" fill="currentColor"/>
            </svg>
            <span class="btn-acess__texto">Alto contraste</span>
          </button>
        </div>`;
      const botoes = container.querySelectorAll('[data-acao]');
      const atualizar = () => {
        container.querySelector('[data-acao="menos"]').disabled = nivel === 0;
        container.querySelector('[data-acao="mais"]').disabled = nivel === NIVEIS.length - 1;
        container.querySelector('[data-acao="contraste"]').setAttribute('aria-pressed', String(contraste));
      };
      botoes.forEach((b) => b.addEventListener('click', () => {
        const acao = b.dataset.acao;
        if (acao === 'contraste') Preferencias.alternarContraste();
        else Preferencias.mudarFonte(acao === 'mais' ? 1 : acao === 'menos' ? -1 : 0);
      }));
      document.addEventListener('preferenciaschange', atualizar);
      atualizar();
    },
  };

  window.Preferencias = Preferencias;
})();
