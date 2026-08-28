package br.com.nutriplan.antropometria.repository;

import br.com.nutriplan.antropometria.domain.CurvaDeCrescimento;
import br.com.nutriplan.antropometria.domain.IndicadorDeCrescimento;
import br.com.nutriplan.paciente.domain.Sexo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CurvaDeCrescimentoRepository extends JpaRepository<CurvaDeCrescimento, Long> {

    Optional<CurvaDeCrescimento> findByIndicadorAndSexoAndMes(
            IndicadorDeCrescimento indicador, Sexo sexo, Integer mes);

    boolean existsByIndicador(IndicadorDeCrescimento indicador);
}
