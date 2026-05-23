package com.game.bunker.repository.catalog;

import com.game.bunker.entity.catalog.ExperienceCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExperienceCatalogRepository extends JpaRepository<ExperienceCatalog, Long> {
    @Query(value = "select * from experience_catalog order by random() limit 1", nativeQuery = true)
    Optional<ExperienceCatalog> findRandom();
}
