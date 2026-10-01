package controller;
import dao.UserDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import security.PermissionMatrix;
import service.EmailService;
import util.*;
import java.io.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
@WebServlet("/api/auth/*")
public class AuthServlet extends BaseServlet {
 private final UserDAO users=new UserDAO();
 protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException{
   if("/me".equals(q.getPathInfo())){HttpSession s=q.getSession(false);if(s==null||s.getAttribute("userId")==null){error(r,401,"Phiên đăng nhập đã hết hạn");return;}writeUser(r,s);return;}error(r,404,"Endpoint không tồn tại");
 }
 protected void doPost(HttpServletRequest q,HttpServletResponse r)throws IOException{
   String path=q.getPathInfo(),b=body(q);
   try{
     if("/login".equals(path)){login(b,q,r);return;}
     if("/logout".equals(path)){HttpSession s=q.getSession(false);if(s!=null)s.invalidate();ok(r,"{\"message\":\"Đã đăng xuất\"}");return;}
     if("/register".equals(path)){register(b,r);return;}
     if("/forgot-password".equals(path)){forgot(b,r);return;}
     if("/reset-password".equals(path)){reset(b,r);return;}
     if("/change-password".equals(path)){change(b,q,r);return;}
     error(r,404,"Endpoint không tồn tại");
   }catch(Exception e){String message=e.getMessage()==null?"Không thể xử lý yêu cầu":e.getMessage();error(r,message.contains("duplicate key")?409:400,message);}
 }
 private void login(String b,HttpServletRequest q,HttpServletResponse r)throws Exception{
   String identity=RequestJson.text(b,"email"),pass=RequestJson.text(b,"password");
   Map<String,Object> u=users.findForLogin(identity);
   if(u==null){genericLoginError(r);return;}
   long id=((Number)u.get("id")).longValue();
   Timestamp locked=(Timestamp)u.get("locked_until");
   if(!Boolean.TRUE.equals(u.get("active"))){error(r,423,"Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên");return;}
   if(locked!=null&&locked.toInstant().isAfter(Instant.now())){
     long seconds=Math.max(1,locked.toInstant().getEpochSecond()-Instant.now().getEpochSecond());
     long minutes=Math.max(1,(seconds+59)/60);
     error(r,423,"Tài khoản đang tạm khóa do đăng nhập sai quá 5 lần. Vui lòng thử lại sau "+minutes+" phút");return;
   }
   String stored=String.valueOf(u.get("password_hash"));
   if(!PasswordUtil.verify(pass,stored)){users.loginFailed(id);genericLoginError(r);return;}
   String upgraded=stored.startsWith("pbkdf2$")?null:PasswordUtil.encode(pass);users.loginSucceeded(id,upgraded);
   List<String> roles=users.roles(id);if(roles.isEmpty()){genericLoginError(r);return;}
   HttpSession s=q.getSession(true);s.setMaxInactiveInterval(30*60);s.setAttribute("userId",id);s.setAttribute("sessionVersion",((Number)u.get("session_version")).intValue());s.setAttribute("roles",roles);s.setAttribute("role",roles.get(0));s.setAttribute("email",u.get("email"));s.setAttribute("fullName",u.get("full_name"));s.setAttribute("territory",u.get("territory"));s.setAttribute("warehouses",users.warehouses(id));s.setAttribute("requiresPasswordChange",u.get("requires_password_change"));writeUser(r,s);
 }
 private void genericLoginError(HttpServletResponse r)throws IOException{error(r,401,"Email hoặc mật khẩu không đúng");}
 private void register(String b,HttpServletResponse r)throws Exception{
   String fullName=RequestJson.text(b,"fullName"),email=RequestJson.text(b,"email"),username=RequestJson.text(b,"username"),password=RequestJson.text(b,"password");
   if(fullName.isBlank()||email.isBlank()||username.isBlank()||!PasswordUtil.isStrong(password))throw new IllegalArgumentException("Thông tin chưa hợp lệ; mật khẩu cần ít nhất 8 ký tự, có chữ và số");
   long id=users.create(username,email,fullName,RequestJson.text(b,"phone"),PasswordUtil.encode(password),"Tự đăng ký",List.of("CUSTOMER"),List.of());
   created(r,"{\"id\":"+id+",\"message\":\"Tạo tài khoản thành công. Bạn có thể đăng nhập ngay.\"}");
 }
 private void forgot(String b,HttpServletResponse r)throws Exception{
   Map<String,Object> u=users.findForLogin(RequestJson.text(b,"email"));
   if(u!=null&&Boolean.TRUE.equals(u.get("active"))){String token=PasswordUtil.randomToken();users.createResetToken(((Number)u.get("id")).longValue(),PasswordUtil.tokenHash(token));EmailService.passwordReset(String.valueOf(u.get("email")),token);}
   ok(r,"{\"message\":\"Nếu tài khoản tồn tại, hướng dẫn đặt lại mật khẩu đã được gửi.\"}");
 }
 private void reset(String b,HttpServletResponse r)throws Exception{
   String token=RequestJson.text(b,"token"),password=RequestJson.text(b,"password");if(!PasswordUtil.isStrong(password))throw new IllegalArgumentException("Mật khẩu mới cần ít nhất 8 ký tự, có chữ và số");
   Long id=users.consumeResetToken(PasswordUtil.tokenHash(token));if(id==null){error(r,400,"Liên kết không hợp lệ hoặc đã hết hạn");return;}users.changePassword(id,PasswordUtil.encode(password));ok(r,"{\"message\":\"Đặt lại mật khẩu thành công\"}");
 }
 private void change(String b,HttpServletRequest q,HttpServletResponse r)throws Exception{
   HttpSession s=q.getSession(false);if(s==null||s.getAttribute("userId")==null){error(r,401,"Chưa đăng nhập");return;}
   String current=RequestJson.text(b,"currentPassword"),password=RequestJson.text(b,"newPassword");if(!PasswordUtil.isStrong(password))throw new IllegalArgumentException("Mật khẩu mới cần ít nhất 8 ký tự, có chữ và số");
   Map<String,Object> u=users.findForLogin(String.valueOf(s.getAttribute("email")));if(u==null||!PasswordUtil.verify(current,String.valueOf(u.get("password_hash")))){error(r,400,"Mật khẩu hiện tại không đúng");return;}
   long id=((Number)s.getAttribute("userId")).longValue();users.changePassword(id,PasswordUtil.encode(password));int version=users.sessionVersion(id);s.setAttribute("sessionVersion",version);s.setAttribute("requiresPasswordChange",false);ok(r,"{\"message\":\"Đã đổi mật khẩu và thu hồi các phiên đăng nhập khác\"}");
 }
 private void writeUser(HttpServletResponse r,HttpSession s)throws IOException{
   @SuppressWarnings("unchecked") List<String> roles=(List<String>)s.getAttribute("roles");@SuppressWarnings("unchecked") List<String> warehouses=(List<String>)s.getAttribute("warehouses");
   String role=roles.get(0);StringBuilder rb=new StringBuilder("[");for(int i=0;i<roles.size();i++){if(i>0)rb.append(',');rb.append('"').append(JsonUtil.esc(roles.get(i))).append('"');}rb.append(']');
   StringBuilder wb=new StringBuilder("[");for(int i=0;i<warehouses.size();i++){if(i>0)wb.append(',');wb.append('"').append(JsonUtil.esc(warehouses.get(i))).append('"');}wb.append(']');
   StringBuilder pb=new StringBuilder("[");int i=0;for(String p:PermissionMatrix.permissions(roles)){if(i++>0)pb.append(',');pb.append('"').append(JsonUtil.esc(p)).append('"');}pb.append(']');
   ok(r,"{\"id\":"+s.getAttribute("userId")+",\"email\":\""+JsonUtil.esc(String.valueOf(s.getAttribute("email")))+"\",\"fullName\":\""+JsonUtil.esc(String.valueOf(s.getAttribute("fullName")))+"\",\"role\":\""+role+"\",\"roles\":"+rb+",\"warehouses\":"+wb+",\"permissions\":"+pb+",\"requiresPasswordChange\":"+Boolean.TRUE.equals(s.getAttribute("requiresPasswordChange"))+"}");
 }
}
