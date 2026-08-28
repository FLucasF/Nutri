package br.com.nutriplan.antropometria.domain;

import br.com.nutriplan.shared.domain.EntidadeDeConta;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

/**
 * Uma avaliação antropométrica do paciente numa data.
 *
 * A entidade guarda as **medidas brutas** e também os **resultados derivados**
 * (percentual de gordura, gasto energético), junto do protocolo e da equação
 * que os produziram.
 *
 * Guardar o derivado é deliberado. Recalcular na leitura faria uma avaliação de
 * dois anos atrás mudar de valor porque a implementação evoluiu — e o histórico
 * do paciente deixaria de ser comparável consigo mesmo. As medidas brutas ficam
 * ao lado justamente para permitir reconferir a conta.
 */
@Entity
@Table(name = "avaliacao_antropometrica", indexes = {
        @Index(name = "ix_avaliacao_conta", columnList = "conta_id"),
        @Index(name = "ix_avaliacao_paciente", columnList = "paciente_id, data")
})
@Getter
@Setter
@NoArgsConstructor
public class AvaliacaoAntropometrica extends EntidadeDeConta {

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @Column(nullable = false)
    private LocalDate data;

    @Column(name = "peso_kg", precision = 6, scale = 2)
    private BigDecimal pesoKg;

    @Column(name = "altura_cm", precision = 6, scale = 2)
    private BigDecimal alturaCm;

    // --- dobras cutâneas, em milímetros -----------------------------------
    @Column(name = "dobra_tricipital",   precision = 6, scale = 2) private BigDecimal dobraTricipital;
    @Column(name = "dobra_bicipital",    precision = 6, scale = 2) private BigDecimal dobraBicipital;
    @Column(name = "dobra_subescapular", precision = 6, scale = 2) private BigDecimal dobraSubescapular;
    @Column(name = "dobra_suprailiaca",  precision = 6, scale = 2) private BigDecimal dobraSuprailiaca;
    @Column(name = "dobra_abdominal",    precision = 6, scale = 2) private BigDecimal dobraAbdominal;
    @Column(name = "dobra_peitoral",     precision = 6, scale = 2) private BigDecimal dobraPeitoral;
    @Column(name = "dobra_coxa",         precision = 6, scale = 2) private BigDecimal dobraCoxa;
    @Column(name = "dobra_panturrilha",  precision = 6, scale = 2) private BigDecimal dobraPanturrilha;
    @Column(name = "dobra_axilar_media", precision = 6, scale = 2) private BigDecimal dobraAxilarMedia;

    // --- circunferências, em centímetros -----------------------------------
    @Column(name = "circ_cintura",     precision = 6, scale = 2) private BigDecimal circCintura;
    @Column(name = "circ_quadril",     precision = 6, scale = 2) private BigDecimal circQuadril;
    @Column(name = "circ_abdomen",     precision = 6, scale = 2) private BigDecimal circAbdomen;
    @Column(name = "circ_braco",       precision = 6, scale = 2) private BigDecimal circBraco;
    @Column(name = "circ_antebraco",   precision = 6, scale = 2) private BigDecimal circAntebraco;
    @Column(name = "circ_coxa",        precision = 6, scale = 2) private BigDecimal circCoxa;
    @Column(name = "circ_panturrilha", precision = 6, scale = 2) private BigDecimal circPanturrilha;
    @Column(name = "circ_torax",       precision = 6, scale = 2) private BigDecimal circTorax;

    // --- composição corporal, derivada e gravada ---------------------------
    @Enumerated(EnumType.STRING)
    @Column(name = "protocolo_composicao", length = 30)
    private ProtocoloComposicao protocoloComposicao;

    @Column(name = "percentual_gordura", precision = 5, scale = 2)
    private BigDecimal percentualGordura;

    @Column(name = "massa_gorda_kg", precision = 6, scale = 2)
    private BigDecimal massaGordaKg;

    @Column(name = "massa_magra_kg", precision = 6, scale = 2)
    private BigDecimal massaMagraKg;

    // --- gasto energético, derivado e gravado ------------------------------
    @Enumerated(EnumType.STRING)
    @Column(name = "equacao_gasto", length = 30)
    private EquacaoGastoEnergetico equacaoGasto;

    @Column(name = "fator_atividade", precision = 4, scale = 2)
    private BigDecimal fatorAtividade;

    @Column(name = "gasto_basal_kcal", precision = 8, scale = 2)
    private BigDecimal gastoBasalKcal;

    @Column(name = "gasto_total_kcal", precision = 8, scale = 2)
    private BigDecimal gastoTotalKcal;

    @Column(length = 2000)
    private String observacoes;

    // ---------------------------------------------------------------- gestação
    // Ficam na própria avaliação: são as mesmas medidas, lidas contra outra
    // referência. Uma tabela separada duplicaria peso e data.

    @Column(name = "semana_gestacional")
    private Integer semanaGestacional;

    /**
     * Peso anterior à gestação. É o que define a faixa de ganho esperado — o
     * IMC de hoje já embute o ganho que se quer avaliar.
     */
    @Column(name = "peso_pre_gestacional_kg", precision = 6, scale = 2)
    private BigDecimal pesoPreGestacionalKg;

    public boolean ehGestacional() {
        return semanaGestacional != null;
    }

    public AvaliacaoAntropometrica(Long contaId, Long pacienteId, LocalDate data) {
        setContaId(contaId);
        this.pacienteId = pacienteId;
        this.data = data;
    }

    /**
     * Índice de massa corporal — peso dividido pelo quadrado da altura em metros.
     * Nulo se faltar peso ou altura: não há como estimar um a partir do outro.
     */
    public BigDecimal getImc() {
        if (pesoKg == null || alturaCm == null || alturaCm.signum() <= 0) {
            return null;
        }
        BigDecimal alturaMetros = alturaCm.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return pesoKg.divide(alturaMetros.multiply(alturaMetros), 2, RoundingMode.HALF_UP);
    }

    /**
     * Relação cintura-quadril. Exige as duas medidas — uma sozinha não permite
     * inferir a distribuição de gordura.
     */
    public BigDecimal getRelacaoCinturaQuadril() {
        if (circCintura == null || circQuadril == null || circQuadril.signum() <= 0) {
            return null;
        }
        return circCintura.divide(circQuadril, 2, RoundingMode.HALF_UP);
    }

    /** Dobras medidas, indexadas para uso pelos protocolos. */
    public Map<Dobra, Double> dobrasMedidas() {
        Map<Dobra, Double> mapa = new EnumMap<>(Dobra.class);
        adicionar(mapa, Dobra.TRICIPITAL, dobraTricipital);
        adicionar(mapa, Dobra.BICIPITAL, dobraBicipital);
        adicionar(mapa, Dobra.SUBESCAPULAR, dobraSubescapular);
        adicionar(mapa, Dobra.SUPRAILIACA, dobraSuprailiaca);
        adicionar(mapa, Dobra.ABDOMINAL, dobraAbdominal);
        adicionar(mapa, Dobra.PEITORAL, dobraPeitoral);
        adicionar(mapa, Dobra.COXA, dobraCoxa);
        adicionar(mapa, Dobra.PANTURRILHA, dobraPanturrilha);
        adicionar(mapa, Dobra.AXILAR_MEDIA, dobraAxilarMedia);
        return mapa;
    }

    public void definirDobra(Dobra dobra, BigDecimal valor) {
        switch (dobra) {
            case TRICIPITAL -> dobraTricipital = valor;
            case BICIPITAL -> dobraBicipital = valor;
            case SUBESCAPULAR -> dobraSubescapular = valor;
            case SUPRAILIACA -> dobraSuprailiaca = valor;
            case ABDOMINAL -> dobraAbdominal = valor;
            case PEITORAL -> dobraPeitoral = valor;
            case COXA -> dobraCoxa = valor;
            case PANTURRILHA -> dobraPanturrilha = valor;
            case AXILAR_MEDIA -> dobraAxilarMedia = valor;
        }
    }

    /** Limpa a composição estimada — usado quando a avaliação é reeditada. */
    public void limparComposicao() {
        protocoloComposicao = null;
        percentualGordura = null;
        massaGordaKg = null;
        massaMagraKg = null;
    }

    public void limparGastoEnergetico() {
        equacaoGasto = null;
        fatorAtividade = null;
        gastoBasalKcal = null;
        gastoTotalKcal = null;
    }

    public boolean temComposicaoEstimada() {
        return protocoloComposicao != null && percentualGordura != null;
    }

    private static void adicionar(Map<Dobra, Double> mapa, Dobra dobra, BigDecimal valor) {
        if (valor != null && valor.signum() > 0) {
            mapa.put(dobra, valor.doubleValue());
        }
    }
}
