package br.com.nutriplan.questionario.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Um envio de questionário a um paciente, e o que ele respondeu.
 *
 * O endereço é um UUID, como o do plano público: um id sequencial permitiria
 * abrir o formulário do vizinho somando 1 ao link.
 */
@Entity
@Table(name = "resposta_questionario", indexes = {
        @Index(name = "ix_resposta_paciente", columnList = "paciente_id"),
        @Index(name = "ix_resposta_publica", columnList = "identificador_publico")
})
@Getter
@Setter
@NoArgsConstructor
public class RespostaDeQuestionario extends BaseEntity {

    @Column(name = "conta_id", nullable = false)
    private Long contaId;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @Column(name = "agendamento_id")
    private Long agendamentoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "questionario_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_resposta_questionario"))
    private Questionario questionario;

    /** A versão do modelo que o paciente viu. */
    @Column(name = "versao_modelo", nullable = false)
    private int versaoModelo;

    @Column(name = "identificador_publico", nullable = false, length = 36)
    private String identificadorPublico = UUID.randomUUID().toString();

    @Column(name = "enviado_em", nullable = false)
    private Instant enviadoEm = Instant.now();

    /** Nulo enquanto pendente. Preenchido, o link deixa de aceitar resposta. */
    @Column(name = "respondido_em")
    private Instant respondidoEm;

    @Column
    private Integer escore;

    @Column(length = 120)
    private String classificacao;

    /** A regra de corte usada, congelada aqui. */
    @Column(name = "faixa_de_corte", length = 500)
    private String faixaDeCorte;

    @OneToMany(mappedBy = "resposta", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordem asc")
    @BatchSize(size = 50)
    private List<ItemDeResposta> itens = new ArrayList<>();

    public RespostaDeQuestionario(Long contaId, Long pacienteId, Questionario questionario) {
        this.contaId = contaId;
        this.pacienteId = pacienteId;
        this.questionario = questionario;
        this.versaoModelo = questionario.getVersaoModelo();
        this.faixaDeCorte = questionario.getFaixaDeCorte();
    }

    public boolean pendente() {
        return respondidoEm == null;
    }
}
