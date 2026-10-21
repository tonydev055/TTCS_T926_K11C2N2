package controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import util.KetNoiDB;
import util.XuLyJson;

/**
 * S8-05: Dashboard điều hành bán hàng và kho.
 * BƯỚC 2: Hoàn thiện 6 thẻ chỉ số với dữ liệu real-time.
 */
@WebServlet("/api/dashboard/*")
public final class DashboardServlet extends NghiepVuKhoServlet {

    @Override
    protected void doGet(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            String p = path(q);
            switch (p) {
                case "/summary" -> send(r, summary());
                case "/daily"   -> send(r, List.of());
                case "/region"  -> send(r, List.of());
                case "/overdue" -> send(r, List.of());
                default -> throw new NoSuchElementException("Không tìm thấy chức năng dashboard");
            }
        } catch (Exception e) { fail(r, e); }
    }

    /** 6 thẻ chỉ số: tất cả trong một connection, thực thi song song bằng CTE tối ưu. */
    private Map<String, Object> summary() throws SQLException {
        String sql =
            "WITH " +
            /* Doanh số hôm nay: tổng payable của đơn đã approved/picking/shipped/delivered/closed tạo hôm nay */
            "  today AS (SELECT COALESCE(SUM(payable),0) AS val FROM orders " +
            "    WHERE status IN ('APPROVED','PICKING','SHIPPED','DELIVERED','CLOSED') " +
            "    AND created_at::date = CURRENT_DATE), " +
            /* Doanh số tháng này: tổng payable tháng hiện tại */
            "  month AS (SELECT COALESCE(SUM(payable),0) AS val FROM orders " +
            "    WHERE status IN ('APPROVED','PICKING','SHIPPED','DELIVERED','CLOSED') " +
            "    AND date_trunc('month', created_at) = date_trunc('month', CURRENT_DATE)), " +
            /* Đơn chờ duyệt: status PENDING_CONFIRM hoặc PENDING_APPROVAL */
            "  pending_approve AS (SELECT COUNT(*) AS val FROM orders " +
            "    WHERE status IN ('PENDING_CONFIRM','PENDING_APPROVAL')), " +
            /* Đơn chờ xuất kho: status APPROVED hoặc PICKING */
            "  pending_ship AS (SELECT COUNT(*) AS val FROM orders WHERE status IN ('APPROVED','PICKING')), " +
            /* Công nợ quá hạn: tổng nợ vượt ngày đến hạn từ receivable_invoice_balances */
            "  overdue AS (SELECT COALESCE(SUM(outstanding),0) AS val " +
            "    FROM receivable_invoice_balances WHERE due_date < CURRENT_DATE AND outstanding > 0), " +
            /* SKU dưới tồn tối thiểu: tham chiếu stock_thresholds và tồn khả dụng (on_hand - reserved) */
            "  low_sku AS (SELECT COUNT(DISTINCT t.product_id) AS val FROM stock_thresholds t " +
            "    JOIN warehouses w ON w.id = t.warehouse_id " +
            "    JOIN products p ON p.id = t.product_id " +
            "    LEFT JOIN inventory i ON i.warehouse_id = w.id AND i.product_id = p.id " +
            "    WHERE w.active AND p.active AND t.minimum_quantity > 0 " +
            "      AND COALESCE(i.on_hand - i.reserved, 0) < t.minimum_quantity) " +
            "SELECT " +
            "  (SELECT val FROM today) AS today_revenue, " +
            "  (SELECT val FROM month) AS month_revenue, " +
            "  (SELECT val FROM pending_approve) AS pending_approve, " +
            "  (SELECT val FROM pending_ship) AS pending_ship, " +
            "  (SELECT val FROM overdue) AS overdue_amount, " +
            "  (SELECT val FROM low_sku) AS low_sku_count";

        try (Connection c = KetNoiDB.getConnection();
             PreparedStatement p = c.prepareStatement(sql);
             ResultSet rs = p.executeQuery()) {
            Map<String, Object> out = new LinkedHashMap<>();
            if (rs.next()) {
                out.put("todayRevenue",    rs.getBigDecimal("today_revenue"));
                out.put("monthRevenue",    rs.getBigDecimal("month_revenue"));
                out.put("pendingApprove",  rs.getLong("pending_approve"));
                out.put("pendingShip",     rs.getLong("pending_ship"));
                out.put("overdueAmount",   rs.getBigDecimal("overdue_amount"));
                out.put("lowSkuCount",     rs.getLong("low_sku_count"));
            }
            return out;
        }
    }
}
