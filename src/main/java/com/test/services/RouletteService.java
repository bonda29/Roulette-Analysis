package com.test.services;

import com.test.models.Bet;
import com.test.models.enums.Color;
import com.test.models.RouletteNumber;
import com.test.models.enums.Number;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static com.test.models.enums.Color.*;

@Slf4j
@Builder
public class RouletteService {
    private static final List<Integer> redNumbers = Arrays.asList(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);
    private static final List<Integer> blackNumbers = Arrays.asList(2, 4, 6, 8, 10, 11, 13, 15, 17, 20, 22, 24, 26, 28, 29, 31, 33, 35);

    private final List<RouletteNumber> wheel;
    private final Random random;

    private static List<RouletteNumber> createWheel() {
        return IntStream.rangeClosed(0, 36)
                .mapToObj(i -> {
                    Color color;
                    if (i == 0) {
                        color = null;
                    } else if (redNumbers.contains(i)) {
                        color = RED;
                    } else if (blackNumbers.contains(i)) {
                        color = BLACK;
                    } else {
                        throw new IllegalStateException("Invalid number: " + i);
                    }

                    return RouletteNumber.builder()
                            .number(i)
                            .color(color)
                            .build();
                })
                .toList();
    }

    public RouletteNumber spinWheel() {
        int index = random.nextInt(wheel.size());
        return wheel.get(index);
    }

    public int evaluateBet(Bet bet, RouletteNumber result) {
        switch (bet.getType()) {
            case NUMBER:
                if (!(bet.getBet() instanceof Number number)) {
                    throw new IllegalArgumentException("Invalid bet: " + bet.getBet());
                }

                if (number.getValue() == result.getNumber()) {
                    return bet.getAmount() * 36;
                }

            case COLOR:
                if (!(bet.getBet() instanceof Color color)) {
                    throw new IllegalArgumentException("Invalid bet: " + bet.getBet());
                }

                if (color == result.getColor()) {
                    return bet.getAmount() * 2;
                }

            case ODD_EVEN:
                if(!(bet.getBet() instanceof Number number)) {
                    throw new IllegalArgumentException("Invalid bet: " + bet.getBet());
                }

                boolean isBetEven = number.getValue() % 2 == 0;
                boolean isResultEven = result.getNumber() % 2 == 0;

                if (isBetEven == isResultEven) {
                    return bet.getAmount() * 2;
                }

            case HIGH_LOW:
                if(!(bet.getBet() instanceof Number number)) {
                    throw new IllegalArgumentException("Invalid bet: " + bet.getBet());
                }

                boolean isBetHigh = number.getValue() > 18;
                boolean isResultHigh = result.getNumber() > 18;

                if (isBetHigh == isResultHigh) {
                    return bet.getAmount() * 2;
                }

            case DOZEN:
                if (!(bet.getBet() instanceof Number number)) {
                    throw new IllegalArgumentException("Invalid bet: " + bet.getBet());
                }

                int dozen = (number.getValue() - 1) / 12;
                if (result.getNumber() != 0 && dozen == number.getValue()) {
                    return bet.getAmount() * 3;
                }

            case COLUMN:
                if (!(bet.getBet() instanceof Number number)) {
                    throw new IllegalArgumentException("Invalid bet: " + bet.getBet());
                }

                int column = (number.getValue() - 1) % 3;
                if (result.getNumber() != 0 && column == number.getValue()) {
                    return bet.getAmount() * 3;
                }

            default:
                throw new IllegalArgumentException("Invalid bet type: " + bet.getType());
        }
    }

    public void playRound(List<Bet> bets) {

    }
}
