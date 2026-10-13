package controller;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import service.CanhBaoTonService;
import util.XuLyJson;
/** GET /api/stock-alerts, /masters; PUT /{warehouseId}/{productId} {minimumQuantity}. */
@WebServlet("/api/stock-alerts/*")
public class CanhBaoTonServlet extends NghiepVuKhoServlet {
 private final CanhBaoTonService service=new CanhBaoTonService();
 protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException{try{
  if(path(q).equals("/"))send(r,service.list(user(q),roles(q)));
  else if(path(q).equals("/masters"))send(r,service.masters(user(q),roles(q)));
  else throw new NoSuchElementException("Không tìm thấy chức năng");
 }catch(Exception e){fail(r,e);}}
 protected void doPut(HttpServletRequest q,HttpServletResponse r)throws IOException{try{
  String[] p=path(q).split("/");if(p.length!=3)throw new NoSuchElementException("Không tìm thấy chức năng");
  var in=XuLyJson.object(body(q));service.save(id(p[1]),id(p[2]),user(q),roles(q),in.get("minimumQuantity"));send(r,Map.of("saved",true));
 }catch(Exception e){fail(r,e);}}
}
