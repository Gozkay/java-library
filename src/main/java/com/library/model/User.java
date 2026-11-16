package com.library.model;
import java.util.Objects;
public class User {
 private final String userId, passwordHash, salt;
 private final Role role;
 private boolean locked;
 private int failedLoginAttempts;
 public User(String userId,String passwordHash,String salt,Role role){this.userId=Objects.requireNonNull(userId);this.passwordHash=Objects.requireNonNull(passwordHash);this.salt=Objects.requireNonNull(salt);this.role=Objects.requireNonNull(role);}
 public String getUserId(){return userId;} public String getPasswordHash(){return passwordHash;} public String getSalt(){return salt;} public Role getRole(){return role;} public boolean isLocked(){return locked;} public int getFailedLoginAttempts(){return failedLoginAttempts;}
 public void recordFailedLogin(){failedLoginAttempts++;if(failedLoginAttempts>=3)locked=true;}
 public void recordSuccessfulLogin(){failedLoginAttempts=0;}
 public void unlock(){locked=false;failedLoginAttempts=0;}
}
