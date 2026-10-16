package controller;import jakarta.servlet.annotation.WebServlet;import jakarta.servlet.http.*;import java.io.IOException;import service.FefoService;
/** GET /api/fefo/{orderId}: gợi ý base quantity từng lô; POST shipments nhận allocations [{binId,quantity}] mỗi dòng, allocationReason bắt buộc. */
@WebServlet("/api/fefo/*") public class FefoServlet extends NghiepVuKhoServlet{protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException{try{send(r,new FefoService().suggest(id(path(q).substring(1)),user(q),roles(q)));}catch(Exception e){fail(r,e);}}}
