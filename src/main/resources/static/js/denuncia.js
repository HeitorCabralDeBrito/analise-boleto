// "Denunciar boleto": formulário usado no resultado e no histórico.
// A denúncia vale para o CPF/CNPJ do beneficiário; outros usuários veem só a quantidade de pessoas que denunciaram.
const Denuncia = {
  MOTIVOS: [
    ['NAO_RECONHECO', 'Não reconheço esta cobrança'],
    ['DADOS_DIFERENTES', 'Os dados não batem com os da empresa'],
    ['GOLPE_CONFIRMADO', 'Paguei e era golpe'],
    ['OUTRO', 'Outro motivo'],
  ],
  contador: 0,

  /** Mensagem de "já denunciado" no lugar do botão. */
  mostrarFeita(container, data) {
    container.innerHTML = `<p class="denuncia-feita mb-0" role="status"><span aria-hidden="true">✓ </span>
      Você denunciou este beneficiário${data ? ' em ' + Html.escapar(data) : ''}. Outros usuários verão apenas que houve
      uma denúncia — não quem denunciou nem o que você escreveu.</p>`;
  },

  /**
   * Monta o botão "Denunciar este boleto" em `container`; ao clicar, abre o formulário no mesmo lugar.
   * `aoConcluir(resultado)` é chamado depois de registrar.
   */
  montar(container, boletoId, opcoes = {}) {
    if (!container) return;
    if (opcoes.jaDenunciado) { this.mostrarFeita(container); return; }
    // Só o botão; o formulário é criado ao clicar (o histórico pode ter dezenas de boletos)
    container.innerHTML = `<button type="button" class="btn-contorno btn-denunciar" aria-expanded="false">
        <span aria-hidden="true">⚑</span> ${opcoes.rotulo || 'Denunciar este boleto'}</button>`;
    container.querySelector('.btn-denunciar').addEventListener('click', () => this.abrirFormulario(container, boletoId, opcoes),
      { once: true });
  },

  abrirFormulario(container, boletoId, opcoes) {
    const id = 'denuncia-' + (++this.contador);
    container.insertAdjacentHTML('beforeend', `
      <form id="${id}" class="form-denuncia d-none" novalidate>
        <fieldset>
          <legend class="fw-semibold fs-6">Por que você está denunciando?</legend>
          ${this.MOTIVOS.map(([valor, texto], i) => `
            <div class="form-check">
              <input class="form-check-input" type="radio" name="${id}-motivo" id="${id}-m${i}" value="${valor}">
              <label class="form-check-label" for="${id}-m${i}">${texto}</label>
            </div>`).join('')}
        </fieldset>
        <label for="${id}-desc" class="form-label mt-2">Conte o que aconteceu <span class="text-muted">(opcional)</span></label>
        <textarea id="${id}-desc" class="form-control campo" rows="2" maxlength="500" aria-describedby="${id}-ajuda"></textarea>
        <div class="form-text" id="${id}-ajuda">Não escreva dados pessoais (seu CPF, senhas, endereço). Até 500 caracteres.</div>
        <div class="texto-erro d-none" id="${id}-erro" role="alert"></div>
        <div class="d-flex gap-2 mt-2 flex-wrap">
          <button type="submit" class="btn-perigo">Enviar denúncia</button>
          <button type="button" class="btn-contorno" data-cancelar>Cancelar</button>
        </div>
      </form>`);

    const botao = container.querySelector('.btn-denunciar');
    const form = container.querySelector('form');
    const erro = container.querySelector(`#${id}-erro`);
    botao.setAttribute('aria-controls', id);
    const abrir = (aberto) => {
      form.classList.toggle('d-none', !aberto);
      botao.classList.toggle('d-none', aberto);
      botao.setAttribute('aria-expanded', String(aberto));
      (aberto ? form.querySelector('input[type="radio"]') : botao).focus();
    };
    botao.addEventListener('click', () => abrir(true));
    abrir(true);
    form.querySelector('[data-cancelar]').addEventListener('click', () => abrir(false));
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const motivo = form.querySelector(`input[name="${id}-motivo"]:checked`);
      if (!motivo) {
        erro.textContent = 'Escolha o motivo da denúncia.';
        erro.classList.remove('d-none');
        form.querySelector('input[type="radio"]').focus();
        return;
      }
      const enviar = form.querySelector('[type="submit"]');
      enviar.disabled = true;
      const { ok, data } = await API.denunciar(boletoId, motivo.value, form.querySelector('textarea').value.trim() || null);
      if (ok) {
        this.mostrarFeita(container, data.data);
        if (opcoes.aoConcluir) opcoes.aoConcluir(data);
      } else {
        enviar.disabled = false;
        erro.textContent = data.erro || Object.values(data)[0] || 'Não foi possível registrar a denúncia. Tente novamente.';
        erro.classList.remove('d-none');
      }
    });
  },
};
