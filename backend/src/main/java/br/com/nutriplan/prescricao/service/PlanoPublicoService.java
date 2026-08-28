package br.com.nutriplan.prescricao.service;

import br.com.nutriplan.alimento.domain.Alimento;
import br.com.nutriplan.alimento.repository.AlimentoRepository;
import br.com.nutriplan.auth.domain.Conta;
import br.com.nutriplan.auth.domain.Perfil;
import br.com.nutriplan.auth.domain.Usuario;
import br.com.nutriplan.auth.repository.ContaRepository;
import br.com.nutriplan.auth.repository.UsuarioRepository;
import br.com.nutriplan.orientacao.repository.ImagemDoPlanoRepository;
import br.com.nutriplan.orientacao.repository.OrientacaoDoPlanoRepository;
import br.com.nutriplan.paciente.repository.PacienteRepository;
import br.com.nutriplan.prescricao.domain.ItemRefeicao;
import br.com.nutriplan.prescricao.domain.PlanoAlimentar;
import br.com.nutriplan.prescricao.domain.StatusPlano;
import br.com.nutriplan.prescricao.dto.PrescricaoDtos;
import br.com.nutriplan.prescricao.repository.PlanoAlimentarRepository;
import br.com.nutriplan.shared.error.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Entrega o plano pelo link do paciente.
 *
 * Este servico atende requisicao **sem autenticacao**, e por isso e o unico do
 * sistema que nao passa pelo contexto de conta. Duas consequencias de projeto:
 *
 *  - o acesso e autorizado pela posse do identificador, que e um UUID — o que
 *    exige que ele nunca apareca em lugar previsivel nem seja derivavel do id;
 *  - a resposta e montada por um DTO proprio, que simplesmente nao tem campo
 *    para observacao interna nem para identificador de conta. A protecao esta
 *    na forma do contrato, e nao em lembrar de filtrar a cada alteracao.
 *
 * Rascunho nunca e servido: o paciente veria um plano pela metade e o tomaria
 * por prescricao.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlanoPublicoService {

    private final PlanoAlimentarRepository planoRepository;
    private final AlimentoRepository alimentoRepository;
    private final PacienteRepository pacienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final ContaRepository contaRepository;
    private final OrientacaoDoPlanoRepository orientacaoDoPlanoRepository;
    private final ImagemDoPlanoRepository imagemDoPlanoRepository;
    private final CalculadoraNutricional calculadora;

    @Transactional(readOnly = true)
    public PrescricaoDtos.PlanoPublicoResponse porIdentificador(String identificador) {
        PlanoAlimentar plano = planoRepository.buscarPorIdentificadorPublico(identificador)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Plano não encontrado. Confira o link recebido."));

        if (!plano.ehVisivelPeloLink()) {
            // Rascunho responde como inexistente: revelar que o link "existe,
            // mas ainda nao" entregaria informacao sobre o trabalho em curso.
            throw new RecursoNaoEncontradoException(
                    "Plano não encontrado. Confira o link recebido.");
        }

        return montar(plano);
    }

    /**
     * Monta a visao do plano a partir da entidade ja carregada.
     *
     * Extraido para que a impressao em PDF use exatamente o mesmo contrato que
     * o paciente le no link. Se fossem dois montadores, o papel e a tela
     * divergiriam na primeira alteracao — e a divergencia apareceria com o
     * plano na mao do paciente.
     */
    @Transactional(readOnly = true)
    public PrescricaoDtos.PlanoPublicoResponse montar(PlanoAlimentar plano) {
        var alimentos = carregarAlimentos(plano);
        var total = calculadora.totalizarRefeicoes(plano.getRefeicoes(), alimentos);
        var composicao = total.composicao();

        String pacienteNome = plano.getPacienteId() == null ? null
                : pacienteRepository.findById(plano.getPacienteId())
                        .map(p -> primeiroNome(p.getNome())).orElse(null);

        Conta conta = contaRepository.findById(plano.getContaId()).orElse(null);
        Usuario profissional = usuarioRepository
                .findFirstByContaIdAndPerfilAndAtivoTrue(plano.getContaId(), Perfil.NUTRICIONISTA)
                .orElse(null);

        List<PrescricaoDtos.RefeicaoPublicaResponse> refeicoes = plano.getRefeicoes().stream()
                .map(refeicao -> new PrescricaoDtos.RefeicaoPublicaResponse(
                        refeicao.getNome(), refeicao.getHorario(), refeicao.getObservacao(),
                        refeicao.getItens().stream().map(this::montarItem).toList()))
                .toList();

        return new PrescricaoDtos.PlanoPublicoResponse(
                plano.getTitulo(),
                pacienteNome,
                profissional == null ? null : profissional.getNome(),
                profissional == null ? null : profissional.getCrn(),
                conta == null ? null : conta.getNome(),
                conta == null ? null : conta.getCorPrimaria(),
                conta == null ? null : conta.getLogoUrl(),
                plano.getMetodo(),
                plano.vigenteEm(LocalDate.now()),
                plano.getStatus() == StatusPlano.ENCERRADO,
                plano.getVigenciaInicio(),
                plano.getVigenciaFim(),
                plano.getOrientacoes(),
                orientacaoDoPlanoRepository.findByPlanoIdOrderByOrdemAsc(plano.getId()).stream()
                        .map(o -> new PrescricaoDtos.OrientacaoPublicaResponse(
                                o.getId(), o.getTitulo(), o.getCorpo(),
                                enderecoDaImagem(plano.getIdentificadorPublico(), o)))
                        .toList(),
                refeicoes,
                new PrescricaoDtos.ResumoPublicoResponse(
                        composicao.getEnergiaKcal(),
                        composicao.getProteinaG(),
                        composicao.getCarboidratoG(),
                        composicao.getLipideosG(),
                        refeicoes.size()));
    }

    /** Nome, tipo e conteudo da figura, prontos para a resposta HTTP. */
    public record ImagemPublica(String nome, String tipo, byte[] conteudo) {}

    private String enderecoDaImagem(String identificador,
                                    br.com.nutriplan.orientacao.domain.OrientacaoDoPlano o) {
        return o.temImagem()
                ? "/api/publico/planos/" + identificador + "/orientacoes/" + o.getId() + "/imagem"
                : null;
    }

    /**
     * A figura de uma orientacao entregue, servida pelo mesmo link do plano.
     *
     * Passa pela mesma porta do plano — identificador valido e plano visivel —
     * porque a figura e parte do que o paciente recebeu. Anexo de outro plano
     * responde como inexistente, e nao como proibido: quem tem o link nao
     * precisa saber que o outro plano existe.
     */
    @Transactional(readOnly = true)
    public ImagemPublica imagemDaOrientacao(String identificador, Long anexoId) {
        PlanoAlimentar plano = planoRepository.buscarPorIdentificadorPublico(identificador)
                .filter(PlanoAlimentar::ehVisivelPeloLink)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Plano não encontrado. Confira o link recebido."));

        var anexo = orientacaoDoPlanoRepository.findById(anexoId)
                .filter(a -> a.getPlanoId().equals(plano.getId()))
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Orientação deste plano", anexoId));

        var arquivo = imagemDoPlanoRepository.findById(anexoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Imagem da orientação", anexoId));

        return new ImagemPublica(anexo.getImagemNome(), anexo.getImagemTipo(),
                arquivo.getConteudo());
    }

    private PrescricaoDtos.ItemPublicoResponse montarItem(ItemRefeicao item) {
        return new PrescricaoDtos.ItemPublicoResponse(
                item.getDescricao(),
                item.porcaoFormatada(),
                item.getGramas(),
                item.getObservacao(),
                item.getEquivalentes().stream()
                        .map(e -> new PrescricaoDtos.EquivalentePublicoResponse(
                                e.getDescricao(), e.porcaoFormatada()))
                        .toList());
    }

    private Map<Long, Alimento> carregarAlimentos(PlanoAlimentar plano) {
        List<Long> ids = plano.getRefeicoes().stream()
                .flatMap(r -> r.getItens().stream())
                .map(ItemRefeicao::getAlimentoId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        // Sem contexto de conta aqui: o plano ja delimita quais alimentos
        // interessam, e sao os que ele proprio referencia.
        return alimentoRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Alimento::getId, Function.identity(), (a, b) -> a));
    }

    /**
     * O paciente e cumprimentado pelo primeiro nome. Nome completo numa pagina
     * acessivel por link e mais dado pessoal exposto do que o necessario.
     */
    private String primeiroNome(String nomeCompleto) {
        if (nomeCompleto == null || nomeCompleto.isBlank()) {
            return null;
        }
        return nomeCompleto.trim().split("\\s+")[0];
    }
}
