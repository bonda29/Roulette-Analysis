package com.test.models.enums;

import com.test.models.abstracts.BetOption;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Column implements BetOption {
    FIRST(1),
    SECOND(2),
    THIRD(3);

    private final int columnNumber;
}
