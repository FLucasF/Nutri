package br.com.nutriplan.agenda.dto;

import br.com.nutriplan.agenda.domain.SituacaoAtendimento;
import br.com.nutriplan.agenda.domain.TipoAtendimento;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** Contratos de entrada e saída do módulo de agenda. */
public final class AgendaDtos {

    private AgendaDtos() {
    }

    public record AgendamentoRequest(
            @NotNull Long pacienteId,
            @NotNull LocalDateTime inicio,
            @NotNull @Min(value = 5, message = "A duração mínima é de 5 minutos")
            @Max(value = 600, message = "A duração máxima é de 10 horas")
            Integer duracaoMinutos,
            @NotNull TipoAtendimento tipo,
            @Size(max = 1000) String observacao
    ) {}

    public record MudancaDeSituacaoRequest(
            @NotNull SituacaoAtendimento situacao,
            @Size(max = 500) String motivo
    ) {}

    public record AgendamentoResponse(
            Long id,
            Long pacienteId,
            String pacienteNome,
            LocalDateTime inicio,
            LocalDateTime fim,
            Integer duracaoMinutos,
            TipoAtendimento tipo,
            String tipoDescricao,
            SituacaoAtendimento situacao,
            String situacaoDescricao,
            List<SituacaoAtendimento> transicoesPermitidas,
            String observacao,
            String motivoDesfecho
    ) {}

    /** Um dia da agenda, com o que o profissional precisa ver de relance. */
    public record DiaDaAgendaResponse(
            java.time.LocalDate data,
            int totalDeAtendimentos,
            int realizados,
            int faltas,
            List<AgendamentoResponse> atendimentos
    ) {}

    /** Catálogo de tipos, com a duração que cada um sugere. */
    public record TipoResponse(TipoAtendimento tipo, String descricao, int duracaoSugeridaMinutos) {}
}
