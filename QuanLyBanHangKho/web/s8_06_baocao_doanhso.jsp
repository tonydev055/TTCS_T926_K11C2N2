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

    .s806-actions {
      display: flex;
      align-items: center;
      gap: 10px;
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
    .s806-btn-excel {
      padding: 9px 16px;
      background: #107c41;
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
    .s806-btn-excel:hover {
      background: #0b5e31;
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

    /* Data Table */
    .s806-table-card {
      background: var(--panel);
      border-radius: var(--radius);
      border: 1px solid var(--line);
      box-shadow: var(--shadow);
      padding: 20px;
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .s806-table-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 12px;
    }
    .s806-table-header h2 {
      font-size: 16px;
      font-weight: 700;
      color: var(--ink);
    }
    .s806-table-wrap {
      overflow-x: auto;
    }
    .s806-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 13px;
    }
    .s806-table th {
      text-align: left;
      padding: 10px 12px;
      font-size: 11px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: .05em;
      color: var(--muted);
      border-bottom: 2px solid var(--line);
      white-space: nowrap;
      background: #faf7f2;
    }
    .s806-table td {
      padding: 12px;
      color: var(--ink);
      border-bottom: 1px solid var(--line);
      vertical-align: middle;
    }
    .s806-table tr:hover td {
      background: var(--orange-soft);
    }
    .s806-table tfoot td {
      font-weight: 700;
      background: #fcfbfa;
      border-top: 2px solid var(--line);
      border-bottom: none;
    }
    .text-right { text-align: right; }
    .text-center { text-align: center; }

    .s806-margin-bar {
      display: flex;
      align-items: center;
      gap: 8px;
      justify-content: flex-end;
    }
    .s806-progress-track {
      width: 60px;
      height: 8px;
      background: #eee4da;
      border-radius: 4px;
      overflow: hidden;
      flex-shrink: 0;
    }
    .s806-progress-fill {
      height: 100%;
      border-radius: 4px;
      background: var(--green);
    }
    .s806-progress-fill.warning { background: var(--orange); }
    .s806-progress-fill.danger { background: var(--red); }
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

        <div class="s806-actions">
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
          <button type="button" class="s806-btn-excel" id="btnExportExcel" title="Xuất dữ liệu ra file Excel">
            📥 Xuất Excel
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

    <!-- Bảng dữ liệu chi tiết (Detailed Data Table) -->
    <main class="s806-table-card">
      <div class="s806-table-header">
        <h2 id="tableTitle">Bảng chi tiết báo cáo doanh số theo Nhân viên</h2>
        <span id="recordCount" style="font-size:12px; color:var(--muted);">0 bản ghi</span>
      </div>

      <div class="s806-table-wrap">
        <table class="s806-table" id="reportTable">
          <thead>
            <tr>
              <th class="text-center" style="width:50px;">#</th>
              <th id="thGroupKey">Nhân viên</th>
              <th class="text-right">Doanh số kỳ này</th>
              <th class="text-right">Doanh số kỳ trước</th>
              <th class="text-center">Tăng trưởng DS</th>
              <th class="text-right">Sản lượng kỳ này</th>
              <th class="text-right">Sản lượng kỳ trước</th>
              <th class="text-center">Tăng trưởng SL</th>
              <th class="text-right">Giá vốn (VND)</th>
              <th class="text-right" style="min-width:140px;">Biên LN gộp (%)</th>
            </tr>
          </thead>
          <tbody id="tableBody">
            <tr>
              <td colspan="10" class="text-center" style="padding:40px 0; color:var(--muted);">
                Đang tải dữ liệu báo cáo...
              </td>
            </tr>
          </tbody>
          <tfoot id="tableFoot" style="display:none;">
            <tr>
              <td class="text-center">Σ</td>
              <td><strong>TỔNG CỘNG</strong></td>
              <td class="text-right" id="footRevenue">0 ₫</td>
              <td class="text-right" id="footPrevRevenue">0 ₫</td>
              <td class="text-center" id="footRevGrowth">—</td>
              <td class="text-right" id="footQty">0</td>
              <td class="text-right" id="footPrevQty">0</td>
              <td class="text-center" id="footQtyGrowth">—</td>
              <td class="text-right" id="footCogs">—</td>
              <td class="text-right" id="footMargin">—</td>
            </tr>
          </tfoot>
        </table>
      </div>
    </main>
  </div>

  <script>
    (function(){
      'use strict';
      var currentGroupBy = 'employee';
      var currentData = null;
      var canViewMargin = <%= isManager %>;
      var today = new Date();

      var GROUP_NAMES = {
        'employee': 'Nhân viên',
        'region': 'Khu vực',
        'dealer': 'Đại lý',
        'category': 'Nhóm hàng',
        'sku': 'Sản phẩm / SKU'
      };

      function formatIsoDate(d){
        return d.toISOString().split('T')[0];
      }

      function money(v){
        if(v == null) return '—';
        return new Intl.NumberFormat('vi-VN', {style:'currency', currency:'VND'}).format(Number(v));
      }
      function num(v){
        if(v == null) return '—';
        return new Intl.NumberFormat('vi-VN').format(Number(v));
      }

      function growthBadge(growth){
        if(growth == null) return '<span class="s806-growth-badge neutral">—</span>';
        var n = Number(growth);
        if(n > 0) return '<span class="s806-growth-badge positive">▲ +' + n.toFixed(1) + '%</span>';
        if(n < 0) return '<span class="s806-growth-badge negative">▼ ' + n.toFixed(1) + '%</span>';
        return '<span class="s806-growth-badge neutral">0.0%</span>';
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
          document.getElementById('thGroupKey').textContent = GROUP_NAMES[currentGroupBy] || 'Tiêu chí';
          document.getElementById('tableTitle').textContent = 'Bảng chi tiết báo cáo doanh số theo ' + (GROUP_NAMES[currentGroupBy] || '');
          loadReport();
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
          loadReport();
        });
      });

      async function loadReport(){
        var tbody = document.getElementById('tableBody');
        var tfoot = document.getElementById('tableFoot');
        tbody.innerHTML = '<tr><td colspan="10" class="text-center" style="padding:40px 0; color:var(--muted);">Đang tải dữ liệu báo cáo...</td></tr>';
        tfoot.style.display = 'none';

        var fromStr = document.getElementById('inputFromDate').value;
        var toStr = document.getElementById('inputToDate').value;
        var compare = document.getElementById('selectCompare').value;

        var url = 's8_06_baocao_doanhso.jsp?action=data' +
          '&groupBy=' + encodeURIComponent(currentGroupBy) +
          '&from=' + encodeURIComponent(fromStr) +
          '&to=' + encodeURIComponent(toStr) +
          '&compare=' + encodeURIComponent(compare);

        try {
          var res = await fetch(url);
          if(!res.ok) throw new Error('Mã phản hồi lỗi: ' + res.status);
          var data = await res.json();
          currentData = data;
          renderData(data);
        } catch(e) {
          tbody.innerHTML = '<tr><td colspan="10" class="text-center" style="padding:40px 0; color:var(--red);">Có lỗi khi tải dữ liệu: ' + (e.message || 'Lỗi kết nối') + '</td></tr>';
        }
      }

      function renderData(data){
        var tbody = document.getElementById('tableBody');
        var tfoot = document.getElementById('tableFoot');
        canViewMargin = Boolean(data.canViewMargin);

        // 1. Cập nhật metric cards
        var s = data.summary || {};
        document.getElementById('valTotalRevenue').textContent = money(s.totalRevenue);
        document.getElementById('subPrevRevenue').textContent = 'Kỳ trước: ' + money(s.totalPrevRevenue);
        document.getElementById('badgeRevGrowth').innerHTML = growthBadge(s.revenueGrowthPct);

        document.getElementById('valTotalQty').textContent = num(s.totalQuantity);
        document.getElementById('subPrevQty').textContent = 'Kỳ trước: ' + num(s.totalPrevQuantity);
        document.getElementById('badgeQtyGrowth').innerHTML = growthBadge(s.quantityGrowthPct);

        if(canViewMargin){
          document.getElementById('valTotalCogs').textContent = money(s.totalCogs);
          document.getElementById('valTotalCogs').classList.remove('s806-blur-val');
          document.getElementById('valAvgMargin').textContent = (s.avgGrossMarginPct != null ? Number(s.avgGrossMarginPct).toFixed(1) : '0.0') + '%';
          document.getElementById('valAvgMargin').classList.remove('s806-blur-val');
        } else {
          document.getElementById('valTotalCogs').textContent = '****** ₫';
          document.getElementById('valTotalCogs').classList.add('s806-blur-val');
          document.getElementById('valAvgMargin').textContent = '****** %';
          document.getElementById('valAvgMargin').classList.add('s806-blur-val');
        }

        // 2. Render table rows
        var items = data.items || [];
        document.getElementById('recordCount').textContent = items.length + ' bản ghi';

        if(items.length === 0){
          tbody.innerHTML = '<tr><td colspan="10" class="text-center" style="padding:40px 0; color:var(--muted);">Không có đơn hàng nào trong khoảng thời gian này.</td></tr>';
          tfoot.style.display = 'none';
          return;
        }

        var html = items.map(function(item, idx){
          var cogsTd, marginTd;
          if(canViewMargin){
            cogsTd = '<td class="text-right">' + money(item.cogs) + '</td>';
            var m = Number(item.grossMarginPct) || 0;
            var barCls = m < 10 ? 'danger' : (m < 25 ? 'warning' : '');
            var barW = Math.min(Math.max(m, 0), 100);
            marginTd = '<td class="text-right">' +
              '<div class="s806-margin-bar">' +
                '<span>' + m.toFixed(1) + '%</span>' +
                '<div class="s806-progress-track"><div class="s806-progress-fill ' + barCls + '" style="width:' + barW + '%;"></div></div>' +
              '</div>' +
            '</td>';
          } else {
            cogsTd = '<td class="text-right" style="color:var(--muted); font-size:11px;"><em>Bảo mật</em></td>';
            marginTd = '<td class="text-right" style="color:var(--muted); font-size:11px;"><em>Bảo mật</em></td>';
          }

          return '<tr>' +
            '<td class="text-center">' + (idx + 1) + '</td>' +
            '<td><strong>' + (item.groupKey || '—') + '</strong></td>' +
            '<td class="text-right" style="font-weight:600;">' + money(item.revenue) + '</td>' +
            '<td class="text-right" style="color:var(--muted);">' + money(item.prevRevenue) + '</td>' +
            '<td class="text-center">' + growthBadge(item.revenueGrowthPct) + '</td>' +
            '<td class="text-right" style="font-weight:600;">' + num(item.quantity) + '</td>' +
            '<td class="text-right" style="color:var(--muted);">' + num(item.prevQuantity) + '</td>' +
            '<td class="text-center">' + growthBadge(item.quantityGrowthPct) + '</td>' +
            cogsTd +
            marginTd +
          '</tr>';
        }).join('');

        tbody.innerHTML = html;

        // 3. Render footer tổng cộng
        tfoot.style.display = '';
        document.getElementById('footRevenue').textContent = money(s.totalRevenue);
        document.getElementById('footPrevRevenue').textContent = money(s.totalPrevRevenue);
        document.getElementById('footRevGrowth').innerHTML = growthBadge(s.revenueGrowthPct);
        document.getElementById('footQty').textContent = num(s.totalQuantity);
        document.getElementById('footPrevQty').textContent = num(s.totalPrevQuantity);
        document.getElementById('footQtyGrowth').innerHTML = growthBadge(s.quantityGrowthPct);

        if(canViewMargin){
          document.getElementById('footCogs').textContent = money(s.totalCogs);
          document.getElementById('footMargin').textContent = (s.avgGrossMarginPct != null ? Number(s.avgGrossMarginPct).toFixed(1) : '0.0') + '%';
        } else {
          document.getElementById('footCogs').innerHTML = '<em>Bảo mật</em>';
          document.getElementById('footMargin').innerHTML = '<em>Bảo mật</em>';
        }
      }

      // Xuất Excel dạng CSV UTF-8 với BOM tương thích 100% Excel tiếng Việt
      document.getElementById('btnExportExcel').addEventListener('click', function(){
        if(!currentData || !currentData.items || currentData.items.length === 0){
          alert('Không có dữ liệu để xuất file.');
          return;
        }
        var groupColName = GROUP_NAMES[currentGroupBy] || 'Tieu chi';
        var headers = [
          'STT',
          groupColName,
          'Doanh so ky nay (VND)',
          'Doanh so ky truoc (VND)',
          'Tang truong DS (%)',
          'San luong ky nay',
          'San luong ky truoc',
          'Tang truong SL (%)'
        ];
        if(canViewMargin){
          headers.push('Gia von COGS (VND)');
          headers.push('Bien loi nhuan gop (%)');
        }

        var csvRows = [headers.join(',')];

        currentData.items.forEach(function(item, idx){
          var row = [
            idx + 1,
            '"' + String(item.groupKey || '').replace(/"/g, '""') + '"',
            Number(item.revenue) || 0,
            Number(item.prevRevenue) || 0,
            item.revenueGrowthPct != null ? Number(item.revenueGrowthPct) : '',
            Number(item.quantity) || 0,
            Number(item.prevQuantity) || 0,
            item.quantityGrowthPct != null ? Number(item.quantityGrowthPct) : ''
          ];
          if(canViewMargin){
            row.push(Number(item.cogs) || 0);
            row.push(item.grossMarginPct != null ? Number(item.grossMarginPct) : '');
          }
          csvRows.push(row.join(','));
        });

        // Dòng tổng cộng
        var s = currentData.summary || {};
        var sumRow = [
          '',
          '"TONG CONG"',
          Number(s.totalRevenue) || 0,
          Number(s.totalPrevRevenue) || 0,
          s.revenueGrowthPct != null ? Number(s.revenueGrowthPct) : '',
          Number(s.totalQuantity) || 0,
          Number(s.totalPrevQuantity) || 0,
          s.quantityGrowthPct != null ? Number(s.quantityGrowthPct) : ''
        ];
        if(canViewMargin){
          sumRow.push(Number(s.totalCogs) || 0);
          sumRow.push(s.avgGrossMarginPct != null ? Number(s.avgGrossMarginPct) : '');
        }
        csvRows.push(sumRow.join(','));

        var csvContent = '\uFEFF' + csvRows.join('\r\n');
        var blob = new Blob([csvContent], {type: 'text/csv;charset=utf-8;'});
        var link = document.createElement('a');
        var fileName = 'BaoCaoDoanhSo_' + currentGroupBy + '_' + document.getElementById('inputFromDate').value + '_' + document.getElementById('inputToDate').value + '.csv';
        link.href = URL.createObjectURL(blob);
        link.setAttribute('download', fileName);
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
      });

      document.getElementById('btnFilter').addEventListener('click', loadReport);
      document.getElementById('selectCompare').addEventListener('change', loadReport);

      // Khởi động load báo cáo lần đầu
      loadReport();
    })();
  </script>
</body>
</html>
