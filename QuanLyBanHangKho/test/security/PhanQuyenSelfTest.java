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
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
