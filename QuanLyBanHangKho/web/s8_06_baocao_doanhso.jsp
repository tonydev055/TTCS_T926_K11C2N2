<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.sql.*, java.util.*, java.math.*, java.time.*, java.time.temporal.ChronoUnit, util.KetNoiDB" %>
<%!
    // Helper escape JSON
    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (ch < ' ') {
                        String t = "000" + Integer.toHexString(ch);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(ch);
                    }
            }
        }
        return sb.toString();
    }
%>
<%
    // Phân quyền: Kiểm tra Quản lý kinh doanh (SALES_MANAGER) hoặc ADMIN
    boolean isManager = false;
    HttpSession userSession = request.getSession(false);
    if (userSession != null) {
        Object rObj = userSession.getAttribute("roles");
        if (rObj instanceof Collection) {
            Collection<?> roles = (Collection<?>) rObj;
            for (Object r : roles) {
                String roleStr = String.valueOf(r);
                if ("SALES_MANAGER".equalsIgnoreCase(roleStr) || "ADMIN".equalsIgnoreCase(roleStr) || "DIRECTOR".equalsIgnoreCase(roleStr)) {
                    isManager = true;
                    break;
                }
            }
        }
    }
    String devRole = request.getParameter("role");
    if ("manager".equalsIgnoreCase(devRole) || "admin".equalsIgnoreCase(devRole)) {
        isManager = true;
    }

    String action = request.getParameter("action");
    if ("data".equalsIgnoreCase(action)) {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        String groupBy = request.getParameter("groupBy");
        if (groupBy == null || groupBy.isBlank()) groupBy = "employee";

        LocalDate today = LocalDate.now();
        LocalDate fromDate, toDate;
        try {
            String fromStr = request.getParameter("from");
            fromDate = (fromStr != null && !fromStr.isBlank()) ? LocalDate.parse(fromStr) : today.withDayOfMonth(1);
        } catch (Exception e) {
            fromDate = today.withDayOfMonth(1);
        }
        try {
            String toStr = request.getParameter("to");
            toDate = (toStr != null && !toStr.isBlank()) ? LocalDate.parse(toStr) : today;
        } catch (Exception e) {
            toDate = today;
        }
        if (fromDate.isAfter(toDate)) {
            LocalDate tmp = fromDate; fromDate = toDate; toDate = tmp;
        }

        String compare = request.getParameter("compare");
        if (compare == null || compare.isBlank()) compare = "previous_period";

        LocalDate prevFrom = null, prevTo = null;
        if ("previous_month".equalsIgnoreCase(compare)) {
            prevFrom = fromDate.minusMonths(1);
            prevTo = toDate.minusMonths(1);
        } else if ("previous_year".equalsIgnoreCase(compare)) {
            prevFrom = fromDate.minusYears(1);
            prevTo = toDate.minusYears(1);
        } else if ("previous_period".equalsIgnoreCase(compare)) {
            long days = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
            prevTo = fromDate.minusDays(1);
            prevFrom = prevTo.minusDays(days - 1);
        }

        String groupExpr;
        switch (groupBy) {
            case "region":
                groupExpr = "COALESCE(NULLIF(TRIM(k.region),''),'Chưa phân vùng')";
                break;
            case "dealer":
                groupExpr = "k.code || ' - ' || k.name";
                break;
            case "category":
                groupExpr = "COALESCE(c.code || ' - ' || c.name, 'Chưa phân loại')";
                break;
            case "sku":
                groupExpr = "p.sku || ' - ' || p.name";
                break;
            case "employee":
            default:
                groupExpr = "COALESCE(u.full_name, 'Chưa phân công')";
                break;
        }

        boolean hasCompare = (prevFrom != null && prevTo != null && !"none".equalsIgnoreCase(compare));

        String sql;
        if (hasCompare) {
            sql =
                "WITH current_period AS (" +
                "  SELECT " + groupExpr + " AS grp, " +
                "         COALESCE(SUM(ol.line_total), 0) AS rev, " +
                "         COALESCE(SUM(ol.base_quantity), 0) AS qty, " +
                "         COALESCE(SUM(ol.base_quantity * p.cost_price), 0) AS cogs " +
                "  FROM orders o " +
                "  JOIN order_lines ol ON ol.order_id = o.id " +
                "  JOIN products p ON p.id = ol.product_id " +
                "  JOIN customers k ON k.id = o.agent_id " +
                "  LEFT JOIN users u ON u.id = k.sales_rep_id " +
                "  LEFT JOIN categories c ON c.id = p.category_id " +
                "  WHERE o.status IN ('APPROVED','PICKING','SHIPPED','DELIVERED','CLOSED') " +
                "    AND o.created_at::date BETWEEN ?::date AND ?::date " +
                "  GROUP BY " + groupExpr +
                "), " +
                "prev_period AS (" +
                "  SELECT " + groupExpr + " AS grp, " +
                "         COALESCE(SUM(ol.line_total), 0) AS prev_rev, " +
                "         COALESCE(SUM(ol.base_quantity), 0) AS prev_qty " +
                "  FROM orders o " +
                "  JOIN order_lines ol ON ol.order_id = o.id " +
                "  JOIN products p ON p.id = ol.product_id " +
                "  JOIN customers k ON k.id = o.agent_id " +
                "  LEFT JOIN users u ON u.id = k.sales_rep_id " +
                "  LEFT JOIN categories c ON c.id = p.category_id " +
                "  WHERE o.status IN ('APPROVED','PICKING','SHIPPED','DELIVERED','CLOSED') " +
                "    AND o.created_at::date BETWEEN ?::date AND ?::date " +
                "  GROUP BY " + groupExpr +
                ") " +
                "SELECT " +
                "  COALESCE(c.grp, p.grp) AS group_key, " +
                "  COALESCE(c.rev, 0) AS revenue, " +
                "  COALESCE(c.qty, 0) AS quantity, " +
                "  COALESCE(c.cogs, 0) AS cogs, " +
                "  CASE WHEN COALESCE(c.rev, 0) > 0 " +
                "       THEN ROUND(((c.rev - c.cogs) / c.rev * 100)::numeric, 2) " +
                "       ELSE 0 END AS gross_margin_pct, " +
                "  COALESCE(p.prev_rev, 0) AS prev_revenue, " +
                "  COALESCE(p.prev_qty, 0) AS prev_quantity, " +
                "  CASE WHEN COALESCE(p.prev_rev, 0) > 0 " +
                "       THEN ROUND(((c.rev - p.prev_rev) / p.prev_rev * 100)::numeric, 2) " +
                "       ELSE NULL END AS revenue_growth_pct, " +
                "  CASE WHEN COALESCE(p.prev_qty, 0) > 0 " +
                "       THEN ROUND(((c.qty - p.prev_qty) / p.prev_qty * 100)::numeric, 2) " +
                "       ELSE NULL END AS quantity_growth_pct " +
                "FROM current_period c " +
                "FULL OUTER JOIN prev_period p ON p.grp = c.grp " +
                "ORDER BY revenue DESC, quantity DESC";
        } else {
            sql =
                "SELECT " +
                "  " + groupExpr + " AS group_key, " +
                "  COALESCE(SUM(ol.line_total), 0) AS revenue, " +
                "  COALESCE(SUM(ol.base_quantity), 0) AS quantity, " +
                "  COALESCE(SUM(ol.base_quantity * p.cost_price), 0) AS cogs, " +
                "  CASE WHEN SUM(ol.line_total) > 0 " +
                "       THEN ROUND(((SUM(ol.line_total) - SUM(ol.base_quantity * p.cost_price)) / SUM(ol.line_total) * 100)::numeric, 2) " +
                "       ELSE 0 END AS gross_margin_pct, " +
                "  0 AS prev_revenue, 0 AS prev_quantity, NULL AS revenue_growth_pct, NULL AS quantity_growth_pct " +
                "FROM orders o " +
                "JOIN order_lines ol ON ol.order_id = o.id " +
                "JOIN products p ON p.id = ol.product_id " +
                "JOIN customers k ON k.id = o.agent_id " +
                "LEFT JOIN users u ON u.id = k.sales_rep_id " +
                "LEFT JOIN categories c ON c.id = p.category_id " +
                "WHERE o.status IN ('APPROVED','PICKING','SHIPPED','DELIVERED','CLOSED') " +
                "  AND o.created_at::date BETWEEN ?::date AND ?::date " +
                "GROUP BY " + groupExpr + " " +
                "ORDER BY revenue DESC, quantity DESC";
        }

        StringBuilder json = new StringBuilder("{");
        json.append("\"canViewMargin\":").append(isManager).append(",");
        json.append("\"groupBy\":\"").append(escapeJson(groupBy)).append("\",");
        json.append("\"fromDate\":\"").append(fromDate).append("\",");
        json.append("\"toDate\":\"").append(toDate).append("\",");
        json.append("\"prevFromDate\":\"").append(prevFrom != null ? prevFrom.toString() : "").append("\",");
        json.append("\"prevToDate\":\"").append(prevTo != null ? prevTo.toString() : "").append("\",");
        json.append("\"items\":[");

        BigDecimal totalRev = BigDecimal.ZERO;
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalCogs = BigDecimal.ZERO;
        BigDecimal totalPrevRev = BigDecimal.ZERO;
        BigDecimal totalPrevQty = BigDecimal.ZERO;

        try (Connection c = KetNoiDB.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, fromDate.toString());
            p.setString(2, toDate.toString());
            if (hasCompare) {
                p.setString(3, prevFrom.toString());
                p.setString(4, prevTo.toString());
            }

            try (ResultSet rs = p.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;

                    String grp = rs.getString("group_key");
                    BigDecimal rev = rs.getBigDecimal("revenue");
                    BigDecimal qty = rs.getBigDecimal("quantity");
                    BigDecimal cogs = rs.getBigDecimal("cogs");
                    BigDecimal marginPct = rs.getBigDecimal("gross_margin_pct");
                    BigDecimal prevRev = rs.getBigDecimal("prev_revenue");
                    BigDecimal prevQty = rs.getBigDecimal("prev_quantity");
                    Object revGrowth = rs.getObject("revenue_growth_pct");
                    Object qtyGrowth = rs.getObject("quantity_growth_pct");

                    if (rev != null) totalRev = totalRev.add(rev);
                    if (qty != null) totalQty = totalQty.add(qty);
                    if (cogs != null) totalCogs = totalCogs.add(cogs);
                    if (prevRev != null) totalPrevRev = totalPrevRev.add(prevRev);
                    if (prevQty != null) totalPrevQty = totalPrevQty.add(prevQty);

                    json.append("{");
                    json.append("\"groupKey\":\"").append(escapeJson(grp)).append("\",");
                    json.append("\"revenue\":").append(rev != null ? rev : 0).append(",");
                    json.append("\"quantity\":").append(qty != null ? qty : 0).append(",");
                    if (isManager) {
                        json.append("\"cogs\":").append(cogs != null ? cogs : 0).append(",");
                        json.append("\"grossMarginPct\":").append(marginPct != null ? marginPct : 0).append(",");
                    } else {
                        json.append("\"cogs\":null,");
                        json.append("\"grossMarginPct\":null,");
                    }
                    json.append("\"prevRevenue\":").append(prevRev != null ? prevRev : 0).append(",");
                    json.append("\"prevQuantity\":").append(prevQty != null ? prevQty : 0).append(",");
                    json.append("\"revenueGrowthPct\":").append(revGrowth != null ? revGrowth : "null").append(",");
                    json.append("\"quantityGrowthPct\":").append(qtyGrowth != null ? qtyGrowth : "null");
                    json.append("}");
                }
            }
        } catch (Exception e) {
            response.setStatus(500);
            out.print("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            return;
        }

        json.append("],\"summary\":{");
        json.append("\"totalRevenue\":").append(totalRev).append(",");
        json.append("\"totalQuantity\":").append(totalQty).append(",");
        if (isManager) {
            json.append("\"totalCogs\":").append(totalCogs).append(",");
            BigDecimal avgMargin = (totalRev.compareTo(BigDecimal.ZERO) > 0)
                ? totalRev.subtract(totalCogs).multiply(new BigDecimal(100)).divide(totalRev, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            json.append("\"avgGrossMarginPct\":").append(avgMargin).append(",");
        } else {
            json.append("\"totalCogs\":null,");
            json.append("\"avgGrossMarginPct\":null,");
        }
        json.append("\"totalPrevRevenue\":").append(totalPrevRev).append(",");
        json.append("\"totalPrevQuantity\":").append(totalPrevQty).append(",");

        BigDecimal revGrowthTotal = (totalPrevRev.compareTo(BigDecimal.ZERO) > 0)
            ? totalRev.subtract(totalPrevRev).multiply(new BigDecimal(100)).divide(totalPrevRev, 2, RoundingMode.HALF_UP)
            : null;
        BigDecimal qtyGrowthTotal = (totalPrevQty.compareTo(BigDecimal.ZERO) > 0)
            ? totalQty.subtract(totalPrevQty).multiply(new BigDecimal(100)).divide(totalPrevQty, 2, RoundingMode.HALF_UP)
            : null;

        json.append("\"revenueGrowthPct\":").append(revGrowthTotal != null ? revGrowthTotal : "null").append(",");
        json.append("\"quantityGrowthPct\":").append(qtyGrowthTotal != null ? qtyGrowthTotal : "null");
        json.append("}}");

        out.print(json.toString());
        return;
    }

    String pageTitle = "Báo cáo doanh số theo nhiều chiều (S8-06)";
%>
<!DOCTYPE html>
<html lang="vi">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title><%= pageTitle %> — KhoFlow</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Be+Vietnam+Pro:wght@400;500;600;700&display=swap" rel="stylesheet">
  <style>
    :root {
      --bg: #f8f6f2;
      --panel: #ffffff;
      --ink: #17120e;
      --muted: #786d64;
      --line: #eee4da;
      --orange: #f47a00;
      --orange-dark: #d96500;
      --orange-soft: #fff1df;
      --green: #16a34a;
      --green-soft: #dcfce7;
      --red: #dc2626;
      --red-soft: #fee2e2;
      --blue: #2563eb;
      --blue-soft: #dbeafe;
      --radius: 12px;
      --shadow: 0 2px 10px rgba(83,48,17,.06);
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: 'Be Vietnam Pro', -apple-system, BlinkMacSystemFont, sans-serif;
      background: var(--bg);
      color: var(--ink);
      line-height: 1.5;
      padding: 24px;
    }
    .s806-container {
      max-width: 1400px;
      margin: 0 auto;
      display: flex;
      flex-direction: column;
      gap: 20px;
    }
    .s806-header {
      background: var(--panel);
      padding: 20px 24px;
      border-radius: var(--radius);
      border: 1px solid var(--line);
      box-shadow: var(--shadow);
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-wrap: wrap;
      gap: 16px;
    }
    .s806-header h1 {
      font-size: 20px;
      font-weight: 700;
      color: var(--ink);
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .s806-header p {
      font-size: 13px;
      color: var(--muted);
      margin-top: 2px;
    }
    .s806-role-badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 5px 12px;
      border-radius: 20px;
      font-size: 12px;
      font-weight: 600;
      background: <%= isManager ? "var(--green-soft)" : "var(--orange-soft)" %>;
      color: <%= isManager ? "var(--green)" : "var(--orange)" %>;
      border: 1px solid <%= isManager ? "#bbf7d0" : "#fed7aa" %>;
    }

    /* Filters Toolbar */
    .s806-filter-card {
      background: var(--panel);
      border-radius: var(--radius);
      border: 1px solid var(--line);
      box-shadow: var(--shadow);
      padding: 20px;
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .s806-group-tabs {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
      border-bottom: 1px solid var(--line);
      padding-bottom: 12px;
    }
    .s806-tab-btn {
      padding: 8px 16px;
      border-radius: 8px;
      border: 1px solid var(--line);
      background: #faf7f2;
      color: var(--ink);
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      transition: all .15s;
    }
    .s806-tab-btn:hover {
      background: var(--orange-soft);
      border-color: var(--orange);
      color: var(--orange-dark);
    }
    .s806-tab-btn.active {
      background: var(--orange);
      border-color: var(--orange);
      color: #fff;
      box-shadow: 0 2px 6px rgba(244,122,0,.3);
    }

    .s806-controls-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 16px;
    }
    .s806-date-group {
      display: flex;
      align-items: center;
      gap: 10px;
      flex-wrap: wrap;
    }
    .s806-input-label {
      font-size: 12px;
      font-weight: 600;
      color: var(--muted);
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .s806-input {
      padding: 8px 12px;
      border: 1px solid var(--line);
      border-radius: 8px;
      font-size: 13px;
      color: var(--ink);
      background: #fff;
      font-family: inherit;
    }
    .s806-input:focus {
      outline: none;
      border-color: var(--orange);
    }
    .s806-presets {
      display: flex;
      gap: 6px;
      flex-wrap: wrap;
    }
    .s806-preset-btn {
      padding: 5px 10px;
      border-radius: 6px;
      border: 1px solid var(--line);
      background: #fff;
      color: var(--muted);
      font-size: 12px;
      font-weight: 500;
      cursor: pointer;
      transition: all .15s;
    }
    .s806-preset-btn:hover {
      border-color: var(--orange);
      color: var(--orange-dark);
    }

    .s806-btn-primary {
      padding: 9px 18px;
      background: var(--orange);
      color: #fff;
      border: none;
      border-radius: 8px;
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      transition: all .15s;
    }
    .s806-btn-primary:hover {
      background: var(--orange-dark);
      transform: translateY(-1px);
    }

    /* Summary Cards */
    .s806-metrics-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      gap: 16px;
    }
    .s806-metric-card {
      background: var(--panel);
      border-radius: var(--radius);
      border: 1px solid var(--line);
      box-shadow: var(--shadow);
      padding: 18px 20px;
      display: flex;
      flex-direction: column;
      gap: 6px;
      position: relative;
      overflow: hidden;
    }
    .s806-metric-card::before {
      content: '';
      position: absolute;
      top: 0; left: 0; right: 0; height: 3px;
      background: var(--card-bar, var(--orange));
    }
    .s806-metric-card.accent-blue::before { --card-bar: var(--blue); }
    .s806-metric-card.accent-green::before { --card-bar: var(--green); }
    .s806-metric-card.accent-red::before { --card-bar: var(--red); }

    .s806-metric-label {
      font-size: 12px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: .05em;
      color: var(--muted);
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .s806-metric-val {
      font-size: 24px;
      font-weight: 700;
      color: var(--ink);
    }
    .s806-growth-badge {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      font-size: 12px;
      font-weight: 600;
      padding: 2px 8px;
      border-radius: 12px;
    }
    .s806-growth-badge.positive {
      background: var(--green-soft);
      color: var(--green);
    }
    .s806-growth-badge.negative {
      background: var(--red-soft);
      color: var(--red);
    }
    .s806-growth-badge.neutral {
      background: #f3f4f6;
      color: var(--muted);
    }
    .s806-blur-val {
      filter: blur(4px);
      user-select: none;
      opacity: .6;
    }
  </style>
</head>
<body>
  <div class="s806-container">
    <header class="s806-header">
      <div>
        <h1>📊 <%= pageTitle %></h1>
        <p>Phân tích hiệu quả kinh doanh, sản lượng và biên lợi nhuận gộp theo nhiều chiều</p>
      </div>
      <div style="display:flex; align-items:center; gap:12px;">
        <span class="s806-role-badge">
          <%= isManager ? "🛡️ Quản lý kinh doanh (Được xem biên lợi nhuận)" : "👤 Nhân viên (Ẩn biên lợi nhuận)" %>
        </span>
        <a href="index.html" style="text-decoration:none; color:var(--orange); font-size:13px; font-weight:600;">← Về trang tổng quan</a>
      </div>
    </header>

    <!-- Bộ lọc đa chiều (Grouping & Comparison Filter Bar) -->
    <section class="s806-filter-card">
      <div>
        <span style="font-size:11px; font-weight:700; text-transform:uppercase; color:var(--muted); letter-spacing:.05em; margin-bottom:8px; display:block;">
          1. CHỌN CHIỀU PHÂN TÍCH (GOM NHÓM)
        </span>
        <div class="s806-group-tabs" id="groupTabs">
          <button type="button" class="s806-tab-btn active" data-grp="employee">👤 Theo Nhân viên</button>
          <button type="button" class="s806-tab-btn" data-grp="region">📍 Theo Khu vực</button>
          <button type="button" class="s806-tab-btn" data-grp="dealer">🏢 Theo Đại lý</button>
          <button type="button" class="s806-tab-btn" data-grp="category">🏷️ Theo Nhóm hàng</button>
          <button type="button" class="s806-tab-btn" data-grp="sku">📦 Theo Sản phẩm / SKU</button>
        </div>
      </div>

      <div class="s806-controls-row">
        <div class="s806-date-group">
          <label class="s806-input-label">
            <span>Từ ngày:</span>
            <input type="date" class="s806-input" id="inputFromDate">
          </label>
          <label class="s806-input-label">
            <span>Đến ngày:</span>
            <input type="date" class="s806-input" id="inputToDate">
          </label>
          <div class="s806-presets">
            <button type="button" class="s806-preset-btn" data-preset="this_month">Tháng này</button>
            <button type="button" class="s806-preset-btn" data-preset="last_month">Tháng trước</button>
            <button type="button" class="s806-preset-btn" data-preset="last_30_days">30 ngày qua</button>
            <button type="button" class="s806-preset-btn" data-preset="this_year">Năm nay</button>
          </div>
        </div>

        <div style="display:flex; align-items:center; gap:12px;">
          <label class="s806-input-label">
            <span>So sánh:</span>
            <select class="s806-input" id="selectCompare">
              <option value="previous_period">Cùng kỳ trước</option>
              <option value="previous_month">Tháng trước</option>
              <option value="previous_year">Cùng kỳ năm ngoái</option>
              <option value="none">Không so sánh</option>
            </select>
          </label>
          <button type="button" class="s806-btn-primary" id="btnFilter">
            🔍 Xem báo cáo
          </button>
        </div>
      </div>
    </section>

    <!-- Thẻ chỉ số tổng hợp (Summary Metric Cards) -->
    <section class="s806-metrics-grid" id="metricsGrid">
      <div class="s806-metric-card">
        <div class="s806-metric-label">
          <span>Tổng doanh số</span>
          <span class="s806-growth-badge neutral" id="badgeRevGrowth">—</span>
        </div>
        <div class="s806-metric-val" id="valTotalRevenue">0 ₫</div>
        <small style="font-size:11px; color:var(--muted);" id="subPrevRevenue">Kỳ trước: 0 ₫</small>
      </div>

      <div class="s806-metric-card accent-blue">
        <div class="s806-metric-label">
          <span>Tổng sản lượng</span>
          <span class="s806-growth-badge neutral" id="badgeQtyGrowth">—</span>
        </div>
        <div class="s806-metric-val" id="valTotalQty">0</div>
        <small style="font-size:11px; color:var(--muted);" id="subPrevQty">Kỳ trước: 0</small>
      </div>

      <div class="s806-metric-card accent-red">
        <div class="s806-metric-label">
          <span>Giá vốn hàng bán (COGS)</span>
          <% if (!isManager) { %><span style="font-size:10px; background:#fee2e2; color:#dc2626; padding:2px 6px; border-radius:10px;">Bảo mật</span><% } %>
        </div>
        <div class="s806-metric-val <%= !isManager ? "s806-blur-val" : "" %>" id="valTotalCogs">
          <%= isManager ? "0 ₫" : "****** ₫" %>
        </div>
        <small style="font-size:11px; color:var(--muted);">
          <%= isManager ? "Tổng giá trị vốn xuất bán" : "Chỉ Quản lý kinh doanh xem được" %>
        </small>
      </div>

      <div class="s806-metric-card accent-green">
        <div class="s806-metric-label">
          <span>Biên lợi nhuận gộp</span>
          <% if (!isManager) { %><span style="font-size:10px; background:#fee2e2; color:#dc2626; padding:2px 6px; border-radius:10px;">Bảo mật</span><% } %>
        </div>
        <div class="s806-metric-val <%= !isManager ? "s806-blur-val" : "" %>" id="valAvgMargin">
          <%= isManager ? "0%" : "****** %" %>
        </div>
        <small style="font-size:11px; color:var(--muted);">
          <%= isManager ? "(Doanh thu - Giá vốn) / Doanh thu" : "Chỉ Quản lý kinh doanh xem được" %>
        </small>
      </div>
    </section>

    <!-- Placeholder cho Bảng dữ liệu & Export Excel (Bước 4) -->
    <main class="s806-panel" id="s806TablePanel">
      <div style="text-align:center; padding:30px 0; color:var(--muted);" id="tablePlaceholder">
        <p style="font-size:14px; font-weight:600; color:var(--ink);">Đã hoàn thành cấu hình Bộ lọc đa chiều & phân quyền Quản lý kinh doanh</p>
        <p style="font-size:13px; margin-top:6px;">Sẵn sàng tích hợp bảng dữ liệu chi tiết và tính năng xuất Excel ở Bước 4.</p>
      </div>
    </main>
  </div>

  <script>
    (function(){
      'use strict';
      var currentGroupBy = 'employee';
      var today = new Date();

      function formatIsoDate(d){
        return d.toISOString().split('T')[0];
      }

      function initDates(){
        var y = today.getFullYear(), m = today.getMonth();
        var firstDay = new Date(y, m, 1);
        document.getElementById('inputFromDate').value = formatIsoDate(firstDay);
        document.getElementById('inputToDate').value = formatIsoDate(today);
      }
      initDates();

      // Tab chuyển chiều phân tích
      document.querySelectorAll('.s806-tab-btn').forEach(function(btn){
        btn.addEventListener('click', function(){
          document.querySelectorAll('.s806-tab-btn').forEach(function(b){ b.classList.remove('active'); });
          btn.classList.add('active');
          currentGroupBy = btn.getAttribute('data-grp');
        });
      });

      // Nút presets ngày
      document.querySelectorAll('.s806-preset-btn').forEach(function(btn){
        btn.addEventListener('click', function(){
          var p = btn.getAttribute('data-preset');
          var y = today.getFullYear(), m = today.getMonth();
          var fromD, toD = today;
          if(p === 'this_month'){ fromD = new Date(y, m, 1); }
          else if(p === 'last_month'){ fromD = new Date(y, m - 1, 1); toD = new Date(y, m, 0); }
          else if(p === 'last_30_days'){ fromD = new Date(); fromD.setDate(today.getDate() - 30); }
          else if(p === 'this_year'){ fromD = new Date(y, 0, 1); }
          document.getElementById('inputFromDate').value = formatIsoDate(fromD);
          document.getElementById('inputToDate').value = formatIsoDate(toD);
        });
      });
    })();
  </script>
</body>
</html>
