package br.com.nutriplan.auth.domain;

/**
 * Subscription plans. The limits are applied by PatientService.checkPlanLimit;
 * a value of -1 means unlimited.
 */
public enum Plan {
    EXPERIMENTAL(5, 1, 30),
    UNDERGRADUATE(20, 1, -1),
    PREMIUM(-1, 1, -1),
    BLACK(-1, -1, -1);

    private final int maxPatients;
    private final int maxAgendas;
    private final int testeDays;

    Plan(int maxPatients, int maxAgendas, int testeDays) {
        this.maxPatients = maxPatients;
        this.maxAgendas = maxAgendas;
        this.testeDays = testeDays;
    }

    public int maxPatients() { return maxPatients; }
    public int maxAgendas()   { return maxAgendas; }
    public int testeDays()  { return testeDays; }

    public boolean unlimitedPatients() { return maxPatients < 0; }
    public boolean allowsIa()           { return this == BLACK; }
}
