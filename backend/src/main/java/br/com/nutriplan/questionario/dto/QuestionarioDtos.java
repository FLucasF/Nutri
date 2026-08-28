package br.com.nutriplan.questionario.dto;

import br.com.nutriplan.questionario.domain.ItemDeResposta;
import br.com.nutriplan.questionario.domain.Opcao;
import br.com.nutriplan.questionario.domain.Pergunta;
import br.com.nutriplan.questionario.domain.Questionario;
import br.com.nutriplan.questionario.domain.RespostaDeQuestionario;
import br.com.nutriplan.questionario.domain.TipoDePergunta;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class QuestionarioDtos {

    private QuestionarioDtos() {
    }

    // ------------------------------------------------------------------ entrada

    public record PerguntaRequest(
            @NotBlank @Size(max = 500) String enunciado,
            @NotNull TipoDePergunta tipo,
            boolean obrigatoria,
            /** "Nunca=0|Às vezes=1|Sempre=2". A pontuação é opcional. */
            @Size(max = 2000) String opcoes,
            @Size(max = 500) String ajuda
    ) {}

    public record QuestionarioRequest(
            @NotBlank @Size(max = 150) String nome,
            @Size(max = 1000) String descricao,
            @Size(max = 120) String instrumento,
            @Size(max = 30) String versao,
            boolean pontuavel,
            /** "0-5=Baixo|6-10=Moderado|11-99=Alto". */
            @Size(max = 500) String faixaDeCorte,
            @NotEmpty(message = "Um questionário precisa de ao menos uma pergunta")
            @Valid List<PerguntaRequest> perguntas
    ) {}

    public record EnvioRequest(
            @NotNull Long questionarioId,
            /** Vincula ao atendimento, quando o envio for para uma consulta. */
            Long agendamentoId
    ) {}

    /** Uma resposta do paciente: a chave é o id da pergunta. */
    public record ItemRespondido(@NotNull Long perguntaId, String valor) {}

    public record PreenchimentoRequest(
            @NotNull @Valid List<ItemRespondido> respostas
    ) {}

    // ------------------------------------------------------------------- saída

    public record OpcaoResponse(String rotulo, Integer pontos) {
        static OpcaoResponse de(Opcao o) {
            return new OpcaoResponse(o.rotulo(), o.pontos());
        }
    }

    public record PerguntaResponse(
            Long id,
            String enunciado,
            TipoDePergunta tipo,
            String tipoDescricao,
            boolean obrigatoria,
            Integer ordem,
            String ajuda,
            List<OpcaoResponse> opcoes
    ) {
        public static PerguntaResponse de(Pergunta p) {
            return new PerguntaResponse(p.getId(), p.getEnunciado(), p.getTipo(),
                    p.getTipo().getDescricao(), p.isObrigatoria(), p.getOrdem(), p.getAjuda(),
                    p.opcoesAnalisadas().stream().map(OpcaoResponse::de).toList());
        }
    }

    public record QuestionarioResponse(
            Long id,
            String nome,
            String descricao,
            String instrumento,
            String versao,
            boolean pontuavel,
            String faixaDeCorte,
            int versaoModelo,
            boolean modeloDoSistema,
            boolean editavel,
            List<PerguntaResponse> perguntas
    ) {
        public static QuestionarioResponse de(Questionario q) {
            return new QuestionarioResponse(q.getId(), q.getNome(), q.getDescricao(),
                    q.getInstrumento(), q.getVersao(), q.isPontuavel(), q.getFaixaDeCorte(),
                    q.getVersaoModelo(), q.ehModeloDoSistema(), !q.ehModeloDoSistema(),
                    q.getPerguntas().stream().map(PerguntaResponse::de).toList());
        }
    }

    public record ItemResponse(String pergunta, String valor, Integer pontos) {
        static ItemResponse de(ItemDeResposta i) {
            return new ItemResponse(i.getPerguntaTexto(), i.getValor(), i.getPontos());
        }
    }

    /**
     * Um envio e o que dele voltou.
     *
     * `versaoModelo` diz qual edição do formulário o paciente viu — o modelo
     * pode ter mudado depois, e as perguntas exibidas aqui vêm da resposta, e
     * não do questionário de hoje.
     */
    public record RespostaResponse(
            Long id,
            Long questionarioId,
            String questionario,
            int versaoModelo,
            String identificadorPublico,
            Instant enviadoEm,
            Instant respondidoEm,
            boolean pendente,
            Integer escore,
            String classificacao,
            List<ItemResponse> itens
    ) {
        public static RespostaResponse de(RespostaDeQuestionario r) {
            return new RespostaResponse(r.getId(), r.getQuestionario().getId(),
                    r.getQuestionario().getNome(), r.getVersaoModelo(),
                    r.getIdentificadorPublico(), r.getEnviadoEm(), r.getRespondidoEm(),
                    r.pendente(), r.getEscore(), r.getClassificacao(),
                    r.getItens().stream().map(ItemResponse::de).toList());
        }
    }

    /**
     * O formulário como o paciente o vê, sem autenticação.
     *
     * Não traz nome de paciente nem identificador de conta: a proteção está na
     * forma do contrato, e não em lembrar de filtrar a cada alteração — o mesmo
     * desenho do plano público.
     */
    public record FormularioPublicoResponse(
            String consultorio,
            String titulo,
            String descricao,
            boolean jaRespondido,
            List<PerguntaResponse> perguntas
    ) {}
}
