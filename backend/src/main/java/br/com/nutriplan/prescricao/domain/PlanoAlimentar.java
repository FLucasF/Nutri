package br.com.nutriplan.prescricao.domain;

import br.com.nutriplan.shared.domain.EntidadeDeConta;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Plano alimentar prescrito para um paciente.
 *
 * O plano e uma entidade unica, vista tanto pelo nutricionista quanto pelo
 * paciente — nao ha copia para cada audiencia. O que muda e o caminho de
 * acesso: o profissional entra autenticado, o paciente abre pelo
 * {@link #identificadorPublico}, um UUID que funciona como o endereco do plano.
 *
 * Usar UUID e nao o id sequencial e deliberado: um identificador previsivel
 * permitiria abrir o plano de outro paciente somando 1 ao numero do link.
 */
@Entity
@Table(name = "plano_alimentar", indexes = {
        @Index(name = "ix_plano_conta", columnList = "conta_id"),
        @Index(name = "ix_plano_paciente", columnList = "paciente_id"),
        @Index(name = "ix_plano_publico", columnList = "identificador_publico")
})
@Getter
@Setter
@NoArgsConstructor
public class PlanoAlimentar extends EntidadeDeConta {

    /** Nulo em plano-modelo, que existe para ser reaproveitado e nao pertence a ninguem. */
    @Column(name = "paciente_id")
    private Long pacienteId;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MetodoPrescricao metodo = MetodoPrescricao.ALIMENTOS;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusPlano status = StatusPlano.RASCUNHO;

    @Column(name = "identificador_publico", nullable = false, length = 36, unique = true)
    private String identificadorPublico = UUID.randomUUID().toString();

    @Column(name = "vigencia_inicio")
    private LocalDate vigenciaInicio;

    @Column(name = "vigencia_fim")
    private LocalDate vigenciaFim;

    /** Orientacoes gerais, exibidas ao paciente antes das refeicoes. */
    @Column(length = 4000)
    private String orientacoes;

    /** Anotacao interna do profissional. Nunca sai no link do paciente. */
    @Column(name = "observacoes_internas", length = 4000)
    private String observacoesInternas;

    /** Plano guardado como ponto de partida para outros, sem paciente vinculado. */
    @Column(nullable = false)
    private boolean modelo = false;

    /** Meta diaria de energia, para comparar com o que foi efetivamente prescrito. */
    @Column(name = "meta_energia_kcal", precision = 10, scale = 2)
    private java.math.BigDecimal metaEnergiaKcal;

    @OneToMany(mappedBy = "plano", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordem asc")
    private List<Refeicao> refeicoes = new ArrayList<>();

    public PlanoAlimentar(Long contaId, String titulo) {
        setContaId(contaId);
        this.titulo = titulo;
    }

    public void adicionarRefeicao(Refeicao refeicao) {
        refeicao.setPlano(this);
        if (refeicao.getOrdem() == null) {
            refeicao.setOrdem(proximaOrdem());
        }
        refeicoes.add(refeicao);
    }

    public void removerRefeicao(Refeicao refeicao) {
        refeicoes.remove(refeicao);
        refeicao.setPlano(null);
    }

    private int proximaOrdem() {
        return refeicoes.stream()
                .map(Refeicao::getOrdem)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(0) + 1;
    }

    /**
     * Renumera as refeicoes em sequencia continua. Chamado apos remocao para
     * que a ordem nao fique com buracos, que confundiriam o reordenamento.
     */
    public void renumerarRefeicoes() {
        List<Refeicao> ordenadas = new ArrayList<>(refeicoes);
        ordenadas.sort(Comparator.comparing(
                Refeicao::getOrdem, Comparator.nullsLast(Comparator.naturalOrder())));
        for (int i = 0; i < ordenadas.size(); i++) {
            ordenadas.get(i).setOrdem(i + 1);
        }
    }

    public boolean ehVisivelPeloLink() {
        return status.ehVisivelAoPaciente();
    }

    public boolean podeSerEditado() {
        return status.permiteEdicao();
    }

    /** Vigente na data de referencia, considerando um intervalo aberto nas pontas. */
    public boolean vigenteEm(LocalDate referencia) {
        if (status != StatusPlano.ATIVO) {
            return false;
        }
        boolean comecou = vigenciaInicio == null || !referencia.isBefore(vigenciaInicio);
        boolean naoTerminou = vigenciaFim == null || !referencia.isAfter(vigenciaFim);
        return comecou && naoTerminou;
    }

    /** Gera um endereco novo, invalidando o link entregue anteriormente. */
    public void regerarIdentificadorPublico() {
        this.identificadorPublico = UUID.randomUUID().toString();
    }

    public int totalDeItens() {
        return refeicoes.stream().mapToInt(r -> r.getItens().size()).sum();
    }
}
