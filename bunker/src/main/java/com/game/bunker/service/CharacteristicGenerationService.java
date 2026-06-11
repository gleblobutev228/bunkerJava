package com.game.bunker.service;

import com.game.bunker.entity.User;
import com.game.bunker.entity.UserCharacteristic;
import com.game.bunker.entity.catalog.CharacteristicCatalog;
import com.game.bunker.entity.catalog.ExperienceCatalog;
import com.game.bunker.entity.catalog.ProfessionCatalog;
import com.game.bunker.repository.catalog.CharacteristicCatalogRepository;
import com.game.bunker.repository.catalog.ExperienceCatalogRepository;
import com.game.bunker.repository.catalog.ProfessionCatalogRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

@Service
public class CharacteristicGenerationService {
    private static final int MIN_AGE = 18;
    private static final int MAX_AGE = 70;
    private static final int MAX_AGE_RETRIES = 20;
    private static final int MAX_EXPERIENCE_RETRIES = 100;
    private static final Pattern YEARS_PATTERN = Pattern.compile("(\\d+)\\s*(год|года|лет)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MONTHS_PATTERN = Pattern.compile("(\\d+)\\s*(месяц|месяца|месяцев)", Pattern.CASE_INSENSITIVE);
    private static final List<String> CATALOG_CHARACTERISTICS = List.of(
            "health",
            "hobby",
            "character",
            "phobia",
            "info",
            "baggage",
            "cards"
    );

    private final ProfessionCatalogRepository professionCatalogRepository;
    private final ExperienceCatalogRepository experienceCatalogRepository;
    private final CharacteristicCatalogRepository characteristicCatalogRepository;

    public CharacteristicGenerationService(ProfessionCatalogRepository professionCatalogRepository, ExperienceCatalogRepository experienceCatalogRepository, CharacteristicCatalogRepository characteristicCatalogRepository) {
        this.professionCatalogRepository = professionCatalogRepository;
        this.experienceCatalogRepository = experienceCatalogRepository;
        this.characteristicCatalogRepository = characteristicCatalogRepository;
    }

    private int randomInt(int minInclusive, int maxInclusive) {
        return ThreadLocalRandom.current().nextInt(minInclusive, maxInclusive + 1);
    }


    public Map<String, String> buildGeneratedCharacter(String nickname, String lobbyId) {
        Map<String, UserCharacteristic> Characteristics = new LinkedHashMap<>();

        GeneratedBioAndExperience generatedBioAndExperience = generateBioAndValidExperience();
        Map<String, CharacteristicCatalog> randomCatalogsByType = loadRandomCharacteristicByType();
        putProfession(CharacterHash, generatedBioAndExperience.experience());
        putBio(CharacterHash, generatedBioAndExperience.bio());
        for (String characteristic : CATALOG_CHARACTERISTICS) {
            putCatalogCharacteristic(CharacterHash, characteristic, randomCatalogsByType.get(characteristic));
        }

        return CharacterHash;
    }

    private GeneratedBioAndExperience generateBioAndValidExperience() {
        for (int ageAttempt = 0; ageAttempt < MAX_AGE_RETRIES; ageAttempt++) {
            int age = randomInt(MIN_AGE, MAX_AGE);
            String gender = ThreadLocalRandom.current().nextBoolean() ? "Мужчина" : "Женщина";
            String bio = gender + ", " + age + " лет";

            ExperienceCatalog experience = experienceCatalogRepository.findRandom(age - 18)
                    .orElseThrow(() -> new NoSuchElementException("Experience catalog is empty"));
            return new GeneratedBioAndExperience(bio, experience.toString());
            }
        throw new IllegalStateException("Failed to generate valid age and experience combination");
    }

    private record GeneratedBioAndExperience(String bio, String experience) {
    }

    // TODO(senior): Загружает все значения характеристик нужных типов и выбирает в памяти; при росте каталога нужен random на уровне БД/кэша.
    private Map<String, CharacteristicCatalog> loadRandomCharacteristicByType() {
        Map<String, List<CharacteristicCatalog>> groupedCatalogs = new LinkedHashMap<>();
        for (CharacteristicCatalog characteristic : characteristicCatalogRepository.findByTypeIn(CATALOG_CHARACTERISTICS)) {
            groupedCatalogs.computeIfAbsent(characteristic.getType(), ignored -> new ArrayList<>()).add(characteristic);
        }

        Map<String, CharacteristicCatalog> randomCharacteristicByType = new LinkedHashMap<>();
        for (String type : CATALOG_CHARACTERISTICS) {
            List<CharacteristicCatalog> characteristics = groupedCatalogs.get(type);
            if (characteristics == null || characteristics.isEmpty()) {
                throw new NoSuchElementException("Catalog is empty for characteristic: " + type);
            }
            randomCharacteristicByType.put(type, characteristics.get(randomInt(0, characteristics.size() - 1)));
        }
        return randomCharacteristicByType;
    }


    private void validateCharacteristicName(String charName) {
        if (!User.CHARACTERISTIC_NAMES.contains(charName)) {
            throw new IllegalArgumentException("Unknown characteristic: " + charName);
        }
    }

    private void putProfession(Map<String, String> hash, String experience) {
        ProfessionCatalog profession = professionCatalogRepository.findRandom()
                .orElseThrow(() -> new NoSuchElementException("Profession catalog is empty"));

        hash.put("profession", profession.getValue());
        hash.put("profession:visible", "0");
        hash.put("profession:description", experience);
    }

    private void putBio(Map<String, String> hash, String bio) {
        hash.put("bio", bio);
        hash.put("bio:visible", "0");
    }

    private void putCatalogCharacteristic(Map<String, String> hash, String type, CharacteristicCatalog characteristic) {
        hash.put(type, characteristic.getValue());
        hash.put(type + ":visible", "0");
        if (characteristic.getDescription() != null) {
            hash.put(type + ":description", characteristic.getDescription());
        }
    }
}
