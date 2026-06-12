package com.game.bunker.characteristic.service;

import com.game.bunker.characteristic.entity.Survivor;
import com.game.bunker.characteristic.entity.SurvivorCharacteristic;
import com.game.bunker.characteristic.entity.catalog.CharacteristicCatalog;
import com.game.bunker.characteristic.entity.catalog.ExperienceCatalog;
import com.game.bunker.characteristic.repository.CharacteristicCatalogRepository;
import com.game.bunker.characteristic.repository.ExperienceCatalogRepository;
import com.game.bunker.lobby.repository.LobbyUniquenessListRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class CharacteristicGenerationService {
    private static final int MIN_AGE = 18;
    private static final int MAX_AGE = 70;
    private static final int MAX_AGE_RETRIES = 20;
    private static final int MAX_UNIQUENESS_RETRIES = 100;
    private static final String PROFESSION_TYPE = "profession";
    private static final List<String> CATALOG_CHARACTERISTICS_NAMES = List.of(
            "health",
            "hobby",
            "character",
            "phobia",
            "info",
            "baggage",
            "cards"
    );


    private final ExperienceCatalogRepository experienceCatalogRepository;
    private final CharacteristicCatalogRepository characteristicCatalogRepository;
    private final LobbyUniquenessListRepository lobbyUniquenessListRepository;

    public CharacteristicGenerationService( ExperienceCatalogRepository experienceCatalogRepository,
                                            CharacteristicCatalogRepository characteristicCatalogRepository,
                                            LobbyUniquenessListRepository lobbyUniquenessListRepository) {
        this.experienceCatalogRepository = experienceCatalogRepository;
        this.characteristicCatalogRepository = characteristicCatalogRepository;
        this.lobbyUniquenessListRepository = lobbyUniquenessListRepository;
    }

    private int randomInt(int minInclusive, int maxInclusive) {
        return ThreadLocalRandom.current().nextInt(minInclusive, maxInclusive + 1);
    }


    public Map<String, SurvivorCharacteristic> createSurvivor(String nickname, String lobbyId) {
        Map<String, SurvivorCharacteristic> survivorCharacteristics = new LinkedHashMap<>();

        GeneratedBioAndExperience generatedBioAndExperience = generateBioAndValidExperience();
        putBio(survivorCharacteristics, generatedBioAndExperience.bio());
        survivorCharacteristics.put("profession", generateCharacteristicByType(PROFESSION_TYPE, lobbyId, generatedBioAndExperience.experience()));
        for (String characteristicName : CATALOG_CHARACTERISTICS_NAMES) {
            survivorCharacteristics.put(characteristicName, generateCharacteristicByType(characteristicName, lobbyId));
        }

        return survivorCharacteristics;
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

    public SurvivorCharacteristic generateCharacteristicByType(String type, String lobbyId) {
        return generateCharacteristicByType(type, lobbyId, null);
    }

    private SurvivorCharacteristic generateCharacteristicByType(String type, String lobbyId, String professionExperience) {
        validateSupportedGenerationType(type);
        for (int attempt = 0; attempt < MAX_UNIQUENESS_RETRIES; attempt++) {
            CharacteristicCatalog catalogCharacteristic = characteristicCatalogRepository.findRandomByType(type);
            if (catalogCharacteristic == null) {
                throw new NoSuchElementException("Characteristic catalog is empty for type: " + type);
            }
            if (catalogCharacteristic.getId() == null) {
                continue;
            }

            boolean isUnique = lobbyUniquenessListRepository.addIfAbsent(lobbyId, catalogCharacteristic.getId());
            if (!isUnique) {
                continue;
            }

            String description = buildCharacteristicDescription(type, catalogCharacteristic.getDescription(), professionExperience);
            return new SurvivorCharacteristic(catalogCharacteristic.getValue(), false, description);
        }
        throw new IllegalStateException("Failed to generate unique characteristic for type: " + type);
    }

    private String buildCharacteristicDescription(String type, String originalDescription, String professionExperience) {
        if (!PROFESSION_TYPE.equals(type)) {
            return originalDescription;
        }

        String validExperience = professionExperience;
        if (validExperience == null || validExperience.isBlank()) {
            validExperience = generateBioAndValidExperience().experience();
        }

        if (originalDescription == null || originalDescription.isBlank()) {
            return validExperience;
        }
        return originalDescription + "\n" + validExperience;
    }

    private void validateCharacteristicName(String charName) {
        if (!Survivor.CHARACTERISTIC_NAMES.contains(charName)) {
            throw new IllegalArgumentException("Unknown characteristic: " + charName);
        }
    }

    private void validateSupportedGenerationType(String type) {
        validateCharacteristicName(type);
        if (PROFESSION_TYPE.equals(type)) {
            return;
        }
        if (!CATALOG_CHARACTERISTICS_NAMES.contains(type)) {
            throw new IllegalArgumentException("Generation is not supported for type: " + type);
        }
    }

    private void putBio(Map<String, SurvivorCharacteristic> map, String bio) {
        SurvivorCharacteristic bioCharacteristic = new SurvivorCharacteristic(
                bio,
                false,
                null
        );
        map.put("bio", bioCharacteristic);
    }
}
