package br.com.nutriplan.antropometria.domain;

/**
 * Dobras cutâneas medidas com adipômetro, em milímetros.
 *
 * O enum existe para que os protocolos declarem quais dobras exigem, e o
 * sistema consiga dizer ao profissional exatamente qual falta — em vez de
 * recusar a estimativa sem explicar.
 */
public enum Dobra {

    TRICIPITAL("Tricipital"),
    BICIPITAL("Bicipital"),
    SUBESCAPULAR("Subescapular"),
    SUPRAILIACA("Supra-ilíaca"),
    ABDOMINAL("Abdominal"),
    PEITORAL("Peitoral"),
    COXA("Coxa"),
    PANTURRILHA("Panturrilha medial"),
    AXILAR_MEDIA("Axilar média");

    private final String descricao;

    Dobra(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
