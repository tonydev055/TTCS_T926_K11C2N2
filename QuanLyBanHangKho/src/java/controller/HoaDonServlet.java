package controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import service.HoaDonService;
import util.XuLyJson;

/** S7-01: danh sách, phát hành, xem/in và lập chứng từ điều chỉnh hóa đơn. */
@WebServlet("/api/invoices/*")
public final class HoaDonServlet extends TaiChinhServlet {
    private final HoaDonService service=new HoaDonService();
    @Override protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException{
        try{String p=path(q);if(p.equals("/")||p.isBlank())send(r,service.list());else send(r,service.detail(id(p.substring(1))));}
        catch(Exception e){fail(r,e);}
    }
    @Override protected void doPost(HttpServletRequest q,HttpServletResponse r)throws IOException{
        try{
            checkWrite(q);
            String p=path(q);Map<String,Object> in=XuLyJson.object(body(q));
            if(p.equals("/")||p.isBlank())created(r,XuLyJson.stringify(service.issue(user(q),in)));
            else{String[] parts=p.split("/");if(parts.length!=3||!parts[2].equals("adjustments"))throw new NoSuchElementException("Không tìm thấy chức năng");created(r,XuLyJson.stringify(service.adjust(id(parts[1]),user(q),in)));}
        }catch(Exception e){fail(r,e);}
    }
}
