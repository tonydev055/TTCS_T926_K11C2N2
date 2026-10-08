package service;

import db.KhachHangDB;
import db.TruyVanDB;
import java.sql.*;
import java.util.*;
import security.PhanQuyen;
import util.KetNoiDB;

/**
 * Nghiệp vụ hồ sơ đại lý (S3-03), điểm giao hàng (S3-04), hạn mức công nợ (S3-05)
 * phân công nhân viên kinh doanh (S3-06) và khoá giao dịch (S3-07).
 * Người có quyền customers.read thấy mọi đại lý; người chỉ có customers.assigned
 * (Nhân viên kinh doanh) chỉ thấy đại lý mình phụ trách.
 */
public class KhachHangService {

    public static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");

    /** Người đang thao tác, lấy từ phiên đã xác thực. */
    public record NguoiThaoTac(long id, List<String> roles) {
        public boolean can(String permission) {
            return PhanQuyen.allows(roles, permission);
        }

        public boolean seesAll() {
            return can("customers.read");
        }

        /** null khi được xem mọi đại lý; ngược lại là ID nhân viên phụ trách được phép xem. */
        public Long onlyRep() {
            return seesAll() ? null : id;
        }
    }

    private final KhachHangDB db = new KhachHangDB();

    public Map<String, Object> list(NguoiThaoTac u, Map<String, String> q) throws SQLException {
        int page = intParam(q.get("page"), 1, 1, 100_000);
        int size = intParam(q.get("size"), 20, 1, 100);
        String status = Objects.toString(q.get("status"), "").trim().toUpperCase(Locale.ROOT);
        if (!status.isEmpty() && !STATUSES.contains(status)) throw new IllegalArgumentException("Trạng thái lọc không hợp lệ");
        var filter = new KhachHangDB.Filter(
            limit(q.get("search"), 100),
            limit(q.get("region"), 150),
            optionalId(q.get("groupId")),
            optionalId(q.get("salesRepId")),
            status,
            u.onlyRep()
        );
        try (Connection c = KetNoiDB.getConnection()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("items", db.list(c, filter, page, size));
            out.put("total", db.count(c, filter));
            out.put("page", page);
            out.put("size", size);
            return out;
        }
    }

    public Map<String, Object> masters(NguoiThaoTac u) throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("groups", db.groups(c));
            out.put("salesReps", db.salesReps(c, u.onlyRep()));
            out.put("regions", db.regions(c, u.onlyRep()));
            out.put("canWrite", u.can("customers.write"));
            out.put("canEditCredit", u.can("customers.credit"));
            out.put("canAssign", u.can("customers.assign"));
            out.put("canLock", u.can("customers.lock"));
            return out;
        }
    }

    public Map<String, Object> detail(NguoiThaoTac u, long id) throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) {
            var row = visible(c, u, id);
            Map<String, Object> out = new LinkedHashMap<>(row);
            out.put("price_list", db.currentPriceList(c, ((Number) row.get("customer_group_id")).longValue()));
            out.put("has_transactions", db.hasTransactions(c, id));
            out.put("open_orders", db.openOrderCount(c, id));
            return out;
        }
    }

    public long create(NguoiThaoTac u, Map<String, Object> data) throws SQLException {
        requireWrite(u);
        try (Connection c = KetNoiDB.getConnection()) {
            begin(c, u);
            try {
                var values = validate(c, data);
                Long rep = toLong(values.get("sales_rep_id"));
                if (rep != null && !u.can("customers.assign"))
                    throw new SecurityException("Chỉ Quản lý kinh doanh được phân công nhân viên phụ trách");
                long id = db.insert(c, values);
                if (rep != null) db.insertAssignment(c, id, null, rep, "Phân công khi tạo đại lý", null, u.id());
                c.commit();
                return id;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    public void update(NguoiThaoTac u, long id, Map<String, Object> data) throws SQLException {
        requireWrite(u);
        try (Connection c = KetNoiDB.getConnection()) {
            begin(c, u);
            try {
                var old = db.findForUpdate(c, id);
                if (old == null) throw new NoSuchElementException("Không tìm thấy đại lý");
                var values = validate(c, data);
                // Người phụ trách chỉ đổi qua API phân công (có lý do và lịch sử); sửa hồ sơ giữ nguyên.
                values.put("sales_rep_id", old.get("sales_rep_id"));
                if (!Objects.equals(old.get("code"), values.get("code")) && db.hasTransactions(c, id))
                    throw new IllegalArgumentException("Đại lý đã phát sinh giao dịch, không thể đổi mã đại lý");
                db.update(c, id, values);
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** Chỉ xoá được đại lý chưa phát sinh giao dịch; đại lý đã có đơn chỉ được chuyển sang Ngừng giao dịch. */
    public void delete(NguoiThaoTac u, long id) throws SQLException {
        requireWrite(u);
        try (Connection c = KetNoiDB.getConnection()) {
            begin(c, u);
            try {
                if (db.findForUpdate(c, id) == null) throw new NoSuchElementException("Không tìm thấy đại lý");
                if (db.hasTransactions(c, id)) throw new IllegalArgumentException(
                    "Đại lý đã phát sinh giao dịch, không thể xoá. Hãy chuyển trạng thái sang Ngừng giao dịch"
                );
                db.delete(c, id);
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    // ----- S3-04: điểm giao hàng -----

    public static final int MAX_ADDRESSES = 50;

    public List<Map<String, Object>> addresses(NguoiThaoTac u, long customerId) throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) {
            visible(c, u, customerId);
            return db.addresses(c, customerId);
        }
    }

    public long createAddress(NguoiThaoTac u, long customerId, Map<String, Object> data) throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) {
            begin(c, u);
            try {
                lockForAddressWrite(c, u, customerId);
                var values = validateAddress(data);
                int count = db.activeAddressCount(c, customerId);
                if (count >= MAX_ADDRESSES) throw new IllegalArgumentException("Mỗi đại lý có tối đa " + MAX_ADDRESSES + " điểm giao hàng");
                boolean makeDefault = count == 0 || Boolean.TRUE.equals(data.get("is_default"));
                long id = db.insertAddress(c, customerId, values, false);
                if (makeDefault) db.setDefaultAddress(c, customerId, id);
                c.commit();
                return id;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    public void updateAddress(NguoiThaoTac u, long customerId, long addressId, Map<String, Object> data) throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) {
            begin(c, u);
            try {
                lockForAddressWrite(c, u, customerId);
                if (db.addressForUpdate(c, customerId, addressId) == null) throw new NoSuchElementException("Không tìm thấy điểm giao hàng");
                db.updateAddress(c, addressId, validateAddress(data));
                if (Boolean.TRUE.equals(data.get("is_default"))) db.setDefaultAddress(c, customerId, addressId);
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    public void setDefaultAddress(NguoiThaoTac u, long customerId, long addressId) throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) {
            begin(c, u);
            try {
                lockForAddressWrite(c, u, customerId);
                if (db.addressForUpdate(c, customerId, addressId) == null) throw new NoSuchElementException("Không tìm thấy điểm giao hàng");
                db.setDefaultAddress(c, customerId, addressId);
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /**
     * Gỡ điểm giao. Điểm đã dùng trong đơn được giữ lại (ẩn khỏi danh sách) để đơn cũ vẫn tra cứu được.
     * Nếu gỡ điểm mặc định, điểm còn lại cũ nhất trở thành mặc định.
     */
    public void deleteAddress(NguoiThaoTac u, long customerId, long addressId) throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) {
            begin(c, u);
            try {
                lockForAddressWrite(c, u, customerId);
                var old = db.addressForUpdate(c, customerId, addressId);
                if (old == null) throw new NoSuchElementException("Không tìm thấy điểm giao hàng");
                if (db.addressUsed(c, addressId)) db.deactivateAddress(c, addressId);
                else db.deleteAddress(c, addressId);
                if (Boolean.TRUE.equals(old.get("is_default"))) {
                    Long next = db.firstActiveAddress(c, customerId);
                    if (next != null) db.setDefaultAddress(c, customerId, next);
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /**
     * Kiểm tra điểm giao cho đơn hàng: phải là điểm đang dùng của đúng đại lý.
     * Database cũng chặn bằng khoá ngoại (orders.delivery_address_id, orders.agent_id).
     */
    public static void requireDeliveryAddress(Connection c, long customerId, long addressId) throws SQLException {
        if (TruyVanDB.one(
            c,
            "SELECT 1 FROM customer_addresses WHERE id=? AND customer_id=? AND active",
            addressId, customerId
        ) == null) throw new IllegalArgumentException("Điểm giao hàng không thuộc đại lý của đơn");
    }

    /** Ghi điểm giao: người khai báo hồ sơ đại lý, hoặc nhân viên kinh doanh phụ trách đại lý đó. */
    private void lockForAddressWrite(Connection c, NguoiThaoTac u, long customerId) throws SQLException {
        var row = db.findForUpdate(c, customerId);
        boolean owner = row != null && Objects.equals(toLong(row.get("sales_rep_id")), u.id()) && u.can("customers.assigned");
        if (row == null || (!u.seesAll() && !owner)) throw new NoSuchElementException("Không tìm thấy đại lý");
        if (!u.can("customers.write") && !owner)
            throw new SecurityException("Bạn không có quyền cập nhật điểm giao hàng của đại lý này");
    }

    static Map<String, Object> validateAddress(Map<String, Object> data) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("address", text(data, "address", "Địa chỉ giao hàng", true, 500));
        v.put("recipient_name", text(data, "recipient_name", "Người nhận", true, 150));
        v.put("phone", DanhMucService.phone(text(data, "phone", "Số điện thoại người nhận", true, 20), true));
        v.put("directions", text(data, "directions", "Ghi chú đường đi", false, 1000));
        Object isDefault = data.get("is_default");
        if (isDefault != null && !(isDefault instanceof Boolean)) throw new IllegalArgumentException("Giá trị mặc định không hợp lệ");
        return v;
    }

    // ----- S3-05: hạn mức công nợ -----

    public static final java.math.BigDecimal MAX_CREDIT_LIMIT = new java.math.BigDecimal("1000000000000");
    public static final int MAX_CREDIT_DAYS = 365;

    /** Đổi hạn mức tiền và số ngày nợ; bắt buộc lý do, ghi lịch sử và nhật ký trong cùng giao dịch. */
    public void updateCredit(NguoiThaoTac u, long customerId, Map<String, Object> data) throws SQLException {
        if (!u.can("customers.credit"))
            throw new SecurityException("Chỉ Kế toán công nợ và Quản lý kinh doanh được sửa hạn mức công nợ");
        java.math.BigDecimal limit = creditLimit(data.get("credit_limit"));
        int days = creditDays(data.get("credit_days"));
        String reason = text(data, "reason", "Lý do thay đổi", true, 500);
        try (Connection c = KetNoiDB.getConnection()) {
            begin(c, u);
            try {
                var old = db.findForUpdate(c, customerId);
                if (old == null || (!u.seesAll() && !Objects.equals(toLong(old.get("sales_rep_id")), u.id())))
                    throw new NoSuchElementException("Không tìm thấy đại lý");
                if (((java.math.BigDecimal) old.get("credit_limit")).compareTo(limit) == 0 &&
                    ((Number) old.get("credit_days")).intValue() == days)
                    throw new IllegalArgumentException("Hạn mức và số ngày nợ không thay đổi");
                db.insertCreditHistory(c, customerId, old, limit, days, reason, u.id());
                db.updateCredit(c, customerId, limit, days);
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    public List<Map<String, Object>> creditHistory(NguoiThaoTac u, long customerId) throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) {
            visible(c, u, customerId);
            return db.creditHistory(c, customerId);
        }
    }

    static java.math.BigDecimal creditLimit(Object raw) {
        try {
            var v = new java.math.BigDecimal(Objects.toString(raw, "").trim());
            if (v.signum() < 0 || v.stripTrailingZeros().scale() > 0 || v.compareTo(MAX_CREDIT_LIMIT) > 0) throw new NumberFormatException();
            return v.setScale(2);
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("Hạn mức công nợ phải là số tiền nguyên (đồng) từ 0 đến 1.000 tỷ");
        }
    }

    static int creditDays(Object raw) {
        String s = Objects.toString(raw, "").trim();
        if (!s.matches("[0-9]{1,3}") || Integer.parseInt(s) > MAX_CREDIT_DAYS)
            throw new IllegalArgumentException("Số ngày nợ tối đa phải là số nguyên từ 0 đến " + MAX_CREDIT_DAYS);
        return Integer.parseInt(s);
    }

    // ----- S3-06: phân công nhân viên kinh doanh -----

    /** Gán hoặc bỏ người phụ trách chính của một đại lý; bắt buộc lý do, ghi lịch sử. */
    public void assign(NguoiThaoTac u, long customerId, Map<String, Object> data) throws SQLException {
        requireAssign(u);
        Long rep = optionalId(data.get("sales_rep_id"));
        String reason = text(data, "reason", "Lý do phân công", true, 500);
        try (Connection c = KetNoiDB.getConnection()) {
            begin(c, u);
            try {
                var old = db.findForUpdate(c, customerId);
                if (old == null) throw new NoSuchElementException("Không tìm thấy đại lý");
                Long from = toLong(old.get("sales_rep_id"));
                if (Objects.equals(from, rep)) throw new IllegalArgumentException("Đại lý đã do nhân viên này phụ trách");
                if (rep != null && !db.isActiveSalesRep(c, rep))
                    throw new IllegalArgumentException("Người phụ trách phải là Nhân viên kinh doanh đang hoạt động");
                db.setSalesRep(c, customerId, rep);
                db.insertAssignment(c, customerId, from, rep, reason, null, u.id());
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /**
     * Chuyển giao hàng loạt đại lý từ một nhân viên (thường là người nghỉ việc) sang nhân viên khác.
     * Không truyền customer_ids thì chuyển toàn bộ đại lý của người cũ. Mọi dòng chung một mã đợt chuyển giao.
     */
    public Map<String, Object> transfer(NguoiThaoTac u, Map<String, Object> data) throws SQLException {
        requireAssign(u);
        Long from = optionalId(data.get("from_user_id"));
        Long to = optionalId(data.get("to_user_id"));
        if (from == null || to == null) throw new IllegalArgumentException("Vui lòng chọn nhân viên bàn giao và nhân viên nhận");
        if (from.equals(to)) throw new IllegalArgumentException("Nhân viên nhận phải khác nhân viên bàn giao");
        String reason = text(data, "reason", "Lý do chuyển giao", true, 500);
        List<Long> only = null;
        if (data.get("customer_ids") != null) {
            if (!(data.get("customer_ids") instanceof List<?> raw) || raw.isEmpty() || raw.size() > 5000)
                throw new IllegalArgumentException("Danh sách đại lý chuyển giao không hợp lệ");
            only = new ArrayList<>();
            for (Object item : raw) only.add(optionalId(item));
            if (only.contains(null)) throw new IllegalArgumentException("Danh sách đại lý chuyển giao không hợp lệ");
        }
        try (Connection c = KetNoiDB.getConnection()) {
            begin(c, u);
            try {
                if (!db.isActiveSalesRep(c, to))
                    throw new IllegalArgumentException("Nhân viên nhận phải là Nhân viên kinh doanh đang hoạt động");
                var ids = db.customersOfRepForUpdate(c, from, only);
                if (ids.isEmpty()) throw new IllegalArgumentException("Nhân viên bàn giao không còn đại lý nào để chuyển");
                if (only != null && ids.size() != new HashSet<>(only).size())
                    throw new IllegalArgumentException("Có đại lý không thuộc nhân viên bàn giao; hãy tải lại danh sách");
                UUID batch = UUID.randomUUID();
                for (long id : ids) {
                    db.setSalesRep(c, id, to);
                    db.insertAssignment(c, id, from, to, reason, batch, u.id());
                }
                c.commit();
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("transferred", ids.size());
                out.put("batch", batch.toString());
                return out;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    public List<Map<String, Object>> assignmentHistory(NguoiThaoTac u, long customerId) throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) {
            visible(c, u, customerId);
            return db.assignmentHistory(c, customerId);
        }
    }

    public List<Map<String, Object>> repWorkload(NguoiThaoTac u) throws SQLException {
        requireAssign(u);
        try (Connection c = KetNoiDB.getConnection()) {
            return db.repWorkload(c);
        }
    }

    /** Số đại lý một người dùng đang phụ trách, dùng để cảnh báo bàn giao khi khoá tài khoản (S1-10). */
    public static long assignedCustomerCount(long userId) throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) {
            return new KhachHangDB().countCustomersOfRep(c, userId);
        }
    }

    private static void requireAssign(NguoiThaoTac u) {
        if (!u.can("customers.assign"))
            throw new SecurityException("Chỉ Quản lý kinh doanh được phân công nhân viên phụ trách đại lý");
    }

    // ----- S3-07: khoá giao dịch -----

    /** Khoá giao dịch (bắt buộc lý do) hoặc mở lại. Trả về số đơn đang dở để giao diện cảnh báo. */
    public Map<String, Object> setTradingLock(NguoiThaoTac u, long customerId, boolean lock, Map<String, Object> data)
        throws SQLException {
        if (!u.can("customers.lock")) throw new SecurityException("Bạn không có quyền khoá hoặc mở giao dịch với đại lý");
        String reason = text(data, "reason", lock ? "Lý do khoá" : "Ghi chú mở khoá", lock, 500);
        try (Connection c = KetNoiDB.getConnection()) {
            begin(c, u);
            try {
                var old = db.findForUpdate(c, customerId);
                if (old == null) throw new NoSuchElementException("Không tìm thấy đại lý");
                if (Boolean.TRUE.equals(old.get("trading_locked")) == lock)
                    throw new IllegalArgumentException(lock ? "Đại lý đang bị khoá giao dịch" : "Đại lý đang được giao dịch bình thường");
                db.setTradingLock(c, customerId, lock, reason, u.id());
                db.insertLockHistory(c, customerId, lock ? "LOCK" : "UNLOCK", reason, u.id());
                long open = db.openOrderCount(c, customerId);
                c.commit();
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("trading_locked", lock);
                out.put("open_orders", open);
                out.put("message", lock
                    ? (open > 0
                        ? "Đã khoá giao dịch. Đại lý còn " + open + " đơn đang xử lý; các đơn này vẫn xử lý tiếp nhưng sẽ có cảnh báo"
                        : "Đã khoá giao dịch với đại lý")
                    : "Đã mở lại giao dịch với đại lý");
                return out;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    public List<Map<String, Object>> lockHistory(NguoiThaoTac u, long customerId) throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) {
            visible(c, u, customerId);
            return db.lockHistory(c, customerId);
        }
    }

    /**
     * Dùng khi tạo đơn mới (S3-09, cổng đại lý): chặn đại lý bị khoá hoặc ngừng giao dịch.
     * Database cũng chặn bằng trigger orders_block_locked_customer.
     */
    public static void requireCanCreateOrder(Connection c, long customerId) throws SQLException {
        var row = TruyVanDB.one(c, "SELECT code,status,trading_locked,lock_reason FROM customers WHERE id=?", customerId);
        if (row == null) throw new NoSuchElementException("Không tìm thấy đại lý");
        if (Boolean.TRUE.equals(row.get("trading_locked")))
            throw new IllegalArgumentException("Đại lý đang bị khoá giao dịch (" + row.get("lock_reason") + "), không thể tạo đơn mới");
        if (!"ACTIVE".equals(row.get("status")))
            throw new IllegalArgumentException("Đại lý đã ngừng giao dịch, không thể tạo đơn mới");
    }

    /** Dùng khi xử lý đơn đang dở: trả về cảnh báo nếu đại lý đã bị khoá, null nếu bình thường. */
    public static String orderWarning(Connection c, long customerId) throws SQLException {
        var row = TruyVanDB.one(c, "SELECT trading_locked,lock_reason FROM customers WHERE id=?", customerId);
        return row != null && Boolean.TRUE.equals(row.get("trading_locked"))
            ? "Đại lý đang bị khoá giao dịch: " + row.get("lock_reason") + ". Kiểm tra kỹ trước khi xử lý tiếp đơn này"
            : null;
    }

    Map<String, Object> validate(Connection c, Map<String, Object> data) throws SQLException {
        Map<String, Object> v = new LinkedHashMap<>();
        String code = text(data, "code", "Mã đại lý", true, 40).toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z0-9][A-Z0-9_.-]{0,39}"))
            throw new IllegalArgumentException("Mã đại lý chỉ gồm chữ không dấu, số, dấu chấm, gạch ngang hoặc gạch dưới");
        v.put("code", code);
        v.put("name", text(data, "name", "Tên đại lý", true, 200));
        String tax = text(data, "tax_code", "Mã số thuế", false, 14);
        if (!tax.isEmpty() && !tax.matches("[0-9]{10}(-[0-9]{3})?"))
            throw new IllegalArgumentException("Mã số thuế gồm 10 chữ số, chi nhánh thêm -XXX (ví dụ 0101234567-001)");
        v.put("tax_code", tax);
        Long group = optionalId(data.get("customer_group_id"));
        if (group == null || !db.groupExists(c, group)) throw new IllegalArgumentException("Vui lòng chọn nhóm khách hàng hợp lệ");
        v.put("customer_group_id", group);
        v.put("region", text(data, "region", "Khu vực", true, 150));
        v.put("phone", DanhMucService.phone(text(data, "phone", "Số điện thoại", false, 20), false));
        Long rep = data.containsKey("sales_rep_id") ? optionalId(data.get("sales_rep_id")) : null;
        if (rep != null && !db.isActiveSalesRep(c, rep))
            throw new IllegalArgumentException("Người phụ trách phải là Nhân viên kinh doanh đang hoạt động");
        v.put("sales_rep_id", rep);
        String status = Objects.toString(data.getOrDefault("status", "ACTIVE"), "").trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) throw new IllegalArgumentException("Trạng thái đại lý không hợp lệ");
        v.put("status", status);
        return v;
    }

    /** Bản ghi đại lý nếu người dùng được phép xem; ngoài phạm vi trả về như không tồn tại. */
    Map<String, Object> visible(Connection c, NguoiThaoTac u, long id) throws SQLException {
        var row = db.find(c, id);
        if (row == null || (!u.seesAll() && !Objects.equals(toLong(row.get("sales_rep_id")), u.id())))
            throw new NoSuchElementException("Không tìm thấy đại lý");
        return row;
    }

    static void begin(Connection c, NguoiThaoTac u) throws SQLException {
        c.setAutoCommit(false);
        TruyVanDB.rows(c, "SELECT set_config('app.actor_id',?,true)", String.valueOf(u.id()));
    }

    private static void requireWrite(NguoiThaoTac u) {
        if (!u.can("customers.write")) throw new SecurityException("Bạn không có quyền khai báo hồ sơ đại lý");
    }

    static String text(Map<String, Object> data, String key, String label, boolean required, int max) {
        Object raw = data.get(key);
        if (raw != null && !(raw instanceof String)) throw new IllegalArgumentException(label + " không hợp lệ");
        String x = raw == null ? "" : ((String) raw).trim();
        if (required && x.isEmpty()) throw new IllegalArgumentException("Vui lòng nhập " + label.toLowerCase(Locale.ROOT));
        if (x.length() > max) throw new IllegalArgumentException(label + " tối đa " + max + " ký tự");
        return x;
    }

    static Long optionalId(Object raw) {
        String s = Objects.toString(raw, "").trim();
        if (s.isEmpty()) return null;
        if (!s.matches("[1-9][0-9]{0,17}")) throw new IllegalArgumentException("Mã tham chiếu không hợp lệ");
        return Long.parseLong(s);
    }

    static Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static String limit(String value, int max) {
        String s = Objects.toString(value, "").trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static int intParam(String raw, int fallback, int min, int max) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Math.min(max, Math.max(min, Integer.parseInt(raw.trim())));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Tham số phân trang không hợp lệ");
        }
    }
}
