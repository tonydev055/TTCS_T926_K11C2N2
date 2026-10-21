package controller;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import service.BaoCaoQuaHanService;
import util.XuatExcelUtil;

/** GET /api/overdue-report[/masters|/export]?q=&region=&staffId=&bucket=, quyền receivables.read. */
@WebServlet("/api/overdue-report/*")
public final class BaoCaoQuaHanServlet extends TaiChinhServlet {
    @Override @SuppressWarnings("unchecked") protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException{
        try{
            var service=new BaoCaoQuaHanService();String p=path(q);
            if(p.equals("/masters")){send(r,service.masters());return;}
            if(!Set.of("/","/export").contains(p))throw new NoSuchElementException("Không tìm thấy báo cáo");
            var f=new HashMap<String,String>();for(String key:List.of("q","region","staffId","bucket"))if(q.getParameter(key)!=null)f.put(key,q.getParameter(key));
            var data=service.report(f);if(p.equals("/")){send(r,data);return;}
            var rows=new ArrayList<List<Object>>();rows.add(List.of("Tính đến",data.get("asOf"),"Tuổi quá hạn tính từ hạn thanh toán"));
            rows.add(List.of("Mã đại lý","Tên đại lý","Khu vực","Nhân viên phụ trách","1–30 ngày","31–60 ngày","61–90 ngày","Trên 90 ngày","Tổng quá hạn","Số ngày quá hạn lớn nhất"));
            for(var row:(List<Map<String,Object>>)data.get("items")){
                var values=new ArrayList<Object>();for(String key:List.of("code","name","region","staff_name","d1","d31","d61","d91","overdue","max_days"))values.add(Objects.requireNonNullElse(row.get(key),""));rows.add(values);
            }
            var summary=(Map<String,Object>)data.get("summary");var total=new ArrayList<Object>(List.of("Tổng cộng","","",""));BaoCaoQuaHanService.AMOUNTS.forEach(k->total.add(summary.get(k)));rows.add(total);
            rows.add(List.of("Nhân viên phụ trách","Số đại lý","1–30 ngày","31–60 ngày","61–90 ngày","Trên 90 ngày","Tổng quá hạn"));
            for(var row:(Collection<Map<String,Object>>)data.get("byStaff")){var values=new ArrayList<Object>(List.of(row.get("staff_name"),row.get("dealer_count")));BaoCaoQuaHanService.AMOUNTS.forEach(k->values.add(row.get(k)));rows.add(values);}
            byte[] bytes=XuatExcelUtil.workbook(List.of("Báo cáo công nợ quá hạn"),rows);
            r.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");r.setHeader("Content-Disposition","attachment; filename=cong-no-qua-han-"+data.get("asOf")+".xlsx");r.setContentLength(bytes.length);r.getOutputStream().write(bytes);
        }catch(Exception e){fail(r,e);}
    }
}
