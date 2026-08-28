package br.com.nutriplan.agenda.domain;

import br.com.nutriplan.shared.domain.EntidadeDeConta;
import br.com.nutriplan.shared.error.RegraDeNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Um atendimento na agenda do consultório.
 *
 * O intervalo é guardado como início mais duração, e não como início e fim.
 * Duração é o que o profissional informa ao marcar, e derivar o fim elimina o
 * estado inconsistente de um fim anterior ao início — que nenhuma validação
 * precisaria vigiar se ele simplesmente não puder existir.
 */
@Entity
@Table(name = "agendamento", indexes = {
        @Index(name = "ix_agendamento_conta", columnList = "conta_id, inicio"),
        @Index(name = "ix_agendamento_paciente", columnList = "paciente_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Agendamento extends EntidadeDeConta {

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @Column(nullable = false)
    private LocalDateTime inicio;

    @Column(name = "duracao_minutos", nullable = false)
    private Integer duracaoMinutos;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoAtendimento tipo = TipoAtendimento.RETORNO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SituacaoAtendimento situacao = SituacaoAtendimento.AGENDADO;

    @Column(length = 1000)
    private String observacao;

    /** Motivo do cancelamento ou da falta, quando registrado. */
    @Column(name = "motivo_desfecho", length = 500)
    private String motivoDesfecho;

    public Agendamento(Long contaId, Long pacienteId, LocalDateTime inicio, int duracaoMinutos) {
        setContaId(contaId);
        this.pacienteId = pacienteId;
        this.inicio = inicio;
        this.duracaoMinutos = duracaoMinutos;
    }

    public LocalDateTime getFim() {
        return inicio.plusMinutes(duracaoMinutos);
    }

    /**
     * Aplica a mudança de situação, recusando transição inválida.
     *
     * A regra vive na entidade e não no serviço porque é invariante do próprio
     * atendimento: qualquer caminho que altere a situação precisa respeitá-la.
     */
    public void mudarSituacaoPara(SituacaoAtendimento destino, String motivo) {
        if (destino == situacao) {
            return;
        }
        if (!situacao.podeIrPara(destino)) {
            throw new RegraDeNegocioException(
                    "Um atendimento %s não pode passar para %s.".formatted(
                            situacao.getDescricao().toLowerCase(),
                            destino.getDescricao().toLowerCase()));
        }
        this.situacao = destino;
        if (motivo != null && !motivo.isBlank()) {
            this.motivoDesfecho = motivo;
        }
    }

    /**
     * Indica sobreposição com outro intervalo.
     *
     * A comparação é estritamente menor de propósito: o fim de um atendimento
     * coincidir com o início de outro é agenda cheia, não conflito.
     */
    public boolean sobrepoe(LocalDateTime outroInicio, LocalDateTime outroFim) {
        return inicio.isBefore(outroFim) && getFim().isAfter(outroInicio);
    }

    public boolean ocupaHorario() {
        return situacao.ocupaHorario();
    }
}
