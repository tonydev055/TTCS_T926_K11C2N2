package controller;

import db.LichSuGiaDB;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.Connection;
import java.util.NoSuchElementException;
import util.*;

@WebServlet("/api/price-history/*")
public class LichSuGiaServlet extends CoSoServlet {

    private final LichSuGiaDB db = new LichSuGiaDB();

    protected void doGet(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try (Connection c = KetNoiDB.getConnection()) {
            String raw = q.getParameter("productId");
            if (raw == null || !raw.matches("[1-9][0-9]*")) throw new IllegalArgumentException(
                "Sản phẩm không hợp lệ"
            );
            ok(r, XuLyJson.stringify(db.findByProduct(c, Long.parseLong(raw))));
        } catch (NoSuchElementException e) {
            error(r, 404, e.getMessage());
        } catch (IllegalArgumentException e) {
            error(r, 400, e.getMessage());
        } catch (Exception e) {
            log("Price history request failed", e);
            error(r, 500, "Không thể tải lịch sử giá");
        }
    }
}
