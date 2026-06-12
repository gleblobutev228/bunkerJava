package com.game.bunker.characteristic.service;

import com.game.bunker.characteristic.entity.Survivor;
import com.game.bunker.characteristic.repository.SurvivorRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.NoSuchElementException;

@Service
public class SurvivorService {
    private final SurvivorRepository survivorRepository;
    private final CharacteristicGenerationService characteristicGenerationService;

    public SurvivorService(SurvivorRepository survivorRepository,
                           CharacteristicGenerationService characteristicGenerationService) {
        this.survivorRepository = survivorRepository;
        this.characteristicGenerationService = characteristicGenerationService;
    }

    public Survivor generateAndSaveSurvivor(String survivorId, String nickname, String lobbyId, Duration ttl) {
        Survivor survivor = new Survivor(characteristicGenerationService.createSurvivor(nickname, lobbyId));
        return survivorRepository.saveWithTtl(survivorId, survivor, ttl);
    }

    public Survivor getSurvivor(String survivorId) {
        return survivorRepository.findById(survivorId)
                .orElseThrow(() -> new NoSuchElementException("Survivor not found: " + survivorId));
    }

    public Survivor getVisibleSurvivor(String survivorId) {
        return survivorRepository.findVisibleById(survivorId)
                .orElseThrow(() -> new NoSuchElementException("Survivor not found: " + survivorId));
    }

    public void openCharacteristic(String survivorId, String characteristicName) {
        validateCharacteristicName(characteristicName);
        survivorRepository.openCharacteristic(survivorId, characteristicName);
    }

    public void deleteSurvivor(String survivorId) {
        survivorRepository.deleteById(survivorId);
    }

    private void validateCharacteristicName(String characteristicName) {
        if (!Survivor.CHARACTERISTIC_NAMES.contains(characteristicName)) {
            throw new IllegalArgumentException("Unknown characteristic: " + characteristicName);
        }
    }
}
