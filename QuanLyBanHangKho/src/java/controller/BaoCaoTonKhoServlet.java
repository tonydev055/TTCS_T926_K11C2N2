package controller;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import service.*;
import util.XuatExcelUtil;

/** GET /api/inventory-report[/masters|/export], quyền reports.inventory. */
@WebServlet("/api/inventory-report/*")
public final class BaoCaoTonKhoServlet extends NghiepVuKhoServlet {
    @Override @SuppressWarnings("unchecked") protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException{
        try{
            String p=path(q);
            if(p.equals("/masters")){send(r,new TonKhoService().masters(user(q),roles(q)));return;}
            if(!Set.of("/","/export").contains(p))throw new NoSuchElementException("Không tìm thấy báo cáo");
            var filters=new HashMap<String,String>();
            for(String key:List.of("from","to","warehouseId","categoryId","q","slowDays"))if(q.getParameter(key)!=null)filters.put(key,q.getParameter(key));
            var data=new BaoCaoTonKhoService().report(user(q),roles(q),filters);
            if(p.equals("/")){send(r,data);return;}
            var rows=new ArrayList<List<Object>>();
            rows.add(List.of("Kỳ",data.get("from"),data.get("to"),"Chụp lúc",data.get("asOf")));
            rows.add(List.of("Giá trị ước tính theo giá vốn danh mục hiện tại; số ngày tồn = tồn bình quân / xuất ròng × số ngày trong kỳ"));
            rows.add(new ArrayList<>(List.of("Kho","SKU","Sản phẩm","Nhóm hàng","ĐVT","Đầu kỳ","Cuối kỳ","Bình quân","Xuất ròng","Giá trị cuối kỳ","Vòng quay","Số ngày tồn","Chậm luân chuyển")));
            for(var row:(List<Map<String,Object>>)data.get("items")){
                var values=new ArrayList<Object>();for(String key:List.of("warehouse_name","sku","name","category_name","base_unit","opening","closing","average_stock","shipped","stock_value","turnover","inventory_days"))values.add(row.getOrDefault(key,"")==null?"Không phát sinh xuất":row.get(key));
                values.add(Boolean.TRUE.equals(row.get("slow"))?"Có":"Không");rows.add(values);
            }
            for(String group:List.of("byWarehouse","byCategory")){
                rows.add(List.of(group.equals("byWarehouse")?"Tổng hợp theo kho":"Tổng hợp theo nhóm hàng","Giá trị cuối kỳ","Giá trị bình quân"));
                for(var row:(Collection<Map<String,Object>>)data.get(group))rows.add(List.of(row.get("name"),row.get("stock_value"),row.get("average_value")));
            }
            byte[] bytes=XuatExcelUtil.workbook(List.of("Báo cáo tồn kho và vòng quay"),rows);
            r.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");r.setHeader("Content-Disposition","attachment; filename=bao-cao-ton-kho-"+java.time.LocalDate.now()+".xlsx");r.setContentLength(bytes.length);r.getOutputStream().write(bytes);
        }catch(Exception e){fail(r,e);}
    }
}
