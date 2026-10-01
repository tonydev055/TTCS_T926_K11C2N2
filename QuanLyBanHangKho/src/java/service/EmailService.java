package service;
import util.DBConnection;
import java.sql.*;
import java.util.logging.Logger;
public final class EmailService {
    private static final Logger LOG=Logger.getLogger(EmailService.class.getName());
    private EmailService(){}
    public static void passwordReset(String email,String token,String applicationUrl){
        String configured=System.getenv("APP_URL");
        String url=passwordResetUrl(configured==null||configured.isBlank()?applicationUrl:configured,token);
        save(email,"Đặt lại mật khẩu KhoFlow","Chúng tôi nhận được yêu cầu đặt lại mật khẩu. Liên kết này chỉ dùng một lần và hết hạn sau 30 phút.",url);
        LOG.info(() -> "Password reset for "+email+": "+url);
    }
    public static void activation(String email,String temporaryPassword){
        save(email,"Kích hoạt tài khoản KhoFlow","Tài khoản của bạn đã được tạo. Mật khẩu tạm thời: "+temporaryPassword,null);
        LOG.info(() -> "Activation for "+email+"; temporary password: "+temporaryPassword);
    }
    private static void save(String recipient,String subject,String body,String actionUrl){
        try(Connection c=DBConnection.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO dev_mailbox_messages(recipient,subject,body,action_url) VALUES(?,?,?,?)")){
            p.setString(1,recipient);p.setString(2,subject);p.setString(3,body);p.setString(4,actionUrl);p.executeUpdate();
        }catch(SQLException e){LOG.warning("Cannot save development email: "+e.getMessage());}
    }
    static String passwordResetUrl(String applicationUrl,String token){
        String base=applicationUrl.endsWith("/")?applicationUrl:applicationUrl+"/";
        return java.net.URI.create(base).resolve("index.html").toString()+"?resetToken="+java.net.URLEncoder.encode(token,java.nio.charset.StandardCharsets.UTF_8);
    }
}
