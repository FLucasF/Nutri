package br.com.nutriplan.paciente.dto;

import br.com.nutriplan.paciente.domain.Paciente;
import br.com.nutriplan.paciente.domain.Sexo;

import java.time.Instant;
import java.time.LocalDate;

public record PacienteResponse(
        Long id,
        String nome,
        String email,
        String telefone,
        LocalDate dataNascimento,
        Integer idade,
        Sexo sexo,
        String cpf,
        String profissao,
        String objetivo,
        String observacoes,
        boolean ativo,
        boolean temAcessoAoApp,
        Instant criadoEm
) {
    public static PacienteResponse de(Paciente p) {
        return new PacienteResponse(
                p.getId(), p.getNome(), p.getEmail(), p.getTelefone(),
                p.getDataNascimento(), p.getIdade(), p.getSexo(), p.getCpf(),
                p.getProfissao(), p.getObjetivo(), p.getObservacoes(),
                p.isAtivo(), p.temAcessoAoApp(), p.getCriadoEm());
    }
}
