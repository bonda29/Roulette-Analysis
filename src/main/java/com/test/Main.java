package com.test;

import com.test.models.Bet;
import com.test.models.RouletteNumber;
import com.test.models.enums.BetType;
import com.test.models.enums.Color;
import com.test.services.RouletteService;
import lombok.extern.slf4j.Slf4j;

import java.util.Random;

@Slf4j
public class Main {
    public static void main(String[] args) {
        RouletteService rouletteService = RouletteService.builder()
                .wheel(RouletteService.createWheel())
                .random(new Random())
                .build();

        double balance = 500;
        double baseBetAmount = 1;
        double betAmount = baseBetAmount;
        double maxBetPercentage = 100; // % of initial balance
        Color betColor = Color.RED;
        double maxRounds = 1000;
        double targetProfit = 100;
        double stopLossLimit = 200;
        double totalProfit = 0;
        double totalLoss = 0;
        double round = 0;

        while (balance >= betAmount && round < maxRounds) {
            Bet bet = Bet.builder()
                    .type(BetType.COLOR)
                    .amount(betAmount)
                    .bet(betColor)
                    .build();

            RouletteNumber result = rouletteService.spinWheel();
//            log.info("Round {}: The ball landed on {} {}", round + 1, result.getNumber(), result.getColor());

            double payout = rouletteService.evaluateBet(bet, result);

            balance -= betAmount;
            if (payout > 0) {
                balance += payout;
                totalProfit += payout - betAmount;

                log.info("Bet on {} wins! Payout: ${}", bet.getBet(), payout);
                log.info("Balance: ${}", balance);

                betAmount = baseBetAmount;
            } else {
                totalLoss += betAmount;
                log.info("Bet on {} loses.", bet.getBet());
                log.info("Balance: ${}", balance);

                betAmount *= 2;

                double maxBetLimit = 500 * maxBetPercentage;
                if (betAmount > maxBetLimit) {
                    betAmount = maxBetLimit;
                }

                if (betAmount > balance) {
                    betAmount = balance;
                }
            }

            round++;
        }

        if (balance <= 0) {
            log.info("You have run out of money.");
        }
//        else if (totalProfit >= targetProfit) {
//            log.info("You have reached your target profit of ${}", totalProfit);
//        } else if (totalLoss >= stopLossLimit) {
//            log.info("Stop-loss limit reached. Total loss: ${}", totalLoss);
//        }
        else {
            log.info("Stopped after {} rounds.", round);
        }

        log.info("Final balance: ${}", balance);
    }
}