package filter;

import db.NguoiDungDB;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;
import security.PhanQuyen;
import util.PhanHoiJsonUtil;

@WebFilter("/api/*")
public class XacThucFilter implements Filter {

    private final NguoiDungDB users = new NguoiDungDB();

    public void doFilter(ServletRequest a, ServletResponse b, FilterChain c)
        throws IOException, ServletException {
        HttpServletRequest q = (HttpServletRequest) a;
        HttpServletResponse r = (HttpServletResponse) b;
        String uri = q.getRequestURI();
        String endpoint = q.getServletPath() + Objects.toString(q.getPathInfo(), "");
        if (
            Set.of("/api/health", "/api/auth/login", "/api/auth/register",
                "/api/auth/forgot-password", "/api/auth/reset-password").contains(endpoint) ||
            q.getServletPath().equals("/api/dev-mailbox")
        ) {
            c.doFilter(a, b);
            return;
        }
        HttpSession s = q.getSession(false);
        if (s == null || s.getAttribute("userId") == null) {
            PhanHoiJsonUtil.send(
                r,
                401,
                "{\"message\":\"Phiên đăng nhập đã hết hạn\",\"action\":\"login\"}"
            );
            return;
        }
        try {
            long id = ((Number) s.getAttribute("userId")).longValue();
            int live = users.sessionVersion(id),
                session = ((Number) s.getAttribute("sessionVersion")).intValue();
            if (live < 0 || live != session) {
                s.invalidate();
                PhanHoiJsonUtil.send(r, 401, "{\"message\":\"Phiên đã bị thu hồi\",\"action\":\"login\"}");
                return;
            }
            if (Boolean.TRUE.equals(s.getAttribute("requiresPasswordChange")) &&
                !Set.of("/api/auth/me", "/api/auth/change-password", "/api/auth/logout").contains(endpoint)) {
                PhanHoiJsonUtil.send(r, 403,
                    "{\"message\":\"Vui lòng đổi mật khẩu tạm trước khi sử dụng chức năng này\",\"action\":\"change-password\"}");
                return;
            }
            if (q.getServletPath().equals("/api/auth")) {
                c.doFilter(a, b);
                return;
            }
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) s.getAttribute("roles");
            String permission = PhanQuyen.permissionFor(uri, q.getMethod());
            if (permission == null || !PhanQuyen.allows(roles, permission)) {
                PhanHoiJsonUtil.send(
                    r,
                    403,
                    "{\"message\":\"Bạn không có quyền thực hiện thao tác này\",\"action\":\"home\"}"
                );
                return;
            }
            c.doFilter(a, b);
        } catch (Exception e) {
            PhanHoiJsonUtil.send(r, 503, "{\"message\":\"Không thể kiểm tra quyền truy cập\"}");
        }
    }
}
