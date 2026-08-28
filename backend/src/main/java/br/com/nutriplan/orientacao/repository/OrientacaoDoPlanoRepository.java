package br.com.nutriplan.orientacao.repository;

import br.com.nutriplan.orientacao.domain.OrientacaoDoPlano;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrientacaoDoPlanoRepository extends JpaRepository<OrientacaoDoPlano, Long> {

    List<OrientacaoDoPlano> findByPlanoIdOrderByOrdemAsc(Long planoId);

    void deleteByPlanoId(Long planoId);
}
