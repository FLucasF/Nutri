package br.com.nutriplan.prescricao.web;

import br.com.nutriplan.prescricao.dto.PrescricaoDtos;
import br.com.nutriplan.prescricao.service.PlanoPublicoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plano visto pelo paciente, pelo link recebido.
 *
 * Único ponto do sistema que responde sem autenticação. A autorização é a posse
 * do identificador, e o contrato de resposta simplesmente não tem campo para
 * dado interno do consultório.
 */
@RestController
@RequestMapping("/api/publico/planos")
@RequiredArgsConstructor
@Tag(name = "Plano do paciente")
@SecurityRequirements
public class PlanoPublicoController {

    private final PlanoPublicoService planoPublicoService;

    @GetMapping("/{identificador}")
    @Operation(summary = "Abre um plano publicado pelo identificador do link",
            description = "Não exige autenticação. Plano em rascunho responde 404, "
                    + "para não revelar trabalho em andamento.")
    public PrescricaoDtos.PlanoPublicoResponse abrir(@PathVariable String identificador) {
        return planoPublicoService.porIdentificador(identificador);
    }

    @GetMapping("/{identificador}/orientacoes/{anexoId}/imagem")
    @Operation(summary = "Baixa a figura de uma orientação entregue neste plano",
            description = "Mesma porta do plano: identificador válido e plano visível. "
                    + "A figura é a cópia entregue, e não a da biblioteca.")
    public ResponseEntity<byte[]> imagemDaOrientacao(@PathVariable String identificador,
                                                     @PathVariable Long anexoId) {
        var imagem = planoPublicoService.imagemDaOrientacao(identificador, anexoId);
        return ResponseEntity.ok()
                .contentType(imagem.tipo() == null
                        ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(imagem.tipo()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + imagem.nome() + "\"")
                .body(imagem.conteudo());
    }
}
