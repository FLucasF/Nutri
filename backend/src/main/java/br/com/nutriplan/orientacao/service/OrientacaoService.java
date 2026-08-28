package br.com.nutriplan.orientacao.service;

import br.com.nutriplan.auth.service.ContextoAtual;
import br.com.nutriplan.orientacao.domain.ImagemDeOrientacao;
import br.com.nutriplan.orientacao.domain.ImagemDoPlano;
import br.com.nutriplan.orientacao.domain.Orientacao;
import br.com.nutriplan.orientacao.domain.OrientacaoDoPlano;
import br.com.nutriplan.orientacao.dto.OrientacaoDtos;
import br.com.nutriplan.orientacao.repository.ImagemDeOrientacaoRepository;
import br.com.nutriplan.orientacao.repository.ImagemDoPlanoRepository;
import br.com.nutriplan.orientacao.repository.OrientacaoDoPlanoRepository;
import br.com.nutriplan.orientacao.repository.OrientacaoRepository;
import br.com.nutriplan.prescricao.service.PlanoAlimentarService;
import br.com.nutriplan.shared.error.RecursoNaoEncontradoException;
import br.com.nutriplan.shared.error.RegraDeNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Biblioteca de orientacoes e o que delas chega ao plano.
 *
 * O sistema traz alguns modelos como ponto de partida. Eles nao sao editaveis —
 * pela mesma razao que as tabelas de referencia de alimentos nao sao: sao
 * acervo compartilhado, e editar em nome de todos seria decidir por consultorio
 * alheio. Quem quer mudar, copia.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrientacaoService {

    private final OrientacaoRepository orientacaoRepository;
    private final OrientacaoDoPlanoRepository doPlanoRepository;
    private final ImagemDeOrientacaoRepository imagemDaBiblioteca;
    private final ImagemDoPlanoRepository imagemDoPlano;
    private final PlanoAlimentarService planoService;
    private final ContextoAtual contextoAtual;

    // ------------------------------------------------------------- biblioteca

    @Transactional(readOnly = true)
    public Page<OrientacaoDtos.OrientacaoResponse> listar(String termo, Pageable pageable) {
        String busca = StringUtils.hasText(termo) ? termo.trim().toLowerCase() : null;
        return orientacaoRepository.visiveisPara(contextoAtual.contaId(), busca, pageable)
                .map(OrientacaoDtos.OrientacaoResponse::de);
    }

    @Transactional(readOnly = true)
    public OrientacaoDtos.OrientacaoResponse detalhar(Long id) {
        return OrientacaoDtos.OrientacaoResponse.de(exigirVisivel(id));
    }

    @Transactional
    public OrientacaoDtos.OrientacaoResponse criar(OrientacaoDtos.OrientacaoRequest req) {
        var orientacao = new Orientacao(contextoAtual.contaId(), req.titulo(), req.corpo());
        orientacaoRepository.save(orientacao);
        log.info("Orientação criada: id={} conta={}", orientacao.getId(), orientacao.getContaId());
        return OrientacaoDtos.OrientacaoResponse.de(orientacao);
    }

    /**
     * Copia um modelo para a biblioteca do consultorio, ja editavel.
     *
     * E o caminho para adaptar um modelo do sistema sem alterar o original —
     * o mesmo desenho de "cadastre um alimento próprio a partir deste".
     */
    @Transactional
    public OrientacaoDtos.OrientacaoResponse duplicar(Long id) {
        Orientacao origem = exigirVisivel(id);
        var copia = new Orientacao(contextoAtual.contaId(),
                recortar(origem.getTitulo() + " (minha versão)", 150), origem.getCorpo());
        orientacaoRepository.save(copia);
        return OrientacaoDtos.OrientacaoResponse.de(copia);
    }

    @Transactional
    public OrientacaoDtos.OrientacaoResponse atualizar(Long id, OrientacaoDtos.OrientacaoRequest req) {
        Orientacao orientacao = exigirDaConta(id);
        orientacao.setTitulo(req.titulo());
        orientacao.setCorpo(req.corpo());
        return OrientacaoDtos.OrientacaoResponse.de(orientacao);
    }

    @Transactional
    public void remover(Long id) {
        Orientacao orientacao = exigirDaConta(id);
        // Inativa: planos ja entregues referenciam esta orientacao como
        // procedencia, e o texto entregue continua valendo.
        orientacao.setAtivo(false);
    }

    // ---------------------------------------------------------- no plano

    @Transactional(readOnly = true)
    public List<OrientacaoDtos.OrientacaoDoPlanoResponse> doPlano(Long planoId) {
        planoService.exigirDaConta(planoId);
        return doPlanoRepository.findByPlanoIdOrderByOrdemAsc(planoId).stream()
                .map(OrientacaoDtos.OrientacaoDoPlanoResponse::de)
                .toList();
    }

    /**
     * Anexa uma orientacao ao plano, copiando o texto.
     *
     * A copia acontece agora, e nao na publicacao: assim o nutricionista pode
     * adaptar o texto para este paciente sem sujar o modelo, e nenhuma edicao
     * posterior da biblioteca alcanca o que foi anexado.
     */
    @Transactional
    public OrientacaoDtos.OrientacaoDoPlanoResponse anexar(Long planoId,
                                                          OrientacaoDtos.AnexoRequest req) {
        planoService.exigirDaConta(planoId);

        String titulo = req.titulo();
        String corpo = req.corpo();
        Long origem = req.orientacaoId();

        if (origem != null) {
            Orientacao modelo = exigirVisivel(origem);
            if (!StringUtils.hasText(titulo)) {
                titulo = modelo.getTitulo();
            }
            if (!StringUtils.hasText(corpo)) {
                corpo = modelo.getCorpo();
            }
        }
        if (!StringUtils.hasText(titulo) || !StringUtils.hasText(corpo)) {
            throw new RegraDeNegocioException(
                    "Informe o texto da orientação, ou escolha uma da biblioteca");
        }

        int ordem = doPlanoRepository.findByPlanoIdOrderByOrdemAsc(planoId).size();
        var anexo = new OrientacaoDoPlano(planoId, recortar(titulo, 150),
                recortar(corpo, 8000), ordem);
        anexo.setOrientacaoId(origem);
        doPlanoRepository.save(anexo);

        // A imagem e copiada junto, e nao referenciada: trocar a figura no
        // modelo depois nao pode mudar o que o paciente ja recebeu.
        if (origem != null) {
            Orientacao modelo = exigirVisivel(origem);
            if (modelo.temImagem()) {
                imagemDaBiblioteca.findById(origem).ifPresent(imagem -> {
                    anexo.setImagemNome(modelo.getImagemNome());
                    anexo.setImagemTipo(modelo.getImagemTipo());
                    imagemDoPlano.save(new ImagemDoPlano(anexo.getId(), imagem.getConteudo()));
                });
            }
        }

        log.info("Orientação anexada ao plano {}: origem={}", planoId, origem);
        return OrientacaoDtos.OrientacaoDoPlanoResponse.de(anexo);
    }

    @Transactional
    public OrientacaoDtos.OrientacaoDoPlanoResponse editarNoPlano(
            Long planoId, Long anexoId, OrientacaoDtos.OrientacaoRequest req) {
        OrientacaoDoPlano anexo = exigirAnexo(planoId, anexoId);
        anexo.setTitulo(req.titulo());
        anexo.setCorpo(req.corpo());
        return OrientacaoDtos.OrientacaoDoPlanoResponse.de(anexo);
    }

    @Transactional
    public void desanexar(Long planoId, Long anexoId) {
        doPlanoRepository.delete(exigirAnexo(planoId, anexoId));
    }

    // ----------------------------------------------------------------- imagem

    /** Nome, tipo e conteudo, prontos para a resposta HTTP. */
    public record Imagem(String nome, String tipo, byte[] conteudo) {}

    /** Uma figura de prato dividido vale mais que o paragrafo que a descreve. */
    private static final int LIMITE_DA_IMAGEM = 2 * 1024 * 1024;

    @Transactional
    public void anexarImagem(Long id, String nome, String tipo, byte[] conteudo) {
        if (conteudo == null || conteudo.length == 0) {
            throw new RegraDeNegocioException("Envie um arquivo não vazio");
        }
        if (conteudo.length > LIMITE_DA_IMAGEM) {
            throw new RegraDeNegocioException("A imagem pode ter no máximo 2 MB");
        }
        if (tipo == null || !tipo.startsWith("image/")) {
            throw new RegraDeNegocioException("O arquivo precisa ser uma imagem");
        }
        Orientacao orientacao = exigirDaConta(id);
        orientacao.setImagemNome(nome);
        orientacao.setImagemTipo(tipo);
        imagemDaBiblioteca.save(new ImagemDeOrientacao(id, conteudo));
    }

    @Transactional(readOnly = true)
    public Imagem imagem(Long id) {
        Orientacao orientacao = exigirVisivel(id);
        var arquivo = imagemDaBiblioteca.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Imagem da orientação", id));
        return new Imagem(orientacao.getImagemNome(), orientacao.getImagemTipo(),
                arquivo.getConteudo());
    }

    /** A imagem entregue num plano — a copia, e nao a da biblioteca. */
    @Transactional(readOnly = true)
    public Imagem imagemDoPlano(Long planoId, Long anexoId) {
        OrientacaoDoPlano anexo = exigirAnexo(planoId, anexoId);
        var arquivo = imagemDoPlano.findById(anexoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Imagem da orientação do plano", anexoId));
        return new Imagem(anexo.getImagemNome(), anexo.getImagemTipo(), arquivo.getConteudo());
    }

    // ------------------------------------------------------------------ apoio

    private OrientacaoDoPlano exigirAnexo(Long planoId, Long anexoId) {
        planoService.exigirDaConta(planoId);
        return doPlanoRepository.findById(anexoId)
                .filter(a -> a.getPlanoId().equals(planoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Orientação do plano " + planoId, anexoId));
    }

    private Orientacao exigirVisivel(Long id) {
        return orientacaoRepository.buscarVisivel(id, contextoAtual.contaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Orientação", id));
    }

    private Orientacao exigirDaConta(Long id) {
        Orientacao orientacao = exigirVisivel(id);
        if (orientacao.ehModeloDoSistema()) {
            throw new RegraDeNegocioException(
                    "Modelos do sistema não podem ser alterados. Duplique para criar a sua versão.");
        }
        return orientacao;
    }

    private String recortar(String texto, int limite) {
        return texto.length() <= limite ? texto : texto.substring(0, limite);
    }
}
