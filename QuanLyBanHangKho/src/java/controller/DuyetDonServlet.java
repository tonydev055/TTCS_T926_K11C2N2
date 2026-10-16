package controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;
import service.DuyetDonService;
import util.XuLyJson;

/**
 * Duyệt đơn hàng (S4-05). Cần quyền orders.approve.
 *   GET  /api/order-approvals                      đơn chờ duyệt kèm lý do lúc gửi và mức vi phạm hiện tại
 *   POST /api/order-approvals/{id}/approve {comment?}   duyệt, giữ chỗ tồn
 *   POST /api/order-approvals/{id}/reject  {comment}    từ chối (bắt buộc ý kiến)
 *   POST /api/order-approvals/{id}/return  {comment}    trả lại nhân viên sửa (bắt buộc ý kiến)
 */
@WebServlet("/api/order-approvals/*")
public class DuyetDonServlet extends DonHangServlet {

    private final DuyetDonService approvals = new DuyetDonService();

    @Override
    protected void doGet(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            if (!path(q).equals("/")) throw new NoSuchElementException("Không tìm thấy chức năng");
            ok(r, XuLyJson.stringify(approvals.danhSachChoDuyet()));
        } catch (Exception e) {
            fail(r, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            String[] parts = path(q).split("/");
            if (parts.length != 3) throw new NoSuchElementException("Không tìm thấy chức năng");
            Map<String, Object> in = XuLyJson.object(body(q));
            ok(r, XuLyJson.stringify(approvals.quyetDinh(parseId(parts[1]), parts[2], in.get("comment"), userId(q))));
        } catch (Exception e) {
            fail(r, e);
        }
    }
}
