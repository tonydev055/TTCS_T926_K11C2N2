package service;

import db.*;
import java.math.*;
import java.sql.*;
import java.util.*;
import util.KetNoiDB;

/** S6-05: xuất theo kết quả soạn, ghi đúng lô, số lượng thiếu và giữ chỗ trong cùng giao dịch. */
public class XuatKhoService {
    public Map<String,Object> list(long user,Collection<String> roles)throws SQLException {
        try(Connection c=KetNoiDB.getConnection()){
            String scope=KhoService.manager(roles)?"":" AND EXISTS(SELECT 1 FROM user_warehouses uw WHERE uw.user_id=? AND uw.warehouse_id=o.warehouse_id)";
            var rows=TruyVanDB.rows(c,"SELECT o.id,o.code AS \"orderCode\",o.status,COALESCE(k.name,cu.full_name,'') AS customer,o.delivery_address AS address,w.name AS warehouse,o.desired_delivery_date::text AS \"desiredDate\",o.shipped_at::text AS \"confirmedAt\",o.payable " +
                "FROM orders o JOIN warehouses w ON w.id=o.warehouse_id LEFT JOIN customers k ON k.id=o.agent_id LEFT JOIN users cu ON cu.id=o.customer_id " +
                "WHERE ((o.status='PICKING' AND EXISTS(SELECT 1 FROM pick_list_orders po JOIN pick_lists p ON p.id=po.pick_list_id WHERE po.order_id=o.id AND p.status='COMPLETED')) OR o.status IN ('SHIPPED','DELIVERED','CLOSED'))"+scope+" ORDER BY o.id DESC LIMIT 200",KhoService.manager(roles)?new Object[]{}:new Object[]{user});
            var pending=new ArrayList<Map<String,Object>>();var shipped=new ArrayList<Map<String,Object>>();var debts=new ArrayList<Map<String,Object>>();
            for(var o:rows){boolean waiting="PICKING".equals(o.get("status"));
                var lines=TruyVanDB.rows(c,"SELECT l.id,p.sku,p.name,u.name AS unit,l.quantity AS \"orderedQty\",CASE WHEN o.status='PICKING' THEN l.quantity ELSE 0 END AS \"reservedQty\",CASE WHEN o.status='PICKING' THEN NULL ELSE l.delivered_base_quantity/l.conversion_factor END AS \"deliveredQty\",CASE WHEN o.status='PICKING' OR l.delivered_base_quantity IS NULL THEN 0 ELSE GREATEST(l.quantity-COALESCE(l.delivered_base_quantity,0)/l.conversion_factor,0) END AS \"shortQty\",l.line_total/l.quantity AS \"unitPrice\" FROM order_lines l JOIN products p ON p.id=l.product_id JOIN product_units u ON u.id=l.unit_id JOIN orders o ON o.id=l.order_id WHERE l.order_id=? ORDER BY l.id",o.get("id"));
                o.put("lines",lines);BigDecimal value=BigDecimal.ZERO;int count=0;
                for(var l:lines){BigDecimal shortQty=(BigDecimal)l.get("shortQty");if(shortQty.signum()>0){count++;value=value.add(shortQty.multiply((BigDecimal)l.get("unitPrice")));}}
                o.put("hasShortfall",count>0);o.put("debtValue",value.setScale(2,RoundingMode.HALF_UP));o.put("debtItems",count);
                if(waiting)pending.add(o);else {shipped.add(o);if(count>0)debts.add(o);}
            }
            return Map.of("pending",pending,"shipped",shipped,"shortfall",debts,"canOverrideExpired",KhoService.manager(roles));
        }
    }

    public void confirm(long orderId,long user,Collection<String> roles,Map<String,Object> input)throws SQLException {
        if(!(input.get("lines") instanceof List<?> items)||items.isEmpty()||items.size()>2000)throw new IllegalArgumentException("Danh sách xuất kho không hợp lệ");
        String reason=KhoService.text(input.get("reason"),"Lý do giao thiếu",1000,false);
        boolean expiredOverride=Boolean.TRUE.equals(input.get("expiredOverride"));
        String expiryReason=KhoService.text(input.get("expiryReason"),"Lý do xuất lô hết hạn",1000,false);
        if(expiredOverride&&(!KhoService.manager(roles)||expiryReason.isBlank()))throw new SecurityException("Chỉ quản lý kho được ghi đè xuất lô hết hạn và phải nhập lý do");
        Map<Long,BigDecimal> quantities=new LinkedHashMap<>();
        for(Object item:items){if(!(item instanceof Map<?,?> line))throw new IllegalArgumentException("Dòng xuất kho không hợp lệ");
            long id=KhoService.id(line.get("lineId"));if(quantities.putIfAbsent(id,PhieuNhapKhoService.quantity(line.get("actualQuantity"),true))!=null)throw new IllegalArgumentException("Dòng xuất bị trùng");}
        try(Connection c=KetNoiDB.getConnection()){c.setAutoCommit(false);try{
            var head=TruyVanDB.one(c,"SELECT warehouse_id FROM orders WHERE id=?",orderId);
            if(head==null||head.get("warehouse_id")==null)throw new NoSuchElementException("Không tìm thấy đơn có kho phục vụ");
            long wh=((Number)head.get("warehouse_id")).longValue();KhoService.access(c,wh,user,roles);
            TruyVanDB.one(c,"SELECT pg_advisory_xact_lock(?)",wh);
            var order=TruyVanDB.one(c,"SELECT status,warehouse_id FROM orders WHERE id=? FOR UPDATE",orderId);
            if(!"PICKING".equals(order.get("status"))||((Number)order.get("warehouse_id")).longValue()!=wh)throw new IllegalArgumentException("Chỉ xuất đơn đang soạn trong kho đã chọn");
            var pick=TruyVanDB.one(c,"SELECT p.id,p.status FROM pick_lists p JOIN pick_list_orders po ON po.pick_list_id=p.id WHERE po.order_id=? FOR UPDATE OF p",orderId);
            if(pick==null||!"COMPLETED".equals(pick.get("status")))throw new IllegalArgumentException("Phải hoàn tất phiếu soạn trước khi xuất kho");
            long pickId=TruyVanDB.id(pick);
            var lines=TruyVanDB.rows(c,"SELECT id,product_id,quantity,base_quantity,conversion_factor FROM order_lines WHERE order_id=? ORDER BY product_id,id FOR UPDATE",orderId);
            if(lines.size()!=quantities.size())throw new IllegalArgumentException("Phải nhập đầy đủ các dòng của đơn");
            BigDecimal total=BigDecimal.ZERO;boolean shortage=false;
            Map<Long,BigDecimal> demand=new TreeMap<>();
            for(var l:lines){BigDecimal q=quantities.get(TruyVanDB.id(l));if(q==null||q.compareTo((BigDecimal)l.get("quantity"))>0)throw new IllegalArgumentException("Dòng xuất không thuộc đơn hoặc vượt số lượng đặt");
                BigDecimal base=q.multiply((BigDecimal)l.get("conversion_factor"));if(base.stripTrailingZeros().scale()>0)throw new IllegalArgumentException("Số lượng xuất quy đổi phải là số nguyên đơn vị cơ sở");
                total=total.add(base);shortage|=q.compareTo((BigDecimal)l.get("quantity"))<0;
                demand.merge(((Number)l.get("product_id")).longValue(),(BigDecimal)l.get("base_quantity"),BigDecimal::add);
            }
            if(total.signum()==0)throw new IllegalArgumentException("Phải xuất ít nhất một sản phẩm; nếu không xuất hãy xử lý lại đơn");
            if(shortage&&reason.isBlank())throw new IllegalArgumentException("Bắt buộc nhập lý do giao thiếu");
            for(var d:demand.entrySet()){
                StockLedgerDB.lockStock(c,wh,d.getKey());
                var reserved=TruyVanDB.one(c,"SELECT COALESCE(sum(quantity),0) qty FROM stock_reservations WHERE order_id=? AND warehouse_id=? AND product_id=? AND status='ACTIVE' AND (expires_at IS NULL OR expires_at>CURRENT_TIMESTAMP)",orderId,wh,d.getKey());
                if(((BigDecimal)reserved.get("qty")).compareTo(d.getValue())<0)throw new IllegalArgumentException("Đơn không còn đủ tồn giữ chỗ");
            }
            TrangThaiDonHang.setContext(c,user,shortage?"Xuất thiếu: "+reason:"Xác nhận xuất kho");
            // Nhả giữ chỗ cũ trước khi trừ tồn; tất cả vẫn khóa trong cùng giao dịch.
            GiuChoTonDB.release(c,orderId);
            for(var l:lines){long lineId=TruyVanDB.id(l);long product=((Number)l.get("product_id")).longValue();
                BigDecimal base=quantities.get(lineId).multiply((BigDecimal)l.get("conversion_factor"));BigDecimal remaining=base;
                var locations=TruyVanDB.rows(c,"SELECT pl.location_id,SUM(pl.actual_quantity) picked FROM pick_list_lines pl WHERE pl.pick_list_id=? AND pl.product_id=? GROUP BY pl.location_id ORDER BY pl.location_id NULLS LAST",pickId,product);
                for(var loc:locations){
                    var used=TruyVanDB.one(c,"SELECT COALESCE(-SUM(m.delta),0) qty FROM stock_movements m JOIN stock_bins b ON b.id=m.bin_id JOIN pick_list_orders po ON po.order_id=m.document_id WHERE m.kind='SHIPMENT_OUT' AND po.pick_list_id=? AND m.product_id=? AND b.location_id IS NOT DISTINCT FROM ?",pickId,product,loc.get("location_id"));
                    BigDecimal allowance=((BigDecimal)loc.get("picked")).subtract((BigDecimal)used.get("qty")).max(BigDecimal.ZERO);
                    if(allowance.signum()==0)continue;
                    var bins=TruyVanDB.rows(c,"SELECT id,on_hand FROM stock_bins WHERE warehouse_id=? AND product_id=? AND location_id IS NOT DISTINCT FROM ? AND on_hand>0 AND (? OR expiry_date IS NULL OR expiry_date>=CURRENT_DATE) ORDER BY (expiry_date<CURRENT_DATE) NULLS FIRST,expiry_date NULLS LAST,id FOR UPDATE",wh,product,loc.get("location_id"),expiredOverride);
                    for(var b:bins){BigDecimal take=remaining.min(allowance).min((BigDecimal)b.get("on_hand"));if(take.signum()>0){
                        StockLedgerDB.change(c,wh,product,TruyVanDB.id(b),take.negate(),"SHIPMENT_OUT",orderId,lineId,user,expiredOverride?"Ghi đè lô hết hạn: "+expiryReason+". "+reason:reason);
                        remaining=remaining.subtract(take);allowance=allowance.subtract(take);
                    }if(remaining.signum()==0||allowance.signum()==0)break;}
                    if(remaining.signum()==0)break;
                }
                if(remaining.signum()>0)throw new IllegalArgumentException("Không đủ hàng hợp lệ theo vị trí đã soạn; kiểm tra lại số lượng xuất");
                TruyVanDB.update(c,"UPDATE order_lines SET delivered_base_quantity=? WHERE id=?",base,lineId);
                if(base.signum()>0)TruyVanDB.update(c,"INSERT INTO stock_reservations(order_id,warehouse_id,product_id,quantity,status,released_at) VALUES(?,?,?,?,'CONSUMED',CURRENT_TIMESTAMP)",orderId,wh,product,base);
            }
            TruyVanDB.update(c,"UPDATE orders SET status='SHIPPED' WHERE id=?",orderId);
            c.commit();
        }catch(SQLException e){c.rollback();throw TrangThaiDonHang.rethrow(e);}catch(RuntimeException e){c.rollback();throw e;}}
    }
}
