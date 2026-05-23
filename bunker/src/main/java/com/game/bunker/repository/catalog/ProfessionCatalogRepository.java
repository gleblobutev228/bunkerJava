package com.game.bunker.repository.catalog;

import com.game.bunker.entity.catalog.ProfessionCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProfessionCatalogRepository extends JpaRepository<ProfessionCatalog, Long> {
    @Query(value = "select * from profession_catalog order by random() limit 1", nativeQuery = true)
    Optional<ProfessionCatalog> findRandom();
}
