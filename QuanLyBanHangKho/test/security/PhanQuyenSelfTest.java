package security;

import java.util.List;

public class PhanQuyenSelfTest {

    public static void main(String[] args) {
        check(
            PhanQuyen.allows(List.of("ADMIN"), "admin.users"),
            "ADMIN phải quản lý được người dùng"
        );
        check(
            !PhanQuyen.allows(List.of("WAREHOUSE"), "admin.users"),
            "Nhân viên kho không được quản lý người dùng"
        );
        check(
            PhanQuyen.allows(List.of("SALES_MANAGER"), "products.cost"),
            "Quản lý kinh doanh được xem giá vốn"
        );
        check(
            !PhanQuyen.allows(List.of("SALES_REP"), "products.cost"),
            "Nhân viên kinh doanh không được xem giá vốn"
        );
        check(
            PhanQuyen.allows(List.of("ACCOUNTANT"), "receivables.read"),
            "Kế toán được xem công nợ"
        );
        check(
            PhanQuyen.allows(
                List.of("ADMIN"),
                PhanQuyen.permissionFor("/api/role-permissions", "GET")
            ),
            "ADMIN được xem ma trận phân quyền"
        );
        check(
            !PhanQuyen.allows(
                List.of("WAREHOUSE"),
                PhanQuyen.permissionFor("/api/role-permissions", "GET")
            ),
            "Nhân viên kho không được xem ma trận phân quyền"
        );
        check(
            "admin.audit".equals(PhanQuyen.permissionFor("/api/audit/api/profile", "GET")),
            "Tên endpoint chèn vào đường dẫn không được đổi quyền cần kiểm tra"
        );
        check(
            "admin.users".equals(PhanQuyen.permissionFor("/api/users/api/profile", "GET")),
            "Đường dẫn người dùng luôn cần quyền quản trị người dùng"
        );
        check(
            PhanQuyen.permissionFor("/api/profiles-export", "GET") == null,
            "Chỉ khớp trọn từng đoạn đường dẫn"
        );
        check(
            "products.read".equals(PhanQuyen.permissionFor("/api/discount-policies/quote", "POST")) &&
                "prices.write".equals(PhanQuyen.permissionFor("/api/discount-policies/quote/1", "POST")),
            "Chỉ đúng endpoint báo giá chiết khấu dùng quyền xem sản phẩm"
        );
        check(
            PhanQuyen.matrix().get("CUSTOMER").contains("profile.self"),
            "Ma trận phải có quyền hồ sơ cá nhân dùng chung"
        );
        check(
            PhanQuyen.knownPermissions().contains("admin.roles"),
            "Danh sách quyền chỉnh sửa phải chứa quyền quản lý phân quyền"
        );
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
