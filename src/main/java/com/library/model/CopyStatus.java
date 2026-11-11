package com.library.model;

/**
 * Life of a copy (brief section 4.11). The legal moves are encoded here once so services
 * cannot make an illegal transition by accident.
 */
public enum CopyStatus {
    AVAILABLE, ON_LOAN, ON_HOLD, LOST, RETIRED;

    public boolean canMoveTo(CopyStatus next) {
        return switch (this) {
            case AVAILABLE -> next == ON_LOAN || next == RETIRED;
            case ON_LOAN   -> next == AVAILABLE || next == ON_HOLD || next == LOST;
            case ON_HOLD   -> next == ON_LOAN || next == AVAILABLE || next == ON_HOLD || next == RETIRED;
            case LOST      -> next == RETIRED;
            case RETIRED   -> false;
        };
    }
}
