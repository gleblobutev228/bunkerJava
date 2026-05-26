package com.game.bunker.repository.catalog;

import com.game.bunker.entity.catalog.ProfessionCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JPA-репозиторий каталога профессий.
 * Используется при генерации нового игрока, чтобы назначить персонажу случайную профессию.
 */
@Repository
public interface ProfessionCatalogRepository extends JpaRepository<ProfessionCatalog, Long> {
    /**
     * Возвращает случайную профессию из каталога.
     *
     * @return Optional со случайной профессией, если каталог заполнен.
     *
     * Call Chain:
     * - Откуда (Inbound): UserService.putProfession.
     * - Куда (Outbound): Spring Data JPA native query, таблица profession_catalog.
     */
    @Query(value = "select * from profession_catalog order by random() limit 1", nativeQuery = true)
    Optional<ProfessionCatalog> findRandom();
}
