package controller;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import service.SoCongNoService;
import util.XuatExcelUtil;

@WebServlet("/api/receivables/*")
public final class SoCongNoServlet extends TaiChinhServlet {
 @Override @SuppressWarnings("unchecked") protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException{
  try{String p=path(q);if(!Set.of("/aging","/export").contains(p))throw new NoSuchElementException("Không tìm thấy báo cáo");
   var data=new SoCongNoService().aging(Objects.toString(q.getParameter("q"),""),Objects.toString(q.getParameter("region"),""),Objects.toString(q.getParameter("age"),""),"true".equals(q.getParameter("overdue")));
   if(p.equals("/aging")){send(r,data);return;}
   List<String> fields=List.of("code","name","region","ar","collected","outstanding","d0","d31","d61","d91","overdue");var rows=new ArrayList<List<Object>>();
   for(var row:(List<Map<String,Object>>)data.get("dealers"))rows.add(fields.stream().map(row::get).toList());
   var totals=(Map<String,Object>)data.get("summary");rows.add(fields.stream().map(f->f.equals("code")?(Object)"Tổng cộng":totals.getOrDefault(f,"")).toList());
   byte[] bytes=XuatExcelUtil.workbook(List.of("Mã đại lý","Tên đại lý","Khu vực","Phải thu","Đã thu","Còn nợ","0–30 ngày","31–60 ngày","61–90 ngày","Trên 90 ngày","Quá hạn"),rows);
   r.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");r.setHeader("Content-Disposition","attachment; filename=cong-no-tuoi-no.xlsx");r.setContentLength(bytes.length);r.getOutputStream().write(bytes);
  }catch(Exception e){fail(r,e);}
 }
}
