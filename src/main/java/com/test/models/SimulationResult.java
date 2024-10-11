package com.test.models;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SimulationResult {
    private double initialBalance;
    private double baseBetAmount;

    private double maxBetPercentage;
    private int maxRounds;

    private double targetBalance;
    private double stopLossLimit;

    private double balance;
    private double profit;
    private double totalProfit;
    private double totalLoss;
    private int roundsPlayed;
    private boolean targetReached;
    private boolean stopLossReached;
    private boolean outOfMoney;
}