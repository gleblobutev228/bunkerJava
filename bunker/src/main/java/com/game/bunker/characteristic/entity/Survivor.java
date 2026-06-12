package com.game.bunker.characteristic.entity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Survivor {
    public static final List<String> CHARACTERISTIC_NAMES = List.of(
            "profession",
            "bio",
            "health",
            "hobby",
            "character",
            "phobia",
            "info",
            "baggage",
            "cards"
    );

    private Map<String, SurvivorCharacteristic> character;

    public Survivor() {
        this.character = new LinkedHashMap<>();
    }

    public Survivor(Map<String, SurvivorCharacteristic> character) {
        this.character = character;
    }

    public Map<String, SurvivorCharacteristic> getCharacter() {
        return character;
    }

    public void setCharacter(Map<String, SurvivorCharacteristic> character) {
        this.character = character;
    }
}
