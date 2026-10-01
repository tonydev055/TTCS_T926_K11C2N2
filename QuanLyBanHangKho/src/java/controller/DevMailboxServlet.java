package controller;
import dao.BaseDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;

@WebServlet("/api/dev-mailbox/*")
public class DevMailboxServlet extends BaseServlet {
    private final BaseDAO dao=new BaseDAO();
    protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException{
        String email=q.getParameter("email");
        if(email==null||email.isBlank()){error(r,400,"Vui lòng chọn địa chỉ hộp thư");return;}
        try{ok(r,jsonRows(dao.query("SELECT id,recipient,subject,body,action_url,created_at FROM dev_mailbox_messages WHERE lower(recipient)=lower(?) ORDER BY created_at DESC LIMIT 50",email.trim())));}
        catch(Exception e){error(r,500,"Không thể tải hộp thư thử nghiệm");}
    }
    protected void doDelete(HttpServletRequest q,HttpServletResponse r)throws IOException{
        String email=q.getParameter("email");
        if(email==null||email.isBlank()){error(r,400,"Vui lòng chọn địa chỉ hộp thư");return;}
        try{dao.update("DELETE FROM dev_mailbox_messages WHERE lower(recipient)=lower(?)",email.trim());ok(r,"{\"message\":\"Đã dọn hộp thư\"}");}
        catch(Exception e){error(r,500,"Không thể dọn hộp thư thử nghiệm");}
    }
}
