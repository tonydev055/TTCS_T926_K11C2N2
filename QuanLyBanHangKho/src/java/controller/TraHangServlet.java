package controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import service.TraHangService;
import util.XuLyJson;

/** S8-01/S8-02: phiếu trả hàng và giảm trừ công nợ theo hoá đơn gốc; phạm vi và vai trò được kiểm tra trong TraHangService. */
@WebServlet("/api/returns/*")
public final class TraHangServlet extends TaiChinhServlet {
    private final TraHangService service = new TraHangService();

    @Override protected void doGet(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            String[] p = path(q).split("/");
            if (path(q).equals("/")) send(r, service.list(user(q), roles(q)));
            else if (p.length == 2 && p[1].equals("invoices")) send(r, service.invoices(user(q), roles(q), q.getParameter("q")));
            else if (p.length == 3 && p[1].equals("invoices")) send(r, service.invoice(id(p[2]), user(q), roles(q)));
            else if (p.length == 2) send(r, service.detail(id(p[1]), user(q), roles(q)));
            else throw new NoSuchElementException("Không tìm thấy chức năng");
        } catch (Exception e) { fail(r, e); }
    }

    @Override protected void doPost(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            checkWrite(q);
            Map<String, Object> in = XuLyJson.object(body(q));
            String[] p = path(q).split("/");
            long user = user(q);
            List<String> roles = roles(q);
            if (path(q).equals("/")) {
                long id = service.create(user, roles, in);
                created(r, XuLyJson.stringify(service.detail(id, user, roles)));
                return;
            }
            if (p.length != 3) throw new NoSuchElementException("Không tìm thấy chức năng");
            long id = id(p[1]);
            switch (p[2]) {
                case "receive" -> service.receive(id, user, roles, in);
                case "reject" -> service.reject(id, user, roles, in);
                case "cancel" -> service.cancel(id, user, roles, in);
                case "credit" -> service.credit(id, user, roles, in);
                default -> throw new NoSuchElementException("Không tìm thấy chức năng");
            }
            send(r, service.detail(id, user, roles));
        } catch (Exception e) { fail(r, e); }
    }
}
