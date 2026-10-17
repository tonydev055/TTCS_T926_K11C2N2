package controller;
import jakarta.servlet.annotation.WebServlet;import jakarta.servlet.http.*;import java.io.IOException;import java.util.*;import service.NhacNoService;
@WebServlet({"/api/reminders/*","/api/dealer-reminders/*"}) public final class NhacNoServlet extends TaiChinhServlet {
 protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException{try{send(r,new NhacNoService().list(user(q),q.getServletPath().equals("/api/dealer-reminders")));}catch(Exception e){fail(r,e);}}
 protected void doPost(HttpServletRequest q,HttpServletResponse r)throws IOException{try{checkWrite(q);var s=new NhacNoService();String p=path(q);if(q.getServletPath().equals("/api/reminders")&&p.equals("/run"))send(r,Map.of("created",s.daily()));else if(q.getServletPath().equals("/api/dealer-reminders")&&p.matches("/[1-9][0-9]*/read")){s.read(user(q),id(p.split("/")[1]));send(r,Map.of("read",true));}else throw new NoSuchElementException("Không tìm thấy chức năng");}catch(Exception e){fail(r,e);}}
}
