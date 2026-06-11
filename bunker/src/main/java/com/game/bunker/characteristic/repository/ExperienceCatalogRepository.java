package com.game.bunker.characteristic.repository;

import com.game.bunker.characteristic.entity.catalog.ExperienceCatalog;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JPA-репозиторий каталога опыта работы.
 * Участвует в генерации профессии и биографии игрока, подбирая случайное описание стажа.
 */
@Repository
public interface ExperienceCatalogRepository extends JpaRepository<ExperienceCatalog, Long> {
    /**
     * Возвращает случайную запись опыта из каталога.
     *
     * @return Optional со случайным опытом, если каталог заполнен.
     *
     * Call Chain:
     * - Откуда (Inbound): UserService.generateBioAndValidExperience.
     * - Куда (Outbound): Spring Data JPA native query, таблица experience_catalog.
     */
    @Query(value = "select * from experience_catalog where min_age >= :minAge order by random() limit 1", nativeQuery = true)
    Optional<ExperienceCatalog> findRandom(@Param("minAge") int minAge);
}
