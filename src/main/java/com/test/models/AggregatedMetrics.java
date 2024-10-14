package com.test.models;

import lombok.Builder;
import lombok.Data;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

@Data
@Builder
public class AggregatedMetrics {
    private long scenarioId;
    private double averageProfit;
    private double medianProfit;
    private double profitStdDev;
    private double probabilityOfRuin;
    private double probabilityOfReachingTarget;
    private double averageRoundsPlayed;
    private double confidenceIntervalLower;
    private double confidenceIntervalUpper;

    public static List<String> getFieldNames() {
        return Arrays.stream(AggregatedMetrics.class.getDeclaredFields())
                .map(Field::getName)
                .toList();
    }
}
