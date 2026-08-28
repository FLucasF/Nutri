package br.com.nutriplan.alimento.service;

import br.com.nutriplan.alimento.domain.Alimento;
import br.com.nutriplan.alimento.domain.FonteDeDados;
import br.com.nutriplan.alimento.domain.MedidaCaseira;
import br.com.nutriplan.alimento.dto.AlimentoDtos;
import br.com.nutriplan.alimento.dto.ComposicaoDto;
import br.com.nutriplan.alimento.repository.AlimentoRepository;
import br.com.nutriplan.alimento.repository.MedidaCaseiraRepository;
import br.com.nutriplan.auth.service.ContextoAtual;
import br.com.nutriplan.shared.error.RecursoNaoEncontradoException;
import br.com.nutriplan.shared.error.RegraDeNegocioException;
import br.com.nutriplan.shared.util.MedidaNoPlural;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlimentoService {

    private final AlimentoRepository alimentoRepository;
    private final MedidaCaseiraRepository medidaCaseiraRepository;
    private final LeitorDeTabelaDeAlimentos leitor;
    private final ContextoAtual contextoAtual;

    /**
     * Busca alimentos.
     *
     * Com termo, ordena por relevância; sem termo, respeita a ordenação pedida
     * pelo cliente — normalmente alfabética, que é o que faz sentido ao navegar
     * um grupo inteiro.
     *
     * Na busca por relevância o Pageable vai deliberadamente sem ordenação: o
     * Spring Data anexaria o sort do cliente ao ORDER BY da consulta, e um
     * "ordenar por descrição" vindo do controller anularia todo o ranqueamento.
     */
    @Transactional(readOnly = true)
    public Page<AlimentoDtos.Resumo> buscar(String termo, String grupo, FonteDeDados fonte, Pageable pageable) {
        Long contaId = contextoAtual.contaId();
        String grupoFiltro = StringUtils.hasText(grupo) ? grupo : null;

        if (!StringUtils.hasText(termo)) {
            return alimentoRepository
                    .buscar(contaId, null, grupoFiltro, fonte, pageable)
                    .map(AlimentoDtos.Resumo::de);
        }

        var semOrdenacao = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return alimentoRepository
                .buscarPorRelevancia(contaId, Alimento.normalizarParaBusca(termo),
                        grupoFiltro, fonte, semOrdenacao)
                .map(AlimentoDtos.Resumo::de);
    }

    @Transactional(readOnly = true)
    public AlimentoDtos.Detalhe detalhar(Long id) {
        Long contaId = contextoAtual.contaId();
        Alimento alimento = exigirVisivel(id);
        return AlimentoDtos.Detalhe.de(
                alimento, medidaCaseiraRepository.visiveisPara(id, contaId), contaId);
    }

    /**
     * Localiza um produto industrializado pelo codigo de barras.
     *
     * O codigo e a forma natural de chegar ao produto quando ele esta na mao do
     * paciente: a embalagem tem o EAN impresso, e digitar treze digitos e mais
     * rapido e menos ambiguo que caçar "Biscoito recheado sabor chocolate 140g"
     * entre 21 mil industrializados de nome irregular.
     *
     * Devolve lista porque o codigo nao e chave: ha duplicidade dentro do Open
     * Food Facts, que e colaborativo, e o consultorio pode ter cadastrado o
     * proprio produto com o mesmo EAN.
     */
    @Transactional(readOnly = true)
    public List<AlimentoDtos.Detalhe> porCodigoDeBarras(String codigo) {
        Long contaId = contextoAtual.contaId();
        String limpo = codigo == null ? "" : codigo.replaceAll("[^0-9]", "");
        if (limpo.isEmpty()) {
            throw new RegraDeNegocioException("Informe o código de barras, só com dígitos");
        }
        return alimentoRepository.porCodigoDeBarras(limpo, contaId).stream()
                .map(a -> AlimentoDtos.Detalhe.de(
                        a, medidaCaseiraRepository.visiveisPara(a.getId(), contaId), contaId))
                .toList();
    }

    /**
     * Cadastra uma porcao usual para o consultorio.
     *
     * Funciona tambem sobre alimentos da base publica — e justamente o caso
     * principal: a TACO nao traz porcoes, entao o nutricionista precisa poder
     * dizer que "1 colher de sopa de arroz" pesa 25 g no atendimento dele.
     * A medida criada fica visivel apenas para o consultorio que a cadastrou.
     */
    @Transactional
    public AlimentoDtos.MedidaResponse adicionarMedida(Long alimentoId, AlimentoDtos.MedidaRequest req) {
        Long contaId = contextoAtual.contaId();
        Alimento alimento = exigirVisivel(alimentoId);

        if (medidaCaseiraRepository.jaExisteNaConta(alimentoId, contaId, req.descricao())) {
            throw new RegraDeNegocioException(
                    "Este consultório já tem uma medida chamada \"%s\" para este alimento"
                            .formatted(req.descricao()));
        }

        var medida = new MedidaCaseira(req.descricao(), req.gramas());
        medida.setAlimento(alimento);
        medida.setContaId(contaId);
        medida.setPadrao(req.padrao());
        medidaCaseiraRepository.save(medida);

        if (req.padrao()) {
            desmarcarOutrasPadrao(alimentoId, contaId, medida.getId());
        }

        log.info("Medida caseira cadastrada: alimento={} conta={} descrição={}",
                alimentoId, contaId, req.descricao());
        return AlimentoDtos.MedidaResponse.de(medida, contaId);
    }

    @Transactional
    public void removerMedida(Long alimentoId, Long medidaId) {
        Long contaId = contextoAtual.contaId();
        MedidaCaseira medida = medidaCaseiraRepository.visivelPara(medidaId, alimentoId, contaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Medida caseira", medidaId));

        if (!medida.editavelPor(contaId)) {
            throw new RegraDeNegocioException(
                    "Porções que acompanham o sistema não podem ser removidas. "
                    + "Cadastre a sua própria versão, que terá precedência sobre ela.");
        }
        medidaCaseiraRepository.delete(medida);
    }

    /**
     * Garante uma unica medida padrao por alimento dentro do consultorio.
     * Porcoes do acervo base nao sao tocadas: pertencem a todos os consultorios.
     */
    private void desmarcarOutrasPadrao(Long alimentoId, Long contaId, Long manterId) {
        medidaCaseiraRepository.visiveisPara(alimentoId, contaId).stream()
                .filter(m -> !m.getId().equals(manterId))
                .filter(m -> m.editavelPor(contaId))
                .filter(MedidaCaseira::isPadrao)
                .forEach(m -> m.setPadrao(false));
    }

    @Transactional(readOnly = true)
    public List<String> listarGrupos() {
        return alimentoRepository.listarGrupos();
    }

    @Transactional(readOnly = true)
    public Alimento exigirVisivel(Long id) {
        return alimentoRepository.buscarVisivel(id, contextoAtual.contaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Alimento", id));
    }

    /**
     * Calcula a composicao de uma porcao.
     *
     * A quantidade pode vir em gramas ou como multiplo de uma medida caseira
     * ("2,5 colheres de sopa"); no segundo caso o peso e derivado da medida.
     */
    @Transactional(readOnly = true)
    public AlimentoDtos.PorcaoCalculada calcularPorcao(Long alimentoId, BigDecimal quantidade, Long medidaId) {
        if (quantidade == null || quantidade.signum() <= 0) {
            throw new RegraDeNegocioException("A quantidade deve ser maior que zero");
        }
        Alimento alimento = exigirVisivel(alimentoId);

        BigDecimal gramas;
        String medidaUsada;
        if (medidaId != null) {
            // Busca filtrada por conta: uma medida cadastrada por outro
            // consultorio nao pode ser usada para calcular aqui.
            MedidaCaseira medida = medidaCaseiraRepository
                    .visivelPara(medidaId, alimentoId, contextoAtual.contaId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException(
                            "Medida caseira do alimento " + alimentoId, medidaId));
            gramas = medida.gramasPara(quantidade);
            // Mesma concordancia usada na prescricao: "2 porções", nao "2 porção".
            medidaUsada = "%s %s".formatted(quantidade.stripTrailingZeros().toPlainString(),
                    MedidaNoPlural.concordar(quantidade, medida.getDescricao()));
        } else {
            gramas = quantidade;
            medidaUsada = "%s g".formatted(quantidade.stripTrailingZeros().toPlainString());
        }

        return new AlimentoDtos.PorcaoCalculada(
                alimento.getId(), alimento.getDescricao(), gramas, medidaUsada,
                ComposicaoDto.de(alimento.composicaoPara(gramas)));
    }

    /**
     * Importa uma tabela de alimentos enviada pelo nutricionista.
     *
     * Os alimentos entram sempre vinculados a conta de quem importou, nunca na
     * base publica: uma planilha enviada por um consultorio nao pode alterar o
     * acervo que os outros enxergam, mesmo que declare fonte TACO ou TBCA.
     *
     * A fonte informada serve de procedencia — o nutricionista responde
     * tecnicamente pelo dado que prescreve, e precisa saber de onde ele veio.
     */
    @Transactional
    public AlimentoDtos.ResultadoImportacao importar(java.io.Reader arquivo,
                                                     FonteDeDados fonte,
                                                     char separador) throws java.io.IOException {
        Long contaId = contextoAtual.contaId();
        var resultado = leitor.ler(arquivo, fonte == null ? FonteDeDados.PERSONALIZADO : fonte, separador);

        if (resultado.vazio()) {
            return new AlimentoDtos.ResultadoImportacao(0, resultado.linhasIgnoradas(), resultado.avisos());
        }

        var avisos = new java.util.ArrayList<>(resultado.avisos());
        var aGravar = new java.util.ArrayList<Alimento>(resultado.alimentos().size());
        var codigosVistos = new java.util.HashSet<String>();
        int repetidos = 0;

        for (Alimento alimento : resultado.alimentos()) {
            // Vinculo obrigatorio: o alimento importado pertence a quem importou.
            alimento.setContaId(contaId);

            String chave = alimento.getCodigoBarras() != null
                    ? alimento.getCodigoBarras()
                    : alimento.getCodigoFonte();
            if (chave != null && !codigosVistos.add(chave)) {
                repetidos++;
                continue;
            }
            aGravar.add(alimento);
        }
        if (repetidos > 0) {
            avisos.add("%d linhas com código repetido foram descartadas.".formatted(repetidos));
        }

        alimentoRepository.saveAll(aGravar);
        log.info("Importação de tabela: conta={} fonte={} gravados={}", contaId, fonte, aGravar.size());

        return new AlimentoDtos.ResultadoImportacao(
                aGravar.size(), resultado.linhasIgnoradas() + repetidos, avisos);
    }

    @Transactional
    public AlimentoDtos.Detalhe criar(AlimentoDtos.AlimentoRequest req) {
        Alimento alimento = new Alimento(req.descricao(), FonteDeDados.PERSONALIZADO);
        alimento.setContaId(contextoAtual.contaId());
        aplicar(req, alimento);

        alimentoRepository.save(alimento);
        log.info("Alimento próprio cadastrado: id={} conta={}", alimento.getId(), alimento.getContaId());
        return AlimentoDtos.Detalhe.de(alimento, alimento.getMedidas(), alimento.getContaId());
    }

    @Transactional
    public AlimentoDtos.Detalhe atualizar(Long id, AlimentoDtos.AlimentoRequest req) {
        Alimento alimento = exigirVisivel(id);
        if (alimento.ehBasePublica()) {
            throw new RegraDeNegocioException(
                    "Alimentos das tabelas de referência não podem ser editados. "
                    + "Cadastre um alimento próprio a partir dele, se precisar ajustar valores.");
        }
        alimento.setDescricao(req.descricao());
        aplicar(req, alimento);
        return AlimentoDtos.Detalhe.de(alimento, alimento.getMedidas(), alimento.getContaId());
    }

    @Transactional
    public void inativar(Long id) {
        Alimento alimento = exigirVisivel(id);
        if (alimento.ehBasePublica()) {
            throw new RegraDeNegocioException("Alimentos das tabelas de referência não podem ser removidos");
        }
        alimento.setAtivo(false);
    }

    private void aplicar(AlimentoDtos.AlimentoRequest req, Alimento alimento) {
        alimento.setGrupo(req.grupo());
        // So digitos: o codigo pode vir pontuado do leitor ou digitado a mao.
        alimento.setCodigoBarras(req.codigoBarras() == null ? null
                : req.codigoBarras().replaceAll("[^0-9]", ""));
        alimento.setMarca(req.marca());
        alimento.setComposicao(req.composicao().paraDominio());

        alimento.getMedidas().clear();
        if (req.medidas() != null) {
            long padroes = req.medidas().stream().filter(AlimentoDtos.MedidaRequest::padrao).count();
            if (padroes > 1) {
                throw new RegraDeNegocioException("Apenas uma medida caseira pode ser marcada como padrão");
            }
            req.medidas().forEach(m -> {
                var medida = new MedidaCaseira(m.descricao(), m.gramas());
                medida.setPadrao(m.padrao());
                // Sem o contaId a porcao seria lida como acervo base e o proprio
                // dono do alimento nao conseguiria edita-la depois.
                medida.setContaId(alimento.getContaId());
                alimento.adicionarMedida(medida);
            });
        }
    }
}
