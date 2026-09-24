package controller;
import jakarta.servlet.annotation.WebServlet; import jakarta.servlet.http.*; import util.*; import java.io.*; import java.sql.*;
@WebServlet("/api/auth/*")
public class AuthServlet extends BaseServlet {
 protected void doPost(HttpServletRequest q,HttpServletResponse r)throws IOException{
   String path=q.getPathInfo(); String b=body(q);
   if("/login".equals(path)){ login(b,q,r); return; }
   error(r,404,"Endpoint không tồn tại");
 }
 private String val(String b,String key){
   var m=java.util.regex.Pattern.compile("\\\""+key+"\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(b);
   return m.find()?m.group(1):"";
 }
 private void login(String b,HttpServletRequest q,HttpServletResponse r)throws IOException{
   String email=val(b,"email"), pass=val(b,"password");
   try(Connection c=DBConnection.getConnection();PreparedStatement p=c.prepareStatement("SELECT id,email,full_name,role,password_hash,active FROM users WHERE lower(email)=lower(?)")){
     p.setString(1,email);try(ResultSet x=p.executeQuery()){
       if(!x.next()||!x.getBoolean("active")||!PasswordUtil.hash(pass).equals(x.getString("password_hash"))){error(r,401,"Email hoặc mật khẩu không đúng");return;}
       HttpSession s=q.getSession(true);s.setAttribute("userId",x.getLong("id"));s.setAttribute("role",x.getString("role"));
       ok(r,"{\"id\":"+x.getLong("id")+",\"email\":\""+JsonUtil.esc(x.getString("email"))+"\",\"fullName\":\""+JsonUtil.esc(x.getString("full_name"))+"\",\"role\":\""+x.getString("role")+"\"}");
     }
   }catch(Exception e){error(r,500,e.getMessage());}
 }
}
