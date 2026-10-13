package service;
import db.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import util.KetNoiDB;
/** S5-07: xuất giảm tồn kho đi, hàng trên đường chưa cộng kho đến. Phụ thuộc S5-03/04. */
public class ChuyenKhoService {
    /** POST /stock-transfers/{id}/cancel {reason}: giữ phiếu/dòng để đối soát, không đổi tồn. */
    public void cancel(long id,long user,Collection<String> roles,String reason)throws SQLException{
        reason=KhoService.text(reason,"Lý do hủy",1000,true);
        try(Connection c=KetNoiDB.getConnection()){c.setAutoCommit(false);try{
            var r=TruyVanDB.one(c,"SELECT * FROM stock_transfers WHERE id=? FOR UPDATE",id);
            if(r==null)throw new NoSuchElementException("Không tìm thấy phiếu chuyển");
            KhoService.access(c,((Number)r.get("source_warehouse_id")).longValue(),user,roles);KhoService.actor(c,user);
            if(!"DRAFT".equals(r.get("status")))throw new IllegalArgumentException("Chỉ hủy phiếu chuyển còn nháp");
            TruyVanDB.update(c,"UPDATE stock_transfers SET status='CANCELLED',cancel_reason=?,cancelled_by=?,cancelled_at=CURRENT_TIMESTAMP WHERE id=?",reason,user,id);c.commit();
        }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
    }
    public Map<String,Object> masters(long user,Collection<String> roles)throws SQLException{
        var out=new LinkedHashMap<>(new PhieuNhapKhoService().masters(user,roles));try(Connection c=KetNoiDB.getConnection()){
            out.put("destinations",TruyVanDB.rows(c,"SELECT id,code,name FROM warehouses WHERE active ORDER BY code"));
            String scope=KhoService.manager(roles)?"":" AND EXISTS(SELECT 1 FROM user_warehouses a WHERE a.warehouse_id=b.warehouse_id AND a.user_id=?)";
            out.put("bins",TruyVanDB.rows(c,"SELECT b.*,p.sku,p.name,COALESCE(l.name,'Chưa phân vị trí') location_name FROM stock_bins b JOIN products p ON p.id=b.product_id LEFT JOIN warehouse_locations l ON l.id=b.location_id WHERE b.on_hand>0"+scope+" ORDER BY p.sku,b.id",KhoService.manager(roles)?new Object[]{}:new Object[]{user}));return out;
        }
    }
    private void access(Connection c,Map<String,Object> row,long user,Collection<String> roles)throws SQLException{
        if(KhoService.manager(roles))return;
        if(TruyVanDB.one(c,"SELECT 1 FROM user_warehouses WHERE user_id=? AND warehouse_id IN (?,?)",user,row.get("source_warehouse_id"),row.get("destination_warehouse_id"))==null)throw new NoSuchElementException("Không tìm thấy phiếu trong kho được phân công");
    }
    public List<Map<String,Object>> list(long user,Collection<String> roles)throws SQLException{
        try(Connection c=KetNoiDB.getConnection()){
            String scope=KhoService.manager(roles)?"":" WHERE EXISTS(SELECT 1 FROM user_warehouses a WHERE a.user_id=? AND a.warehouse_id IN(t.source_warehouse_id,t.destination_warehouse_id))";
            return TruyVanDB.rows(c,"SELECT t.*,s.name AS source_name,d.name AS destination_name FROM stock_transfers t JOIN warehouses s ON s.id=t.source_warehouse_id JOIN warehouses d ON d.id=t.destination_warehouse_id"+scope+" ORDER BY t.id DESC LIMIT 100",KhoService.manager(roles)?new Object[]{}:new Object[]{user});
        }
    }
    public Map<String,Object> detail(long id,long user,Collection<String> roles)throws SQLException{
        try(Connection c=KetNoiDB.getConnection()){var r=TruyVanDB.one(c,"SELECT * FROM stock_transfers WHERE id=?",id);if(r==null)throw new NoSuchElementException("Không tìm thấy phiếu chuyển");access(c,r,user,roles);
            r.put("lines",TruyVanDB.rows(c,"SELECT l.*,p.sku,p.name,u.name AS unit_name,b.lot_number,b.expiry_date::text AS expiry_date FROM stock_transfer_lines l JOIN products p ON p.id=l.product_id JOIN product_units u ON u.id=l.unit_id JOIN stock_bins b ON b.id=l.source_bin_id WHERE l.transfer_id=? ORDER BY l.id",id));return r;
        }
    }
    public long create(long user,Collection<String> roles,Map<String,Object> in)throws SQLException{
        long src=KhoService.id(in.get("sourceId")),dst=KhoService.id(in.get("destinationId"));if(src==dst)throw new IllegalArgumentException("Kho đi và kho đến phải khác nhau");
        String note=KhoService.text(in.get("note"),"Ghi chú",1000,false);if(!(in.get("lines") instanceof List<?> lines)||lines.isEmpty()||lines.size()>200)throw new IllegalArgumentException("Cần từ 1 đến 200 dòng");
        try(Connection c=KetNoiDB.getConnection()){c.setAutoCommit(false);try{KhoService.access(c,src,user,roles);KhoService.actor(c,user);
            if(TruyVanDB.one(c,"SELECT id FROM warehouses WHERE id=? AND active",dst)==null)throw new IllegalArgumentException("Kho đến không hợp lệ");
            // Lấy id trước để mã phiếu trùng id và không tiêu hai giá trị sequence.
            long id=((Number)TruyVanDB.one(c,"SELECT nextval('stock_transfers_id_seq') AS id").get("id")).longValue();
            TruyVanDB.update(c,"INSERT INTO stock_transfers(id,code,source_warehouse_id,destination_warehouse_id,note,created_by) VALUES(?,?,?,?,?,?)",id,"CK-"+id,src,dst,note,user);
            for(Object raw:lines){if(!(raw instanceof Map<?,?> l))throw new IllegalArgumentException("Dòng chuyển không hợp lệ");long bin=KhoService.id(l.get("binId")),unit=KhoService.id(l.get("unitId"));BigDecimal qty=PhieuNhapKhoService.quantity(l.get("quantity"),false);
                var b=TruyVanDB.one(c,"SELECT b.product_id,u.factor FROM stock_bins b JOIN products p ON p.id=b.product_id JOIN product_units u ON u.product_id=p.id AND u.id=? AND u.active WHERE b.id=? AND b.warehouse_id=?",unit,bin,src);
                if(b==null)throw new IllegalArgumentException("Lô/vị trí hoặc đơn vị không thuộc kho đi");if(qty.multiply((BigDecimal)b.get("factor")).stripTrailingZeros().scale()>0)throw new IllegalArgumentException("Quy đổi phải là số nguyên đơn vị cơ sở");
                TruyVanDB.update(c,"INSERT INTO stock_transfer_lines(transfer_id,source_bin_id,product_id,unit_id,quantity,factor) VALUES(?,?,?,?,?,?)",id,bin,b.get("product_id"),unit,qty,b.get("factor"));
            }
            c.commit();return id;
        }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
    }
    public void send(long id,long user,Collection<String> roles)throws SQLException{
        try(Connection c=KetNoiDB.getConnection()){c.setAutoCommit(false);try{var r=TruyVanDB.one(c,"SELECT * FROM stock_transfers WHERE id=? FOR UPDATE",id);if(r==null)throw new NoSuchElementException("Không tìm thấy phiếu");
            long wh=((Number)r.get("source_warehouse_id")).longValue();KhoService.access(c,wh,user,roles);KhoService.actor(c,user);if(!"DRAFT".equals(r.get("status")))throw new IllegalArgumentException("Phiếu đã xuất chuyển");
            for(var l:TruyVanDB.rows(c,"SELECT * FROM stock_transfer_lines WHERE transfer_id=? ORDER BY product_id,source_bin_id,id",id))
                StockLedgerDB.change(c,wh,((Number)l.get("product_id")).longValue(),((Number)l.get("source_bin_id")).longValue(),((BigDecimal)l.get("base_quantity")).negate(),"TRANSFER_OUT",id,TruyVanDB.id(l),user,String.valueOf(r.get("note")));
            TruyVanDB.update(c,"UPDATE stock_transfers SET status='IN_TRANSIT',sent_at=CURRENT_TIMESTAMP WHERE id=?",id);c.commit();
        }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
    }
    public void receive(long id,long user,Collection<String> roles,Map<String,Object> in)throws SQLException{
        String reason=KhoService.text(in.get("reason"),"Lý do chênh lệch",1000,false);
        if(!(in.get("lines") instanceof List<?> received)||received.isEmpty()||received.size()>200)throw new IllegalArgumentException("Nhập số nhận cho mọi dòng");
        Map<Long,Map<?,?>> inputs=new HashMap<>();for(Object raw:received){if(!(raw instanceof Map<?,?> m))throw new IllegalArgumentException("Dòng nhận không hợp lệ");if(inputs.put(KhoService.id(m.get("lineId")),m)!=null)throw new IllegalArgumentException("Dòng nhận bị trùng");}
        try(Connection c=KetNoiDB.getConnection()){c.setAutoCommit(false);try{
            var r=TruyVanDB.one(c,"SELECT * FROM stock_transfers WHERE id=? FOR UPDATE",id);if(r==null)throw new NoSuchElementException("Không tìm thấy phiếu");
            long wh=((Number)r.get("destination_warehouse_id")).longValue();KhoService.access(c,wh,user,roles);KhoService.actor(c,user);
            if(!"IN_TRANSIT".equals(r.get("status")))throw new IllegalArgumentException("Phiếu chưa xuất chuyển hoặc đã nhận");
            var ls=TruyVanDB.rows(c,"SELECT l.*,b.lot_number,b.expiry_date FROM stock_transfer_lines l JOIN stock_bins b ON b.id=l.source_bin_id WHERE transfer_id=? ORDER BY l.product_id,l.id",id);
            if(ls.size()!=inputs.size())throw new IllegalArgumentException("Phải ghi nhận đủ mọi dòng của phiếu");
            for(var l:ls){var m=inputs.get(TruyVanDB.id(l));if(m==null)throw new IllegalArgumentException("Dòng nhận không thuộc phiếu");
                BigDecimal qty=PhieuNhapKhoService.quantity(m.get("receivedQuantity"),true),base=qty.multiply((BigDecimal)l.get("factor"));long loc=KhoService.id(m.get("locationId"));
                if(base.stripTrailingZeros().scale()>0)throw new IllegalArgumentException("Số nhận quy đổi phải là số nguyên");
                if(qty.compareTo((BigDecimal)l.get("quantity"))>0)throw new IllegalArgumentException("Số nhận không được vượt số đã xuất chuyển");
                if(qty.compareTo((BigDecimal)l.get("quantity"))!=0&&reason.isEmpty())throw new IllegalArgumentException("Nhận chênh lệch phải nhập lý do");
                if(TruyVanDB.one(c,"SELECT id FROM warehouse_locations WHERE id=? AND warehouse_id=? AND active",loc,wh)==null)throw new IllegalArgumentException("Vị trí không thuộc kho đến");
                long product=((Number)l.get("product_id")).longValue();StockLedgerDB.lockStock(c,wh,product);
                long bin=StockLedgerDB.bin(c,wh,product,loc,String.valueOf(l.get("lot_number")),l.get("expiry_date"));
                StockLedgerDB.change(c,wh,product,bin,base,"TRANSFER_IN",id,TruyVanDB.id(l),user,reason);
                TruyVanDB.update(c,"UPDATE stock_transfer_lines SET received_quantity=?,destination_location_id=? WHERE id=?",qty,loc,l.get("id"));
            }
            TruyVanDB.update(c,"UPDATE stock_transfers SET status='RECEIVED',received_at=CURRENT_TIMESTAMP,receive_reason=? WHERE id=?",reason,id);c.commit();
        }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
    }
}

