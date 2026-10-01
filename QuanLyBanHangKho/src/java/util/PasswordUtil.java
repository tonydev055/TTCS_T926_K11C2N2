package util;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
public final class PasswordUtil {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ITERATIONS = 120_000;
    private PasswordUtil(){}
    /** Legacy SHA-256 kept only so an existing database can be upgraded at login. */
    public static String hash(String s){
        try{MessageDigest md=MessageDigest.getInstance("SHA-256");return Base64.getEncoder().encodeToString(md.digest(s.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new RuntimeException(e);}
    }
    public static String encode(String password){
        try{
            byte[] salt=new byte[16]; RANDOM.nextBytes(salt);
            KeySpec spec=new PBEKeySpec(password.toCharArray(),salt,ITERATIONS,256);
            byte[] value=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return "pbkdf2$"+ITERATIONS+"$"+Base64.getEncoder().encodeToString(salt)+"$"+Base64.getEncoder().encodeToString(value);
        }catch(Exception e){throw new RuntimeException(e);}
    }
    public static boolean verify(String password,String encoded){
        if(encoded==null)return false;
        if(!encoded.startsWith("pbkdf2$"))return MessageDigest.isEqual(hash(password).getBytes(StandardCharsets.UTF_8),encoded.getBytes(StandardCharsets.UTF_8));
        try{
            String[] p=encoded.split("\\$");
            byte[] salt=Base64.getDecoder().decode(p[2]);
            byte[] expected=Base64.getDecoder().decode(p[3]);
            byte[] actual=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec(password.toCharArray(),salt,Integer.parseInt(p[1]),expected.length*8)).getEncoded();
            return MessageDigest.isEqual(expected,actual);
        }catch(Exception e){return false;}
    }
    public static boolean isStrong(String password){
        return password!=null&&password.length()>=8&&password.matches(".*[A-Za-z].*")&&password.matches(".*\\d.*");
    }
    public static String randomToken(){byte[] b=new byte[32];RANDOM.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    public static String tokenHash(String token){return hash(token);}
}
