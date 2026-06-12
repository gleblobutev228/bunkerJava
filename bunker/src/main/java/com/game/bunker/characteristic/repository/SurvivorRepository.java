package com.game.bunker.characteristic.repository;

import com.game.bunker.characteristic.entity.Survivor;
import com.game.bunker.characteristic.entity.SurvivorCharacteristic;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class SurvivorRepository {
    private final StringRedisTemplate redisTemplate;

    public SurvivorRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Survivor save(String survivorId, Survivor survivor) {
        redisTemplate.opsForHash().putAll(survivorKey(survivorId), toHash(survivor));
        return survivor;
    }

    public Survivor saveWithTtl(String survivorId, Survivor survivor, Duration ttl) {
        save(survivorId, survivor);
        redisTemplate.expire(survivorKey(survivorId), ttl);
        return survivor;
    }

    public Optional<Survivor> findById(String survivorId) {
        return findById(survivorId, false);
    }

    public Optional<Survivor> findVisibleById(String survivorId) {
        return findById(survivorId, true);
    }

    public void openCharacteristic(String survivorId, String characteristicName) {
        redisTemplate.opsForHash().put(survivorKey(survivorId), characteristicName + ":visible", "1");
    }

    public void deleteById(String survivorId) {
        redisTemplate.delete(survivorKey(survivorId));
    }

    private Optional<Survivor> findById(String survivorId, boolean hideInvisible) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(survivorKey(survivorId));
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(fromHash(entries, hideInvisible));
    }

    private Map<String, String> toHash(Survivor survivor) {
        Map<String, String> hash = new LinkedHashMap<>();
        Map<String, SurvivorCharacteristic> characteristics = survivor.getCharacter();
        for (String name : Survivor.CHARACTERISTIC_NAMES) {
            SurvivorCharacteristic characteristic = characteristics == null ? null : characteristics.get(name);
            hash.put(name, characteristic == null ? "" : nullToEmpty(characteristic.getValue()));
            hash.put(name + ":visible", characteristic != null && characteristic.isVisible() ? "1" : "0");
            if (!"bio".equals(name) && characteristic != null && characteristic.getDescription() != null) {
                hash.put(name + ":description", characteristic.getDescription());
            }
        }
        return hash;
    }

    private Survivor fromHash(Map<Object, Object> hash, boolean hideInvisible) {
        Map<String, SurvivorCharacteristic> characteristics = new LinkedHashMap<>();
        for (String name : Survivor.CHARACTERISTIC_NAMES) {
            String value = (String) hash.getOrDefault(name, "");
            boolean visible = "1".equals(hash.get(name + ":visible"));
            String description = (String) hash.get(name + ":description");
            if (hideInvisible && !visible) {
                value = null;
                description = null;
            }
            characteristics.put(name, new SurvivorCharacteristic(value, visible, description));
        }
        return new Survivor(characteristics);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String survivorKey(String survivorId) {
        return "survivor:" + survivorId;
    }
}
