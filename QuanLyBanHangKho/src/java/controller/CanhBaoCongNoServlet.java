package controller;
import jakarta.servlet.annotation.WebServlet;import jakarta.servlet.http.*;import java.io.IOException;import service.CanhBaoCongNoService;import util.XuLyJson;
@WebServlet("/api/credit-alerts/*") public final class CanhBaoCongNoServlet extends TaiChinhServlet {
 protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException{try{send(r,new CanhBaoCongNoService().list());}catch(Exception e){fail(r,e);}}
 protected void doPost(HttpServletRequest q,HttpServletResponse r)throws IOException{try{checkWrite(q);new CanhBaoCongNoService().policy(user(q),XuLyJson.object(body(q)));send(r,new CanhBaoCongNoService().list());}catch(Exception e){fail(r,e);}}
}
