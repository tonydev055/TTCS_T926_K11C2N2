package controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import service.XuatKhoService;
import util.XuLyJson;

/** GET /api/shipments, POST /api/shipments/{orderId} {lines:[{lineId,actualQuantity}],reason}. */
@WebServlet("/api/shipments/*")
public class XuatKhoServlet extends NghiepVuKhoServlet {
    private final XuatKhoService service = new XuatKhoService();
    @Override protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException {
        try {if(!path(q).equals("/"))throw new NoSuchElementException("Không tìm thấy chức năng");send(r,service.list(user(q),roles(q)));}
        catch(Exception e){fail(r,e);}
    }
    @Override protected void doPost(HttpServletRequest q,HttpServletResponse r)throws IOException {
        try {String p=path(q);if(!p.matches("/\\d+"))throw new NoSuchElementException("Không tìm thấy đơn hàng");
            service.confirm(id(p.substring(1)),user(q),roles(q),XuLyJson.object(body(q)));
            send(r,Map.of("message","Đã xác nhận xuất kho"));}
        catch(Exception e){fail(r,e);}
    }
}
