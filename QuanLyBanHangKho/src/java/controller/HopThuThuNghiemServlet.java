package controller;

import db.CoSoDB;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;

@WebServlet("/api/dev-mailbox/*")
public class HopThuThuNghiemServlet extends CoSoServlet {

    private final CoSoDB db = new CoSoDB();

    protected void service(HttpServletRequest q, HttpServletResponse r) throws jakarta.servlet.ServletException, IOException {
        if (!service.ThuDienTuService.developmentMailbox()) {
            error(r, 404, "Hộp thư thử nghiệm đã tắt");
            return;
        }
        super.service(q, r);
    }

    protected void doGet(HttpServletRequest q, HttpServletResponse r) throws IOException {
        if ("/accounts".equals(q.getPathInfo())) {
            try {
                ok(
                    r,
                    jsonRows(
                        db.query(
                            "SELECT lower(email) AS email FROM users UNION SELECT lower(recipient) AS email FROM dev_mailbox_messages ORDER BY email"
                        )
                    )
                );
            } catch (Exception e) {
                error(r, 500, "Không thể tải danh sách địa chỉ hộp thư");
            }
            return;
        }
        String email = q.getParameter("email");
        if (email == null || email.isBlank()) {
            error(r, 400, "Vui lòng chọn địa chỉ hộp thư");
            return;
        }
        try {
            ok(
                r,
                jsonRows(
                    db.query(
                        "SELECT id,recipient,subject,body,action_url,created_at FROM dev_mailbox_messages WHERE lower(recipient)=lower(?) ORDER BY created_at DESC LIMIT 50",
                        email.trim()
                    )
                )
            );
        } catch (Exception e) {
            error(r, 500, "Không thể tải hộp thư thử nghiệm");
        }
    }

    protected void doDelete(HttpServletRequest q, HttpServletResponse r) throws IOException {
        String email = q.getParameter("email");
        if (email == null || email.isBlank()) {
            error(r, 400, "Vui lòng chọn địa chỉ hộp thư");
            return;
        }
        try {
            db.update(
                "DELETE FROM dev_mailbox_messages WHERE lower(recipient)=lower(?)",
                email.trim()
            );
            ok(r, "{\"message\":\"Đã dọn hộp thư\"}");
        } catch (Exception e) {
            error(r, 500, "Không thể dọn hộp thư thử nghiệm");
        }
    }
}
