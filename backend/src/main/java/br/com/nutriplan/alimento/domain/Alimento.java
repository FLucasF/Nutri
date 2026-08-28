package br.com.nutriplan.alimento.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Alimento com composicao nutricional conhecida.
 *
 * Diferente das demais entidades do sistema, o alimento nao estende
 * EntidadeDeConta: os itens das bases publicas (TACO, TBCA) sao compartilhados
 * por todos os consultorios e tem contaId nulo. Alimentos cadastrados por um
 * nutricionista carregam a conta dele e so aparecem para ela — a consulta
 * combina os dois casos com "conta_id is null or conta_id = :conta".
 */
@Entity
@Table(name = "alimento", indexes = {
        @Index(name = "ix_alimento_conta", columnList = "conta_id"),
        @Index(name = "ix_alimento_descricao", columnList = "descricao"),
        @Index(name = "ix_alimento_fonte_codigo", columnList = "fonte, codigo_fonte"),
        @Index(name = "ix_alimento_codigo_barras", columnList = "codigo_barras")
})
@Getter
@Setter
@NoArgsConstructor
public class Alimento extends BaseEntity {

    /** Nulo para alimentos de base publica; preenchido para cadastro proprio. */
    @Column(name = "conta_id")
    private Long contaId;

    @Column(nullable = false, length = 250)
    private String descricao;

    /**
     * Versao da descricao sem acentos e em minusculas, gravada na escrita.
     * Permite buscar "acucar" e achar "Acucar" sem depender de collation do
     * banco, que difere entre H2 e PostgreSQL.
     */
    @Column(name = "descricao_busca", nullable = false, length = 250)
    private String descricaoBusca;

    @Column(length = 100)
    private String grupo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FonteDeDados fonte;

    /** Codigo do alimento na tabela de origem, quando existir. */
    @Column(name = "codigo_fonte", length = 30)
    private String codigoFonte;

    @Column(length = 100)
    private String marca;

    /** EAN/GTIN do produto industrializado. Nulo para alimentos in natura. */
    @Column(name = "codigo_barras", length = 20)
    private String codigoBarras;

    @Embedded
    private ComposicaoNutricional composicao = new ComposicaoNutricional();

    @OneToMany(mappedBy = "alimento", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("descricao asc")
    private List<MedidaCaseira> medidas = new ArrayList<>();

    // ------------------------------------------------------- campos de receita
    // Esparsos: nulos nos 23.945 registros das tabelas de referencia. Existem
    // aqui, e nao numa tabela a parte, porque uma receita e um alimento — e
    // tratar as duas coisas como uma so e o que faz a receita entrar na busca,
    // aceitar medida caseira e ser prescrita sem codigo novo.

    @Column(name = "modo_preparo", length = 4000)
    private String modoPreparo;

    /**
     * Peso da preparacao pronta. Nao e a soma dos ingredientes: cozinhar perde
     * ou ganha agua. Nulo significa nao informado — e a soma dos ingredientes
     * serve de estimativa, o que a interface diz em vez de esconder.
     */
    @Column(name = "rendimento_gramas", precision = 12, scale = 3)
    private BigDecimal rendimentoGramas;

    /** Em quantas porcoes a preparacao rende, quando o nutricionista informa. */
    @Column(name = "porcoes")
    private Integer porcoes;

    @OneToMany(mappedBy = "receita", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordem asc")
    @BatchSize(size = 50)
    private List<IngredienteReceita> ingredientes = new ArrayList<>();

    public boolean ehReceita() {
        return fonte == FonteDeDados.RECEITA;
    }

    @Column(nullable = false)
    private boolean ativo = true;

    public Alimento(String descricao, FonteDeDados fonte) {
        setDescricao(descricao);
        this.fonte = fonte;
    }

    /** Mantem descricaoBusca sempre sincronizada com descricao. */
    public void setDescricao(String descricao) {
        this.descricao = descricao;
        this.descricaoBusca = normalizarParaBusca(descricao);
    }

    public void adicionarMedida(MedidaCaseira medida) {
        medida.setAlimento(this);
        medidas.add(medida);
    }

    public boolean ehBasePublica() {
        return contaId == null;
    }

    /**
     * Composicao correspondente a uma quantidade em gramas.
     */
    public ComposicaoNutricional composicaoPara(BigDecimal gramas) {
        return composicao.paraGramas(gramas);
    }

    public static String normalizarParaBusca(String texto) {
        if (texto == null) {
            return "";
        }
        String semAcento = java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return semAcento.toLowerCase().trim();
    }
}
