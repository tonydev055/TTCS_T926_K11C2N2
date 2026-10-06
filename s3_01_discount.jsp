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
    /* ─── Design tokens (đồng bộ với KhoFlow shared styles) ─── */
    :root {
      --bg-base:       #f5f2ed;
      --bg-card:       #ffffff;
      --bg-surface:    #f8fafc;
      --bg-hover:      #f1f5f9;
      --border:        #e2e8f0;
      --border-focus:  #f26522;
      --accent:        #f26522;
      --accent-light:  rgba(242,101,34,.12);
      --accent-glow:   rgba(242,101,34,.28);
      --success:       #10b981;
      --danger:        #ef4444;
      --danger-bg:     rgba(239,68,68,.08);
      --danger-border: rgba(239,68,68,.25);
      --info-bg:       rgba(59,130,246,.07);
      --text-primary:  #1e293b;
      --text-secondary:#64748b;
      --text-muted:    #94a3b8;
      --radius-sm:     6px;
      --radius-md:     10px;
      --radius-lg:     16px;
      --radius-full:   9999px;
      --shadow-card:   0 8px 28px -4px rgba(0,0,0,.09), 0 2px 8px -2px rgba(0,0,0,.04);
      --shadow-btn:    0 4px 16px rgba(242,101,34,.38);
      --transition:    .2s cubic-bezier(.4,0,.2,1);
    }

    /* ─── Reset ─── */
    *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0 }
    html { font-size: 15px }
    body {
      font-family: 'Be Vietnam Pro', Arial, sans-serif;
      background: var(--bg-base);
      color: var(--text-primary);
      min-height: 100vh;
      display: flex;
    }

    /* ─── Layout ─── */
    .app-layout { display: flex; width: 100%; min-height: 100vh }

    /* ─── Sidebar ─── */
    .sidebar {
      width: 220px; min-height: 100vh;
      background: var(--bg-card); border-right: 1px solid var(--border);
      display: flex; flex-direction: column; flex-shrink: 0;
      position: sticky; top: 0; height: 100vh; overflow-y: auto;
    }
    .sidebar-brand {
      display: flex; align-items: center; gap: 10px;
      padding: 20px 18px 14px; border-bottom: 1px solid var(--border);
    }
    .brand-mark {
      width: 36px; height: 36px; flex-shrink: 0;
      background: linear-gradient(135deg, var(--accent), #e05310);
      color: #fff; font-weight: 800; font-size: 13px;
      border-radius: 10px; display: flex; align-items: center; justify-content: center;
      box-shadow: 0 4px 12px var(--accent-glow);
    }
    .brand-name { font-size: 15px; font-weight: 700 }
    .brand-sub  { font-size: 11px; color: var(--text-muted); margin-top: 1px }
    .sidebar-section { padding: 16px 12px 6px }
    .sidebar-label {
      font-size: 10px; font-weight: 700; letter-spacing: .1em;
      text-transform: uppercase; color: var(--text-muted);
      padding: 0 8px; margin-bottom: 8px;
    }
    .sidebar-link {
      display: block; padding: 9px 12px; border-radius: var(--radius-sm);
      font-size: 13px; font-weight: 500; color: var(--text-secondary);
      cursor: pointer; transition: all var(--transition);
      text-decoration: none; border: none; background: none;
      width: 100%; text-align: left; font-family: inherit;
    }
    .sidebar-link:hover { background: var(--bg-hover); color: var(--text-primary) }
    .sidebar-link.active { background: var(--accent-light); color: var(--accent); font-weight: 600 }
    .sidebar-divider { height: 1px; background: var(--border); margin: 8px 12px }

    /* ─── Main ─── */
    .main-area { flex: 1; display: flex; flex-direction: column; min-width: 0 }
    .top-bar {
      background: var(--bg-card); border-bottom: 2px dashed var(--border);
      padding: 14px 32px; display: flex; align-items: center;
      justify-content: space-between; flex-shrink: 0;
    }
    .top-bar-brand { font-size: 14px; font-weight: 700 }
    .top-bar-role  { font-size: 13px; color: var(--text-secondary); font-weight: 500 }
    .content-area  { flex: 1; padding: 32px; display: flex; flex-direction: column; gap: 22px }

    /* ─── Page header ─── */
    .page-title { font-size: 26px; font-weight: 800; letter-spacing: -.5px }
    .page-sub   { font-size: 13px; color: var(--text-secondary); margin-top: 4px }

    /* ─── Tabs ─── */
    .tab-bar { display: flex; gap: 4px }
    .tab-btn {
      padding: 8px 20px; border-radius: var(--radius-sm);
      border: 1px solid var(--border); background: var(--bg-card);
      font-family: inherit; font-size: 13.5px; font-weight: 600;
      color: var(--text-secondary); cursor: pointer; transition: all var(--transition);
    }
    .tab-btn:hover { border-color: var(--accent); color: var(--accent) }
    .tab-btn.active { background: var(--accent-light); border-color: var(--accent); color: var(--accent) }

    /* ─── Info banner ─── */
    .info-banner {
      padding: 10px 16px; background: var(--info-bg);
      border: 1px solid rgba(59,130,246,.2); border-radius: var(--radius-sm);
      font-size: 12.5px; color: #2563eb;
      display: flex; align-items: center; gap: 8px;
    }

    /* ─── Card ─── */
    .card { background: var(--bg-card); border: 1px solid var(--border); border-radius: var(--radius-lg); box-shadow: var(--shadow-card); overflow: hidden }
    .card-body { padding: 24px 28px }

    /* ─── Toolbar ─── */
    .toolbar { display: flex; align-items: flex-end; gap: 12px; flex-wrap: wrap }
    .toolbar-left { display: flex; gap: 12px; flex: 1; flex-wrap: wrap }
    .field-wrap { display: flex; flex-direction: column; gap: 4px }
    .field-wrap.grow { flex: 1; min-width: 200px }
    .field-label { font-size: 12px; font-weight: 600; color: var(--text-secondary) }

    /* ─── Form controls ─── */
    .fc {
      padding: 9px 14px; border: 1px solid var(--border); border-radius: var(--radius-sm);
      font-size: 13.5px; font-family: inherit; color: var(--text-primary);
      background: var(--bg-surface); transition: all var(--transition); outline: none; width: 100%;
    }
    .fc:focus { border-color: var(--border-focus); box-shadow: 0 0 0 3px var(--accent-glow); background: #fff }
    .fc.error { border-color: var(--danger); box-shadow: 0 0 0 3px var(--danger-bg) }
    select.fc {
      appearance: none;
      background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%2364748b' d='M6 8L1 3h10z'/%3E%3C/svg%3E");
      background-repeat: no-repeat; background-position: right 12px center; padding-right: 36px; cursor: pointer;
    }

    /* ─── Buttons ─── */
    .btn {
      display: inline-flex; align-items: center; justify-content: center; gap: 6px;
      padding: 10px 18px; border-radius: var(--radius-sm);
      font-size: 13px; font-weight: 600; font-family: inherit;
      cursor: pointer; border: none; transition: all var(--transition); white-space: nowrap;
    }
    .btn:disabled { opacity: .45; cursor: not-allowed; pointer-events: none }
    .btn-primary  { background: linear-gradient(135deg,var(--accent),#e05310); color: #fff; box-shadow: var(--shadow-btn) }
    .btn-primary:hover  { transform: translateY(-1px); box-shadow: 0 6px 22px var(--accent-glow) }
    .btn-primary:active { transform: translateY(0) }
    .btn-secondary { background: var(--bg-surface); color: var(--text-secondary); border: 1px solid var(--border) }
    .btn-secondary:hover { background: var(--bg-hover); color: var(--text-primary); border-color: #c0cad8 }
    .btn-edit { background: rgba(242,101,34,.08); color: var(--accent); border: 1px solid rgba(242,101,34,.2); padding: 7px 20px; font-size: 13px }
    .btn-edit:hover { background: var(--accent-light); border-color: var(--accent) }
    .btn-ghost-danger { background: transparent; color: var(--danger); border: 1px solid var(--danger-border); font-size: 12px; padding: 6px 12px }
    .btn-ghost-danger:hover { background: var(--danger-bg) }

    /* ─── Table ─── */
    .table-wrap { border: 1px solid var(--border); border-radius: var(--radius-md); overflow: hidden; margin-top: 16px }
    table.tbl { width: 100%; border-collapse: collapse; font-size: 13.5px }
    table.tbl thead tr { background: var(--bg-surface) }
    table.tbl th { padding: 11px 16px; font-size: 13px; font-weight: 700; color: var(--text-secondary); text-align: left; border-bottom: 1px solid var(--border) }
    table.tbl td { padding: 13px 16px; border-bottom: 1px solid var(--border); vertical-align: middle }
    table.tbl tr:last-child td { border-bottom: none }
    table.tbl tbody tr { transition: background var(--transition) }
    table.tbl tbody tr:hover td { background: var(--bg-hover) }
    .policy-name { font-weight: 600 }
    .badge { display: inline-flex; align-items: center; gap: 4px; padding: 3px 9px; border-radius: var(--radius-full); font-size: 11.5px; font-weight: 600 }
    .badge-sku  { background: rgba(59,130,246,.1); color: #2563eb; border: 1px solid rgba(59,130,246,.25) }
    .badge-grp  { background: rgba(16,185,129,.1); color: #059669; border: 1px solid rgba(16,185,129,.25) }
    .badge-best { display: inline-flex; align-items: center; gap: 4px; font-size: 11.5px; font-weight: 700; color: var(--success) }
    .txt-muted  { color: var(--text-muted); font-size: 12px }
    .tiers-qty  { color: var(--text-secondary); font-size: 13px }
    .tiers-val  { color: var(--text-primary); font-weight: 600; font-size: 12.5px }

    /* ─── Empty state ─── */
    .empty-state { padding: 56px 24px; text-align: center; color: var(--text-muted) }
    .empty-icon  { font-size: 38px; margin-bottom: 12px; opacity: .5 }
    .empty-title { font-size: 14px; font-weight: 600; color: var(--text-secondary); margin-bottom: 4px }
    .empty-desc  { font-size: 12.5px }

    /* ─── Form card ─── */
    .form-card { background: var(--bg-card); border: 1px solid var(--border); border-radius: var(--radius-lg); padding: 28px }
    .form-section-title { font-size: 16px; font-weight: 700; margin-bottom: 20px }
    .fg { display: flex; flex-direction: column; gap: 5px; margin-bottom: 16px }
    .fg-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px }
    .fl { font-size: 13px; font-weight: 600 }
    .fl .req { color: var(--danger); margin-left: 2px }
    .field-err { font-size: 11.5px; color: var(--danger); margin-top: 3px; display: none }
    .field-err.show { display: block }

    /* ─── Alert ─── */
    .alert { padding: 10px 14px; border-radius: var(--radius-sm); font-size: 13px; font-weight: 500; margin-bottom: 14px; display: none; align-items: center; gap: 8px }
    .alert.show { display: flex }
    .alert-danger  { background: var(--danger-bg); border: 1px solid var(--danger-border); color: #dc2626 }

    /* ─── Tier rows ─── */
    .tiers-header { font-size: 14px; font-weight: 700; margin-bottom: 12px }
    .tier-row { display: grid; grid-template-columns: 1fr 1fr auto; gap: 12px; align-items: end; margin-bottom: 12px; animation: rowIn .18s ease both }
    @keyframes rowIn { from { opacity:0; transform:translateY(-6px) } to { opacity:1; transform:translateY(0) } }
    .tier-lbl { font-size: 12.5px; font-weight: 600; color: var(--text-secondary); margin-bottom: 4px }
    .add-tier-btn {
      display: inline-flex; align-items: center; gap: 6px;
      background: none; border: none; font-family: inherit;
      font-size: 13px; font-weight: 600; color: var(--accent);
      cursor: pointer; padding: 4px 0; margin-top: 4px; transition: color var(--transition);
    }
    .add-tier-btn:hover { color: #e05310 }

    /* ─── Form actions ─── */
    .form-actions { display: flex; gap: 10px; margin-top: 20px; flex-wrap: wrap }

    /* ─── Check tab ─── */
    .check-card { background: var(--bg-card); border: 1px solid var(--border); border-radius: var(--radius-lg); overflow: hidden }
    .check-inputs { display: grid; grid-template-columns: 2fr 1fr 1fr; gap: 16px; padding: 24px 28px; border-bottom: 1px solid var(--border); align-items: end }
    .check-btn-row { padding: 0 28px 20px }
    .check-banner { padding: 12px 28px; border-bottom: 1px solid rgba(59,130,246,.15); font-size: 13px; color: #2563eb; font-weight: 600; display: none; align-items: center; gap: 8px }
    .check-banner.show { display: flex }
    .check-result { padding: 0 28px 28px; margin-top: 16px }
    .check-summary {
      display: grid; grid-template-columns: repeat(3,1fr);
      gap: 1px; background: var(--border);
      border: 1px solid var(--border); border-radius: var(--radius-md);
      overflow: hidden; margin-top: 16px;
    }
    .sum-cell   { background: var(--bg-card); padding: 18px 22px; display: flex; flex-direction: column; gap: 3px }
    .sum-label  { font-size: 12px; color: var(--text-secondary) }
    .sum-value  { font-size: 20px; font-weight: 800 }
    .sum-value.c-accent  { color: var(--accent) }
    .sum-value.c-success { color: var(--success) }
    table.cmp-tbl { width: 100%; border-collapse: collapse; font-size: 13px; margin-top: 16px }
    table.cmp-tbl thead tr { background: var(--bg-surface) }
    table.cmp-tbl th { padding: 10px 14px; font-size: 12.5px; font-weight: 700; color: var(--text-secondary); text-align: left; border-bottom: 1px solid var(--border) }
    table.cmp-tbl td { padding: 12px 14px; border-bottom: 1px solid var(--border); vertical-align: middle }
    table.cmp-tbl tr:last-child td { border-bottom: none }

    /* ─── Toast ─── */
    .toast-wrap { position: fixed; top: 20px; right: 24px; z-index: 9999; display: flex; flex-direction: column; gap: 8px }
    .toast {
      display: flex; align-items: center; gap: 10px;
      padding: 12px 18px; border-radius: var(--radius-sm);
      font-size: 13px; font-weight: 600;
      box-shadow: 0 8px 24px rgba(0,0,0,.14);
      animation: tIn .22s ease both; min-width: 260px; max-width: 360px;
    }
    @keyframes tIn  { from { opacity:0; transform:translateX(24px) } to { opacity:1; transform:translateX(0) } }
    @keyframes tOut { from { opacity:1 } to { opacity:0; transform:translateX(24px) } }
    .toast.success { background: #ecfdf5; border: 1px solid rgba(16,185,129,.35); color: #059669 }
    .toast.error   { background: #fef2f2; border: 1px solid rgba(239,68,68,.35);  color: #dc2626 }

    /* ─── Utility ─── */
    .hidden { display: none !important }

    /* ─── Responsive ─── */
    @media (max-width: 768px) {
      .sidebar { display: none }
      .content-area { padding: 20px 16px }
      .top-bar { padding: 12px 16px }
      .check-inputs { grid-template-columns: 1fr }
      .fg-row { grid-template-columns: 1fr }
      .tier-row { grid-template-columns: 1fr 1fr }
      .tier-row > div:last-child { grid-column: 1 / -1 }
    }
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

      <!-- ══ VIEW: DANH SÁCH ══ -->
      <div id="vList">
        <div>
          <h1 class="page-title">Chiết khấu theo số lượng</h1>
          <p class="page-sub">Thiết lập mức ưu đãi theo SKU hoặc nhóm hàng.</p>
        </div>

        <!-- Tabs -->
        <div class="tab-bar">
          <button class="tab-btn active" id="tBtnPolicy" onclick="switchTab('policy')">Chính sách</button>
          <button class="tab-btn" id="tBtnCheck" onclick="switchTab('check')">Kiểm tra áp dụng</button>
        </div>

        <!-- Info -->
        <div class="info-banner">
          <span style="font-size:14px;flex-shrink:0">ℹ</span>
          <span>Khi nhiều chính sách cùng áp dụng, chọn mức chiết khấu <strong>có lợi nhất</strong> cho khách hàng. Không cộng dồn.</span>
        </div>

        <!-- ── Sub: Danh sách chính sách ── -->
        <div id="sPolicyList">
          <div class="card">
            <div class="card-body">
              <!-- Toolbar -->
              <div class="toolbar">
                <div class="toolbar-left">
                  <div class="field-wrap grow">
                    <span class="field-label">Tìm kiếm</span>
                    <input id="searchInput" type="text" class="fc" placeholder="Tên chính sách, SKU...">
                  </div>
                  <div class="field-wrap">
                    <span class="field-label">Phạm vi</span>
                    <select id="filterScope" class="fc" style="min-width:140px">
                      <option value="">Tất cả</option>
                      <option value="SKU">SKU</option>
                      <option value="GROUP">Nhóm hàng</option>
                    </select>
                  </div>
                </div>
                <button class="btn btn-primary" onclick="openAdd()">+ Thêm chính sách</button>
              </div>

              <!-- Table -->
              <div class="table-wrap">
                <table class="tbl" id="policyTable">
                  <thead>
                    <tr>
                      <th>Chính sách</th>
                      <th>Phạm vi áp dụng</th>
                      <th>Bậc số lượng</th>
                      <th>Chiết khấu</th>
                      <th>Thao tác</th>
                    </tr>
                  </thead>
                  <tbody id="tBody"></tbody>
                </table>
                <div class="empty-state hidden" id="emptyState">
                  <div class="empty-icon">📋</div>
                  <div class="empty-title">Chưa có chính sách nào</div>
                  <div class="empty-desc">Nhấn <strong>+ Thêm chính sách</strong> để tạo mới.</div>
                </div>
              </div>
            </div>
          </div>
        </div>

        </div>
      </div>
    </div>
  </div>
</div>
</body>
</html>