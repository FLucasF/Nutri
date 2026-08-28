package br.com.nutriplan.handout.repository;

import br.com.nutriplan.handout.domain.HandoutImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HandoutImageRepository extends JpaRepository<HandoutImage, Long> {
}
