package com.domainknowledge.shared.controller;

import com.domainknowledge.document.exception.DocumentNotFoundException;
import com.domainknowledge.infrastructure.storage.exception.StorageException;
import com.domainknowledge.ingestion.exception.EmptyFileException;
import com.domainknowledge.ingestion.exception.EventPublishException;
import com.domainknowledge.ingestion.exception.UnsupportedDocumentTypeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Erros da API em Problem Details (RFC 9457): JSON com {@code type},
 * {@code title}, {@code status}, {@code detail} e {@code instance}. A classe
 * mãe já cuida das exceções do Spring MVC (parâmetro inválido, parte do
 * multipart faltando, arquivo grande demais...); aqui entram as do domínio.
 */
@Slf4j
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    /** O mesmo limite que o multipart aplica, pra mensagem não mentir se ele mudar. */
    private final DataSize maxFileSize;

    ApiExceptionHandler(@Value("${spring.servlet.multipart.max-file-size}") DataSize maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    @ExceptionHandler(EmptyFileException.class)
    ProblemDetail emptyFile(EmptyFileException e) {
        return problem(HttpStatus.BAD_REQUEST, "Arquivo vazio", e.getMessage());
    }

    @ExceptionHandler(UnsupportedDocumentTypeException.class)
    ProblemDetail unsupportedType(UnsupportedDocumentTypeException e) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de arquivo não aceito", e.getMessage());
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    ProblemDetail notFound(DocumentNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Documento não encontrado", e.getMessage());
    }

    /** RF8: o documento ficou {@code RECEIVED}; o {@code documentId} vai na resposta. */
    @ExceptionHandler(EventPublishException.class)
    ProblemDetail publishFailed(EventPublishException e) {
        log.warn("Falha ao publicar o evento do documento {}", e.getDocumentId(), e);
        ProblemDetail problem = problem(HttpStatus.SERVICE_UNAVAILABLE, "Fila indisponível",
                "O documento foi recebido, mas não entrou na fila. Envie o mesmo arquivo de novo em instantes.");
        problem.setProperty("documentId", e.getDocumentId());
        return problem;
    }

    @ExceptionHandler(StorageException.class)
    ProblemDetail storageFailed(StorageException e) {
        log.warn("Falha no storage", e);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Storage indisponível",
                "Não foi possível guardar o arquivo agora. Tente de novo em instantes.");
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.CONTENT_TOO_LARGE, "Arquivo grande demais",
                "O arquivo passa do tamanho máximo aceito (%d MB).".formatted(maxFileSize.toMegabytes()));
        return handleExceptionInternal(e, problem, headers, HttpStatus.CONTENT_TOO_LARGE, request);
    }

    /**
     * Qualquer erro não previsto: 500, também em Problem Details, sem expor o
     * detalhe interno. O erro completo vai pro log.
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Erro inesperado", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno",
                "Algo deu errado do nosso lado. Tente de novo; se continuar, avise o suporte.");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
