package br.com.nutriplan.patient.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import br.com.nutriplan.auth.service.CurrentContext;
import br.com.nutriplan.patient.domain.AttachmentKind;
import br.com.nutriplan.patient.domain.PatientAttachment;
import br.com.nutriplan.patient.domain.PatientAttachmentContent;
import br.com.nutriplan.patient.dto.AttachmentDtos;
import br.com.nutriplan.patient.repository.PatientAttachmentContentRepository;
import br.com.nutriplan.patient.repository.PatientAttachmentRepository;
import br.com.nutriplan.shared.error.BusinessRuleException;
import br.com.nutriplan.shared.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Os arquivos e links guardados junto do prontuário.
 *
 * É o caminho de entrada do que já existe fora do sistema — e o que responde
 * a dúvida dele sobre os pacientes ativos: o cardápio antigo não é recriado,
 * é anexado. O histórico fica onde o paciente está, e a virada acontece na
 * consulta seguinte de cada um.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PatientAttachmentService {

    /** Laudo, exame e cardápio em PDF cabem folgado. */
    private static final int LIMIT = 15 * 1024 * 1024;

    private final PatientAttachmentRepository attachmentRepository;
    private final PatientAttachmentContentRepository contentRepository;
    private final PatientService patientService;
    private final CurrentContext currentContext;

    @Transactional(readOnly = true)
    public List<AttachmentDtos.AttachmentResponse> list(Long patientId) {
        Long accountId = currentContext.accountId();
        patientService.accountRequire(patientId);
        return attachmentRepository
                .findByAccountIdAndPatientIdOrderByReferenceDateDescIdDesc(accountId, patientId)
                .stream()
                .map(AttachmentDtos.AttachmentResponse::from)
                .toList();
    }

    /**
     * Anexa um arquivo.
     *
     * O título é obrigatório e o nome do arquivo não serve de título por
     * omissão: "documento(1).pdf" não diz nada daqui a um ano, e é daqui a um
     * ano que alguém vai procurar.
     */
    @Transactional
    public AttachmentDtos.AttachmentResponse attachFile(
            Long patientId, String title, String notes, LocalDate referenceDate,
            String fileName, String fileType, byte[] content) {

        Long accountId = currentContext.accountId();
        patientService.accountRequire(patientId);

        if (content == null || content.length == 0) {
            throw new BusinessRuleException("Envie um arquivo não vazio.");
        }
        if (content.length > LIMIT) {
            throw new BusinessRuleException("O arquivo pode ter no máximo 15 MB.");
        }
        if (!StringUtils.hasText(title)) {
            throw new BusinessRuleException(
                    "Dê um título ao anexo. O nome do arquivo raramente diz o que ele é.");
        }

        var attachment = new PatientAttachment(accountId, patientId, title.trim(),
                AttachmentKind.FILE);
        attachment.setFileName(fileName);
        attachment.setFileType(fileType);
        attachment.setFileSize((long) content.length);
        attachment.setNotes(notes);
        attachment.setReferenceDate(referenceDate);
        attachmentRepository.save(attachment);

        contentRepository.save(new PatientAttachmentContent(attachment.getId(), content));
        log.info("Anexo guardado: id={} paciente={} bytes={}",
                attachment.getId(), patientId, content.length);
        return AttachmentDtos.AttachmentResponse.from(attachment);
    }

    /** Guarda um link — o endereço do plano no sistema antigo, por exemplo. */
    @Transactional
    public AttachmentDtos.AttachmentResponse attachLink(
            Long patientId, AttachmentDtos.LinkRequest request) {

        Long accountId = currentContext.accountId();
        patientService.accountRequire(patientId);

        String url = request.url() == null ? "" : request.url().trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new BusinessRuleException(
                    "O link precisa começar com http:// ou https://.");
        }

        var attachment = new PatientAttachment(accountId, patientId,
                request.title().trim(), AttachmentKind.LINK);
        attachment.setUrl(url);
        attachment.setNotes(request.notes());
        attachment.setReferenceDate(request.referenceDate());
        attachmentRepository.save(attachment);
        return AttachmentDtos.AttachmentResponse.from(attachment);
    }

    @Transactional(readOnly = true)
    public Download download(Long patientId, Long attachmentId) {
        PatientAttachment attachment = require(patientId, attachmentId);
        if (!attachment.isFile()) {
            throw new BusinessRuleException("Este anexo é um link, não um arquivo.");
        }
        var content = contentRepository.findById(attachmentId)
                .orElseThrow(() -> new NotFoundException("Arquivo do anexo", attachmentId));
        return new Download(attachment.getFileName(), attachment.getFileType(),
                content.getContent());
    }

    @Transactional
    public void remove(Long patientId, Long attachmentId) {
        PatientAttachment attachment = require(patientId, attachmentId);
        contentRepository.findById(attachmentId).ifPresent(contentRepository::delete);
        attachmentRepository.delete(attachment);
        log.info("Anexo removido: id={} paciente={}", attachmentId, patientId);
    }

    private PatientAttachment require(Long patientId, Long attachmentId) {
        return attachmentRepository
                .findByIdAndAccountId(attachmentId, currentContext.accountId())
                .filter(a -> a.getPatientId().equals(patientId))
                .orElseThrow(() -> new NotFoundException("Anexo", attachmentId));
    }

    public record Download(String name, String type, byte[] content) {}
}
