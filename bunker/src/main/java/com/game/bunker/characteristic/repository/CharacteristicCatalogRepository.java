package com.game.bunker.characteristic.repository;

import com.game.bunker.characteristic.entity.catalog.CharacteristicCatalog;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * JPA-репозиторий каталога характеристик персонажа.
 * Используется генератором игрока для выбора значений здоровья, хобби, характера, фобии и других карточек.
 */
@Repository
public interface CharacteristicCatalogRepository extends JpaRepository<CharacteristicCatalog, Long> {
    /**
     * Возвращает каталог характеристик по набору типов.
     *
     * @param types типы характеристик, которые нужны генератору.
     * @return записи каталога с указанными типами.
     *
     * Call Chain:
     * - Откуда (Inbound): UserService.loadRandomCatalogsByType.
     * - Куда (Outbound): Spring Data JPA, таблица characteristic_catalog.
     */
    List<CharacteristicCatalog> findByTypeIn(Collection<String> types);

    @Query(value = "SELECT * FROM characteristic_catalog WHERE type = :type ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
    CharacteristicCatalog findRandomByType(@Param("type") String type);
}
