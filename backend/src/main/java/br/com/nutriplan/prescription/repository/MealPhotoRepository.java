package br.com.nutriplan.prescription.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.nutriplan.prescription.domain.MealPhoto;

public interface MealPhotoRepository extends JpaRepository<MealPhoto, Long> {
}
