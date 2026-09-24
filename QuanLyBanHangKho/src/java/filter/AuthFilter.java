package filter;
import jakarta.servlet.*; import jakarta.servlet.annotation.WebFilter; import jakarta.servlet.http.*; import java.io.*;
@WebFilter("/api/*")
public class AuthFilter implements Filter{
 public void doFilter(ServletRequest a,ServletResponse b,FilterChain c)throws IOException,ServletException{
   HttpServletRequest q=(HttpServletRequest)a; HttpServletResponse r=(HttpServletResponse)b;
   String u=q.getRequestURI();
   if(u.endsWith("/api/health")||u.contains("/api/auth/")){c.doFilter(a,b);return;}
   HttpSession s=q.getSession(false);
   if(s==null||s.getAttribute("userId")==null){r.setStatus(401);r.setContentType("application/json");r.getWriter().write("{\"message\":\"Chưa đăng nhập\"}");return;}
   c.doFilter(a,b);
 }
}
