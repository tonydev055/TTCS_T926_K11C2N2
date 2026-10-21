package controller;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import service.SoChiTietCongNoService;
import util.XuatExcelUtil;

/** GET /api/debt-statement/masters?q=; /?customerId=&from=&to=; /export cùng bộ lọc. Quyền receivables.read. */
@WebServlet("/api/debt-statement/*")
public final class SoChiTietCongNoServlet extends TaiChinhServlet {
    @Override @SuppressWarnings("unchecked") protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException{
        try{
            var service=new SoChiTietCongNoService();String p=path(q);
            if(p.equals("/masters")){send(r,service.customers(q.getParameter("q")));return;}
            if(!Set.of("/","/export").contains(p))throw new NoSuchElementException("Không tìm thấy sổ công nợ");
            var data=service.report(id(q.getParameter("customerId")),q.getParameter("from"),q.getParameter("to"));
            if(p.equals("/")){send(r,data);return;}
            var party=(Map<String,Object>)data.get("customer");var rows=new ArrayList<List<Object>>();
            rows.add(List.of("Đại lý",party.get("code"),party.get("name")));rows.add(List.of("Từ ngày",data.get("from"),"Đến ngày",data.get("to")));
            rows.add(List.of("Ngày phát sinh","Chứng từ","Nghiệp vụ","Tăng phải thu","Giảm phải thu","Số dư lũy kế"));
            rows.add(List.of("","Số dư đầu kỳ","","","",data.get("openingBalance")));
            for(var row:(List<Map<String,Object>>)data.get("items"))rows.add(List.of(row.get("event_date"),row.get("reference"),kind(row.get("kind")),row.get("debit"),row.get("credit"),row.get("balance")));
            rows.add(List.of("","Tổng phát sinh / Số dư cuối kỳ","",data.get("debit"),data.get("credit"),data.get("closingBalance")));
            byte[] bytes=XuatExcelUtil.workbook(List.of("Sổ chi tiết công nợ đại lý"),rows);String code=party.get("code").toString().replaceAll("[^A-Za-z0-9_.-]","_");
            r.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");r.setHeader("Content-Disposition","attachment; filename=so-cong-no-"+code+"-"+data.get("from")+"-"+data.get("to")+".xlsx");r.setContentLength(bytes.length);r.getOutputStream().write(bytes);
        }catch(Exception e){fail(r,e);}
    }
    private static String kind(Object kind){return Map.of("INVOICE","Hóa đơn","ADJUSTMENT","Điều chỉnh","PAYMENT","Thu tiền","REVERSAL","Hoàn tác thu tiền").getOrDefault(Objects.toString(kind,""),Objects.toString(kind,""));}
}
