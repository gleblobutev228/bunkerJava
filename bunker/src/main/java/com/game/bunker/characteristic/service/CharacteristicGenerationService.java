package com.game.bunker.characteristic.service;

import com.game.bunker.characteristic.entity.Survivor;
import com.game.bunker.characteristic.entity.SurvivorCharacteristic;
import com.game.bunker.characteristic.entity.catalog.CharacteristicCatalog;
import com.game.bunker.characteristic.entity.catalog.ExperienceCatalog;
import com.game.bunker.characteristic.repository.CharacteristicCatalogRepository;
import com.game.bunker.characteristic.repository.ExperienceCatalogRepository;
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

    public CharacteristicGenerationService( ExperienceCatalogRepository experienceCatalogRepository, CharacteristicCatalogRepository characteristicCatalogRepository) {
        this.experienceCatalogRepository = experienceCatalogRepository;
        this.characteristicCatalogRepository = characteristicCatalogRepository;
    }

    private int randomInt(int minInclusive, int maxInclusive) {
        return ThreadLocalRandom.current().nextInt(minInclusive, maxInclusive + 1);
    }


    public Map<String, SurvivorCharacteristic> createSurvivor(String nickname, String lobbyId) {
        Map<String, SurvivorCharacteristic> survivorCharacteristics = new LinkedHashMap<>();

        GeneratedBioAndExperience generatedBioAndExperience = generateBioAndValidExperience();
        Map<String, CharacteristicCatalog> randomCatalogsByType = loadRandomCharacteristicByType();
        putProfession(survivorCharacteristics, generatedBioAndExperience.experience());
        putBio(survivorCharacteristics, generatedBioAndExperience.bio());
        for (String characteristicName : CATALOG_CHARACTERISTICS_NAMES) {
            putCatalogCharacteristic(survivorCharacteristics, characteristicName , randomCatalogsByType.get(characteristicName));
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
        for (CharacteristicCatalog characteristic : characteristicCatalogRepository.findByTypeIn(CATALOG_CHARACTERISTICS_NAMES)) {
            groupedCatalogs.computeIfAbsent(characteristic.getType(), ignored -> new ArrayList<>()).add(characteristic);
        }

        Map<String, CharacteristicCatalog> randomCharacteristicByType = new LinkedHashMap<>();
        for (String type : CATALOG_CHARACTERISTICS_NAMES) {
            List<CharacteristicCatalog> characteristics = groupedCatalogs.get(type);

        }
        return randomCharacteristicByType;
    }

    private CharacteristicCatalog getRandomCharacteristic(){

    }

    private void validateCharacteristicName(String charName) {
        if (!Survivor.CHARACTERISTIC_NAMES.contains(charName)) {
            throw new IllegalArgumentException("Unknown characteristic: " + charName);
        }
    }

    private void putProfession(Map<String, SurvivorCharacteristic> map, String experience) {
        CharacteristicCatalog profession = characteristicCatalogRepository.findRandomByType("profession");

        SurvivorCharacteristic professionCharacteristic = new SurvivorCharacteristic(
                profession.getValue(),
                false,
                experience
        );
        map.put("profession", professionCharacteristic);
    }

    private void putBio(Map<String, SurvivorCharacteristic> map, String bio) {
        SurvivorCharacteristic bioCharacteristic = new SurvivorCharacteristic(
                bio,
                false,
                null
        );
        map.put("bio", bioCharacteristic);
    }

    private void putCatalogCharacteristic(Map<String, SurvivorCharacteristic> map, String type, CharacteristicCatalog catalogCharacteristic) {
        SurvivorCharacteristic characteristic = new SurvivorCharacteristic(
               catalogCharacteristic.getValue(),
               false,
                catalogCharacteristic.getDescription()
        );
        map.put(type, characteristic);
    }

    private boolean addToUniquenessList(){

    }
}
