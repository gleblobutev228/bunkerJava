package com.game.bunker.repository.catalog;

import com.game.bunker.entity.catalog.CharacteristicCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
