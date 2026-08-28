package br.com.nutriplan.questionario.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

/**
 * Um formulário que o consultório aplica.
 *
 * Conta nula identifica modelo do sistema. Instrumentos publicados —
 * rastreamento metabólico, FINDRISC, escalas de sono — têm licença própria, e
 * por isso o sistema traz só um modelo genérico de autoria própria: cada
 * consultório cadastra os instrumentos que tem direito de usar.
 */
@Entity
@Table(name = "questionario",
        indexes = @Index(name = "ix_questionario_conta", columnList = "conta_id"))
@Getter
@Setter
@NoArgsConstructor
public class Questionario extends BaseEntity {

    @Column(name = "conta_id")
    private Long contaId;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(length = 1000)
    private String descricao;

    /** Nome do instrumento publicado, quando for um deles. */
    @Column(length = 120)
    private String instrumento;

    @Column(length = 30)
    private String versao;

    @Column(nullable = false)
    private boolean pontuavel = false;

    @Column(name = "faixa_de_corte", length = 500)
    private String faixaDeCorte;

    /**
     * Incrementa a cada edição.
     *
     * A resposta guarda o número que respondeu, e é isso que permite saber qual
     * formulário o paciente viu sem reconstruí-lo.
     */
    @Column(name = "versao_modelo", nullable = false)
    private int versaoModelo = 1;

    @Column(nullable = false)
    private boolean ativo = true;

    @OneToMany(mappedBy = "questionario", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordem asc")
    @BatchSize(size = 50)
    private List<Pergunta> perguntas = new ArrayList<>();

    public Questionario(Long contaId, String nome) {
        this.contaId = contaId;
        this.nome = nome;
    }

    public boolean ehModeloDoSistema() {
        return contaId == null;
    }
}
