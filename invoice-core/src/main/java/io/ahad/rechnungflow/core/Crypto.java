package io.ahad.rechnungflow.core;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
public final class Crypto {
    private static final SecureRandom RANDOM=new SecureRandom();
    public static final int PASSWORD_ITERATIONS=600_000;
    private Crypto() {}
    public static byte[] random(int size) { byte[] b=new byte[size];RANDOM.nextBytes(b);return b; }
    public static String token() { return Base64.getUrlEncoder().withoutPadding().encodeToString(random(32)); }
    public static String sha(byte[] data) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}
        catch(GeneralSecurityException e){throw new IllegalStateException(e);}
    }
    public static String sha(String data) { return sha(data.getBytes(StandardCharsets.UTF_8)); }
    public static boolean equal(String a,String b) {
        return a!=null&&b!=null&&MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));
    }
    public static String password(String value) {
        Problem.require(value!=null&&value.length()>=14&&value.length()<=128,400,"PASSWORD_POLICY");
        byte[] salt=random(16);return "pbkdf2-sha256$"+PASSWORD_ITERATIONS+"$"+Base64.getEncoder().encodeToString(salt)+"$"+Base64.getEncoder().encodeToString(derive(value,salt,PASSWORD_ITERATIONS));
    }
    public static boolean verify(String value,String stored) {
        if(value==null||value.length()>128)return false;
        try {
            String[] p=stored.split("\\$"); int n=Integer.parseInt(p[1]);
            if(p.length!=4||!p[0].equals("pbkdf2-sha256")||n<600_000||n>2_000_000)return false;
            byte[] salt=Base64.getDecoder().decode(p[2]),hash=Base64.getDecoder().decode(p[3]);
            return salt.length==16&&hash.length==32&&MessageDigest.isEqual(hash,derive(value,salt,n));
        } catch(Exception e) { return false; }
    }
    private static byte[] derive(String value,byte[] salt,int iterations) {
        PBEKeySpec spec=new PBEKeySpec(value.toCharArray(),salt,iterations,256);
        try {return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}
        catch(GeneralSecurityException e){throw new IllegalStateException(e);}
        finally{spec.clearPassword();}
    }
    public static byte[] encrypt(byte[] key,byte[] plain,String aad) {
        try {
            byte[] nonce=random(12);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
            c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));byte[] payload=c.doFinal(plain);
            return ByteBuffer.allocate(4+12+payload.length).putInt(0x52463101).put(nonce).put(payload).array();
        } catch(GeneralSecurityException e){throw new IllegalStateException(e);}
    }
    public static byte[] decrypt(byte[] key,byte[] data,String aad) {
        try {
            if(data.length<32)throw new GeneralSecurityException();ByteBuffer b=ByteBuffer.wrap(data);
            if(b.getInt()!=0x52463101)throw new GeneralSecurityException();byte[] nonce=new byte[12];b.get(nonce);
            byte[] payload=new byte[b.remaining()];b.get(payload);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
            c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));return c.doFinal(payload);
        } catch(GeneralSecurityException|RuntimeException e){throw Problem.of(500,"STORAGE_INTEGRITY");}
    }
}
