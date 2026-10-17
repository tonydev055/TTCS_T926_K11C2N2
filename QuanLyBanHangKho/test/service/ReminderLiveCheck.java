package service;

import db.TruyVanDB;
import util.KetNoiDB;
import util.MatKhauUtil;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Kiểm tra chủ động do người dùng chạy; chỉ dùng database riêng, không tự gửi khi build. */
public final class ReminderLiveCheck {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) throw new IllegalArgumentException("Cần action, tên database kiểm thử và email nhận");
        String action=args[0], database=args[1], recipient=args[2];
        if (!database.matches("khoflow_s7_test_[0-9]{14}")) throw new IllegalArgumentException("Chỉ dùng database kiểm thử riêng");
        if (!recipient.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new IllegalArgumentException("Email nhận không hợp lệ");
        if (action.equals("create")) {
            try (Connection c=KetNoiDB.getConnection(); Statement s=c.createStatement()) { s.execute("CREATE DATABASE "+database); }
            System.out.println("Đã tạo database riêng: "+database); return;
        }
        if (!List.of("seed","preview","send").contains(action)) throw new IllegalArgumentException("Action không hợp lệ");
        try (Connection c=KetNoiDB.getConnection()) {
            if (!database.equals(TruyVanDB.one(c,"SELECT current_database() name").get("name"))) throw new IllegalStateException("DB_URL không trỏ đúng database kiểm thử");
            if (action.equals("seed")) {
                if (args.length!=4) throw new IllegalArgumentException("Cần danh sách migration");
                if (TruyVanDB.one(c,"SELECT to_regclass('public.users') name").get("name")!=null) throw new IllegalStateException("Chỉ khởi tạo database mới, không ghi đè dữ liệu");
                for (String migration:Files.readAllLines(Path.of(args[3]))) {
                    if (!migration.matches("[a-z0-9-]+\\.sql")) throw new IllegalArgumentException("Tên migration không hợp lệ");
                    try (Statement s=c.createStatement()) { s.execute(Files.readString(Path.of("database",migration))); }
                }
                c.setAutoCommit(false);
                try {
                    long dealer=TruyVanDB.id(TruyVanDB.one(c,"INSERT INTO users(username,email,full_name,password_hash) VALUES('s708.mail.check',?,'KIỂM THỬ S7-08 - không phải đại lý thật',?) RETURNING id",recipient,MatKhauUtil.encode(UUID.randomUUID().toString())));
                    long group=TruyVanDB.id(TruyVanDB.one(c,"INSERT INTO customer_groups(code,name) VALUES('S708-MAIL-CHECK','Nhóm kiểm thử email riêng') RETURNING id"));
                    long customer=TruyVanDB.id(TruyVanDB.one(c,"INSERT INTO customers(code,name,customer_group_id,region,portal_user_id,credit_days) VALUES('TEST-S708-MAIL','KIỂM THỬ - không phải khoản nợ thật',?,'Kiểm thử cục bộ',?,3) RETURNING id",group,dealer));
                    long actor=TruyVanDB.id(TruyVanDB.one(c,"SELECT u.id FROM users u JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id WHERE r.code='ADMIN' ORDER BY u.id LIMIT 1"));
                    KhoService.actor(c,actor);
                    for (int days:List.of(0,10)) TruyVanDB.update(c,"INSERT INTO invoices(invoice_number,customer_id,period_from,period_to,subtotal,discount_total,total_amount,issued_by,issued_at,note) VALUES(?,?,CURRENT_DATE-?,CURRENT_DATE-?, ?,0,?,?,CURRENT_TIMESTAMP-(? * INTERVAL '1 day'),'KIỂM THỬ S7-08 theo yêu cầu; không phải hóa đơn kinh doanh thật')",days==0?"TEST-S708-SOON":"TEST-S708-OVERDUE",customer,days,days,days==0?1250000:250000,days==0?1250000:250000,actor,days);
                    c.commit();
                } catch (Exception e) { c.rollback(); throw e; }
            }
            var invoices=TruyVanDB.rows(c,"SELECT i.invoice_number,i.total_amount,i.due_date::text due_date,u.email FROM invoices i JOIN customers c ON c.id=i.customer_id JOIN users u ON u.id=c.portal_user_id ORDER BY i.id");
            if (invoices.size()!=2 || invoices.stream().anyMatch(r->!recipient.equals(r.get("email")))) throw new IllegalStateException("Dừng: dữ liệu hoặc email nhận ngoài phạm vi kiểm thử");
            if (((Number)TruyVanDB.one(c,"SELECT count(*) n FROM mail_outbox WHERE recipient<>?",recipient).get("n")).longValue()!=0) throw new IllegalStateException("Dừng: có thư gửi người nhận khác");
            for (var invoice:invoices) System.out.println(invoice.get("invoice_number")+" | "+invoice.get("total_amount")+" VND | hạn "+invoice.get("due_date")+" | "+recipient);
        }
        if (action.equals("send")) {
            if (ThuDienTuService.developmentMailbox()) throw new IllegalStateException("Cần MAIL_MODE=smtp để gửi thật");
            SmtpService.settings(System.getenv());
            int created=new NhacNoService().daily();
            if (new NhacNoService().daily()!=0) throw new AssertionError("Lượt chạy thứ hai không được tạo trùng");
            System.out.println("Thông báo mới: "+created+"; chạy lại trong ngày: 0 (không trùng)");
            MailOutboxWorker.tick(); MailOutboxWorker.tick();
            try (Connection c=KetNoiDB.getConnection()) {
                var result=TruyVanDB.rows(c,"SELECT m.id,m.status,m.attempts,m.sent_at::text sent_at,m.last_error FROM payment_reminders r JOIN mail_outbox m ON m.id=r.mail_outbox_id ORDER BY m.id");
                for (var row:result) System.out.println(row);
                if (result.size()!=2 || result.stream().anyMatch(r->!"SENT".equals(r.get("status")))) throw new IllegalStateException("Chưa gửi đủ 2 thư: xem status/last_error ở trên; không kết luận đã thành công");
                System.out.println("ĐÃ GỬI 2 THƯ NHẮC NỢ QUA LUỒNG S7-08. Kiểm tra Inbox/Spam của email nhận.");
            }
        }
    }
}
