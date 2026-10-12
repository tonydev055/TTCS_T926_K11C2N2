package service;

import db.KhachHangDB;
import db.TruyVanDB;
import java.sql.*;
import java.util.*;
import security.PhanQuyen;
import util.KetNoiDB;

/**
 * Nghiệp vụ hồ sơ đại lý (S3-03) và điểm giao hàng (S3-04).
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
            return out;
        }
    }

    public Map<String, Object> detail(NguoiThaoTac u, long id) throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) {
            var row = visible(c, u, id);
            Map<String, Object> out = new LinkedHashMap<>(row);
            out.put("price_list", db.currentPriceList(c, ((Number) row.get("customer_group_id")).longValue()));
            out.put("has_transactions", db.hasTransactions(c, id));
            return out;
        }
    }

    public long create(NguoiThaoTac u, Map<String, Object> data) throws SQLException {
        requireWrite(u);
        try (Connection c = KetNoiDB.getConnection()) {
            begin(c, u);
            try {
                var values = validate(c, data);
                long id = db.insert(c, values);
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
        Long rep = optionalId(data.get("sales_rep_id"));
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
