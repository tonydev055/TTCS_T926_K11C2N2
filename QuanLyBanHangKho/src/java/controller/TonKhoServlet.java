package controller;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import service.TonKhoService;
/** S5-05: GET /api/inventory?q=&warehouseId=&categoryId=&state=&page=&pageSize=; /masters; /ledger?warehouseId=&productId=. Không ghi/xoá tồn trực tiếp. */
@WebServlet("/api/inventory/*")
public class TonKhoServlet extends NghiepVuKhoServlet {
    private final TonKhoService service=new TonKhoService();
    protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException{try{
        if(path(q).equals("/masters"))send(r,service.masters(user(q),roles(q)));
        else if(path(q).equals("/ledger"))send(r,service.ledger(id(q.getParameter("warehouseId")),id(q.getParameter("productId")),user(q),roles(q)));
        else if(path(q).equals("/")){Map<String,String> filters=new HashMap<>();for(String key:List.of("q","warehouseId","categoryId","state","page","pageSize"))filters.put(key,q.getParameter(key));send(r,service.list(user(q),roles(q),filters));}
        else throw new NoSuchElementException("Không tìm thấy chức năng");
    }catch(Exception e){fail(r,e);}}
}
