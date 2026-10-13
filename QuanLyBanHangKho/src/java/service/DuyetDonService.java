package service;

import db.DuyetDonDB;
import java.sql.SQLException;
import java.util.*;

/**
 * Duyệt đơn vượt hạn mức hoặc bán dưới giá sàn (S4-05). Chỉ người có quyền orders.approve (kiểm tra ở XacThucFilter
 * qua PhanQuyen.permissionFor). Từ chối và Trả lại sửa bắt buộc nhập ý kiến.
 */
public class DuyetDonService {

    private final DuyetDonDB db = new DuyetDonDB();

    public List<Map<String, Object>> danhSachChoDuyet() throws SQLException {
        return db.pending();
    }

    public Map<String, Object> quyetDinh(long orderId, String rawAction, Object rawComment, long actor) throws SQLException {
        String action = Objects.toString(rawAction, "").toUpperCase(Locale.ROOT);
        if (!DuyetDonDB.ACTIONS.contains(action)) throw new NoSuchElementException("Không tìm thấy chức năng");
        if (rawComment != null && !(rawComment instanceof String)) throw new IllegalArgumentException("Ý kiến không hợp lệ");
        String comment = Objects.toString(rawComment, "").trim();
        if (!action.equals("APPROVE") && comment.isEmpty())
            throw new IllegalArgumentException(action.equals("REJECT")
                ? "Vui lòng nhập ý kiến khi từ chối đơn" : "Vui lòng nhập ý kiến để nhân viên biết cần sửa gì");
        if (comment.length() > 1000) throw new IllegalArgumentException("Ý kiến tối đa 1000 ký tự");
        String status = db.decide(orderId, action, comment, actor);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", orderId);
        out.put("status", status);
        out.put("statusLabel", TrangThaiDonHang.label(status));
        return out;
    }
}
