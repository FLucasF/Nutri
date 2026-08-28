package br.com.nutriplan.prescricao.service;

import br.com.nutriplan.alimento.domain.Alimento;
import br.com.nutriplan.alimento.domain.MedidaCaseira;
import br.com.nutriplan.alimento.dto.ComposicaoDto;
import br.com.nutriplan.alimento.repository.AlimentoRepository;
import br.com.nutriplan.alimento.repository.MedidaCaseiraRepository;
import br.com.nutriplan.auth.service.ContextoAtual;
import br.com.nutriplan.paciente.domain.Paciente;
import br.com.nutriplan.paciente.repository.PacienteRepository;
import br.com.nutriplan.prescricao.domain.EquivalenteItem;
import br.com.nutriplan.prescricao.domain.ItemRefeicao;
import br.com.nutriplan.prescricao.domain.MetodoPrescricao;
import br.com.nutriplan.prescricao.domain.PlanoAlimentar;
import br.com.nutriplan.prescricao.domain.Refeicao;
import br.com.nutriplan.prescricao.domain.StatusPlano;
import br.com.nutriplan.prescricao.dto.PrescricaoDtos;
import br.com.nutriplan.prescricao.repository.PlanoAlimentarRepository;
import br.com.nutriplan.shared.error.RecursoNaoEncontradoException;
import br.com.nutriplan.shared.error.RegraDeNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlanoAlimentarService {

    private final PlanoAlimentarRepository planoRepository;
    private final AlimentoRepository alimentoRepository;
    private final MedidaCaseiraRepository medidaRepository;
    private final PacienteRepository pacienteRepository;
    private final CalculadoraNutricional calculadora;
    private final ContextoAtual contextoAtual;

    // ------------------------------------------------------------------ leitura

    @Transactional(readOnly = true)
    public Page<PrescricaoDtos.PlanoResumo> listar(Long pacienteId, Boolean modelo,
                                                   String termo, Pageable pageable) {
        Long contaId = contextoAtual.contaId();
        String busca = StringUtils.hasText(termo) ? termo.trim() : null;

        Page<PlanoAlimentar> pagina = planoRepository.buscar(contaId, pacienteId, modelo, busca, pageable);
        Map<Long, String> nomes = nomesDePacientes(pagina.getContent());

        return pagina.map(plano -> {
            var alimentos = carregarAlimentosDoPlano(plano, contaId);
            var total = calculadora.totalizarRefeicoes(plano.getRefeicoes(), alimentos);
            return new PrescricaoDtos.PlanoResumo(
                    plano.getId(), plano.getTitulo(), plano.getPacienteId(),
                    nomes.get(plano.getPacienteId()), plano.getMetodo(), plano.getStatus(),
                    plano.getVigenciaInicio(), plano.getVigenciaFim(), plano.isModelo(),
                    plano.getRefeicoes().size(), plano.totalDeItens(),
                    total.composicao().getEnergiaKcal(), plano.getAtualizadoEm());
        });
    }

    @Transactional(readOnly = true)
    public PrescricaoDtos.PlanoResponse detalhar(Long id) {
        Long contaId = contextoAtual.contaId();
        PlanoAlimentar plano = exigirDaConta(id);
        return montarResposta(plano, contaId);
    }

    @Transactional(readOnly = true)
    public PlanoAlimentar exigirDaConta(Long id) {
        return planoRepository.carregarCompleto(id, contextoAtual.contaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Plano alimentar", id));
    }

    // ------------------------------------------------------------------ escrita

    @Transactional
    public PrescricaoDtos.PlanoResponse criar(PrescricaoDtos.PlanoRequest req) {
        Long contaId = contextoAtual.contaId();
        validarVinculo(req, contaId);

        var plano = new PlanoAlimentar(contaId, req.titulo());
        aplicarCabecalho(req, plano);
        substituirRefeicoes(plano, req.refeicoes(), contaId);

        planoRepository.save(plano);
        log.info("Plano criado: id={} paciente={} conta={}", plano.getId(), plano.getPacienteId(), contaId);
        return montarResposta(plano, contaId);
    }

    @Transactional
    public PrescricaoDtos.PlanoResponse atualizar(Long id, PrescricaoDtos.PlanoRequest req) {
        Long contaId = contextoAtual.contaId();
        PlanoAlimentar plano = exigirDaConta(id);

        if (!plano.podeSerEditado()) {
            throw new RegraDeNegocioException(
                    "Plano encerrado não pode ser alterado. Duplique-o para criar uma nova versão.");
        }
        validarVinculo(req, contaId);

        plano.setTitulo(req.titulo());
        aplicarCabecalho(req, plano);
        substituirRefeicoes(plano, req.refeicoes(), contaId);

        return montarResposta(plano, contaId);
    }

    /**
     * Publica o plano, tornando-o visivel pelo link do paciente.
     *
     * Um plano sem item nenhum nao pode ser publicado: o paciente abriria o
     * link e encontraria uma pagina vazia, o que e pior do que nao ter link.
     */
    @Transactional
    public PrescricaoDtos.PlanoResponse publicar(Long id) {
        Long contaId = contextoAtual.contaId();
        PlanoAlimentar plano = exigirDaConta(id);

        if (plano.isModelo()) {
            throw new RegraDeNegocioException(
                    "Modelo não e publicado. Aplique-o a um paciente para gerar um plano.");
        }
        if (plano.totalDeItens() == 0) {
            throw new RegraDeNegocioException("Inclua ao menos um item antes de publicar o plano");
        }
        plano.setStatus(StatusPlano.ATIVO);
        if (plano.getVigenciaInicio() == null) {
            plano.setVigenciaInicio(LocalDate.now());
        }
        log.info("Plano publicado: id={} link={}", plano.getId(), plano.getIdentificadorPublico());
        return montarResposta(plano, contaId);
    }

    @Transactional
    public PrescricaoDtos.PlanoResponse encerrar(Long id) {
        Long contaId = contextoAtual.contaId();
        PlanoAlimentar plano = exigirDaConta(id);
        plano.setStatus(StatusPlano.ENCERRADO);
        if (plano.getVigenciaFim() == null) {
            plano.setVigenciaFim(LocalDate.now());
        }
        return montarResposta(plano, contaId);
    }

    @Transactional
    public PrescricaoDtos.PlanoResponse voltarParaRascunho(Long id) {
        Long contaId = contextoAtual.contaId();
        PlanoAlimentar plano = exigirDaConta(id);
        plano.setStatus(StatusPlano.RASCUNHO);
        return montarResposta(plano, contaId);
    }

    /** Invalida o link entregue e gera outro — usado se o endereco vazar. */
    @Transactional
    public PrescricaoDtos.PlanoResponse regerarLink(Long id) {
        Long contaId = contextoAtual.contaId();
        PlanoAlimentar plano = exigirDaConta(id);
        plano.regerarIdentificadorPublico();
        log.info("Link do plano regerado: id={}", plano.getId());
        return montarResposta(plano, contaId);
    }

    @Transactional
    public void remover(Long id) {
        PlanoAlimentar plano = exigirDaConta(id);
        planoRepository.delete(plano);
        log.info("Plano removido: id={}", id);
    }

    /** Copia um plano — para revisar sem perder o que o paciente ja recebeu. */
    @Transactional
    public PrescricaoDtos.PlanoResponse duplicar(Long id, Long pacienteId, String titulo) {
        Long contaId = contextoAtual.contaId();
        PlanoAlimentar origem = exigirDaConta(id);

        Long destino = pacienteId != null ? pacienteId : origem.getPacienteId();
        if (destino == null) {
            throw new RegraDeNegocioException("Informe o paciente que receberá a cópia");
        }
        exigirPaciente(destino, contaId);

        var copia = new PlanoAlimentar(contaId,
                StringUtils.hasText(titulo) ? titulo : origem.getTitulo() + " (cópia)");
        copia.setPacienteId(destino);
        copia.setMetodo(origem.getMetodo());
        copia.setOrientacoes(origem.getOrientacoes());
        copia.setObservacoesInternas(origem.getObservacoesInternas());
        copia.setMetaEnergiaKcal(origem.getMetaEnergiaKcal());
        copia.setStatus(StatusPlano.RASCUNHO);

        for (Refeicao refeicao : origem.getRefeicoes()) {
            var nova = new Refeicao(refeicao.getNome(), refeicao.getHorario());
            nova.setOrdem(refeicao.getOrdem());
            nova.setObservacao(refeicao.getObservacao());

            for (ItemRefeicao item : refeicao.getItens()) {
                var novoItem = new ItemRefeicao(item.getDescricao());
                novoItem.setAlimentoId(item.getAlimentoId());
                novoItem.setMedidaId(item.getMedidaId());
                novoItem.setDescricaoMedida(item.getDescricaoMedida());
                novoItem.setQuantidade(item.getQuantidade());
                novoItem.setGramas(item.getGramas());
                novoItem.setOrdem(item.getOrdem());
                novoItem.setObservacao(item.getObservacao());

                for (EquivalenteItem equivalente : item.getEquivalentes()) {
                    var novo = new EquivalenteItem(equivalente.getDescricao());
                    novo.setAlimentoId(equivalente.getAlimentoId());
                    novo.setMedidaId(equivalente.getMedidaId());
                    novo.setDescricaoMedida(equivalente.getDescricaoMedida());
                    novo.setQuantidade(equivalente.getQuantidade());
                    novo.setGramas(equivalente.getGramas());
                    novoItem.adicionarEquivalente(novo);
                }
                nova.adicionarItem(novoItem);
            }
            copia.adicionarRefeicao(nova);
        }

        planoRepository.save(copia);
        return montarResposta(copia, contaId);
    }

    // ------------------------------------------------------------------ montagem

    private void aplicarCabecalho(PrescricaoDtos.PlanoRequest req, PlanoAlimentar plano) {
        plano.setMetodo(req.metodo());
        plano.setModelo(req.modelo());
        plano.setPacienteId(req.modelo() ? null : req.pacienteId());
        plano.setVigenciaInicio(req.vigenciaInicio());
        plano.setVigenciaFim(req.vigenciaFim());
        plano.setOrientacoes(req.orientacoes());
        plano.setObservacoesInternas(req.observacoesInternas());
        plano.setMetaEnergiaKcal(req.metaEnergiaKcal());

        if (req.vigenciaInicio() != null && req.vigenciaFim() != null
                && req.vigenciaFim().isBefore(req.vigenciaInicio())) {
            throw new RegraDeNegocioException("A vigência não pode terminar antes de começar");
        }
    }

    private void validarVinculo(PrescricaoDtos.PlanoRequest req, Long contaId) {
        if (req.modelo()) {
            return;
        }
        if (req.pacienteId() == null) {
            throw new RegraDeNegocioException(
                    "Informe o paciente, ou marque o plano como modelo se ele não for de ninguém");
        }
        exigirPaciente(req.pacienteId(), contaId);
    }

    private Paciente exigirPaciente(Long pacienteId, Long contaId) {
        return pacienteRepository.findByIdAndContaId(pacienteId, contaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Paciente", pacienteId));
    }

    /**
     * Reconstroi as refeicoes a partir do pedido.
     *
     * A substituicao e integral em vez de incremental: a tela de prescricao
     * envia o plano inteiro a cada gravacao, e casar item a item para descobrir
     * o que mudou traria complexidade sem ganho — o volume de um plano e
     * pequeno, e reconstruir e menos sujeito a erro do que sincronizar.
     */
    private void substituirRefeicoes(PlanoAlimentar plano,
                                     List<PrescricaoDtos.RefeicaoRequest> pedidas,
                                     Long contaId) {
        plano.getRefeicoes().clear();
        if (pedidas == null || pedidas.isEmpty()) {
            return;
        }

        var alimentos = carregarAlimentosPedidos(pedidas, contaId);
        var medidas = carregarMedidasPedidas(pedidas, contaId);

        for (PrescricaoDtos.RefeicaoRequest pedida : pedidas) {
            var refeicao = new Refeicao(pedida.nome(), pedida.horario());
            refeicao.setObservacao(pedida.observacao());

            if (pedida.itens() != null) {
                for (PrescricaoDtos.ItemRequest itemPedido : pedida.itens()) {
                    refeicao.adicionarItem(montarItem(itemPedido, plano.getMetodo(), alimentos, medidas));
                }
            }
            plano.adicionarRefeicao(refeicao);
        }
        plano.renumerarRefeicoes();
        plano.getRefeicoes().forEach(Refeicao::renumerarItens);
    }

    private ItemRefeicao montarItem(PrescricaoDtos.ItemRequest pedido,
                                    MetodoPrescricao metodo,
                                    Map<Long, Alimento> alimentos,
                                    Map<Long, MedidaCaseira> medidas) {

        Alimento alimento = pedido.alimentoId() == null ? null : alimentos.get(pedido.alimentoId());
        if (pedido.alimentoId() != null && alimento == null) {
            throw new RecursoNaoEncontradoException("Alimento", pedido.alimentoId());
        }

        String descricao = StringUtils.hasText(pedido.descricao())
                ? pedido.descricao()
                : (alimento != null ? alimento.getDescricao() : null);
        if (!StringUtils.hasText(descricao)) {
            throw new RegraDeNegocioException(
                    "Cada item precisa de um alimento ou de uma descrição livre");
        }

        var item = new ItemRefeicao(descricao);
        item.setAlimentoId(pedido.alimentoId());
        item.setObservacao(pedido.observacao());

        // Plano qualitativo nao quantifica: "salada a vontade" nao tem numero,
        // e inventar um seria criar dado clinico que ninguem prescreveu.
        if (metodo.ehQuantificado()) {
            var porcao = resolverPorcao(pedido.alimentoId(), pedido.medidaId(),
                    pedido.quantidade(), medidas);
            item.setMedidaId(porcao.medidaId());
            item.setDescricaoMedida(porcao.descricaoMedida());
            item.setQuantidade(porcao.quantidade());
            item.setGramas(porcao.gramas());
        }

        if (pedido.equivalentes() != null && !pedido.equivalentes().isEmpty()) {
            if (!metodo.admiteEquivalentes()) {
                throw new RegraDeNegocioException(
                        "Substituições só existem no método por equivalentes");
            }
            for (var equivalentePedido : pedido.equivalentes()) {
                var equivalente = new EquivalenteItem(equivalentePedido.descricao());
                equivalente.setAlimentoId(equivalentePedido.alimentoId());
                var porcao = resolverPorcao(equivalentePedido.alimentoId(),
                        equivalentePedido.medidaId(), equivalentePedido.quantidade(), medidas);
                equivalente.setMedidaId(porcao.medidaId());
                equivalente.setDescricaoMedida(porcao.descricaoMedida());
                equivalente.setQuantidade(porcao.quantidade());
                equivalente.setGramas(porcao.gramas());
                item.adicionarEquivalente(equivalente);
            }
        }
        return item;
    }

    private record Porcao(Long medidaId, String descricaoMedida,
                          BigDecimal quantidade, BigDecimal gramas) {}

    /**
     * Converte "3 colheres de sopa" no peso que o calculo usa.
     *
     * Sem medida informada, a quantidade ja e o peso em gramas.
     */
    private Porcao resolverPorcao(Long alimentoId, Long medidaId,
                                  BigDecimal quantidade, Map<Long, MedidaCaseira> medidas) {
        if (quantidade == null) {
            return new Porcao(null, null, null, null);
        }
        if (medidaId == null) {
            return new Porcao(null, null, quantidade, quantidade);
        }

        MedidaCaseira medida = medidas.get(medidaId);
        if (medida == null) {
            throw new RecursoNaoEncontradoException("Medida caseira", medidaId);
        }
        if (alimentoId != null && !medida.getAlimento().getId().equals(alimentoId)) {
            throw new RegraDeNegocioException(
                    "A porção informada não pertence ao alimento escolhido");
        }
        return new Porcao(medidaId, medida.getDescricao(), quantidade, medida.gramasPara(quantidade));
    }

    // ------------------------------------------------------------------ apoio

    private Map<Long, Alimento> carregarAlimentosPedidos(
            List<PrescricaoDtos.RefeicaoRequest> refeicoes, Long contaId) {

        List<Long> ids = new ArrayList<>();
        for (var refeicao : refeicoes) {
            if (refeicao.itens() == null) {
                continue;
            }
            for (var item : refeicao.itens()) {
                if (item.alimentoId() != null) {
                    ids.add(item.alimentoId());
                }
                if (item.equivalentes() != null) {
                    item.equivalentes().stream()
                            .map(PrescricaoDtos.EquivalenteRequest::alimentoId)
                            .filter(java.util.Objects::nonNull)
                            .forEach(ids::add);
                }
            }
        }
        return ids.isEmpty() ? Map.of() : indexar(alimentoRepository.buscarVisiveis(ids, contaId));
    }

    private Map<Long, MedidaCaseira> carregarMedidasPedidas(
            List<PrescricaoDtos.RefeicaoRequest> refeicoes, Long contaId) {

        List<Long> ids = new ArrayList<>();
        for (var refeicao : refeicoes) {
            if (refeicao.itens() == null) {
                continue;
            }
            for (var item : refeicao.itens()) {
                if (item.medidaId() != null) {
                    ids.add(item.medidaId());
                }
                if (item.equivalentes() != null) {
                    item.equivalentes().stream()
                            .map(PrescricaoDtos.EquivalenteRequest::medidaId)
                            .filter(java.util.Objects::nonNull)
                            .forEach(ids::add);
                }
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        // Filtra por visibilidade: porcao de outro consultorio nao serve aqui.
        return medidaRepository.findAllById(ids).stream()
                .filter(m -> m.getContaId() == null || m.getContaId().equals(contaId))
                .collect(Collectors.toMap(MedidaCaseira::getId, Function.identity(), (a, b) -> a));
    }

    /** Alimentos citados por um plano ja gravado, para totalizar. */
    private Map<Long, Alimento> carregarAlimentosDoPlano(PlanoAlimentar plano, Long contaId) {
        List<Long> ids = plano.getRefeicoes().stream()
                .flatMap(r -> r.getItens().stream())
                .map(ItemRefeicao::getAlimentoId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        return ids.isEmpty() ? Map.of() : indexar(alimentoRepository.buscarVisiveis(ids, contaId));
    }

    private Map<Long, Alimento> indexar(List<Alimento> alimentos) {
        return alimentos.stream()
                .collect(Collectors.toMap(Alimento::getId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, String> nomesDePacientes(List<PlanoAlimentar> planos) {
        List<Long> ids = planos.stream()
                .map(PlanoAlimentar::getPacienteId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> nomes = new HashMap<>();
        pacienteRepository.findAllById(ids).forEach(p -> nomes.put(p.getId(), p.getNome()));
        return nomes;
    }

    // ------------------------------------------------------------------ resposta

    PrescricaoDtos.PlanoResponse montarResposta(PlanoAlimentar plano, Long contaId) {
        var alimentos = carregarAlimentosDoPlano(plano, contaId);

        List<PrescricaoDtos.RefeicaoResponse> refeicoes = plano.getRefeicoes().stream()
                .map(refeicao -> {
                    var total = calculadora.totalizar(refeicao.getItens(), alimentos);
                    return new PrescricaoDtos.RefeicaoResponse(
                            refeicao.getId(), refeicao.getNome(), refeicao.getHorario(),
                            refeicao.getOrdem(), refeicao.getObservacao(),
                            refeicao.getItens().stream().map(this::montarItemResposta).toList(),
                            montarTotal(total, null));
                })
                .toList();

        var totalDoDia = calculadora.totalizarRefeicoes(plano.getRefeicoes(), alimentos);

        String pacienteNome = plano.getPacienteId() == null ? null
                : pacienteRepository.findById(plano.getPacienteId())
                        .map(Paciente::getNome).orElse(null);

        return new PrescricaoDtos.PlanoResponse(
                plano.getId(), plano.getTitulo(), plano.getPacienteId(), pacienteNome,
                plano.getMetodo(), plano.getMetodo().getDescricao(),
                plano.getStatus(), plano.getStatus().getDescricao(),
                plano.getIdentificadorPublico(),
                plano.getVigenciaInicio(), plano.getVigenciaFim(),
                plano.getOrientacoes(), plano.getObservacoesInternas(),
                plano.getMetaEnergiaKcal(), plano.isModelo(),
                refeicoes, montarTotal(totalDoDia, plano.getMetaEnergiaKcal()),
                plano.getCriadoEm(), plano.getAtualizadoEm());
    }

    private PrescricaoDtos.ItemResponse montarItemResposta(ItemRefeicao item) {
        return new PrescricaoDtos.ItemResponse(
                item.getId(), item.getAlimentoId(), item.getMedidaId(),
                item.getDescricao(), item.porcaoFormatada(),
                item.getQuantidade(), item.getGramas(), item.getOrdem(), item.getObservacao(),
                item.getEquivalentes().stream()
                        .map(e -> new PrescricaoDtos.EquivalenteResponse(
                                e.getId(), e.getAlimentoId(), e.getDescricao(),
                                e.porcaoFormatada(), e.getGramas()))
                        .toList());
    }

    private PrescricaoDtos.TotalResponse montarTotal(CalculadoraNutricional.Total total,
                                                     BigDecimal metaKcal) {
        var distribuicao = calculadora.distribuicaoDeMacros(total.composicao());
        return new PrescricaoDtos.TotalResponse(
                ComposicaoDto.de(total.composicao()),
                total.itensNoCalculo(), total.itensForaDoCalculo(),
                total.nutrientesIncompletos(), total.nutrientesSemDado(),
                total.confiavel(),
                distribuicao == null ? null : new PrescricaoDtos.DistribuicaoResponse(
                        distribuicao.proteinaPct(), distribuicao.carboidratoPct(),
                        distribuicao.lipideoPct(), distribuicao.energiaCalculadaKcal()),
                calculadora.adequacaoEnergetica(total.composicao(), metaKcal));
    }
}
