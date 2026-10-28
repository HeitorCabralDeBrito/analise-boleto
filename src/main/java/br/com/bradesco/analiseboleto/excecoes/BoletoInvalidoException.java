package br.com.bradesco.analiseboleto.excecoes;

public class BoletoInvalidoException extends RuntimeException {
    public BoletoInvalidoException(String mensagem) {
        super(mensagem);
    }
}
