package com.library.model;

/** A login account. Librarians are plain Users; members are {@link Member}s. Passwords are never stored in plain text. */
public class User {
    private String userId;
    private String name;
    private Role role;
    private String passwordHash;
    private String salt;
    private int failedLoginAttempts;
    private boolean locked;

    protected User() { } // Gson

    public User(String userId, String name, Role role, String passwordHash, String salt) {
        this.userId = userId;
        this.name = name;
        this.role = role;
        this.passwordHash = passwordHash;
        this.salt = salt;
    }

    public String getUserId() { return userId; }
    public String getName() { return name; }
    public Role getRole() { return role; }
    public String getPasswordHash() { return passwordHash; }
    public String getSalt() { return salt; }
    public void setPassword(String passwordHash, String salt) { this.passwordHash = passwordHash; this.salt = salt; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public void setFailedLoginAttempts(int n) { this.failedLoginAttempts = n; }
    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }
}
