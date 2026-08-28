package br.com.nutriplan.prescricao.service;

import br.com.nutriplan.orientacao.repository.ImagemDoPlanoRepository;
import br.com.nutriplan.prescricao.domain.PlanoAlimentar;
import br.com.nutriplan.prescricao.domain.StatusPlano;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;

/**
 * Emite o plano em PDF.
 *
 * Existe como servico proprio, e nao como metodo no controller, porque o
 * caminho inteiro precisa correr numa transacao so: carregar o plano, percorrer
 * refeicoes e itens — que sao colecoes carregadas sob demanda — e so entao
 * desenhar a folha. Montar fora da transacao falha na primeira refeicao, com um
 * erro que nao menciona PDF nenhum.
 */
@Service
@RequiredArgsConstructor
public class ImpressaoDoPlanoService {

    private final PlanoAlimentarService planoService;
    private final PlanoPublicoService planoPublicoService;
    private final GeradorDePdfDoPlano gerador;
    private final ImagemDoPlanoRepository imagemDoPlanoRepository;

    public record PdfDoPlano(String nomeDoArquivo, byte[] conteudo) {}

    @Transactional(readOnly = true)
    public PdfDoPlano emitir(Long id) {
        PlanoAlimentar plano = planoService.exigirDaConta(id);
        // Rascunho tambem imprime: conferir a folha antes de publicar e parte
        // do trabalho. E a folha que se identifica, em vez de a rota recusar.
        boolean rascunho = plano.getStatus() == StatusPlano.RASCUNHO;
        var visao = planoPublicoService.montar(plano);
        byte[] conteudo = gerador.gerar(visao, rascunho, figurasDe(visao));
        return new PdfDoPlano(nomeDoArquivo(plano.getTitulo()), conteudo);
    }

    /**
     * Carrega as figuras das orientacoes deste plano.
     *
     * Uma consulta por figura, e nao uma so com todas: sao poucas por plano, e
     * a alternativa seria arrastar arquivo de plano nenhum para a memoria.
     */
    private Map<Long, byte[]> figurasDe(
            br.com.nutriplan.prescricao.dto.PrescricaoDtos.PlanoPublicoResponse visao) {
        var figuras = new HashMap<Long, byte[]>();
        for (var anexo : visao.orientacoesAnexadas()) {
            if (anexo.imagem() != null) {
                imagemDoPlanoRepository.findById(anexo.id())
                        .ifPresent(i -> figuras.put(anexo.id(), i.getConteudo()));
            }
        }
        return figuras;
    }

    /**
     * Nome de arquivo previsivel e sem acento.
     *
     * Acento e espaco em nome de anexo quebram em cliente de e-mail antigo e em
     * sistema de arquivos que nao fala UTF-8 — e o arquivo chega ao paciente
     * justamente por esses caminhos.
     */
    private String nomeDoArquivo(String titulo) {
        String limpo = Normalizer.normalize(titulo, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("(^-|-$)", "")
                .toLowerCase();
        return (limpo.isEmpty() ? "plano" : limpo) + ".pdf";
    }
}
