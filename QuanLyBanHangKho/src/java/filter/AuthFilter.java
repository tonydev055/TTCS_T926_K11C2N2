package filter;
import dao.UserDAO;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import security.PermissionMatrix;
import util.JsonUtil;
import java.io.*;
import java.util.*;
@WebFilter("/api/*")
public class AuthFilter implements Filter{
 private final UserDAO users=new UserDAO();
 public void doFilter(ServletRequest a,ServletResponse b,FilterChain c)throws IOException,ServletException{
   HttpServletRequest q=(HttpServletRequest)a;HttpServletResponse r=(HttpServletResponse)b;String uri=q.getRequestURI();
   if(uri.endsWith("/api/health")||uri.contains("/api/dev-mailbox")||uri.contains("/api/auth/login")||uri.contains("/api/auth/register")||uri.contains("/api/auth/forgot-password")||uri.contains("/api/auth/reset-password")){c.doFilter(a,b);return;}
   HttpSession s=q.getSession(false);if(s==null||s.getAttribute("userId")==null){JsonUtil.send(r,401,"{\"message\":\"Phiên đăng nhập đã hết hạn\",\"action\":\"login\"}");return;}
   try{
     long id=((Number)s.getAttribute("userId")).longValue();int live=users.sessionVersion(id),session=((Number)s.getAttribute("sessionVersion")).intValue();
     if(live<0||live!=session){s.invalidate();JsonUtil.send(r,401,"{\"message\":\"Phiên đã bị thu hồi\",\"action\":\"login\"}");return;}
     if(uri.contains("/api/auth/")){c.doFilter(a,b);return;}
     @SuppressWarnings("unchecked") List<String> roles=(List<String>)s.getAttribute("roles");String permission=PermissionMatrix.permissionFor(uri,q.getMethod());
     if(permission==null||!PermissionMatrix.allows(roles,permission)){JsonUtil.send(r,403,"{\"message\":\"Bạn không có quyền thực hiện thao tác này\",\"action\":\"home\"}");return;}
     c.doFilter(a,b);
   }catch(Exception e){JsonUtil.send(r,503,"{\"message\":\"Không thể kiểm tra quyền truy cập\"}");}
 }
}
