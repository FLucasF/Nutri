package br.com.nutriplan.auth.domain;

/**
 * Planos de assinatura. Os limites sao aplicados pelo LimitePlanoService;
 * valor -1 significa ilimitado.
 */
public enum Plano {
    EXPERIMENTAL(5, 1, 30),
    GRADUACAO(20, 1, -1),
    PREMIUM(-1, 1, -1),
    BLACK(-1, -1, -1);

    private final int maxPacientes;
    private final int maxAgendas;
    private final int diasDeTeste;

    Plano(int maxPacientes, int maxAgendas, int diasDeTeste) {
        this.maxPacientes = maxPacientes;
        this.maxAgendas = maxAgendas;
        this.diasDeTeste = diasDeTeste;
    }

    public int maxPacientes() { return maxPacientes; }
    public int maxAgendas()   { return maxAgendas; }
    public int diasDeTeste()  { return diasDeTeste; }

    public boolean pacientesIlimitados() { return maxPacientes < 0; }
    public boolean permiteIa()           { return this == BLACK; }
}
