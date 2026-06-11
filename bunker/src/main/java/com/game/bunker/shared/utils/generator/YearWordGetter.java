package com.game.bunker.shared.utils.generator;

public class YearWordGetter {
    public static String getYearWord(Integer num){
        int preLastDigit = (num % 100) / 10;
        if (preLastDigit == 1) {
            return "лет";
        }
        return switch (num % 10) {
            case 1 -> "год";
            case 2, 3, 4 -> "года";
            default -> "лет";
        };
    }
}
