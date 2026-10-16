package controller;
import jakarta.servlet.annotation.WebServlet;import jakarta.servlet.http.*;import java.io.IOException;import java.util.*;import service.*;
/** GET /api/stock-card?warehouseId&productId&from&to&page; GET /documents/{movementId}. Số lượng đơn vị cơ sở. */
@WebServlet("/api/stock-card/*") public class TheKhoServlet extends NghiepVuKhoServlet {
 private final TheKhoService service=new TheKhoService();
 protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException{try{String p=path(q);if(p.equals("/masters"))send(r,service.masters(user(q),roles(q)));else if(p.startsWith("/documents/"))send(r,service.document(id(p.substring(11)),user(q),roles(q)));else if(p.equals("/")){int page;try{page=Integer.parseInt(Objects.toString(q.getParameter("page"),"1"));}catch(Exception e){throw new IllegalArgumentException("Trang không hợp lệ");}send(r,service.list(user(q),roles(q),id(q.getParameter("warehouseId")),id(q.getParameter("productId")),q.getParameter("from"),q.getParameter("to"),page));}else throw new NoSuchElementException("Không tìm thấy chức năng");}catch(Exception e){fail(r,e);}}
}
