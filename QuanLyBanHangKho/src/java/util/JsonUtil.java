package util;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
public final class JsonUtil {
    private JsonUtil(){}
    public static void send(HttpServletResponse r,int status,String json)throws IOException{
        r.setStatus(status); r.setCharacterEncoding("UTF-8"); r.setContentType("application/json");
        r.getWriter().write(json);
    }
    public static String esc(String s) {
    String value=Json.stringify(s==null?"":s);return value.substring(1,value.length()-1);
}
}
