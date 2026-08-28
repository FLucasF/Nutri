package br.com.nutriplan.prescricao.web;

import br.com.nutriplan.prescricao.dto.PrescricaoDtos;
import br.com.nutriplan.prescricao.service.ImpressaoDoPlanoService;
import br.com.nutriplan.prescricao.service.PlanoAlimentarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/prescricoes")
@RequiredArgsConstructor
@Tag(name = "Prescrição")
public class PlanoAlimentarController {

    private final PlanoAlimentarService planoService;
    private final ImpressaoDoPlanoService impressaoService;

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Gera o plano alimentar em PDF, pronto para entregar na consulta",
            description = "Funciona também sobre rascunho: conferir a folha antes de publicar "
                    + "faz parte do trabalho, e a folha se identifica como rascunho.")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        var pdf = impressaoService.emitir(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                // "inline": o profissional confere antes de enviar, e um
                // download direto obrigaria a abrir o arquivo por fora.
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + pdf.nomeDoArquivo() + "\"")
                .body(pdf.conteudo());
    }

    @GetMapping
    @Operation(summary = "Lista planos alimentares do consultório")
    public Page<PrescricaoDtos.PlanoResumo> listar(
            @RequestParam(required = false) Long pacienteId,
            @RequestParam(required = false) Boolean modelo,
            @RequestParam(required = false) String termo,
            @PageableDefault(size = 20, sort = "atualizadoEm", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return planoService.listar(pacienteId, modelo, termo, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha um plano, com totais por refeição e do dia")
    public PrescricaoDtos.PlanoResponse detalhar(@PathVariable Long id) {
        return planoService.detalhar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria um plano alimentar")
    public PrescricaoDtos.PlanoResponse criar(@Valid @RequestBody PrescricaoDtos.PlanoRequest req) {
        return planoService.criar(req);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Substitui o conteúdo de um plano")
    public PrescricaoDtos.PlanoResponse atualizar(@PathVariable Long id,
                                                  @Valid @RequestBody PrescricaoDtos.PlanoRequest req) {
        return planoService.atualizar(id, req);
    }

    @PostMapping("/{id}/publicar")
    @Operation(summary = "Pública o plano, liberando o link do paciente")
    public PrescricaoDtos.PlanoResponse publicar(@PathVariable Long id) {
        return planoService.publicar(id);
    }

    @PostMapping("/{id}/encerrar")
    @Operation(summary = "Encerra o plano, que permanece visível marcado como encerrado")
    public PrescricaoDtos.PlanoResponse encerrar(@PathVariable Long id) {
        return planoService.encerrar(id);
    }

    @PostMapping("/{id}/rascunho")
    @Operation(summary = "Devolve o plano para rascunho, tirando-o do ar")
    public PrescricaoDtos.PlanoResponse voltarParaRascunho(@PathVariable Long id) {
        return planoService.voltarParaRascunho(id);
    }

    @PostMapping("/{id}/regerar-link")
    @Operation(summary = "Gera um novo endereço público, invalidando o link já entregue")
    public PrescricaoDtos.PlanoResponse regerarLink(@PathVariable Long id) {
        return planoService.regerarLink(id);
    }

    @PostMapping("/{id}/duplicar")
    @Operation(summary = "Duplica o plano como novo rascunho")
    public PrescricaoDtos.PlanoResponse duplicar(
            @PathVariable Long id,
            @RequestParam(required = false) Long pacienteId,
            @RequestParam(required = false) String titulo) {
        return planoService.duplicar(id, pacienteId, titulo);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove um plano")
    public void remover(@PathVariable Long id) {
        planoService.remover(id);
    }
}
