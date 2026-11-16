// Identidade visual. Carregado no <head> (sem defer) para aplicar o tema antes de a página aparecer,
// evitando que ela pisque com o tema errado.
// O tema escolhido fica no localStorage; o servidor informa o tema padrão e se o tema acadêmico
// está disponível (os logotipos do Bradesco ficam fora do repositório e podem não existir).
(function () {
  const TEMAS = ['proprio', 'academico'];

  function ler(chave) {
    try { return localStorage.getItem(chave); } catch { return null; }
  }
  function gravar(chave, valor) {
    try { localStorage.setItem(chave, valor); } catch { /* navegação privada: segue sem lembrar */ }
  }
  function aplicar(tema) {
    if (document.documentElement.dataset.theme === tema) return;
    document.documentElement.dataset.theme = tema;
    document.dispatchEvent(new CustomEvent('temachange', { detail: tema }));
  }

  const inicial = ler('bs_tema') || ler('bs_tema_padrao');
  document.documentElement.dataset.theme = TEMAS.includes(inicial) ? inicial : 'proprio';

  const config = fetch('/api/config/tema')
    .then((r) => (r.ok ? r.json() : null))
    .catch(() => null)
    .then((cfg) => {
      cfg = cfg || { padrao: 'proprio', academicoDisponivel: false };
      gravar('bs_tema_padrao', cfg.padrao);
      if (!cfg.academicoDisponivel) aplicar('proprio');
      else if (!ler('bs_tema')) aplicar(cfg.padrao);
      return cfg;
    });

  window.Tema = {
    config,
    atual: () => document.documentElement.dataset.theme,
    definir(tema) {
      if (!TEMAS.includes(tema)) return;
      gravar('bs_tema', tema);
      aplicar(tema);
    },
  };
})();
