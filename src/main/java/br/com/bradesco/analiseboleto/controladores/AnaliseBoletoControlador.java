package br.com.bradesco.analiseboleto.controladores;

import br.com.bradesco.analiseboleto.dtos.RequisicaoBoletoDTO;
import br.com.bradesco.analiseboleto.dtos.RespostaAnaliseDTO;
import br.com.bradesco.analiseboleto.servicos.AnaliseFraudeServico;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController // Diz ao Spring que esta classe vai receber chamadas da internet (API REST)
@RequestMapping("/api/boletos") // Cria o endereço base para acessar este controlador
public class AnaliseBoletoControlador {

    private final AnaliseFraudeServico servico;

    // Construtor: conecta a recepção (Controlador) ao cérebro (Serviço)
    public AnaliseBoletoControlador(AnaliseFraudeServico servico) {
        this.servico = servico;
    }

    // Cria a rota exata: http://localhost:8080/api/boletos/analisar
    @PostMapping("/analisar")
    public RespostaAnaliseDTO analisarBoleto(@RequestBody RequisicaoBoletoDTO requisicao) {
        // Recebe os dados do App (RequestBody) e manda o Serviço trabalhar
        return servico.analisar(requisicao);
    }
}
