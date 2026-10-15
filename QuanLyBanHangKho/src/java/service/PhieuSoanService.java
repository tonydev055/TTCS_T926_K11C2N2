package service;

import db.TruyVanDB;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import util.KetNoiDB;

/** S6-03: soạn theo vị trí kho, có thể gộp nhiều đơn trong cùng kho. */
public class PhieuSoanService {
    public List<Map<String,Object>> list(long user,Collection<String> roles)throws SQLException {
        try(Connection c=KetNoiDB.getConnection()) {
            String scope=KhoService.manager(roles)?"":" AND EXISTS(SELECT 1 FROM user_warehouses uw WHERE uw.user_id=? AND uw.warehouse_id=p.warehouse_id)";
            return TruyVanDB.rows(c,"SELECT p.*,w.name warehouse_name,u.full_name created_by_name,(SELECT count(*) FROM pick_list_lines l WHERE l.pick_list_id=p.id) line_count,(SELECT count(*) FROM pick_list_lines l WHERE l.pick_list_id=p.id AND l.picked) picked_count FROM pick_lists p JOIN warehouses w ON w.id=p.warehouse_id LEFT JOIN users u ON u.id=p.created_by WHERE 1=1"+scope+" ORDER BY p.id DESC LIMIT 100",KhoService.manager(roles)?new Object[]{}:new Object[]{user});
        }
    }
    public Map<String,Object> availableOrders(long user,Collection<String> roles)throws SQLException {
        try(Connection c=KetNoiDB.getConnection()) {
            String scope=KhoService.manager(roles)?"":" AND EXISTS(SELECT 1 FROM user_warehouses uw WHERE uw.user_id=? AND uw.warehouse_id=o.warehouse_id)";
            var rows=TruyVanDB.rows(c,"SELECT o.id,o.code,o.warehouse_id,w.name warehouse_name,COALESCE(k.name,u.full_name,'') customer_name,o.approved_at FROM orders o JOIN warehouses w ON w.id=o.warehouse_id LEFT JOIN users u ON u.id=o.customer_id LEFT JOIN customers k ON k.id=o.agent_id WHERE o.status='APPROVED' AND NOT EXISTS(SELECT 1 FROM pick_list_orders x WHERE x.order_id=o.id)"+scope+" ORDER BY o.approved_at NULLS LAST,o.id",KhoService.manager(roles)?new Object[]{}:new Object[]{user});
            return Map.of("orders",rows,"warehouses",TruyVanDB.rows(c,"SELECT w.id,w.code,w.name FROM warehouses w WHERE w.active"+(KhoService.manager(roles)?"":" AND EXISTS(SELECT 1 FROM user_warehouses uw WHERE uw.user_id=? AND uw.warehouse_id=w.id)")+" ORDER BY w.code",KhoService.manager(roles)?new Object[]{}:new Object[]{user}));
        }
    }
    public Map<String,Object> detail(long id,long user,Collection<String> roles)throws SQLException {
        try(Connection c=KetNoiDB.getConnection()) {
            var p=TruyVanDB.one(c,"SELECT p.*,w.name warehouse_name FROM pick_lists p JOIN warehouses w ON w.id=p.warehouse_id WHERE p.id=?",id);
            if(p==null)throw new NoSuchElementException("Không tìm thấy phiếu soạn"); KhoService.access(c,((Number)p.get("warehouse_id")).longValue(),user,roles);
            p.put("orders",TruyVanDB.rows(c,"SELECT o.id,o.code,o.status FROM pick_list_orders x JOIN orders o ON o.id=x.order_id WHERE x.pick_list_id=? ORDER BY o.code",id));
            p.put("lines",TruyVanDB.rows(c,"SELECT l.*,pr.sku,pr.name product_name,pr.base_unit,COALESCE(wl.code||' · '||wl.name,'Chưa phân vị trí') location_name,COALESCE(wl.code,'~~~~') location_code FROM pick_list_lines l JOIN products pr ON pr.id=l.product_id LEFT JOIN warehouse_locations wl ON wl.id=l.location_id WHERE l.pick_list_id=? ORDER BY COALESCE(wl.code,'~~~~'),pr.sku,l.id",id));
            return p;
        }
    }
    public long create(long user,Collection<String> roles,Map<String,Object> in)throws SQLException {
        long warehouse=KhoService.id(in.get("warehouseId")); Object raw=in.get("orderIds");
        if(!(raw instanceof List<?> values)||values.isEmpty()||values.size()>100)throw new IllegalArgumentException("Chọn ít nhất một đơn đã duyệt (tối đa 100 đơn)");
        LinkedHashSet<Long> orders=new LinkedHashSet<>();for(Object v:values)if(!orders.add(KhoService.id(v)))throw new IllegalArgumentException("Đơn hàng bị chọn trùng");
        try(Connection c=KetNoiDB.getConnection()){c.setAutoCommit(false);try {
            KhoService.access(c,warehouse,user,roles);KhoService.actor(c,user);
            TruyVanDB.one(c,"SELECT pg_advisory_xact_lock(?)",warehouse);
            var orderRows=TruyVanDB.rows(c,"SELECT id,code,status,warehouse_id FROM orders WHERE id IN ("+String.join(",",Collections.nCopies(orders.size(),"?"))+") ORDER BY id FOR UPDATE",orders.toArray());
            if(orderRows.size()!=orders.size())throw new IllegalArgumentException("Có đơn hàng không tồn tại");
            for(var o:orderRows){if(!"APPROVED".equals(o.get("status")))throw new IllegalArgumentException("Chỉ được chọn đơn ở trạng thái Đã duyệt: "+o.get("code"));if(o.get("warehouse_id")==null||((Number)o.get("warehouse_id")).longValue()!=warehouse)throw new IllegalArgumentException("Các đơn phải thuộc cùng kho đã chọn: "+o.get("code"));if(TruyVanDB.one(c,"SELECT 1 FROM pick_list_orders x JOIN pick_lists p ON p.id=x.pick_list_id WHERE x.order_id=?",o.get("id"))!=null)throw new IllegalArgumentException("Đơn "+o.get("code")+" đã có phiếu soạn");}
            long id=TruyVanDB.id(TruyVanDB.one(c,"INSERT INTO pick_lists(code,warehouse_id,created_by) VALUES('TEMP-'||nextval('pick_lists_id_seq'),?,?) RETURNING id",warehouse,user));
            TruyVanDB.update(c,"UPDATE pick_lists SET code=? WHERE id=?","PS-"+id,id);
            for(long oid:orders){TruyVanDB.update(c,"INSERT INTO pick_list_orders(pick_list_id,order_id) VALUES(?,?)",id,oid);
                TruyVanDB.update(c,"UPDATE orders SET status='PICKING' WHERE id=?",oid);
            }
            // Phân bổ đủ số lượng qua các vị trí, không dồn toàn bộ vào một vị trí thiếu hàng.
            var demands=TruyVanDB.rows(c,"SELECT ol.product_id,SUM(ol.base_quantity) qty FROM order_lines ol WHERE ol.order_id IN ("+String.join(",",Collections.nCopies(orders.size(),"?"))+") GROUP BY ol.product_id ORDER BY ol.product_id",orders.toArray());
            for(var demand:demands){
                long product=((Number)demand.get("product_id")).longValue();
                BigDecimal remaining=(BigDecimal)demand.get("qty");
                var reserved=TruyVanDB.one(c,"SELECT COALESCE(SUM(quantity),0) qty FROM stock_reservations WHERE warehouse_id=? AND product_id=? AND status='ACTIVE' AND order_id IN (SELECT order_id FROM pick_list_orders WHERE pick_list_id=?)",warehouse,product,id);
                if(((BigDecimal)reserved.get("qty")).compareTo(remaining)<0)throw new IllegalArgumentException("Đơn không còn đủ tồn giữ chỗ; hãy kiểm tra lại trước khi soạn");
                for(var bucket:TruyVanDB.rows(c,"SELECT location_id,SUM(on_hand) qty FROM stock_bins WHERE warehouse_id=? AND product_id=? AND on_hand>0 GROUP BY location_id ORDER BY location_id NULLS LAST",warehouse,product)){
                    BigDecimal take=remaining.min((BigDecimal)bucket.get("qty"));
                    if(take.signum()>0)TruyVanDB.update(c,"INSERT INTO pick_list_lines(pick_list_id,product_id,location_id,requested_quantity) VALUES(?,?,?,?)",id,product,bucket.get("location_id"),take);
                    remaining=remaining.subtract(take);if(remaining.signum()==0)break;
                }
                if(remaining.signum()>0)throw new IllegalArgumentException("Tồn vị trí không đủ để lập phiếu soạn");
            }
            if(TruyVanDB.one(c,"SELECT 1 FROM pick_list_lines WHERE pick_list_id=?",id)==null)throw new IllegalArgumentException("Các đơn đã chọn không có dòng hàng");
            c.commit();return id;
        }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
    }
    public void saveLines(long id,long user,Collection<String> roles,Map<String,Object> in)throws SQLException {
        if(!(in.get("lines") instanceof List<?> lines)||lines.isEmpty()||lines.size()>2000)throw new IllegalArgumentException("Danh sách dòng soạn không hợp lệ");
        try(Connection c=KetNoiDB.getConnection()){c.setAutoCommit(false);try {
            var p=TruyVanDB.one(c,"SELECT * FROM pick_lists WHERE id=? FOR UPDATE",id);if(p==null)throw new NoSuchElementException("Không tìm thấy phiếu soạn");KhoService.access(c,((Number)p.get("warehouse_id")).longValue(),user,roles);if(!"PICKING".equals(p.get("status")))throw new IllegalArgumentException("Phiếu soạn đã hoàn tất");KhoService.actor(c,user);
            Set<Long> seen=new HashSet<>();for(Object item:lines){if(!(item instanceof Map<?,?> m))throw new IllegalArgumentException("Dòng soạn không hợp lệ");long line=KhoService.id(m.get("lineId"));if(!seen.add(line))throw new IllegalArgumentException("Dòng soạn bị trùng");BigDecimal qty=PhieuNhapKhoService.quantity(m.get("actualQuantity"),true);String reason=KhoService.text(m.get("shortageReason"),"Lý do soạn thiếu",1000,false);
                var current=TruyVanDB.one(c,"SELECT requested_quantity FROM pick_list_lines WHERE id=? AND pick_list_id=?",line,id);if(current==null)throw new IllegalArgumentException("Dòng hàng không thuộc phiếu soạn");if(qty.compareTo((BigDecimal)current.get("requested_quantity"))>0)throw new IllegalArgumentException("Không được soạn vượt số lượng yêu cầu");if(qty.compareTo((BigDecimal)current.get("requested_quantity"))>0)throw new IllegalArgumentException("Không được soạn vượt số lượng yêu cầu");if(qty.compareTo((BigDecimal)current.get("requested_quantity"))<0&&reason.isBlank())throw new IllegalArgumentException("Bắt buộc nhập lý do khi soạn thiếu");
                TruyVanDB.update(c,"UPDATE pick_list_lines SET actual_quantity=?,picked=TRUE,shortage_reason=? WHERE id=? AND pick_list_id=?",qty,reason,line,id);
            }
            long total=((Number)TruyVanDB.one(c,"SELECT count(*) n FROM pick_list_lines WHERE pick_list_id=?",id).get("n")).longValue();long picked=((Number)TruyVanDB.one(c,"SELECT count(*) n FROM pick_list_lines WHERE pick_list_id=? AND picked",id).get("n")).longValue();
            if(total>0&&total==picked){TruyVanDB.update(c,"UPDATE pick_lists SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP WHERE id=?",id);}
            c.commit();
        }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
    }
}
