package br.com.nutriplan.prescricao.domain;

/**
 * Ciclo de vida do plano alimentar.
 *
 * O paciente so enxerga plano publicado. Rascunho e trabalho em andamento do
 * profissional, e mostra-lo pela metade seria pior do que nao mostrar nada.
 */
public enum StatusPlano {

    /** Em elaboracao. Invisivel para o paciente. */
    RASCUNHO("Rascunho", false),

    /** Publicado e vigente. Visivel pelo link do paciente. */
    ATIVO("Ativo", true),

    /**
     * Substituido ou vencido. Continua visivel para consulta, marcado como
     * encerrado — o paciente precisa saber que aquele plano nao vale mais,
     * e o historico nao pode sumir do prontuario.
     */
    ENCERRADO("Encerrado", true);

    private final String descricao;
    private final boolean visivelAoPaciente;

    StatusPlano(String descricao, boolean visivelAoPaciente) {
        this.descricao = descricao;
        this.visivelAoPaciente = visivelAoPaciente;
    }

    public String getDescricao() {
        return descricao;
    }

    public boolean ehVisivelAoPaciente() {
        return visivelAoPaciente;
    }

    public boolean permiteEdicao() {
        return this != ENCERRADO;
    }
}
