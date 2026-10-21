package controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import util.XuLyJson;

/**
 * S8-05: Khởi tạo DashboardServlet điều hành bán hàng và kho.
 * Kế thừa NghiepVuKhoServlet và kiểm tra quyền reports.sales.
 */
@WebServlet("/api/dashboard/*")
public final class DashboardServlet extends NghiepVuKhoServlet {

    @Override
    protected void doGet(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            String p = path(q);
            switch (p) {
                case "/summary" -> send(r, Map.of("message", "Dashboard summary placeholder"));
                case "/daily"   -> send(r, List.of());
                case "/region"  -> send(r, List.of());
                case "/overdue" -> send(r, List.of());
                default -> throw new NoSuchElementException("Không tìm thấy chức năng dashboard");
            }
        } catch (Exception e) { fail(r, e); }
    }
}
