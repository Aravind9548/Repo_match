package com.repomatch.tracker;

/** Where an application stands. Order matters: it's the pipeline left to right on the board. */
public enum ApplicationStatus {
    /** Approved from an alert; resume not chosen or not submitted yet. */
    APPROVED,
    /** The person submitted it themselves. */
    APPLIED,
    /** Online assessment / take-home received. */
    ASSESSMENT,
    INTERVIEW,
    OFFER,
    REJECTED,
    /** No reply for a long time. */
    GHOSTED,
    WITHDRAWN;

    public boolean isFinal() {
        return this == OFFER || this == REJECTED || this == GHOSTED || this == WITHDRAWN;
    }
}
