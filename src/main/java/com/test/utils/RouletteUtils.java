package com.test.utils;

public class RouletteUtils {
    public static double probabilityFail(double initialBalance, double baseBet) {
        double pFail = Math.pow((19d / 37), Math.log((initialBalance / baseBet))); //todo
        return Math.floor(pFail);
    }

    public static double probabilityWin(double initialBalance, double baseBet) {
        return 1 - probabilityFail(initialBalance, baseBet);
    }

    public static double estimatedProfit(double initialBalance, double baseBet, int numberOfRounds) {
        double pFail = probabilityFail(initialBalance, baseBet);
        return -(pFail * initialBalance) + ((1 - pFail) * (numberOfRounds / 2d) * baseBet);
    }

    public static double calculateInitialBalance(double baseBet) {
        return baseBet * Math.pow(2, 10);
    }
}
