package com.game.bunker.repository.catalog;

import com.game.bunker.entity.catalog.CharacteristicCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CharacteristicCatalogRepository extends JpaRepository<CharacteristicCatalog, Long> {
    List<CharacteristicCatalog> findByTypeIn(Collection<String> types);
}
