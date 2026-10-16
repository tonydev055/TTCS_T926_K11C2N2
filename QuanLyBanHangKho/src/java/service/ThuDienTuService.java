package service;

import db.TruyVanDB;
import java.sql.*;
import java.util.Map;

/** Queue mail in the same transaction as the account/token being created. */
public final class ThuDienTuService {
    private ThuDienTuService() {}
    public static boolean developmentMailbox() { return mode(System.getenv()).equals("dev"); }
    static String mode(Map<String, String> env) {
        String mode=env.getOrDefault("MAIL_MODE", "dev").trim();
        if (!mode.equals("dev") && !mode.equals("smtp"))
            throw new IllegalArgumentException("MAIL_MODE phải là dev hoặc smtp");
        return mode;
    }
    public static void passwordReset(Connection c,String email,String token,String applicationUrl) throws SQLException {
        String configured=System.getenv("APP_URL");
        String url=passwordResetUrl(configured==null||configured.isBlank()?applicationUrl:configured,token);
        enqueue(c,email,"Đặt lại mật khẩu KhoFlow",
            "Chúng tôi nhận được yêu cầu đặt lại mật khẩu. Liên kết này chỉ dùng một lần và hết hạn sau 30 phút.",url);
    }
    public static void activation(Connection c,String email,String temporaryPassword) throws SQLException {
        enqueue(c,email,"Kích hoạt tài khoản KhoFlow","Tài khoản của bạn đã được tạo. Mật khẩu tạm thời: "+temporaryPassword+
            "\nVui lòng đổi mật khẩu ngay khi đăng nhập lần đầu.",null);
    }
    static void enqueue(Connection c,String recipient,String subject,String body,String actionUrl) throws SQLException {
        enqueue(c,recipient,subject,body,actionUrl,System.getenv());
    }
    static void enqueue(Connection c,String recipient,String subject,String body,String actionUrl,Map<String,String> env) throws SQLException {
        enqueueTracked(c,recipient,subject,body,actionUrl,env);
    }
    static Map<String,Object> enqueueTracked(Connection c,String recipient,String subject,String body,String actionUrl,Map<String,String> env) throws SQLException {
        if(mode(env).equals("dev")) {
            var row=TruyVanDB.one(c,"INSERT INTO dev_mailbox_messages(recipient,subject,body,action_url) VALUES(?,?,?,?) RETURNING id",recipient,subject,body,actionUrl);
            return Map.of("mode","DEV","id",row.get("id"));
        } else {
            SmtpService.settings(env);
            var row=TruyVanDB.one(c,"INSERT INTO mail_outbox(recipient,subject,body,action_url,expires_at) VALUES(?,?,?,?,CURRENT_TIMESTAMP+INTERVAL '25 minutes') RETURNING id",recipient,subject,body,actionUrl);
            return Map.of("mode","SMTP","id",row.get("id"));
        }
    }
    static String passwordResetUrl(String applicationUrl,String token) {
        String base=applicationUrl.endsWith("/")?applicationUrl:applicationUrl+"/";
        return java.net.URI.create(base).resolve("index.html")+"?resetToken="+
            java.net.URLEncoder.encode(token,java.nio.charset.StandardCharsets.UTF_8);
    }
}
