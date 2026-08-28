package br.com.nutriplan.antropometria.domain;

import br.com.nutriplan.paciente.domain.Sexo;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Protocolos de estimativa de composição corporal a partir de dobras cutâneas.
 *
 * Cada protocolo declara quais dobras exige e se depende de sexo e idade. Isso
 * permite ao sistema recusar a estimativa nomeando o que falta, em vez de
 * completar a conta com dobra ausente — o que inventaria composição corporal.
 *
 * Os protocolos que estimam densidade corporal convertem para percentual de
 * gordura pela equação de Siri. Faulkner devolve o percentual diretamente.
 *
 * O protocolo aplicado fica gravado com o resultado: avaliações estimadas por
 * protocolos diferentes não são comparáveis entre si, porque cada um tem seu
 * próprio erro-padrão, e a diferença entre eles seria lida como mudança do
 * paciente.
 */
public enum ProtocoloComposicao {

    /**
     * Faulkner — quatro dobras, percentual direto, sem dependência de idade.
     * Simples e muito usado na prática clínica brasileira.
     */
    FAULKNER(
            "Faulkner",
            EnumSet.of(Dobra.TRICIPITAL, Dobra.SUBESCAPULAR, Dobra.SUPRAILIACA, Dobra.ABDOMINAL),
            false, false) {
        @Override
        public double percentualDeGordura(Map<Dobra, Double> dobras, Sexo sexo, Integer idade) {
            return somar(dobras) * 0.153 + 5.783;
        }
    },

    /**
     * Jackson e Pollock — três dobras, específicas por sexo, com correção por idade.
     */
    POLLOCK_3(
            "Pollock 3 dobras",
            EnumSet.noneOf(Dobra.class), // depende do sexo; ver dobrasExigidas
            true, true) {
        @Override
        public Set<Dobra> dobrasExigidas(Sexo sexo) {
            return sexo == Sexo.MASCULINO
                    ? EnumSet.of(Dobra.PEITORAL, Dobra.ABDOMINAL, Dobra.COXA)
                    : EnumSet.of(Dobra.TRICIPITAL, Dobra.SUPRAILIACA, Dobra.COXA);
        }

        @Override
        public double percentualDeGordura(Map<Dobra, Double> dobras, Sexo sexo, Integer idade) {
            double soma = somarApenas(dobras, dobrasExigidas(sexo));
            double densidade = sexo == Sexo.MASCULINO
                    ? 1.10938 - 0.0008267 * soma + 0.0000016 * soma * soma - 0.0002574 * idade
                    : 1.0994921 - 0.0009929 * soma + 0.0000023 * soma * soma - 0.0001392 * idade;
            return siri(densidade);
        }
    },

    /**
     * Jackson e Pollock — sete dobras. Mais medidas, menor erro-padrão.
     */
    POLLOCK_7(
            "Pollock 7 dobras",
            EnumSet.of(Dobra.PEITORAL, Dobra.AXILAR_MEDIA, Dobra.TRICIPITAL, Dobra.SUBESCAPULAR,
                    Dobra.ABDOMINAL, Dobra.SUPRAILIACA, Dobra.COXA),
            true, true) {
        @Override
        public double percentualDeGordura(Map<Dobra, Double> dobras, Sexo sexo, Integer idade) {
            double soma = somarApenas(dobras, dobrasExigidas(sexo));
            double densidade = sexo == Sexo.MASCULINO
                    ? 1.112 - 0.00043499 * soma + 0.00000055 * soma * soma - 0.00028826 * idade
                    : 1.097 - 0.00046971 * soma + 0.00000056 * soma * soma - 0.00012828 * idade;
            return siri(densidade);
        }
    },

    /**
     * Durnin e Womersley — quatro dobras, sem correção por idade na soma.
     * Os coeficientes variam por faixa etária; aqui usa-se a faixa de adultos.
     */
    DURNIN_WOMERSLEY(
            "Durnin e Womersley",
            EnumSet.of(Dobra.BICIPITAL, Dobra.TRICIPITAL, Dobra.SUBESCAPULAR, Dobra.SUPRAILIACA),
            true, false) {
        @Override
        public double percentualDeGordura(Map<Dobra, Double> dobras, Sexo sexo, Integer idade) {
            double log = Math.log10(somar(dobras));
            double densidade = sexo == Sexo.MASCULINO
                    ? 1.1765 - 0.0744 * log
                    : 1.1567 - 0.0717 * log;
            return siri(densidade);
        }
    };

    private final String descricao;
    private final Set<Dobra> dobrasFixas;
    private final boolean exigeSexo;
    private final boolean exigeIdade;

    ProtocoloComposicao(String descricao, Set<Dobra> dobrasFixas,
                        boolean exigeSexo, boolean exigeIdade) {
        this.descricao = descricao;
        this.dobrasFixas = dobrasFixas;
        this.exigeSexo = exigeSexo;
        this.exigeIdade = exigeIdade;
    }

    public String getDescricao() {
        return descricao;
    }

    public boolean exigeSexo() {
        return exigeSexo;
    }

    public boolean exigeIdade() {
        return exigeIdade;
    }

    /** Dobras necessárias. Alguns protocolos variam o conjunto conforme o sexo. */
    public Set<Dobra> dobrasExigidas(Sexo sexo) {
        return dobrasFixas;
    }

    /** Quais das dobras exigidas não foram medidas. */
    public List<Dobra> dobrasFaltantes(Map<Dobra, Double> dobras, Sexo sexo) {
        return dobrasExigidas(sexo).stream()
                .filter(d -> dobras.get(d) == null || dobras.get(d) <= 0)
                .toList();
    }

    public abstract double percentualDeGordura(Map<Dobra, Double> dobras, Sexo sexo, Integer idade);

    /**
     * Siri — converte densidade corporal em percentual de gordura.
     */
    protected static double siri(double densidade) {
        return (4.95 / densidade - 4.50) * 100;
    }

    protected static double somar(Map<Dobra, Double> dobras) {
        return dobras.values().stream().filter(java.util.Objects::nonNull).mapToDouble(Double::doubleValue).sum();
    }

    protected static double somarApenas(Map<Dobra, Double> dobras, Set<Dobra> quais) {
        return quais.stream()
                .map(dobras::get)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
    }
}
