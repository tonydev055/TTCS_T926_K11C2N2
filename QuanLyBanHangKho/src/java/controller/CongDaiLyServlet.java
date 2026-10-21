package controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;
import service.CongDaiLyService;
import util.XuLyJson;

/**
 * Cổng đặt hàng dành cho đại lý (S4-10). Cần quyền orders.self; đại lý lấy từ tài khoản đăng nhập.
 *   GET  /api/portal/me                    thông tin đại lý, điểm giao, bảng giá, công nợ còn lại, có đặt được không
 *   GET  /api/portal/products?q=           sản phẩm và giá thuộc nhóm khách hàng của đại lý
 *   POST /api/portal/quote {lines}         tính tiền giỏ hàng, công nợ còn lại sau đơn, lỗi tồn (không lưu)
 *   POST /api/portal/orders {deliveryPointId, desiredDate, note, lines}   gửi đơn → Chờ nhân viên xác nhận
 *   GET  /api/portal/orders                đơn của đại lý
 *   GET  /api/portal/orders/{id}           chi tiết đơn
 *   POST /api/portal/orders/{id}/cancel {reason}   huỷ đơn khi nhân viên chưa xác nhận
 */
@WebServlet("/api/portal/*")
public class CongDaiLyServlet extends DonHangServlet {

    private final CongDaiLyService portal = new CongDaiLyService();

    @Override
    protected void doGet(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            String p = path(q);
            long uid = userId(q);
            if (p.equals("/me")) ok(r, XuLyJson.stringify(portal.thongTin(uid)));
            else if (p.equals("/products")) ok(r, XuLyJson.stringify(portal.sanPham(uid, q.getParameter("q"))));
            else if (p.equals("/orders")) ok(r, XuLyJson.stringify(portal.donCuaToi(uid)));
            else if (p.matches("/orders/\\d+")) ok(r, XuLyJson.stringify(portal.chiTiet(uid, parseId(p.substring(8)))));
            else throw new NoSuchElementException("Không tìm thấy chức năng");
        } catch (Exception e) {
            fail(r, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            String p = path(q);
            long uid = userId(q);
            Map<String, Object> in = XuLyJson.object(body(q));
            if (p.equals("/quote")) ok(r, XuLyJson.stringify(portal.tinhTien(uid, in.get("lines"))));
            else if (p.equals("/orders")) created(r, XuLyJson.stringify(portal.guiDon(uid, in)));
            else if(p.matches("/orders/\\d+/reorder"))
                ok(r,XuLyJson.stringify(portal.datLai(uid,parseId(p.split("/")[2]),in.get("lineIds"))));
            else if (p.matches("/orders/\\d+/cancel"))
                ok(r, XuLyJson.stringify(portal.huy(uid, parseId(p.split("/")[2]), in.get("reason"))));
            else throw new NoSuchElementException("Không tìm thấy chức năng");
        } catch (Exception e) {
            fail(r, e);
        }
    }
}
