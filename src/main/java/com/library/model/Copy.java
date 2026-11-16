package com.library.model;

public class Copy {
    private final String copyId;
    private final String itemId;
    private CopyStatus status;

    public Copy(String copyId, String itemId, CopyStatus status) {
        this.copyId = copyId;
        this.itemId = itemId;
        this.status = status;
    }

    public String getCopyId() { return copyId; }
    public String getItemId() { return itemId; }
    public CopyStatus getStatus() { return status; }
    public void setStatus(CopyStatus status) { this.status = status; }
}
