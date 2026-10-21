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
 * BƯỚC 5: Tối ưu hiệu năng truy vấn DB dưới 2 giây và hoàn thiện bảo mật.
 *
 * GET /api/dashboard/summary — 6 thẻ chỉ số tổng hợp (1 CTE query tối ưu)
 * GET /api/dashboard/daily   — doanh số theo ngày trong tháng hiện tại
 * GET /api/dashboard/region  — doanh số theo khu vực trong tháng hiện tại
 * GET /api/dashboard/overdue — đại lý quá hạn công nợ, sắp xếp giảm dần theo số tiền
 *
 * Quyền: reports.sales (quản lý kinh doanh). Toàn bộ dữ liệu lấy từ PostgreSQL thực tế.
 */
@WebServlet("/api/dashboard/*")
public final class DashboardServlet extends NghiepVuKhoServlet {

    @Override
    protected void doGet(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            String p = path(q);
            switch (p) {
                case "/summary" -> send(r, summary());
                case "/daily"   -> send(r, daily());
                case "/region"  -> send(r, region());
                case "/overdue" -> send(r, overdue());
                default -> throw new NoSuchElementException("Không tìm thấy chức năng dashboard");
            }
        } catch (Exception e) { fail(r, e); }
    }

    /** 6 thẻ chỉ số: thực thi trong 1 kết nối duy nhất với CTE tối ưu để < 2 giây. */
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

    /** Doanh số theo ngày trong tháng hiện tại. */
    private List<Map<String, Object>> daily() throws SQLException {
        String sql =
            "SELECT d::date AS day, COALESCE(SUM(o.payable),0) AS revenue " +
            "FROM generate_series(date_trunc('month',CURRENT_DATE), CURRENT_DATE, '1 day') AS d " +
            "LEFT JOIN orders o ON o.created_at::date = d::date " +
            "  AND o.status IN ('APPROVED','PICKING','SHIPPED','DELIVERED','CLOSED') " +
            "GROUP BY d ORDER BY d";
        try (Connection c = KetNoiDB.getConnection();
             PreparedStatement p = c.prepareStatement(sql);
             ResultSet rs = p.executeQuery()) {
            List<Map<String, Object>> out = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("day",     rs.getString("day"));
                row.put("revenue", rs.getBigDecimal("revenue"));
                out.add(row);
            }
            return out;
        }
    }

    /** Doanh số theo khu vực trong tháng hiện tại. */
    private List<Map<String, Object>> region() throws SQLException {
        String sql =
            "SELECT COALESCE(k.region, 'Không xác định') AS region, " +
            "       COALESCE(SUM(o.payable),0) AS revenue " +
            "FROM orders o " +
            "LEFT JOIN customers k ON k.id = o.agent_id " +
            "WHERE o.status IN ('APPROVED','PICKING','SHIPPED','DELIVERED','CLOSED') " +
            "  AND date_trunc('month', o.created_at) = date_trunc('month', CURRENT_DATE) " +
            "GROUP BY COALESCE(k.region,'Không xác định') " +
            "ORDER BY revenue DESC";
        try (Connection c = KetNoiDB.getConnection();
             PreparedStatement p = c.prepareStatement(sql);
             ResultSet rs = p.executeQuery()) {
            List<Map<String, Object>> out = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("region",  rs.getString("region"));
                row.put("revenue", rs.getBigDecimal("revenue"));
                out.add(row);
            }
            return out;
        }
    }

    /** Danh sách đại lý quá hạn công nợ, sắp xếp giảm dần theo số tiền còn nợ quá hạn. */
    private List<Map<String, Object>> overdue() throws SQLException {
        String sql =
            "SELECT k.code, k.name, k.region, " +
            "       COALESCE(SUM(b.outstanding),0) AS overdue_amount, " +
            "       MAX(b.due_date)::text AS latest_due, " +
            "       EXTRACT(DAY FROM CURRENT_DATE - MIN(b.due_date))::int AS max_days_late " +
            "FROM receivable_invoice_balances b " +
            "JOIN customers k ON k.id = b.customer_id " +
            "WHERE b.due_date < CURRENT_DATE AND b.outstanding > 0 " +
            "GROUP BY k.id, k.code, k.name, k.region " +
            "ORDER BY overdue_amount DESC " +
            "LIMIT 50";
        try (Connection c = KetNoiDB.getConnection();
             PreparedStatement p = c.prepareStatement(sql);
             ResultSet rs = p.executeQuery()) {
            List<Map<String, Object>> out = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("code",          rs.getString("code"));
                row.put("name",          rs.getString("name"));
                row.put("region",        rs.getString("region"));
                row.put("overdueAmount", rs.getBigDecimal("overdue_amount"));
                row.put("latestDue",     rs.getString("latest_due"));
                row.put("maxDaysLate",   rs.getInt("max_days_late"));
                out.add(row);
            }
            return out;
        }
    }
}
