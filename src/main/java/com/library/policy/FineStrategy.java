package com.library.policy;

import com.library.model.Member;

import java.math.BigDecimal;

@FunctionalInterface
public interface FineStrategy {
    BigDecimal calculate(long daysLate, Member member);
}
