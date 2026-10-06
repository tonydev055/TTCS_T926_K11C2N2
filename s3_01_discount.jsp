<%--=============================================================================S3-01 · Khai báo chính sách chiết khấu
  theo sản lượng KhoFlow — Quản lý bán hàng & kho | Sprint 3 · Epic EP-02 · MoSCoW:
  Must=============================================================================► SQL SCRIPT — Chạy trong
  database "quanlybanhangkho" : ───────────────────────────────────────────────────── CREATE TABLE IF NOT EXISTS
  DiscountPolicy ( id SERIAL PRIMARY KEY, name VARCHAR(200) NOT NULL, scope VARCHAR(10) NOT NULL CHECK (scope IN
  ('SKU','GROUP')), target_id VARCHAR(100) NOT NULL, target_name VARCHAR(200) NOT NULL, discount_type VARCHAR(10) NOT
  NULL CHECK (discount_type IN ('PERCENT','FIXED')), is_active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT
  NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW() ); CREATE TABLE IF NOT EXISTS DiscountTier ( id
  SERIAL PRIMARY KEY, policy_id INT NOT NULL REFERENCES DiscountPolicy(id) ON DELETE CASCADE, qty_from INT NOT NULL
  CHECK (qty_from>= 1),
  value NUMERIC(15,2) NOT NULL CHECK (value > 0),
  sort_order INT NOT NULL DEFAULT 0
  );

  ► QUY TẮC ÁP DỤNG: Khi nhiều chính sách cùng áp dụng, chọn chính sách
  cho mức giảm giá VNĐ cao nhất. Không cộng dồn. (Best-of, non-cumulative)

  ► NHÚNG VÀO BACKEND (Servlet/DAO):
  Tìm 3 dòng có nhãn [BACKEND-INJECT] trong khối
  <script> bên dưới:
      • BACKEND - INJECT: SKUS    → inject từ ProductDAO.getAllSKUs()
      • BACKEND - INJECT: GROUPS  → inject từ ProductGroupDAO.getAll()
      • BACKEND - INJECT: STORE   → inject từ DiscountPolicyDAO.getAll()
    Thay mảng[] bằng vòng lặp JSP scriptlet hoặc API fetch().
=============================================================================
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>S3-01 · Chính sách chiết khấu — KhoFlow</title>
  <meta name="description" content="Khai báo chính sách chiết khấu theo sản lượng — KhoFlow Sprint 3">
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Be+Vietnam+Pro:wght@400;500;600;700;800&display=swap" rel="stylesheet">
  <style>
    /* CSS tạm thời trong quá trình xây dựng layout */
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: 'Be Vietnam Pro', sans-serif; background: #f5f2ed; color: #1c1917; }
  </style>
</head>
<body>
<div class="app-layout">

  <!-- ═══════════════ SIDEBAR ═══════════════ -->
  <aside class="sidebar">
    <div class="sidebar-brand">
      <div class="brand-mark">KF</div>
      <div>
        <div class="brand-name">KhoFlow</div>
        <div class="brand-sub">Quản lý bán hàng &amp; kho</div>
      </div>
    </div>
    <div class="sidebar-section">
      <div class="sidebar-label">Sprint 3</div>
      <button class="sidebar-link active" onclick="gotoList()">Chiết khấu số lượng</button>
      <button class="sidebar-link" onclick="navigate('lich-su-gia')">Lịch sử thay đổi giá</button>
      <div class="sidebar-divider"></div>
      <button class="sidebar-link" onclick="navigate('sprint3')">Sprint 3 · S3-01 &amp; S3-02</button>
    </div>
  </aside>

  <!-- ═══════════════ MAIN ═══════════════ -->
  <div class="main-area">
    <div class="top-bar">
      <span class="top-bar-brand">Hệ thống bán hàng &amp; kho</span>
      <span class="top-bar-role">Quản lý kinh doanh</span>
    </div>

    <div class="content-area">

      <div id="contentPlaceholder" style="padding: 32px; background: #fff; border-radius: 12px; border: 1px solid #e7e2da;">
        <h2 style="font-size: 18px; font-weight: 700; color: #1c1917; margin-bottom: 8px;">Khai báo chính sách chiết khấu</h2>
        <p style="font-size: 13.5px; color: #78716c;">Module đang được hoàn thiện các thành phần giao diện và chức năng.</p>
      </div>
    </div>
  </div>
</div>
</body>
</html>