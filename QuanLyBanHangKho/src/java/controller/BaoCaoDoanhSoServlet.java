package controller;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import service.BaoCaoDoanhSoService;
import util.XuatExcelUtil;
/** GET /api/sales-report[/export]?from=&to=&groupBy=employee|region|dealer|category|sku&compare=none|previous_period|previous_month|previous_year. */
@WebServlet("/api/sales-report/*")
public final class BaoCaoDoanhSoServlet extends NghiepVuKhoServlet {
 @Override @SuppressWarnings("unchecked") protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException{try{
  if(!Set.of("/","/export").contains(path(q)))throw new NoSuchElementException("Không tìm thấy báo cáo");
  var filters=new HashMap<String,String>();for(String key:List.of("from","to","groupBy","compare"))if(q.getParameter(key)!=null)filters.put(key,q.getParameter(key));
  var data=new BaoCaoDoanhSoService().report(roles(q),filters);if(path(q).equals("/")){send(r,data);return;}
  boolean margin=Boolean.TRUE.equals(data.get("canViewMargin"));var headers=new ArrayList<>(List.of("Nhóm","Doanh số","Kỳ trước","Tăng trưởng (%)","Sản lượng","Sản lượng kỳ trước","Tăng trưởng sản lượng (%)"));if(margin)headers.addAll(List.of("Giá vốn ước tính hiện tại","Biên lợi nhuận ước tính (%)"));
  var rows=new ArrayList<List<Object>>();rows.add(List.of("Từ ngày",data.get("from"),"Đến ngày",data.get("to"),"Nhóm",data.get("groupBy")));rows.add(List.of("Kỳ so sánh",data.get("prevFrom"),data.get("prevTo")));rows.add(new ArrayList<>(headers));
  var records=new ArrayList<>((List<Map<String,Object>>)data.get("items"));var total=new LinkedHashMap<>((Map<String,Object>)data.get("summary"));total.put("group_key","Tổng cộng");records.add(total);
  for(var row:records){var values=new ArrayList<Object>();for(String key:List.of("group_key","revenue","prev_revenue","revenue_growth_pct","quantity","prev_quantity","quantity_growth_pct"))values.add(Objects.requireNonNullElse(row.get(key),"—"));if(margin){values.add(row.get("cogs"));values.add(Objects.requireNonNullElse(row.get("gross_margin_pct"),"—"));}rows.add(values);}
  byte[] bytes=XuatExcelUtil.workbook(List.of("Báo cáo doanh số"),rows);r.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");r.setHeader("Content-Disposition","attachment; filename=bao-cao-doanh-so-"+data.get("groupBy")+"-"+data.get("from")+"-"+data.get("to")+".xlsx");r.setContentLength(bytes.length);r.getOutputStream().write(bytes);
 }catch(Exception e){fail(r,e);}}
}
