package controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import service.XacNhanGiaoHangService;
import util.XuLyJson;

/** S6-08: API xác nhận giao thành công/thất bại và xem ảnh chứng từ đã xác thực. */
@WebServlet("/api/delivery-confirmations/*")
@MultipartConfig(fileSizeThreshold = 1_048_576, maxFileSize = 2_097_152, maxRequestSize = 3_145_728)
public final class XacNhanGiaoHangServlet extends NghiepVuKhoServlet {
    private final XacNhanGiaoHangService service = new XacNhanGiaoHangService();

    @Override protected void doGet(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            String p = path(q);
            if (p.equals("/") || p.isBlank()) {
                send(r, service.list(user(q), roles(q)));
                return;
            }
            String[] parts = p.split("/");
            if (parts.length == 3 && parts[2].equals("proof")) {
                long id = id(parts[1]);
                Path file = service.proofPath(id, user(q), roles(q));
                r.setStatus(200);
                r.setContentType("image/png");
                r.setHeader("X-Content-Type-Options", "nosniff");
                r.setHeader("Cache-Control", "private, no-store");
                r.setContentLengthLong(Files.size(file));
                Files.copy(file, r.getOutputStream());
                return;
            }
            error(r, 404, "Không tìm thấy chức năng");
        } catch (Exception e) {
            if (e instanceof IOException && e.getMessage() != null && e.getMessage().contains("tệp quá lớn"))
                error(r, 400, e.getMessage());
            else fail(r, e);
        }
    }

    @Override protected void doPost(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            String p = path(q);
            if (!p.matches("/\\d+")) throw new NoSuchElementException("Không tìm thấy đơn hàng");
            long orderId = id(p.substring(1));
            String outcome = value(q, "outcome");
            String recipient = value(q, "recipientName");
            String deliveredAt = value(q, "deliveredAt");
            String reason = value(q, "failureReason");
            byte[] proof = null;
            try {
                Part part = q.getPart("proofImage");
                if (part != null && part.getSize() > 0) {
                    if (part.getSize() > 2_097_152) throw new IllegalArgumentException("Ảnh chứng từ tối đa 2 MB");
                    try (var in = part.getInputStream()) { proof = in.readAllBytes(); }
                }
            } catch (IllegalStateException | ServletException e) {
                throw new IllegalArgumentException("Ảnh chứng từ quá lớn hoặc biểu mẫu không hợp lệ (tối đa 2 MB)");
            }
            var result = service.confirm(orderId, user(q), roles(q), outcome, recipient, deliveredAt, reason, proof);
            created(r, XuLyJson.stringify(result));
        } catch (IllegalStateException e) {
            error(r, 400, "Ảnh hoặc biểu mẫu quá lớn; ảnh chứng từ tối đa 2 MB");
        } catch (Exception e) {
            fail(r, e);
        }
    }

    private String value(HttpServletRequest q, String name) throws IOException, ServletException {
        Part p = q.getPart(name);
        if (p == null) return "";
        try (var in = p.getInputStream()) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        }
    }
}
