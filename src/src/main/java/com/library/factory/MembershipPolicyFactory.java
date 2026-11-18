package com.library.factory;

import com.library.model.MembershipType;
import com.library.policy.*;

public class MembershipPolicyFactory {
    public MembershipPolicy createPolicy(MembershipType type) {
        if (type == null) {
            throw new IllegalArgumentException("Membership type is required.");
        }

        switch (type) {
            case STUDENT:
                return new StudentPolicy();
            case STAFF:
                return new StaffPolicy();
            case SENIOR:
                return new SeniorPolicy();
            default:
                throw new IllegalArgumentException("Unsupported membership type.");
        }
    }
}
