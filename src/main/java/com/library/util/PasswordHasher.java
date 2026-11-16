package com.library.util;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.*;
import java.util.Base64;
public final class PasswordHasher {
 private static final SecureRandom RANDOM=new SecureRandom();
 private PasswordHasher(){}
 public static String newSalt(){byte[] b=new byte[16];RANDOM.nextBytes(b);return Base64.getEncoder().encodeToString(b);}
 public static String hash(String password,String salt){
  PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),Base64.getDecoder().decode(salt),120000,256);
  try{return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());}
  catch(GeneralSecurityException e){throw new IllegalStateException("Password hashing failed.",e);}
  finally{spec.clearPassword();}
 }
 public static boolean verify(String password,String salt,String expected){return MessageDigest.isEqual(hash(password,salt).getBytes(java.nio.charset.StandardCharsets.UTF_8),expected.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
}
