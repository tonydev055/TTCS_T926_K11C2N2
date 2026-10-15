package controller;
import jakarta.servlet.annotation.WebServlet;import jakarta.servlet.http.*;import java.io.IOException;import java.util.*;import service.LoHangService;import util.XuLyJson;
/** GET /api/lots?warehouseId=&state=&includeEmpty=true|false (mặc định ẩn lô tồn 0); PUT /products/{id} {lotManaged,alertDays}; PUT /{binId} {manufacturedDate}. */
@WebServlet("/api/lots/*") public class LoHangServlet extends NghiepVuKhoServlet {
 private final LoHangService service=new LoHangService();
 protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException{try{if(!path(q).equals("/"))throw new NoSuchElementException("Không tìm thấy chức năng");send(r,service.list(user(q),roles(q),q.getParameter("warehouseId"),q.getParameter("state"),"true".equals(q.getParameter("includeEmpty"))));}catch(Exception e){fail(r,e);}}
 protected void doPut(HttpServletRequest q,HttpServletResponse r)throws IOException{try{String p=path(q);var in=XuLyJson.object(body(q));if(p.matches("/products/\\d+"))service.settings(id(p.substring(10)),user(q),roles(q),in);else if(p.matches("/\\d+"))service.manufacture(id(p.substring(1)),user(q),roles(q),in);else throw new NoSuchElementException("Không tìm thấy chức năng");send(r,Map.of("message","Đã lưu thông tin lô"));}catch(Exception e){fail(r,e);}}
}
