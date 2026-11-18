package com.library.model;

/** One physical copy of an {@link Item}. copyId looks like "I-3-C2" (item id + copy number). */
public class Copy {
    private String copyId;
    private CopyStatus status = CopyStatus.AVAILABLE;

    private Copy() { } // Gson

    public Copy(String copyId) { this.copyId = copyId; }

    public String getCopyId() { return copyId; }
    public CopyStatus getStatus() { return status; }

    /** Validated move following the life-of-a-copy table. */
    public void moveTo(CopyStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Copy " + copyId + " cannot move from " + status + " to " + next);
        }
        status = next;
    }

    public boolean isAvailable() { return status == CopyStatus.AVAILABLE; }
}
