package controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import service.DieuChinhTonService;
import util.XuLyJson;

/** S8-03: phiếu điều chỉnh tồn kho có phê duyệt; phạm vi kho và người duyệt kiểm tra trong DieuChinhTonService. */
@WebServlet("/api/stock-adjustments/*")
public final class DieuChinhTonServlet extends TaiChinhServlet {
    private final DieuChinhTonService service = new DieuChinhTonService();

    @Override protected void doGet(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            String[] p = path(q).split("/");
            if (path(q).equals("/")) send(r, service.list(user(q), roles(q)));
            else if (p.length == 2 && p[1].equals("bins")) send(r, service.bins(id(q.getParameter("warehouseId")), q.getParameter("q"), user(q), roles(q)));
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
                case "approve" -> service.approve(id, user, roles, in);
                case "reject" -> service.reject(id, user, roles, in);
                case "cancel" -> service.cancel(id, user, roles, in);
                default -> throw new NoSuchElementException("Không tìm thấy chức năng");
            }
            send(r, service.detail(id, user, roles));
        } catch (Exception e) { fail(r, e); }
    }
}
