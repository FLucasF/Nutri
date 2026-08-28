package br.com.nutriplan.exame.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Um resultado de exame de um paciente.
 *
 * Guarda a faixa de referencia que usou, e nao so o valor. E a mesma decisao do
 * protocolo gravado na avaliacao antropometrica e do peso gravado no item de
 * refeicao: o resultado e um registro do que se sabia naquela data, e corrigir
 * o cadastro depois nao pode reescrever o passado.
 */
@Entity
@Table(name = "exame", indexes = {
        @Index(name = "ix_exame_conta", columnList = "conta_id"),
        @Index(name = "ix_exame_paciente", columnList = "paciente_id, data_coleta")
})
@Getter
@Setter
@NoArgsConstructor
public class Exame extends BaseEntity {

    @Column(name = "conta_id", nullable = false)
    private Long contaId;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parametro_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_exame_parametro"))
    private ParametroExame parametro;

    @Column(name = "data_coleta", nullable = false)
    private LocalDate dataColeta;

    /**
     * Nulo significa parametro pedido e ainda nao determinado. Nunca zero:
     * zero e um resultado, ausencia e outra coisa.
     */
    @Column(precision = 12, scale = 3)
    private BigDecimal valor;

    @Column(nullable = false, length = 20)
    private String unidade;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ClassificacaoDoExame classificacao;

    @Column(name = "referencia_min", precision = 12, scale = 3)
    private BigDecimal referenciaMin;

    @Column(name = "referencia_max", precision = 12, scale = 3)
    private BigDecimal referenciaMax;

    @Column(length = 1000)
    private String observacao;

    @Column(name = "laudo_nome", length = 200)
    private String laudoNome;

    @Column(name = "laudo_tipo", length = 100)
    private String laudoTipo;

    // O binario fica em laudo_exame, que nenhuma listagem toca. Aqui ficam so
    // nome e tipo, para que "tem laudo?" nao custe uma consulta.

    public Exame(Long contaId, Long pacienteId, ParametroExame parametro, LocalDate dataColeta) {
        this.contaId = contaId;
        this.pacienteId = pacienteId;
        this.parametro = parametro;
        this.dataColeta = dataColeta;
        this.unidade = parametro.getUnidadePadrao();
    }

    public boolean temLaudo() {
        return laudoNome != null;
    }

    /** Texto pronto da faixa usada, para exibir junto do resultado. */
    public String referenciaComoTexto() {
        if (referenciaMin != null && referenciaMax != null) {
            return "%s a %s".formatted(limpo(referenciaMin), limpo(referenciaMax));
        }
        if (referenciaMax != null) {
            return "até %s".formatted(limpo(referenciaMax));
        }
        if (referenciaMin != null) {
            return "a partir de %s".formatted(limpo(referenciaMin));
        }
        return null;
    }

    private String limpo(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString().replace(".", ",");
    }
}
