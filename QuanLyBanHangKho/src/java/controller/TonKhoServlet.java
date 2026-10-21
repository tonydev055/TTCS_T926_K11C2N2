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
        else if(path(q).equals("/export"))export(q,r);
        else if(path(q).equals("/ledger"))send(r,service.ledger(id(q.getParameter("warehouseId")),id(q.getParameter("productId")),user(q),roles(q)));
        else if(path(q).equals("/")){Map<String,String> filters=new HashMap<>();for(String key:List.of("q","warehouseId","categoryId","state","page","pageSize"))filters.put(key,q.getParameter(key));send(r,service.list(user(q),roles(q),filters));}
        else throw new NoSuchElementException("Không tìm thấy chức năng");
    }catch(Exception e){fail(r,e);}}
    /** GET /api/inventory/export: cùng bộ lọc sổ tồn, xuất mọi trang, tối đa 50.000 dòng. */
    @SuppressWarnings("unchecked") private void export(HttpServletRequest q,HttpServletResponse r)throws Exception{
        var filters=new HashMap<String,String>();for(String key:List.of("q","warehouseId","categoryId","state"))filters.put(key,q.getParameter(key));
        var data=service.export(user(q),roles(q),filters);var rows=new ArrayList<List<Object>>();
        for(var row:(List<Map<String,Object>>)data.get("items"))rows.add(List.of(row.get("warehouse_code"),row.get("warehouse_name"),row.get("sku"),row.get("name"),row.get("base_unit"),row.get("on_hand"),row.get("reserved"),row.get("available")));
        byte[] bytes=util.XuatExcelUtil.workbook(List.of("Mã kho","Kho","SKU","Sản phẩm","Đơn vị cơ sở","Tồn thực tế","Tồn giữ chỗ","Tồn khả dụng"),rows);
        String code=data.get("warehouseCode").toString().replaceAll("[^A-Za-z0-9_.-]","_");
        r.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");r.setHeader("Content-Disposition","attachment; filename=ton-kho-"+code+"-"+java.time.LocalDate.now()+".xlsx");r.setContentLength(bytes.length);r.getOutputStream().write(bytes);
    }
}
