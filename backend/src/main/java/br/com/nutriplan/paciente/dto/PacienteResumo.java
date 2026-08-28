package br.com.nutriplan.paciente.dto;

import br.com.nutriplan.paciente.domain.Paciente;

/** Projecao enxuta para listagens e seletores. */
public record PacienteResumo(Long id, String nome, String email, Integer idade, boolean ativo) {
    public static PacienteResumo de(Paciente p) {
        return new PacienteResumo(p.getId(), p.getNome(), p.getEmail(), p.getIdade(), p.isAtivo());
    }
}
