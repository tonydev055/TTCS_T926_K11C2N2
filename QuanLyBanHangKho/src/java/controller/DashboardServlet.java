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
 *
 * GET /api/dashboard/summary — 6 thẻ chỉ số tổng hợp (1 CTE query tối ưu)
 * GET /api/dashboard/daily   — doanh số theo ngày trong tháng hiện tại
 * GET /api/dashboard/region  — doanh số theo khu vực trong tháng hiện tại
 * GET /api/dashboard/overdue — danh sách đại lý quá hạn công nợ (sắp xếp giảm dần)
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

    /** Kiểm tra xem view receivable_invoice_balances (Sprint 7) có tồn tại trong database hay không. */
    private static boolean hasInvoiceBalances(Connection c) {
        try (PreparedStatement p = c.prepareStatement("SELECT to_regclass('public.receivable_invoice_balances')");
             ResultSet rs = p.executeQuery()) {
            return rs.next() && rs.getObject(1) != null;
        } catch (SQLException e) {
            return false;
        }
    }

    /** 6 thẻ chỉ số: thực thi trong 1 kết nối duy nhất với CTE tối ưu để < 2 giây. */
    private Map<String, Object> summary() throws SQLException {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("todayRevenue",   BigDecimal.ZERO);
        out.put("monthRevenue",   BigDecimal.ZERO);
        out.put("pendingApprove", 0L);
        out.put("pendingShip",    0L);
        out.put("overdueAmount",  BigDecimal.ZERO);
        out.put("lowSkuCount",    0L);

        try (Connection c = KetNoiDB.getConnection()) {
            boolean useInvoice = hasInvoiceBalances(c);
            String overdueCte = useInvoice
                ? "  overdue AS (SELECT COALESCE(SUM(outstanding),0) AS val FROM receivable_invoice_balances WHERE due_date < CURRENT_DATE AND outstanding > 0), "
                : "  overdue AS (SELECT COALESCE(SUM(overdue_amount),0) AS val FROM customer_credit_status WHERE overdue_amount > 0), ";

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
                /* Công nợ quá hạn */
                overdueCte +
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

            try (PreparedStatement p = c.prepareStatement(sql);
                 ResultSet rs = p.executeQuery()) {
                if (rs.next()) {
                    BigDecimal todayRev = rs.getBigDecimal("today_revenue");
                    BigDecimal monthRev = rs.getBigDecimal("month_revenue");
                    BigDecimal overdueAmt = rs.getBigDecimal("overdue_amount");
                    out.put("todayRevenue",   todayRev != null ? todayRev : BigDecimal.ZERO);
                    out.put("monthRevenue",   monthRev != null ? monthRev : BigDecimal.ZERO);
                    out.put("pendingApprove", rs.getLong("pending_approve"));
                    out.put("pendingShip",    rs.getLong("pending_ship"));
                    out.put("overdueAmount",  overdueAmt != null ? overdueAmt : BigDecimal.ZERO);
                    out.put("lowSkuCount",    rs.getLong("low_sku_count"));
                }
            }
        }
        return out;
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
                BigDecimal rev = rs.getBigDecimal("revenue");
                row.put("revenue", rev != null ? rev : BigDecimal.ZERO);
                out.add(row);
            }
            return out;
        }
    }

    /** Doanh số theo khu vực trong tháng hiện tại. */
    private List<Map<String, Object>> region() throws SQLException {
        String sql =
            "SELECT COALESCE(NULLIF(TRIM(k.region), ''), 'Khac') AS region, " +
            "       COALESCE(SUM(o.payable),0) AS revenue " +
            "FROM orders o " +
            "LEFT JOIN customers k ON k.id = o.agent_id " +
            "WHERE o.status IN ('APPROVED','PICKING','SHIPPED','DELIVERED','CLOSED') " +
            "  AND date_trunc('month', o.created_at) = date_trunc('month', CURRENT_DATE) " +
            "GROUP BY COALESCE(NULLIF(TRIM(k.region), ''), 'Khac') " +
            "ORDER BY revenue DESC";
        try (Connection c = KetNoiDB.getConnection();
             PreparedStatement p = c.prepareStatement(sql);
             ResultSet rs = p.executeQuery()) {
            List<Map<String, Object>> out = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                String reg = rs.getString("region");
                row.put("region",  "Khac".equals(reg) ? "Khác" : reg);
                BigDecimal rev = rs.getBigDecimal("revenue");
                row.put("revenue", rev != null ? rev : BigDecimal.ZERO);
                out.add(row);
            }
            return out;
        }
    }

    /** Danh sách đại lý quá hạn công nợ, sắp xếp giảm dần theo số tiền còn nợ quá hạn. */
    private List<Map<String, Object>> overdue() throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) {
            boolean useInvoice = hasInvoiceBalances(c);
            String sql = useInvoice
                ? "SELECT k.code, k.name, COALESCE(NULLIF(TRIM(k.region),''),'Khác') AS region, " +
                  "       COALESCE(SUM(b.outstanding),0) AS overdue_amount, " +
                  "       MAX(b.due_date)::text AS latest_due, " +
                  "       (CURRENT_DATE - MIN(b.due_date))::int AS max_days_late " +
                  "FROM receivable_invoice_balances b " +
                  "JOIN customers k ON k.id = b.customer_id " +
                  "WHERE b.due_date < CURRENT_DATE AND b.outstanding > 0 " +
                  "GROUP BY k.id, k.code, k.name, k.region " +
                  "ORDER BY overdue_amount DESC " +
                  "LIMIT 50"
                : "SELECT c.code, c.name, COALESCE(NULLIF(TRIM(c.region),''),'Khác') AS region, " +
                  "       s.overdue_amount, " +
                  "       TO_CHAR(CURRENT_DATE - (s.max_overdue_days * INTERVAL '1 day'), 'YYYY-MM-DD') AS latest_due, " +
                  "       s.max_overdue_days AS max_days_late " +
                  "FROM customer_credit_status s " +
                  "JOIN customers c ON c.id = s.customer_id " +
                  "WHERE s.overdue_amount > 0 " +
                  "ORDER BY s.overdue_amount DESC, s.max_overdue_days DESC " +
                  "LIMIT 50";

            try (PreparedStatement p = c.prepareStatement(sql);
                 ResultSet rs = p.executeQuery()) {
                List<Map<String, Object>> out = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("code",          rs.getString("code"));
                    row.put("name",          rs.getString("name"));
                    String reg = rs.getString("region");
                    row.put("region",        "Khac".equals(reg) ? "Khác" : reg);
                    BigDecimal ovd = rs.getBigDecimal("overdue_amount");
                    row.put("overdueAmount", ovd != null ? ovd : BigDecimal.ZERO);
                    row.put("latestDue",     rs.getString("latest_due"));
                    row.put("maxDaysLate",   rs.getInt("max_days_late"));
                    out.add(row);
                }
                return out;
            }
        }
    }
}
