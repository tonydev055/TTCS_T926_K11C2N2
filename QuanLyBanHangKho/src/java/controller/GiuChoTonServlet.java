package controller;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import service.HetHanGiuChoService;
import service.KhoService;
import util.XuLyJson;
/** GET /api/reservations; GET/PUT /settings {holdHours}; deadline mới không thay đổi giữ chỗ hiện tại. */
@WebServlet("/api/reservations/*")
public class GiuChoTonServlet extends NghiepVuKhoServlet {
    private final HetHanGiuChoService service=new HetHanGiuChoService();
    protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException{try{
        if(path(q).equals("/settings"))send(r,service.settings());
        else if(path(q).equals("/"))send(r,service.list(user(q),roles(q)));
        else throw new NoSuchElementException("Không tìm thấy chức năng");
    }catch(Exception e){fail(r,e);}}
    protected void doPut(HttpServletRequest q,HttpServletResponse r)throws IOException{try{
        if(!path(q).equals("/settings"))throw new NoSuchElementException("Không tìm thấy chức năng");
        var in=XuLyJson.object(body(q));long hours=KhoService.id(in.get("holdHours"));if(hours>168)throw new IllegalArgumentException("Thời hạn tối đa 168 giờ");service.settings((int)hours,user(q),roles(q));send(r,service.settings());
    }catch(Exception e){fail(r,e);}}
}

