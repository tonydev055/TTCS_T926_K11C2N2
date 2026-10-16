package controller;

import jakarta.servlet.http.*;
import java.util.*;

/** Kiểm tra request ghi tài chính cùng nguồn và JSON trước khi gọi service. */
public abstract class TaiChinhServlet extends NghiepVuKhoServlet {
    protected void checkWrite(HttpServletRequest q){
        if(!Objects.toString(q.getContentType(),"").toLowerCase(Locale.ROOT).startsWith("application/json"))throw new IllegalArgumentException("Yêu cầu phải là JSON");
        if("cross-site".equals(q.getHeader("Sec-Fetch-Site")))throw new SecurityException("Nguồn yêu cầu không hợp lệ");
        String origin=q.getHeader("Origin");
        if(origin!=null){java.net.URI o=java.net.URI.create(origin);int port=o.getPort()<0?("https".equals(o.getScheme())?443:80):o.getPort();if(!q.getScheme().equals(o.getScheme())||!q.getServerName().equalsIgnoreCase(o.getHost())||q.getServerPort()!=port)throw new SecurityException("Nguồn yêu cầu không hợp lệ");}
    }
}
