package com.test.models;

import com.test.models.enums.Color;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RouletteNumber {
    private int number;
    private Color color;
}
