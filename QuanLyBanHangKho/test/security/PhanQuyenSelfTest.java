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
            PhanQuyen.matrix().get("CUSTOMER").contains("profile.self"),
            "Ma trận phải có quyền hồ sơ cá nhân dùng chung"
        );
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
