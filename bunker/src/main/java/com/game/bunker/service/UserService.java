package com.game.bunker.service;

import com.game.bunker.entity.User;
import com.game.bunker.entity.catalog.CharacteristicCatalog;
import com.game.bunker.entity.catalog.ExperienceCatalog;
import com.game.bunker.entity.catalog.ProfessionCatalog;
import com.game.bunker.repository.UserRepository;
import com.game.bunker.repository.catalog.CharacteristicCatalogRepository;
import com.game.bunker.repository.catalog.ExperienceCatalogRepository;
import com.game.bunker.repository.catalog.ProfessionCatalogRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class UserService {
    private static final Duration DEFAULT_TTL = Duration.ofSeconds(7200);
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

    private final UserRepository userRepository;
    private final ProfessionCatalogRepository professionCatalogRepository;
    private final ExperienceCatalogRepository experienceCatalogRepository;
    private final CharacteristicCatalogRepository characteristicCatalogRepository;

    public UserService(UserRepository userRepository,
                       ProfessionCatalogRepository professionCatalogRepository,
                       ExperienceCatalogRepository experienceCatalogRepository,
                       CharacteristicCatalogRepository characteristicCatalogRepository) {
        this.userRepository = userRepository;
        this.professionCatalogRepository = professionCatalogRepository;
        this.experienceCatalogRepository = experienceCatalogRepository;
        this.characteristicCatalogRepository = characteristicCatalogRepository;
    }

    public User saveUser(User user){
        if (user.getId() == null || user.getId().isBlank()) {
            user.setId(UUID.randomUUID().toString());
        }
        return userRepository.saveWithTtl(user, DEFAULT_TTL);
    }

    public User generateAndSaveUser(String lobbyId, String nickname, Duration ttl) {
        String userId = UUID.randomUUID().toString();
        Map<String, String> hash = buildGeneratedUserHash(nickname, lobbyId);
        return userRepository.saveHashWithTtl(userId, hash, ttl);
    }

    public void openCharacteristic(String userId, String charName) {
        validateCharacteristicName(charName);
        userRepository.openCharacteristic(userId, charName);
    }

    public void setReady(String userId, boolean ready) {
        userRepository.setReady(userId, ready);
    }

    public boolean exists(String userId) {
        return userRepository.existsById(userId);
    }

    public User getVisibleUser(String userId) {
        return userRepository.findVisibleById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
    }

    public User getUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
    }

    private void validateCharacteristicName(String charName) {
        if (!User.CHARACTERISTIC_NAMES.contains(charName)) {
            throw new IllegalArgumentException("Unknown characteristic: " + charName);
        }
    }

    private Map<String, String> buildGeneratedUserHash(String nickname, String lobbyId) {
        Map<String, String> hash = new LinkedHashMap<>();
        hash.put("nickname", nickname == null ? "" : nickname);
        hash.put("ready", "false");
        hash.put("lobby_id", lobbyId);

        GeneratedBioAndExperience generatedBioAndExperience = generateBioAndValidExperience();
        Map<String, CharacteristicCatalog> randomCatalogsByType = loadRandomCatalogsByType();
        putProfession(hash, generatedBioAndExperience.experience());
        putBio(hash, generatedBioAndExperience.bio());
        for (String characteristic : CATALOG_CHARACTERISTICS) {
            putCatalogCharacteristic(hash, characteristic, randomCatalogsByType.get(characteristic));
        }

        return hash;
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

    private Map<String, CharacteristicCatalog> loadRandomCatalogsByType() {
        Map<String, List<CharacteristicCatalog>> groupedCatalogs = new LinkedHashMap<>();
        for (CharacteristicCatalog characteristic : characteristicCatalogRepository.findByTypeIn(CATALOG_CHARACTERISTICS)) {
            groupedCatalogs.computeIfAbsent(characteristic.getType(), ignored -> new ArrayList<>()).add(characteristic);
        }

        Map<String, CharacteristicCatalog> randomCatalogsByType = new LinkedHashMap<>();
        for (String type : CATALOG_CHARACTERISTICS) {
            List<CharacteristicCatalog> characteristics = groupedCatalogs.get(type);
            if (characteristics == null || characteristics.isEmpty()) {
                throw new NoSuchElementException("Catalog is empty for characteristic: " + type);
            }
            randomCatalogsByType.put(type, characteristics.get(randomInt(0, characteristics.size() - 1)));
        }
        return randomCatalogsByType;
    }

    private void putCatalogCharacteristic(Map<String, String> hash, String type, CharacteristicCatalog characteristic) {
        hash.put(type, characteristic.getValue());
        hash.put(type + ":visible", "0");
        if (characteristic.getDescription() != null) {
            hash.put(type + ":description", characteristic.getDescription());
        }
    }

    private int randomInt(int minInclusive, int maxInclusive) {
        return ThreadLocalRandom.current().nextInt(minInclusive, maxInclusive + 1);
    }

    private GeneratedBioAndExperience generateBioAndValidExperience() {
        for (int ageAttempt = 0; ageAttempt < MAX_AGE_RETRIES; ageAttempt++) {
            int age = randomInt(MIN_AGE, MAX_AGE);
            String gender = ThreadLocalRandom.current().nextBoolean() ? "Мужчина" : "Женщина";
            String bio = gender + ", " + age + " лет";

            for (int experienceAttempt = 0; experienceAttempt < MAX_EXPERIENCE_RETRIES; experienceAttempt++) {
                ExperienceCatalog experience = experienceCatalogRepository.findRandom()
                        .orElseThrow(() -> new NoSuchElementException("Experience catalog is empty"));
                if (isExperienceValidForAge(experience.getValue(), age)) {
                    return new GeneratedBioAndExperience(bio, experience.getValue());
                }
            }
        }

        throw new IllegalStateException("Failed to generate valid age and experience combination");
    }

    private boolean isExperienceValidForAge(String experience, int age) {
        return extractExperienceYears(experience) < age - MIN_AGE;
    }

    private int extractExperienceYears(String experience) {
        if (experience == null || experience.isBlank()) {
            return Integer.MAX_VALUE;
        }

        Matcher yearsMatcher = YEARS_PATTERN.matcher(experience);
        if (yearsMatcher.find()) {
            return Integer.parseInt(yearsMatcher.group(1));
        }

        Matcher monthsMatcher = MONTHS_PATTERN.matcher(experience);
        if (monthsMatcher.find()) {
            return 0;
        }

        return Integer.MAX_VALUE;
    }

    private record GeneratedBioAndExperience(String bio, String experience) {
    }
}
