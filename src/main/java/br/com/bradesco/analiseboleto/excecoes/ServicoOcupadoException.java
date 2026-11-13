package br.com.bradesco.analiseboleto.excecoes;

/** Limite de leituras simultâneas atingido: o cliente deve tentar de novo em alguns segundos. */
public class ServicoOcupadoException extends RuntimeException {
    public ServicoOcupadoException(String mensagem) {
        super(mensagem);
    }
}
