package br.com.nutriplan.patient.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.nutriplan.shared.richtext.RichTextDocument;
import br.com.nutriplan.auth.service.CurrentContext;
import br.com.nutriplan.patient.domain.PatientNote;
import br.com.nutriplan.patient.domain.PatientTag;
import br.com.nutriplan.patient.domain.PatientTagLink;
import br.com.nutriplan.patient.dto.ProfileDtos;
import br.com.nutriplan.patient.repository.PatientNoteRepository;
import br.com.nutriplan.patient.repository.PatientTagLinkRepository;
import br.com.nutriplan.patient.repository.PatientTagRepository;
import br.com.nutriplan.shared.error.BusinessRuleException;
import br.com.nutriplan.shared.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * As TAGs do paciente e as anotações do nutricionista sobre ele.
 *
 * As duas coisas servem ao mesmo pedido da página 4: chegar à consulta de
 * retorno sabendo quem é o paciente sem reler o prontuário inteiro. A TAG
 * responde isso na listagem; a anotação, na ficha.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PatientProfileService {

    private final PatientTagRepository tagRepository;
    private final PatientTagLinkRepository linkRepository;
    private final PatientNoteRepository noteRepository;
    private final PatientService patientService;
    private final CurrentContext currentContext;
    private final ObjectMapper mapper;

    // -------------------------------------------------------------- as TAGs

    @Transactional(readOnly = true)
    public List<ProfileDtos.TagResponse> tags() {
        return tagRepository.visibleTo(currentContext.accountId()).stream()
                .map(ProfileDtos.TagResponse::from)
                .toList();
    }

    @Transactional
    public ProfileDtos.TagResponse createTag(ProfileDtos.TagRequest request) {
        Long accountId = currentContext.accountId();
        String name = normalized(request.name());
        if (name.isEmpty()) {
            throw new BusinessRuleException("A TAG precisa de um nome.");
        }
        tagRepository.findByAccountIdAndName(accountId, name).ifPresent(existing -> {
            throw new BusinessRuleException("Você já tem uma TAG chamada " + name + ".");
        });
        var tag = tagRepository.save(new PatientTag(accountId, name));
        return ProfileDtos.TagResponse.from(tag);
    }

    /**
     * Redefine as TAGs do paciente.
     *
     * O conjunto inteiro chega de uma vez porque marcar e desmarcar é uma
     * operação só do ponto de vista de quem usa: ele abre a lista, escolhe, e
     * fecha.
     */
    @Transactional
    public List<ProfileDtos.TagResponse> setTags(Long patientId, ProfileDtos.PatientTagsRequest request) {
        Long accountId = currentContext.accountId();
        patientService.accountRequire(patientId);

        List<Long> wanted = request.tagIds() == null ? List.of()
                : request.tagIds().stream().distinct().toList();
        List<PatientTagLink> current = linkRepository.ofPatient(patientId);

        // Compara em vez de apagar tudo e reinserir. Apagar e inserir na mesma
        // transação esbarra na chave única antes de o delete ser descarregado —
        // e trocar a ligação de uma TAG que continua marcada não muda nada além
        // do id da linha.
        for (PatientTagLink link : current) {
            if (!wanted.contains(link.getTag().getId())) {
                linkRepository.delete(link);
            }
        }

        var applied = new ArrayList<PatientTag>();
        for (Long tagId : wanted) {
            PatientTag tag = tagRepository.visibleFind(tagId, accountId)
                    .orElseThrow(() -> new NotFoundException("TAG", tagId));
            boolean already = current.stream()
                    .anyMatch(link -> link.getTag().getId().equals(tagId));
            if (!already) {
                linkRepository.save(new PatientTagLink(patientId, tag));
            }
            applied.add(tag);
        }
        log.info("TAGs do paciente atualizadas: paciente={} total={}", patientId, applied.size());
        return applied.stream().map(ProfileDtos.TagResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ProfileDtos.TagResponse> tagsOf(Long patientId) {
        patientService.accountRequire(patientId);
        return linkRepository.ofPatient(patientId).stream()
                .map(link -> ProfileDtos.TagResponse.from(link.getTag()))
                .toList();
    }

    // ---------------------------------------------------------- as anotações

    @Transactional(readOnly = true)
    public List<ProfileDtos.NoteResponse> notes(Long patientId) {
        Long accountId = currentContext.accountId();
        patientService.accountRequire(patientId);
        return noteRepository
                .findByAccountIdAndPatientIdOrderByCreatedAtDesc(accountId, patientId).stream()
                .map(ProfileDtos.NoteResponse::from)
                .toList();
    }

    @Transactional
    public ProfileDtos.NoteResponse addNote(Long patientId, ProfileDtos.NoteRequest request) {
        Long accountId = currentContext.accountId();
        patientService.accountRequire(patientId);
        // O documento vazio do editor não é string vazia: ele chega como um
        // parágrafo sem texto. Por isso a checagem é sobre o que se lê, e não
        // sobre o que se recebe.
        var document = RichTextDocument.ofTextOrDocument(request.body(), mapper);
        if (!StringUtils.hasText(RichTextDocument.plainText(document.json(), mapper))) {
            throw new BusinessRuleException("Escreva a anotação.");
        }
        var note = noteRepository.save(
                new PatientNote(accountId, patientId, document.json()));
        return ProfileDtos.NoteResponse.from(note);
    }

    @Transactional
    public void removeNote(Long patientId, Long noteId) {
        Long accountId = currentContext.accountId();
        PatientNote note = noteRepository.findById(noteId)
                .filter(n -> n.getAccountId().equals(accountId))
                .filter(n -> n.getPatientId().equals(patientId))
                .orElseThrow(() -> new NotFoundException("Anotação", noteId));
        noteRepository.delete(note);
    }

    private static String normalized(String name) {
        return name == null ? "" : name.trim().toUpperCase();
    }
}
