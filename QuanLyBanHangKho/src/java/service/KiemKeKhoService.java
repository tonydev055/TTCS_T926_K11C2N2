package service;
import db.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import util.KetNoiDB;
/** S5-08: snapshot tồn theo bin, chỉ quản lý kho chốt; không ghi đè biến động phát sinh sau snapshot. */
public class KiemKeKhoService {
    private String category(Long category){return category==null?"":" AND p.category_id IN(WITH RECURSIVE cat(id) AS(SELECT id FROM categories WHERE id=? UNION SELECT x.id FROM categories x JOIN cat ON x.parent_id=cat.id) SELECT id FROM cat)";}
    public List<Map<String,Object>> list(long user,Collection<String> roles)throws SQLException{
        try(Connection c=KetNoiDB.getConnection()){
            String scope=KhoService.manager(roles)?"":" WHERE EXISTS(SELECT 1 FROM user_warehouses a WHERE a.user_id=? AND a.warehouse_id=n.warehouse_id)";
            return TruyVanDB.rows(c,"SELECT n.*,w.name AS warehouse_name FROM stock_counts n JOIN warehouses w ON w.id=n.warehouse_id"+scope+" ORDER BY n.id DESC LIMIT 100",KhoService.manager(roles)?new Object[]{}:new Object[]{user});
        }
    }
    public Map<String,Object> detail(long id,long user,Collection<String> roles)throws SQLException{
        try(Connection c=KetNoiDB.getConnection()){
            var r=TruyVanDB.one(c,"SELECT * FROM stock_counts WHERE id=?",id);if(r==null)throw new NoSuchElementException("Không tìm thấy phiếu kiểm kê");KhoService.access(c,((Number)r.get("warehouse_id")).longValue(),user,roles);
            r.put("lines",TruyVanDB.rows(c,"SELECT l.*,p.sku,p.name,p.base_unit,b.lot_number,COALESCE(v.name,'Chưa phân vị trí') location_name,(l.actual_quantity-l.snapshot_quantity) difference FROM stock_count_lines l JOIN products p ON p.id=l.product_id JOIN stock_bins b ON b.id=l.bin_id LEFT JOIN warehouse_locations v ON v.id=b.location_id WHERE l.count_id=? ORDER BY l.product_id,l.id",id));r.put("canConfirm",roles.contains("WH_MANAGER"));return r;
        }
    }
    public long create(long user,Collection<String> roles,Map<String,Object> in)throws SQLException{
        long wh=KhoService.id(in.get("warehouseId"));Long cat=in.get("categoryId")==null?null:KhoService.id(in.get("categoryId"));
        try(Connection c=KetNoiDB.getConnection()){c.setAutoCommit(false);try{KhoService.access(c,wh,user,roles);KhoService.actor(c,user);TruyVanDB.one(c,"SELECT pg_advisory_xact_lock(?)",wh);
            if(cat!=null&&TruyVanDB.one(c,"SELECT id FROM categories WHERE id=?",cat)==null)throw new IllegalArgumentException("Nhóm hàng không hợp lệ");
            var bins=TruyVanDB.rows(c,"SELECT b.id,b.product_id,b.on_hand FROM stock_bins b JOIN products p ON p.id=b.product_id WHERE b.warehouse_id=?"+category(cat)+" ORDER BY b.product_id,b.id FOR UPDATE OF b",cat==null?new Object[]{wh}:new Object[]{wh,cat});
            if(bins.isEmpty())throw new IllegalArgumentException("Kho/nhóm chưa có dòng tồn để kiểm kê");
            long id=TruyVanDB.id(TruyVanDB.one(c,"INSERT INTO stock_counts(code,warehouse_id,category_id,created_by) VALUES('KK-'||nextval('stock_counts_id_seq'),?,?,?) RETURNING id",wh,cat,user));
            for(var b:bins)TruyVanDB.update(c,"INSERT INTO stock_count_lines(count_id,bin_id,product_id,snapshot_quantity) VALUES(?,?,?,?)",id,b.get("id"),b.get("product_id"),b.get("on_hand"));c.commit();return id;
        }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
    }
    public void counts(long id,long user,Collection<String> roles,Map<String,Object> in)throws SQLException{
        if(!(in.get("lines") instanceof List<?> raw)||raw.isEmpty()||raw.size()>5000)throw new IllegalArgumentException("Nhập số thực đếm cho các dòng kiểm kê");
        try(Connection c=KetNoiDB.getConnection()){c.setAutoCommit(false);try{
            var r=TruyVanDB.one(c,"SELECT * FROM stock_counts WHERE id=? FOR UPDATE",id);if(r==null)throw new NoSuchElementException("Không tìm thấy phiếu");KhoService.access(c,((Number)r.get("warehouse_id")).longValue(),user,roles);if(!"DRAFT".equals(r.get("status")))throw new IllegalArgumentException("Phiếu đã chốt");KhoService.actor(c,user);
            Set<Long> seen=new HashSet<>();for(Object item:raw){if(!(item instanceof Map<?,?> l))throw new IllegalArgumentException("Dòng kiểm kê không hợp lệ");long line=KhoService.id(l.get("lineId"));if(!seen.add(line))throw new IllegalArgumentException("Dòng kiểm kê bị trùng");BigDecimal qty=PhieuNhapKhoService.quantity(l.get("actualQuantity"),true);if(qty.stripTrailingZeros().scale()>0)throw new IllegalArgumentException("Số đếm phải nguyên theo đơn vị cơ sở");
                if(TruyVanDB.update(c,"UPDATE stock_count_lines SET actual_quantity=? WHERE id=? AND count_id=?",qty,line,id)==0)throw new IllegalArgumentException("Dòng kiểm kê không thuộc phiếu");}c.commit();
        }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
    }
    public void confirm(long id,long user,Collection<String> roles,String reason)throws SQLException{
        if(!roles.contains("WH_MANAGER"))throw new SecurityException("Chỉ Quản lý kho được chốt kiểm kê");
        reason=KhoService.text(reason,"Lý do điều chỉnh",1000,true);
        try(Connection c=KetNoiDB.getConnection()){c.setAutoCommit(false);try{
            var r=TruyVanDB.one(c,"SELECT * FROM stock_counts WHERE id=? FOR UPDATE",id);if(r==null)throw new NoSuchElementException("Không tìm thấy phiếu");long wh=((Number)r.get("warehouse_id")).longValue();KhoService.access(c,wh,user,roles);KhoService.actor(c,user);TruyVanDB.one(c,"SELECT pg_advisory_xact_lock(?)",wh);
            if(!"DRAFT".equals(r.get("status")))throw new IllegalArgumentException("Phiếu đã chốt");
            var ls=TruyVanDB.rows(c,"SELECT * FROM stock_count_lines WHERE count_id=? ORDER BY product_id,id",id);
            Long cat=r.get("category_id")==null?null:((Number)r.get("category_id")).longValue();
            var current=TruyVanDB.rows(c,"SELECT b.id,b.on_hand FROM stock_bins b JOIN products p ON p.id=b.product_id WHERE b.warehouse_id=?"+category(cat)+" ORDER BY b.product_id,b.id FOR UPDATE OF b",cat==null?new Object[]{wh}:new Object[]{wh,cat});
            if(current.size()!=ls.size())throw new IllegalArgumentException("Tồn/lô đã thay đổi sau khi tạo phiếu. Hãy tạo kiểm kê mới.");
            Map<Long,BigDecimal> quantities=new HashMap<>();for(var b:current)quantities.put(TruyVanDB.id(b),(BigDecimal)b.get("on_hand"));
            for(var l:ls){if(l.get("actual_quantity")==null)throw new IllegalArgumentException("Chưa nhập đủ số thực đếm");if(!Objects.equals(quantities.get(((Number)l.get("bin_id")).longValue()),l.get("snapshot_quantity")))throw new IllegalArgumentException("Tồn đã thay đổi sau snapshot; không ghi đè biến động mới. Hãy tạo kiểm kê mới.");}
            // Khóa SKU theo thứ tự ổn định trước khi ưu tiên bút toán cộng theo lô.
            for(long product:ls.stream().map(l->((Number)l.get("product_id")).longValue()).distinct().sorted().toList())StockLedgerDB.lockStock(c,wh,product);
            ls.sort(Comparator.<Map<String,Object>,BigDecimal>comparing(l->((BigDecimal)l.get("actual_quantity")).subtract((BigDecimal)l.get("snapshot_quantity"))).reversed());
            for(var l:ls){long product=((Number)l.get("product_id")).longValue();BigDecimal delta=((BigDecimal)l.get("actual_quantity")).subtract((BigDecimal)l.get("snapshot_quantity"));StockLedgerDB.change(c,wh,product,((Number)l.get("bin_id")).longValue(),delta,"COUNT",id,TruyVanDB.id(l),user,reason);}
            TruyVanDB.update(c,"UPDATE stock_counts SET status='POSTED',reason=?,posted_by=?,posted_at=CURRENT_TIMESTAMP WHERE id=?",reason,user,id);c.commit();
        }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
    }
}

