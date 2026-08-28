package br.com.nutriplan.exame.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** O pedido de exames que o nutricionista entrega ao paciente. */
@Entity
@Table(name = "solicitacao_exame",
        indexes = @Index(name = "ix_solicitacao_paciente", columnList = "paciente_id, data"))
@Getter
@Setter
@NoArgsConstructor
public class SolicitacaoDeExame extends BaseEntity {

    @Column(name = "conta_id", nullable = false)
    private Long contaId;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @Column(nullable = false)
    private LocalDate data;

    @Column(length = 1000)
    private String observacao;

    @OneToMany(mappedBy = "solicitacao", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<ParametroSolicitado> parametros = new ArrayList<>();

    public SolicitacaoDeExame(Long contaId, Long pacienteId, LocalDate data) {
        this.contaId = contaId;
        this.pacienteId = pacienteId;
        this.data = data;
    }
}
