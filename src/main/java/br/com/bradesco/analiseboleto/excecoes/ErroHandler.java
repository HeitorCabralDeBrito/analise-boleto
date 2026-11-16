package br.com.bradesco.analiseboleto.excecoes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.ErrorResponse;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class ErroHandler {

    private static final Logger log = LoggerFactory.getLogger(ErroHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            errors.put(field, error.getDefaultMessage());
        });
        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(BoletoInvalidoException.class)
    public ResponseEntity<Map<String, String>> handleBoletoInvalido(BoletoInvalidoException ex) {
        return ResponseEntity.badRequest().body(Map.of("erro", ex.getMessage()));
    }

    @ExceptionHandler(ServicoOcupadoException.class)
    public ResponseEntity<Map<String, String>> handleServicoOcupado(ServicoOcupadoException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header("Retry-After", "10")
            .body(Map.of("erro", ex.getMessage()));
    }

    @ExceptionHandler(AcessoNegadoException.class)
    public ResponseEntity<Map<String, String>> handleAcessoNegado(AcessoNegadoException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("erro", ex.getMessage()));
    }

    /** JSON malformado ou valor fora do esperado (ex.: tipo de entrada inexistente): erro de quem chamou, não do servidor. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleCorpoInvalido(HttpMessageNotReadableException ex) {
        log.warn("Requisição com corpo inválido: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.badRequest().body(Map.of("erro", "Dados enviados em formato inválido."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleArquivoGrande(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
            .body(Map.of("erro", "Arquivo grande demais. Envie um arquivo menor."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneral(Exception ex) {
        // Erros do próprio Spring MVC (404, 405, parâmetro ausente...) mantêm o status correto
        if (ex instanceof ErrorResponse resposta && !resposta.getStatusCode().is5xxServerError()) {
            log.warn("Requisição recusada ({}): {}", resposta.getStatusCode().value(), ex.getMessage());
            return ResponseEntity.status(resposta.getStatusCode())
                .body(Map.of("erro", "Requisição inválida. Confira os dados e tente de novo."));
        }
        log.error("Erro interno não tratado", ex);
        return ResponseEntity.internalServerError().body(Map.of("erro", "Erro interno. Tente novamente."));
    }
}
