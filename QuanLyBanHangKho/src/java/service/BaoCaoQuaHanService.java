package service;

import db.TruyVanDB;
import util.KetNoiDB;
import java.sql.*;
import java.util.*;
import java.math.BigDecimal;

/** S8-09: tuổi quá hạn tính từ due_date; chỉ lấy dư nợ dương thực tế sau đối trừ/điều chỉnh. */
public final class BaoCaoQuaHanService {
    public static final List<String> AMOUNTS=List.of("d1","d31","d61","d91","overdue");
    public Map<String,Object> masters()throws SQLException{
        try(Connection c=KetNoiDB.getConnection()){
            return Map.of("staff",TruyVanDB.rows(c,"SELECT DISTINCT u.id,u.full_name FROM users u JOIN customers c ON c.sales_rep_id=u.id ORDER BY u.full_name,u.id"),
                "regions",TruyVanDB.rows(c,"SELECT DISTINCT region FROM customers WHERE region<>'' ORDER BY region"));
        }
    }
    public Map<String,Object> report(Map<String,String> f)throws SQLException{
        String q=KhoService.text(f.get("q"),"Từ khóa",150,false),region=KhoService.text(f.get("region"),"Khu vực",150,false),bucket=Objects.toString(f.get("bucket"),"");
        if(!Set.of("","d1","d31","d61","d91").contains(bucket))throw new IllegalArgumentException("Nhóm tuổi quá hạn không hợp lệ");
        String staff=Objects.toString(f.get("staffId"),"");Long staffId=staff.isBlank()||staff.equals("unassigned")?null:KhoService.id(staff);
        var args=new ArrayList<Object>();var where=new StringBuilder(" WHERE b.outstanding>0 AND b.due_date<CURRENT_DATE");
        if(!q.isEmpty()){where.append(" AND (strpos(lower(c.code),lower(?))>0 OR strpos(lower(c.name),lower(?))>0)");args.add(q);args.add(q);}
        if(!region.isEmpty()){where.append(" AND c.region=?");args.add(region);}
        if(staff.equals("unassigned"))where.append(" AND c.sales_rep_id IS NULL");
        else if(staffId!=null){where.append(" AND c.sales_rep_id=?");args.add(staffId);}
        try(Connection c=KetNoiDB.getConnection()){
            String sql="SELECT c.id,c.code,c.name,c.region,COALESCE(c.sales_rep_id,0) staff_id,COALESCE(u.full_name,'Chưa phân công') staff_name,"+
                "sum(b.outstanding) FILTER(WHERE CURRENT_DATE-b.due_date BETWEEN 1 AND 30) d1,"+
                "sum(b.outstanding) FILTER(WHERE CURRENT_DATE-b.due_date BETWEEN 31 AND 60) d31,"+
                "sum(b.outstanding) FILTER(WHERE CURRENT_DATE-b.due_date BETWEEN 61 AND 90) d61,"+
                "sum(b.outstanding) FILTER(WHERE CURRENT_DATE-b.due_date>90) d91,sum(b.outstanding) overdue,max(CURRENT_DATE-b.due_date) max_days "+
                "FROM customers c JOIN receivable_invoice_balances b ON b.customer_id=c.id LEFT JOIN users u ON u.id=c.sales_rep_id"+where+
                " GROUP BY c.id,c.code,c.name,c.region,c.sales_rep_id,u.full_name";
            if(!bucket.isEmpty()){
                String condition=Map.of("d1","BETWEEN 1 AND 30","d31","BETWEEN 31 AND 60","d61","BETWEEN 61 AND 90","d91",">90").get(bucket);
                sql+=" HAVING COALESCE(sum(b.outstanding) FILTER(WHERE CURRENT_DATE-b.due_date "+condition+"),0)>0";
            }
            var rows=TruyVanDB.rows(c,sql+" ORDER BY COALESCE(sum(b.outstanding) FILTER(WHERE CURRENT_DATE-b.due_date>90),0) DESC,sum(b.outstanding) DESC,max(CURRENT_DATE-b.due_date) DESC,c.code,c.id LIMIT 50001",args.toArray());
            if(rows.size()>50000)throw new IllegalArgumentException("Báo cáo vượt 50.000 đại lý; hãy thu hẹp bộ lọc");
            var totals=zeros();var groups=new LinkedHashMap<Object,Map<String,Object>>();
            for(var row:rows){
                var group=groups.computeIfAbsent(row.get("staff_id"),k->{var g=zeros();g.put("staff_id",k);g.put("staff_name",row.get("staff_name"));g.put("dealer_count",0);return g;});
                group.put("dealer_count",((Integer)group.get("dealer_count"))+1);
                for(String key:AMOUNTS){var amount=(BigDecimal)row.get(key);if(amount==null)amount=BigDecimal.ZERO;row.put(key,amount);totals.put(key,((BigDecimal)totals.get(key)).add(amount));group.put(key,((BigDecimal)group.get(key)).add(amount));}
            }
            return Map.of("items",rows,"byStaff",groups.values(),"summary",totals,"asOf",java.time.LocalDate.now().toString());
        }
    }
    private static LinkedHashMap<String,Object> zeros(){var out=new LinkedHashMap<String,Object>();AMOUNTS.forEach(k->out.put(k,BigDecimal.ZERO));return out;}
}
