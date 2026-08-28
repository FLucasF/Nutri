package br.com.nutriplan.paciente.service;

import br.com.nutriplan.auth.service.ContextoAtual;
import br.com.nutriplan.auth.service.UsuarioAutenticado;
import br.com.nutriplan.paciente.domain.Paciente;
import br.com.nutriplan.paciente.dto.PacienteRequest;
import br.com.nutriplan.paciente.dto.PacienteResponse;
import br.com.nutriplan.paciente.dto.PacienteResumo;
import br.com.nutriplan.paciente.repository.PacienteRepository;
import br.com.nutriplan.shared.error.RecursoNaoEncontradoException;
import br.com.nutriplan.shared.error.RegraDeNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PacienteService {

    private final PacienteRepository pacienteRepository;
    private final ContextoAtual contextoAtual;
    private final ImportadorDePacientes importador;

    @Transactional(readOnly = true)
    public Page<PacienteResumo> listar(String termo, Boolean ativo, Pageable pageable) {
        String busca = StringUtils.hasText(termo) ? termo.trim() : null;
        return pacienteRepository.buscar(contextoAtual.contaId(), busca, ativo, pageable)
                .map(PacienteResumo::de);
    }

    @Transactional(readOnly = true)
    public PacienteResponse buscar(Long id) {
        return PacienteResponse.de(exigirDaConta(id));
    }

    /**
     * Carrega o paciente ja restrito a conta do usuario logado. Usado tambem
     * pelos outros modulos (prescricao, antropometria, agenda) para que a
     * checagem de posse exista num lugar so.
     */
    @Transactional(readOnly = true)
    public Paciente exigirDaConta(Long id) {
        return pacienteRepository.findByIdAndContaId(id, contextoAtual.contaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Paciente", id));
    }

    @Transactional
    public PacienteResponse criar(PacienteRequest req) {
        UsuarioAutenticado usuario = contextoAtual.exigirUsuario();
        verificarLimiteDoPlano(usuario);

        if (StringUtils.hasText(req.cpf())
                && pacienteRepository.existsByContaIdAndCpf(usuario.getContaId(), req.cpf())) {
            throw new RegraDeNegocioException("Já existe um paciente com este CPF neste consultório");
        }

        Paciente paciente = new Paciente(usuario.getContaId(), req.nome());
        aplicar(req, paciente);
        pacienteRepository.save(paciente);

        log.info("Paciente criado: id={} conta={}", paciente.getId(), usuario.getContaId());
        return PacienteResponse.de(paciente);
    }

    @Transactional
    public PacienteResponse atualizar(Long id, PacienteRequest req) {
        Paciente paciente = exigirDaConta(id);
        aplicar(req, paciente);
        return PacienteResponse.de(paciente);
    }

    /**
     * Inativa em vez de apagar: prontuario, prescricoes e lancamentos
     * financeiros do paciente precisam continuar existindo.
     */
    @Transactional
    public void inativar(Long id) {
        Paciente paciente = exigirDaConta(id);
        paciente.setAtivo(false);
        log.info("Paciente inativado: id={}", id);
    }

    @Transactional
    public PacienteResponse reativar(Long id) {
        Paciente paciente = exigirDaConta(id);
        verificarLimiteDoPlano(contextoAtual.exigirUsuario());
        paciente.setAtivo(true);
        return PacienteResponse.de(paciente);
    }

    /**
     * Importa pacientes de uma planilha.
     *
     * Grava um a um em vez de em lote de proposito: uma linha rejeitada — CPF ja
     * cadastrado, limite do plano atingido — nao pode derrubar as outras. Quem
     * importa duzentos pacientes precisa saber quais entraram, e nao receber
     * "falhou" sobre o arquivo inteiro.
     *
     * O limite do plano vale aqui como vale no cadastro manual. Importar nao e
     * um atalho para contorna-lo: quando o limite e atingido, a importacao para
     * e diz em que linha parou.
     */
    @Transactional
    public ResultadoDaImportacao importar(java.io.Reader entrada, char separador)
            throws java.io.IOException {

        UsuarioAutenticado usuario = contextoAtual.exigirUsuario();
        var leitura = importador.ler(entrada, separador);
        List<String> avisos = new ArrayList<>(leitura.avisos());
        int importados = 0;
        int ignorados = leitura.ignoradas();

        for (var linha : leitura.linhas()) {
            var req = linha.paciente();
            try {
                verificarLimiteDoPlano(usuario);
            } catch (RegraDeNegocioException e) {
                avisos.add("Importação interrompida na linha %d: %s"
                        .formatted(linha.numero(), e.getMessage()));
                ignorados += leitura.linhas().size() - importados - ignorados;
                break;
            }

            if (StringUtils.hasText(req.cpf())
                    && pacienteRepository.existsByContaIdAndCpf(usuario.getContaId(), req.cpf())) {
                ignorados++;
                if (avisos.size() < 25) {
                    avisos.add("Linha %d ignorada: já existe paciente com o CPF %s."
                            .formatted(linha.numero(), req.cpf()));
                }
                continue;
            }

            Paciente paciente = new Paciente(usuario.getContaId(), req.nome());
            aplicar(req, paciente);
            pacienteRepository.save(paciente);
            importados++;
        }

        log.info("Pacientes importados: {} de {} linhas, conta={}",
                importados, leitura.linhas().size(), usuario.getContaId());
        return new ResultadoDaImportacao(importados, ignorados, avisos);
    }

    public record ResultadoDaImportacao(int importados, int ignorados, List<String> avisos) {}

    private void verificarLimiteDoPlano(UsuarioAutenticado usuario) {
        var plano = usuario.getPlano();
        if (plano.pacientesIlimitados()) {
            return;
        }
        long ativos = pacienteRepository.countByContaIdAndAtivoTrue(usuario.getContaId());
        if (ativos >= plano.maxPacientes()) {
            throw new RegraDeNegocioException(
                    "O plano %s permite no máximo %d pacientes ativos. Faça upgrade para cadastrar mais."
                            .formatted(plano.name(), plano.maxPacientes()));
        }
    }

    private void aplicar(PacienteRequest req, Paciente paciente) {
        paciente.setNome(req.nome());
        paciente.setEmail(req.email());
        paciente.setTelefone(req.telefone());
        paciente.setDataNascimento(req.dataNascimento());
        paciente.setSexo(req.sexo());
        paciente.setCpf(req.cpf());
        paciente.setProfissao(req.profissao());
        paciente.setObjetivo(req.objetivo());
        paciente.setObservacoes(req.observacoes());
    }
}
