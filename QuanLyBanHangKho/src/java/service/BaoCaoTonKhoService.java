package service;

import db.TruyVanDB;
import util.KetNoiDB;
import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.*;

/** S8-07: tồn bình quân gia quyền theo thời gian; giá trị quy theo giá vốn danh mục hiện tại. */
public final class BaoCaoTonKhoService {
    public Map<String,Object> report(long user, Collection<String> roles, Map<String,String> f) throws SQLException {
        LocalDate from, to;
        try {
            from=LocalDate.parse(f.getOrDefault("from", LocalDate.now().minusDays(29).toString()));
            to=LocalDate.parse(f.getOrDefault("to", LocalDate.now().toString()));
        } catch (RuntimeException e) { throw new IllegalArgumentException("Ngày báo cáo không hợp lệ"); }
        if(from.isAfter(to)||to.isAfter(LocalDate.now())||ChronoUnit.DAYS.between(from,to)>366)
            throw new IllegalArgumentException("Chọn kỳ tối đa 367 ngày, không sau hôm nay");
        String q=KhoService.text(f.get("q"),"Từ khóa",100,false);
        int slow;
        try { slow=Integer.parseInt(f.getOrDefault("slowDays","90")); }
        catch(RuntimeException e){throw new IllegalArgumentException("Ngưỡng chậm luân chuyển không hợp lệ");}
        if(slow<1||slow>3650)throw new IllegalArgumentException("Ngưỡng chậm luân chuyển từ 1 đến 3650 ngày");
        // Kỳ hiện tại chỉ tính đến lúc chụp báo cáo, không giả định tồn cho phần còn lại của ngày.
        LocalDateTime start=from.atStartOfDay(), end=to.equals(LocalDate.now())?LocalDateTime.now():to.plusDays(1).atStartOfDay();
        try(Connection c=KetNoiDB.getConnection()) {
            c.setAutoCommit(false);c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            var args=new ArrayList<Object>(List.of(Timestamp.valueOf(start),Timestamp.valueOf(end)));
            StringBuilder where=new StringBuilder(" WHERE true");
            if(!KhoService.manager(roles)){where.append(" AND EXISTS(SELECT 1 FROM user_warehouses uw WHERE uw.warehouse_id=w.id AND uw.user_id=?)");args.add(user);}
            if(f.get("warehouseId")!=null&&!f.get("warehouseId").isBlank()){
                long wh=KhoService.id(f.get("warehouseId"));KhoService.access(c,wh,user,roles);where.append(" AND w.id=?");args.add(wh);
            }
            if(f.get("categoryId")!=null&&!f.get("categoryId").isBlank()){
                where.append(" AND p.category_id IN(WITH RECURSIVE cat(id) AS(SELECT id FROM categories WHERE id=? UNION SELECT x.id FROM categories x JOIN cat ON x.parent_id=cat.id) SELECT id FROM cat)");args.add(KhoService.id(f.get("categoryId")));
            }
            if(!q.isEmpty()){where.append(" AND (strpos(lower(p.sku),lower(?))>0 OR strpos(lower(p.name),lower(?))>0)");args.add(q);args.add(q);}
            String sql="WITH period AS(SELECT ?::timestamp a,?::timestamp b), keys AS("+
                "SELECT warehouse_id,product_id FROM inventory UNION SELECT warehouse_id,product_id FROM stock_movements), raw AS("+
                "SELECT w.id warehouse_id,w.code warehouse_code,w.name warehouse_name,p.id product_id,p.sku,p.name,p.base_unit,g.id category_id,g.name category_name,p.cost_price,"+
                "COALESCE(i.on_hand,0)-COALESCE(m.after_start,0) opening,"+
                "COALESCE(i.on_hand,0)-COALESCE(m.after_end,0) closing,"+
                "COALESCE(i.on_hand,0)-COALESCE(m.after_start,0)+COALESCE(m.weighted,0) average_stock,COALESCE(m.shipped,0) shipped "+
                "FROM keys k JOIN warehouses w ON w.id=k.warehouse_id JOIN products p ON p.id=k.product_id JOIN categories g ON g.id=p.category_id "+
                "CROSS JOIN period t LEFT JOIN inventory i ON i.warehouse_id=k.warehouse_id AND i.product_id=k.product_id "+
                "LEFT JOIN LATERAL(SELECT sum(delta) FILTER(WHERE created_at>=t.a) after_start,sum(delta) FILTER(WHERE created_at>=t.b) after_end,"+
                "sum(delta*extract(epoch FROM(t.b-created_at))/NULLIF(extract(epoch FROM(t.b-t.a)),0)) FILTER(WHERE created_at>=t.a AND created_at<t.b) weighted,"+
                "sum(-delta) FILTER(WHERE created_at>=t.a AND created_at<t.b AND kind IN('SHIPMENT_OUT','DELIVERY_RETURN')) shipped "+
                "FROM stock_movements WHERE warehouse_id=k.warehouse_id AND product_id=k.product_id AND created_at>=t.a) m ON true"+where+
                ") SELECT *,closing*cost_price stock_value,average_stock*cost_price average_value,"+
                "CASE WHEN average_stock>0 THEN greatest(shipped,0)/average_stock ELSE NULL END turnover,"+
                "CASE WHEN shipped>0 AND average_stock>=0 THEN average_stock*extract(epoch FROM((SELECT b-a FROM period)))/86400/shipped ELSE NULL END inventory_days "+
                "FROM raw ORDER BY warehouse_code,sku LIMIT 50001";
            var rows=TruyVanDB.rows(c,sql,args.toArray());
            if(rows.size()>50000)throw new IllegalArgumentException("Báo cáo vượt 50.000 dòng; hãy thu hẹp bộ lọc");
            var warehouses=new LinkedHashMap<Object,Map<String,Object>>();var categories=new LinkedHashMap<Object,Map<String,Object>>();
            for(var row:rows){
                var days=(BigDecimal)row.get("inventory_days");
                row.put("slow",((BigDecimal)row.get("closing")).signum()>0&&(days==null||days.compareTo(BigDecimal.valueOf(slow))>=0));
                total(warehouses,row,"warehouse_id","warehouse_name");total(categories,row,"category_id","category_name");
            }
            c.commit();
            return Map.of("items",rows,"byWarehouse",warehouses.values(),"byCategory",categories.values(),"from",from.toString(),"to",to.toString(),"asOf",end.toString(),"slowDays",slow);
        }
    }
    private static void total(Map<Object,Map<String,Object>> groups,Map<String,Object> row,String key,String name){
        var group=groups.computeIfAbsent(row.get(key),k->new LinkedHashMap<>(Map.of("name",row.get(name),"stock_value",BigDecimal.ZERO,"average_value",BigDecimal.ZERO)));
        for(String field:List.of("stock_value","average_value"))group.put(field,((BigDecimal)group.get(field)).add((BigDecimal)row.get(field)));
    }
}
