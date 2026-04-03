package com.game.bunker.utils.generator;

import java.util.concurrent.ThreadLocalRandom;

public class LobbyCodeGenerator {
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 6;
    /*генерирует код типа ABC234 без похожих сиволов.
     * использует ThreadLocalRandom для возможного использования в многопоточке
     */
    public static String generate() {
        char[] result = new char[CODE_LENGTH];
        var random = ThreadLocalRandom.current();

        for (int i = 0; i < CODE_LENGTH; i++) {
            result[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(result);
    }
}
