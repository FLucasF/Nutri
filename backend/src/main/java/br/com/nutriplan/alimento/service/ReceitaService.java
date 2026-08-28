package br.com.nutriplan.alimento.service;

import br.com.nutriplan.alimento.domain.Alimento;
import br.com.nutriplan.alimento.domain.FonteDeDados;
import br.com.nutriplan.alimento.domain.IngredienteReceita;
import br.com.nutriplan.alimento.domain.MedidaCaseira;
import br.com.nutriplan.alimento.dto.ComposicaoDto;
import br.com.nutriplan.alimento.dto.ReceitaDtos;
import br.com.nutriplan.alimento.repository.AlimentoRepository;
import br.com.nutriplan.alimento.repository.MedidaCaseiraRepository;
import br.com.nutriplan.auth.service.ContextoAtual;
import br.com.nutriplan.shared.error.RecursoNaoEncontradoException;
import br.com.nutriplan.shared.error.RegraDeNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Receitas do consultorio.
 *
 * Uma receita e um {@link Alimento} com fonte {@code RECEITA} e composicao
 * calculada em vez de tabelada. A decisao vale por si: como receita e alimento,
 * ela ja aparece na busca, aceita medida caseira, entra numa refeicao e carrega
 * a procedencia na prescricao — sem uma linha de codigo a mais em nenhum desses
 * lugares. O que este servico acrescenta e o calculo e a lista de ingredientes.
 *
 * Uma receita pode entrar noutra: refogado dentro de torta. O unico limite e
 * nao usar a propria receita como ingrediente dela mesma, direta ou
 * indiretamente — o que produziria composicao infinita.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReceitaService {

    private final AlimentoRepository alimentoRepository;
    private final MedidaCaseiraRepository medidaCaseiraRepository;
    private final CalculadoraDeReceita calculadora;
    private final ContextoAtual contextoAtual;

    @Transactional(readOnly = true)
    public Page<ReceitaDtos.ReceitaResumo> listar(String termo, Pageable pageable) {
        return alimentoRepository
                .buscarReceitas(contextoAtual.contaId(), normalizar(termo), pageable)
                .map(this::resumir);
    }

    @Transactional(readOnly = true)
    public ReceitaDtos.ReceitaResponse detalhar(Long id) {
        return montarResposta(exigirReceita(id));
    }

    @Transactional
    public ReceitaDtos.ReceitaResponse criar(ReceitaDtos.ReceitaRequest req) {
        Alimento receita = new Alimento(req.nome(), FonteDeDados.RECEITA);
        receita.setContaId(contextoAtual.contaId());
        aplicar(req, receita);
        alimentoRepository.save(receita);

        log.info("Receita criada: id={} conta={} ingredientes={}",
                receita.getId(), receita.getContaId(), receita.getIngredientes().size());
        return montarResposta(receita);
    }

    @Transactional
    public ReceitaDtos.ReceitaResponse atualizar(Long id, ReceitaDtos.ReceitaRequest req) {
        Alimento receita = exigirReceita(id);
        receita.setDescricao(req.nome());
        aplicar(req, receita);
        return montarResposta(receita);
    }

    @Transactional
    public void remover(Long id) {
        Alimento receita = exigirReceita(id);
        // Inativa em vez de apagar: a receita pode estar prescrita em plano
        // ativo, e o plano precisa continuar dizendo o que foi prescrito.
        receita.setAtivo(false);
        log.info("Receita inativada: id={}", id);
    }

    // ------------------------------------------------------------------- apoio

    private Alimento exigirReceita(Long id) {
        Alimento alimento = alimentoRepository
                .buscarVisivel(id, contextoAtual.contaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Receita", id));
        if (!alimento.ehReceita()) {
            throw new RecursoNaoEncontradoException("Receita", id);
        }
        if (alimento.getContaId() == null) {
            throw new RegraDeNegocioException("Esta receita não pertence ao seu consultório");
        }
        return alimento;
    }

    private void aplicar(ReceitaDtos.ReceitaRequest req, Alimento receita) {
        receita.setGrupo(req.grupo());
        receita.setModoPreparo(req.modoPreparo());
        receita.setRendimentoGramas(req.rendimentoGramas());
        receita.setPorcoes(req.porcoes());

        List<Long> ids = req.ingredientes().stream()
                .map(ReceitaDtos.IngredienteRequest::alimentoId)
                .distinct()
                .toList();
        Map<Long, Alimento> porId = new LinkedHashMap<>();
        alimentoRepository.buscarVisiveis(ids, contextoAtual.contaId())
                .forEach(a -> porId.put(a.getId(), a));

        receita.getIngredientes().clear();
        int ordem = 0;
        for (var pedido : req.ingredientes()) {
            Alimento alimento = porId.get(pedido.alimentoId());
            if (alimento == null) {
                throw new RecursoNaoEncontradoException("Alimento", pedido.alimentoId());
            }
            recusarCiclo(receita, alimento);

            var ingrediente = new IngredienteReceita(
                    receita, alimento, resolverGramas(alimento, pedido), ordem++);
            ingrediente.setQuantidade(pedido.quantidade());
            ingrediente.setMedidaId(pedido.medidaId());
            if (pedido.medidaId() != null) {
                ingrediente.setDescricaoMedida(medidaCaseiraRepository
                        .visivelPara(pedido.medidaId(), alimento.getId(), contextoAtual.contaId())
                        .map(MedidaCaseira::getDescricao)
                        .orElse(null));
            }
            receita.getIngredientes().add(ingrediente);
        }

        var calculo = calculadora.calcular(receita.getIngredientes(), req.rendimentoGramas());
        receita.setComposicao(calculo.composicao());
        sincronizarMedidas(receita, calculo.rendimentoUsado());
    }

    private BigDecimal resolverGramas(Alimento alimento, ReceitaDtos.IngredienteRequest pedido) {
        if (pedido.medidaId() == null) {
            return pedido.quantidade();
        }
        MedidaCaseira medida = medidaCaseiraRepository
                .visivelPara(pedido.medidaId(), alimento.getId(), contextoAtual.contaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Medida caseira do alimento " + alimento.getId(), pedido.medidaId()));
        return medida.gramasPara(pedido.quantidade());
    }

    /**
     * Impede que uma receita use a si mesma como ingrediente, direta ou
     * indiretamente. Sem isso, calcular a composicao entraria em recursao —
     * e o erro so apareceria na gravacao seguinte, longe da causa.
     */
    private void recusarCiclo(Alimento receita, Alimento candidato) {
        if (receita.getId() != null && contem(candidato, receita.getId(), 0)) {
            throw new RegraDeNegocioException(
                    ("\"%s\" não pode ser ingrediente desta receita: ela já usa esta receita, "
                            + "direta ou indiretamente.").formatted(candidato.getDescricao()));
        }
    }

    private boolean contem(Alimento candidato, Long receitaId, int profundidade) {
        if (candidato.getId() != null && candidato.getId().equals(receitaId)) {
            return true;
        }
        // Uma receita dentro de outra dentro de outra e legitimo; dez niveis
        // ja indica engano, e o limite evita percorrer um grafo torto.
        if (profundidade > 10 || !candidato.ehReceita()) {
            return false;
        }
        return candidato.getIngredientes().stream()
                .anyMatch(i -> contem(i.getAlimento(), receitaId, profundidade + 1));
    }

    /**
     * Mantem as porcoes derivadas do rendimento.
     *
     * A receita nasce com as medidas caseiras que fazem sentido para ela — a
     * preparacao inteira e, quando o nutricionista informa em quantas porcoes
     * ela rende, a porcao. Sem isso a receita entraria no plano em gramas, que
     * e exatamente o que o resto do sistema evita.
     */
    private void sincronizarMedidas(Alimento receita, BigDecimal rendimento) {
        receita.getMedidas().removeIf(m -> DERIVADAS.contains(m.getDescricao()));
        if (rendimento == null || rendimento.signum() <= 0) {
            return;
        }

        var inteira = new MedidaCaseira(RECEITA_INTEIRA, rendimento.stripTrailingZeros());
        inteira.setAlimento(receita);
        inteira.setContaId(receita.getContaId());
        receita.getMedidas().add(inteira);

        if (receita.getPorcoes() != null && receita.getPorcoes() > 0) {
            BigDecimal porPorcao = rendimento.divide(
                    BigDecimal.valueOf(receita.getPorcoes()), 3, RoundingMode.HALF_UP);
            var porcao = new MedidaCaseira(PORCAO, porPorcao.stripTrailingZeros());
            porcao.setAlimento(receita);
            porcao.setContaId(receita.getContaId());
            porcao.setPadrao(true);
            receita.getMedidas().add(porcao);
        } else {
            inteira.setPadrao(true);
        }
    }

    private static final String RECEITA_INTEIRA = "receita inteira";
    private static final String PORCAO = "porção";
    private static final List<String> DERIVADAS = List.of(RECEITA_INTEIRA, PORCAO);

    private ReceitaDtos.ReceitaResponse montarResposta(Alimento receita) {
        var calculo = calculadora.calcular(receita.getIngredientes(), receita.getRendimentoGramas());

        BigDecimal gramasPorPorcao = null;
        ComposicaoDto composicaoDaPorcao = null;
        if (receita.getPorcoes() != null && receita.getPorcoes() > 0
                && calculo.rendimentoUsado().signum() > 0) {
            gramasPorPorcao = calculo.rendimentoUsado()
                    .divide(BigDecimal.valueOf(receita.getPorcoes()), 3, RoundingMode.HALF_UP);
            composicaoDaPorcao = ComposicaoDto.de(receita.composicaoPara(gramasPorPorcao));
        }

        List<ReceitaDtos.IngredienteResponse> ingredientes = new ArrayList<>();
        for (IngredienteReceita i : receita.getIngredientes()) {
            ingredientes.add(new ReceitaDtos.IngredienteResponse(
                    i.getId(),
                    i.getAlimento().getId(),
                    i.getAlimento().getDescricao(),
                    i.getAlimento().getFonte().getDescricao(),
                    i.getMedidaId(),
                    i.quantidadeFormatada(),
                    i.getGramas()));
        }

        return new ReceitaDtos.ReceitaResponse(
                receita.getId(),
                receita.getDescricao(),
                receita.getGrupo(),
                receita.getModoPreparo(),
                calculo.rendimentoUsado(),
                calculo.rendimentoEstimado(),
                calculo.pesoDosIngredientes(),
                receita.getPorcoes(),
                gramasPorPorcao,
                ComposicaoDto.de(receita.getComposicao()),
                composicaoDaPorcao,
                calculo.nutrientesIncompletos(),
                ingredientes);
    }

    private ReceitaDtos.ReceitaResumo resumir(Alimento receita) {
        return new ReceitaDtos.ReceitaResumo(
                receita.getId(),
                receita.getDescricao(),
                receita.getGrupo(),
                receita.getIngredientes().size(),
                receita.getRendimentoGramas(),
                receita.getPorcoes(),
                receita.getComposicao().getEnergiaKcal());
    }

    private String normalizar(String termo) {
        if (termo == null || termo.isBlank()) {
            return null;
        }
        return Alimento.normalizarParaBusca(termo.trim());
    }
}
