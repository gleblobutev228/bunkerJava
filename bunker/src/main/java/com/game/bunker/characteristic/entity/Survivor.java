package com.game.bunker.characteristic.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@Setter
@Getter
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
}
