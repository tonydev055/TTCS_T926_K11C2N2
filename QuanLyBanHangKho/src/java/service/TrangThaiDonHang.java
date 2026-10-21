package service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

/**
 * Vòng đời đơn hàng (S4-06). Bước chuyển hợp lệ được kiểm tra lần cuối bởi trigger order_status_guard
 * trong database; lớp này giữ nhãn hiển thị và tiện ích dùng chung cho service.
 */
public final class TrangThaiDonHang {

    public static final String DRAFT = "DRAFT";
    public static final String PENDING_CONFIRM = "PENDING_CONFIRM";
    public static final String PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String APPROVED = "APPROVED";
    public static final String PICKING = "PICKING";
    public static final String SHIPPED = "SHIPPED";
    public static final String DELIVERED = "DELIVERED";
    public static final String CLOSED = "CLOSED";
    public static final String REJECTED = "REJECTED";
    public static final String CANCELLED = "CANCELLED";

    private static final Map<String, String> LABELS;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(DRAFT, "Nháp");
        m.put(PENDING_CONFIRM, "Chờ nhân viên xác nhận");
        m.put(PENDING_APPROVAL, "Chờ duyệt");
        m.put(APPROVED, "Đã duyệt");
        m.put(PICKING, "Đang soạn hàng");
        m.put(SHIPPED, "Đã xuất");
        m.put(DELIVERED, "Đã giao");
        m.put(CLOSED, "Đóng");
        m.put(REJECTED, "Từ chối");
        m.put(CANCELLED, "Huỷ");
        LABELS = Collections.unmodifiableMap(m);
    }

    /** Trạng thái còn huỷ được: chưa xuất kho và chưa kết thúc. */
    public static final Set<String> CANCELLABLE = Set.of(DRAFT, PENDING_CONFIRM, PENDING_APPROVAL, APPROVED, PICKING);

    /** Trạng thái kết thúc, không còn xử lý. */
    public static final Set<String> FINISHED = Set.of(CLOSED, REJECTED, CANCELLED);

    private TrangThaiDonHang() {}

    public static Map<String, String> labels() {
        return LABELS;
    }

    public static String label(Object status) {
        return LABELS.getOrDefault(Objects.toString(status, ""), Objects.toString(status, ""));
    }

    public static boolean known(String status) {
        return LABELS.containsKey(status);
    }

    /**
     * Đặt người thực hiện và lý do cho trigger ghi lịch sử/nhật ký. Chỉ có hiệu lực trong giao dịch hiện tại,
     * nên phải gọi sau setAutoCommit(false).
     */
    public static void setContext(Connection c, long actor, String reason) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(
                "SELECT set_config('app.actor_id',?,true), set_config('app.status_reason',?,true)")) {
            p.setString(1, String.valueOf(actor));
            p.setString(2, reason == null ? "" : reason);
            p.executeQuery();
        }
    }

    /**
     * Lỗi nghiệp vụ do trigger trả về (SQLSTATE P0001) mang thông báo tiếng Việt dành cho người dùng;
     * chuyển thành IllegalArgumentException để servlet trả 400. Lỗi khác giữ nguyên.
     */
    public static SQLException rethrow(SQLException e) {
        if ("P0001".equals(e.getSQLState())) {
            String message = e.getMessage();
            if (e instanceof org.postgresql.util.PSQLException p && p.getServerErrorMessage() != null)
                message = p.getServerErrorMessage().getMessage();
            throw new IllegalArgumentException(message);
        }
        return e;
    }
}
