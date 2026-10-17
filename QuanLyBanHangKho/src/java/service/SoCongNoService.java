package service;
import db.TruyVanDB;
import util.KetNoiDB;
import java.sql.*;
import java.math.BigDecimal;
import java.util.*;

/** S7-03: báo cáo từ hóa đơn, điều chỉnh và khoản thu thực tế. */
public final class SoCongNoService {
 public Map<String,Object> aging(String keyword,String region,String age,boolean overdue)throws SQLException{
  if(!Set.of("","0","31","61","91").contains(age))throw new IllegalArgumentException("Nhóm tuổi nợ không hợp lệ");
  String q=KhoService.text(keyword,"Từ khóa",150,false),area=KhoService.text(region,"Khu vực",150,false);
  try(Connection c=KetNoiDB.getConnection()){
   var rows=TruyVanDB.rows(c,"SELECT c.id,c.code,c.name,c.region,COALESCE(sum(b.total_amount+b.adjustment_amount),0) ar,COALESCE(sum(b.paid_amount),0) collected,COALESCE(sum(b.outstanding),0) outstanding,"+
    "COALESCE(sum(b.outstanding) FILTER(WHERE CURRENT_DATE-b.issued_at::date<=30),0) d0,"+
    "COALESCE(sum(b.outstanding) FILTER(WHERE CURRENT_DATE-b.issued_at::date BETWEEN 31 AND 60),0) d31,"+
    "COALESCE(sum(b.outstanding) FILTER(WHERE CURRENT_DATE-b.issued_at::date BETWEEN 61 AND 90),0) d61,"+
    "COALESCE(sum(b.outstanding) FILTER(WHERE CURRENT_DATE-b.issued_at::date>90),0) d91,"+
    "COALESCE(sum(b.outstanding) FILTER(WHERE b.due_date<CURRENT_DATE AND b.outstanding>0),0) overdue "+
    "FROM customers c LEFT JOIN receivable_invoice_balances b ON b.customer_id=c.id WHERE (?='' OR c.code ILIKE '%'||?||'%' OR c.name ILIKE '%'||?||'%') AND (?='' OR c.region=?) GROUP BY c.id ORDER BY c.code",q,q,q,area,area);
   rows.removeIf(r->overdue&&((BigDecimal)r.get("overdue")).signum()<=0||!age.isEmpty()&&((BigDecimal)r.get("d"+age)).signum()<=0);
   var totals=new LinkedHashMap<String,Object>();for(String field:List.of("ar","collected","outstanding","d0","d31","d61","d91","overdue"))totals.put(field,rows.stream().map(r->(BigDecimal)r.get(field)).reduce(BigDecimal.ZERO,BigDecimal::add));
   return Map.of("dealers",rows,"summary",totals,"dealerCount",rows.size(),"regions",TruyVanDB.rows(c,"SELECT DISTINCT region FROM customers WHERE region<>'' ORDER BY region"));
  }
 }
}
