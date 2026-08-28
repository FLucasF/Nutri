package br.com.nutriplan.agenda.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Ciclo de vida de um atendimento agendado.
 *
 * As transições são explícitas porque o histórico do paciente depende delas: um
 * atendimento que volta de "realizado" para "agendado" apagaria o registro de
 * que a consulta aconteceu, e a falta deixaria de constar no acompanhamento.
 * Situações terminais não voltam atrás — corrigir um lançamento errado é
 * trabalho de quem opera, não uma transição de estado.
 */
public enum SituacaoAtendimento {

    AGENDADO("Agendado"),
    CONFIRMADO("Confirmado"),
    REALIZADO("Realizado"),
    FALTOU("Faltou"),
    CANCELADO("Cancelado");

    private final String descricao;

    SituacaoAtendimento(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /** Situações alcançáveis a partir desta. */
    public Set<SituacaoAtendimento> transicoesPermitidas() {
        return switch (this) {
            case AGENDADO -> EnumSet.of(CONFIRMADO, REALIZADO, FALTOU, CANCELADO);
            case CONFIRMADO -> EnumSet.of(REALIZADO, FALTOU, CANCELADO);
            // Terminais: o atendimento já teve desfecho.
            case REALIZADO, FALTOU, CANCELADO -> EnumSet.noneOf(SituacaoAtendimento.class);
        };
    }

    public boolean podeIrPara(SituacaoAtendimento destino) {
        return transicoesPermitidas().contains(destino);
    }

    /** Atendimento cancelado libera o horário; os demais o ocupam. */
    public boolean ocupaHorario() {
        return this != CANCELADO;
    }

    public boolean ehTerminal() {
        return transicoesPermitidas().isEmpty();
    }
}
