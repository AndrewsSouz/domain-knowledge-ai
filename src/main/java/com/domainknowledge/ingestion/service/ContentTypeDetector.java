package com.domainknowledge.ingestion.service;

import com.domainknowledge.ingestion.model.DocumentType;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

/**
 * Descobre o tipo do arquivo pelo conteúdo, não só pela extensão (RF1).
 *
 * <p>O Tika lê os primeiros bytes (a "assinatura" do formato: todo PDF começa
 * com {@code %PDF-}, todo DOCX é um ZIP) e usa o nome do arquivo só como dica.
 * Quando o conteúdo e o nome discordam, vale o conteúdo: um texto renomeado
 * pra {@code .pdf} continua sendo texto.
 *
 * <p>Limites do {@code tika-core}, conferidos na versão 4.2.0:
 * <ul>
 *   <li>Ele reconhece um pacote do Office, mas não distingue DOCX de XLSX sem
 *       a extensão; DOCX sem {@code .docx} no nome é recusado.</li>
 *   <li>Ele não abre o ZIP pra conferir o conteúdo: um ZIP qualquer chamado
 *       {@code .docx} passa aqui e falha na extração de texto (fatia 2), que
 *       traz os módulos completos do Tika.</li>
 *   <li>Arquivo vazio não tem bytes pra ler, então o nome decide. Por isso o
 *       upload barra arquivo vazio antes de chamar este detector.</li>
 * </ul>
 */
@Component
public class ContentTypeDetector {

    private final Tika tika = new Tika();

    /**
     * @param content  conteúdo do arquivo; o começo do stream é consumido
     * @param filename nome original, usado como dica
     * @return o tipo, se for um dos aceitos
     */
    public Optional<DocumentType> detect(InputStream content, String filename) throws IOException {
        return DocumentType.fromMediaType(tika.detect(content, filename));
    }
}
