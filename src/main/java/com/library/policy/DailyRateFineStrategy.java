package com.library.policy;

import com.library.factory.MembershipPolicyFactory;
import com.library.model.Member;

import java.math.BigDecimal;
import java.util.Objects;

public final class DailyRateFineStrategy implements FineStrategy {
    private final MembershipPolicyFactory policies;

    public DailyRateFineStrategy(MembershipPolicyFactory policies) {
        this.policies = Objects.requireNonNull(policies, "membership policy factory required");
    }

    @Override
    public BigDecimal calculate(long daysLate, Member member) {
        Objects.requireNonNull(member, "member required");
        if (daysLate <= 0) {
            return BigDecimal.ZERO;
        }
        return policies.createPolicy(member.getMembershipType()).getDailyFineRate()
                .multiply(BigDecimal.valueOf(daysLate));
    }
}
