package com.library.policy;

import java.math.BigDecimal;

public class StudentPolicy implements MembershipPolicy {

    @Override
    public int getLoanLimit() {
        return 3;
    }

    @Override
    public int getRenewalLimit() {
        return 1;
    }

    @Override
    public BigDecimal getDailyFineRate() {
        return new BigDecimal("50.00");
    }

    @Override
    public int getAdditionalLoanPeriodDays() {
        return 0;
    }
}