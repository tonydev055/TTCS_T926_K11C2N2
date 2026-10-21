<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.sql.*, java.util.*, java.math.*, java.time.*, util.KetNoiDB" %>
<%
    // S8-06: Báo cáo doanh số theo nhiều chiều - Bước 1: Khởi tạo khung layout & kế thừa UI/UX chung
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
    .s806-panel {
      background: var(--panel);
      border-radius: var(--radius);
      border: 1px solid var(--line);
      box-shadow: var(--shadow);
      padding: 20px;
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
      <div>
        <a href="index.html" style="text-decoration:none; color:var(--orange); font-size:13px; font-weight:600;">← Về trang tổng quan</a>
      </div>
    </header>

    <main class="s806-panel" id="s806Content">
      <div style="text-align:center; padding: 40px 0; color:var(--muted);">
        <p style="font-size:16px; font-weight:600; color:var(--ink);">Khởi tạo module báo cáo doanh số S8-06</p>
        <p style="font-size:13px; margin-top:8px;">Hệ thống đang sẵn sàng nạp các bộ lọc đa chiều và kết nối dữ liệu.</p>
      </div>
    </main>
  </div>
</body>
</html>
