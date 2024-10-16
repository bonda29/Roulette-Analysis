package com.test.services;

import com.test.models.Bet;
import com.test.models.enums.*;
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

    public static List<RouletteNumber> createWheel() {
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

    public double evaluateBet(Bet bet, RouletteNumber result) {
        switch (bet.getType()) {
            case NUMBER: {
                if (!(bet.getBet() instanceof Number betNumber)) {
                    throw new IllegalArgumentException("Invalid bet: " + bet.getBet());
                }
                if (betNumber.getValue() == result.getNumber()) {
                    return bet.getAmount() * 36;
                }
                break;
            }
            case COLOR: {
                if (!(bet.getBet() instanceof Color betColor)) {
                    throw new IllegalArgumentException("Invalid bet: " + bet.getBet());
                }
                if (betColor == result.getColor()) {
                    return bet.getAmount() * 2;
                }
                break;
            }
            case ODD_EVEN: {
                if (result.getNumber() == 0) {
                    break;
                }

                if (!(bet.getBet() instanceof OddEven betValue)) {
                    throw new IllegalArgumentException("Invalid bet: " + bet.getBet());
                }

                boolean isResultEven = result.getNumber() % 2 == 0;
                if ((betValue == OddEven.EVEN && isResultEven) || (betValue == OddEven.ODD && !isResultEven)) {
                    return bet.getAmount() * 2;
                }

                break;
            }
            case HIGH_LOW: {
                if (result.getNumber() == 0) {
                    break;
                }

                if (!(bet.getBet() instanceof HighLow betValue)) {
                    throw new IllegalArgumentException("Invalid bet: " + bet.getBet());
                }

                boolean isResultHigh = result.getNumber() >= 19 && result.getNumber() <= 36;
                if ((betValue == HighLow.HIGH && isResultHigh) || (betValue == HighLow.LOW && !isResultHigh)) {
                    return bet.getAmount() * 2;
                }

                break;
            }
            case DOZEN: {
                if (result.getNumber() == 0) {
                    break;
                }

                if (!(bet.getBet() instanceof Dozen betDozen)) {
                    throw new IllegalArgumentException("Invalid bet: " + bet.getBet());
                }

                int resultDozen = (result.getNumber() - 1) / 12 + 1;
                if (betDozen.getDozenNumber() == resultDozen) {
                    return bet.getAmount() * 3;
                }

                break;
            }
            case COLUMN: {
                if (!(bet.getBet() instanceof Column betColumn)) {
                    throw new IllegalArgumentException("Invalid bet: " + bet.getBet());
                }

                int resultColumn = ((result.getNumber() - 1) % 3) + 1;
                if (betColumn.getColumnNumber() == resultColumn) {
                    return bet.getAmount() * 3;
                }

                break;
            }
            default:
                throw new IllegalArgumentException("Invalid bet type: " + bet.getType());
        }

        return 0;
    }

    public void playRound(List<Bet> bets) {
        RouletteNumber result = spinWheel();

        log.info("The ball landed on {} {}", result.getNumber(), result.getColor() != null ? result.getColor() : "");

        double totalPayout = 0;
        for (Bet bet : bets) {
            double payout = evaluateBet(bet, result);

            if (payout > 0) {
                log.info("Bet on {} wins! Payout: {}", bet.getBet(), payout);
            } else {
                log.info("Bet on {} loses.", bet.getBet());
            }

            totalPayout += payout;
        }

        log.info("Total payout: {}", totalPayout);
    }
}
