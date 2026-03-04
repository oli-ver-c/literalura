package com.alura.literalura.repository;

import com.alura.literalura.model.Autor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface AutorRepository extends JpaRepository<Autor, Long> {
    Optional<Autor> findByNombreContainingIgnoreCase(String nombre);

    @Query("SELECT a FROM Autor a WHERE a.fechaNacimiento <= :year AND (a.fechaFallecimiento IS NULL OR a.fechaFallecimiento >= :year)")
    List<Autor> findAutoresVivosEnYear(@Param("year") Integer year);

    @Query("SELECT a FROM Autor a ORDER BY a.fechaNacimiento")
    List<Autor> findAllOrderByFechaNacimiento();
}