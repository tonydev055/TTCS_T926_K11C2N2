package util;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
public final class PasswordUtil {
    private PasswordUtil(){}
    public static String hash(String s){
        try{MessageDigest md=MessageDigest.getInstance("SHA-256");return Base64.getEncoder().encodeToString(md.digest(s.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new RuntimeException(e);}
    }
}
