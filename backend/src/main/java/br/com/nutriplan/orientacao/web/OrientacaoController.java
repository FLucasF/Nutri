package br.com.nutriplan.orientacao.web;

import br.com.nutriplan.orientacao.dto.OrientacaoDtos;
import br.com.nutriplan.orientacao.service.OrientacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Orientações nutricionais")
public class OrientacaoController {

    private final OrientacaoService orientacaoService;

    // ------------------------------------------------------------- biblioteca

    @GetMapping("/orientacoes")
    @Operation(summary = "Lista a biblioteca de orientações",
            description = "Traz os modelos do sistema e os textos do próprio consultório. "
                    + "Os do consultório vêm primeiro.")
    public Page<OrientacaoDtos.OrientacaoResponse> listar(
            @RequestParam(required = false) String termo,
            @PageableDefault(size = 25) Pageable pageable) {
        return orientacaoService.listar(termo, pageable);
    }

    @GetMapping("/orientacoes/{id}")
    @Operation(summary = "Detalha uma orientação")
    public OrientacaoDtos.OrientacaoResponse detalhar(@PathVariable Long id) {
        return orientacaoService.detalhar(id);
    }

    @PostMapping("/orientacoes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria uma orientação própria")
    public OrientacaoDtos.OrientacaoResponse criar(
            @Valid @RequestBody OrientacaoDtos.OrientacaoRequest req) {
        return orientacaoService.criar(req);
    }

    @PostMapping("/orientacoes/{id}/duplicar")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cópia a orientação para a biblioteca do consultório",
            description = "É o caminho para adaptar um modelo do sistema sem alterar o original.")
    public OrientacaoDtos.OrientacaoResponse duplicar(@PathVariable Long id) {
        return orientacaoService.duplicar(id);
    }

    @PutMapping("/orientacoes/{id}")
    @Operation(summary = "Atualiza uma orientação do consultório")
    public OrientacaoDtos.OrientacaoResponse atualizar(
            @PathVariable Long id, @Valid @RequestBody OrientacaoDtos.OrientacaoRequest req) {
        return orientacaoService.atualizar(id, req);
    }

    @DeleteMapping("/orientacoes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Inativa a orientação",
            description = "Não apaga: planos já entregues a referenciam como procedência.")
    public void remover(@PathVariable Long id) {
        orientacaoService.remover(id);
    }

    @PostMapping(value = "/orientacoes/{id}/imagem",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Anexa uma imagem à orientação",
            description = "Até 2 MB. Uma figura de prato dividido vale mais que o parágrafo "
                    + "que a descreve, e é o material que o paciente consulta na cozinha.")
    public void anexarImagem(@PathVariable Long id,
                             @RequestParam("arquivo") MultipartFile arquivo) throws IOException {
        orientacaoService.anexarImagem(id, arquivo.getOriginalFilename(),
                arquivo.getContentType(), arquivo.getBytes());
    }

    @GetMapping("/orientacoes/{id}/imagem")
    @Operation(summary = "Baixa a imagem da orientação")
    public ResponseEntity<byte[]> imagem(@PathVariable Long id) {
        return responder(orientacaoService.imagem(id));
    }

    @GetMapping("/prescricoes/{planoId}/orientacoes/{anexoId}/imagem")
    @Operation(summary = "Baixa a imagem entregue neste plano",
            description = "É a cópia congelada no anexo, e não a da biblioteca.")
    public ResponseEntity<byte[]> imagemDoPlano(@PathVariable Long planoId,
                                                @PathVariable Long anexoId) {
        return responder(orientacaoService.imagemDoPlano(planoId, anexoId));
    }

    private ResponseEntity<byte[]> responder(OrientacaoService.Imagem imagem) {
        return ResponseEntity.ok()
                .contentType(imagem.tipo() == null
                        ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(imagem.tipo()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + imagem.nome() + "\"")
                .body(imagem.conteudo());
    }

    // ---------------------------------------------------------------- no plano

    @GetMapping("/prescricoes/{planoId}/orientacoes")
    @Operation(summary = "Orientações anexadas ao plano")
    public List<OrientacaoDtos.OrientacaoDoPlanoResponse> doPlano(@PathVariable Long planoId) {
        return orientacaoService.doPlano(planoId);
    }

    @PostMapping("/prescricoes/{planoId}/orientacoes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Anexa uma orientação ao plano",
            description = "O texto é copiado no momento do anexo: editar a biblioteca depois "
                    + "não altera o que o paciente recebeu, e o texto anexado pode ser adaptado "
                    + "a este paciente sem sujar o modelo.")
    public OrientacaoDtos.OrientacaoDoPlanoResponse anexar(
            @PathVariable Long planoId, @Valid @RequestBody OrientacaoDtos.AnexoRequest req) {
        return orientacaoService.anexar(planoId, req);
    }

    @PutMapping("/prescricoes/{planoId}/orientacoes/{anexoId}")
    @Operation(summary = "Edita o texto anexado a este plano")
    public OrientacaoDtos.OrientacaoDoPlanoResponse editarNoPlano(
            @PathVariable Long planoId, @PathVariable Long anexoId,
            @Valid @RequestBody OrientacaoDtos.OrientacaoRequest req) {
        return orientacaoService.editarNoPlano(planoId, anexoId, req);
    }

    @DeleteMapping("/prescricoes/{planoId}/orientacoes/{anexoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a orientação do plano")
    public void desanexar(@PathVariable Long planoId, @PathVariable Long anexoId) {
        orientacaoService.desanexar(planoId, anexoId);
    }
}
