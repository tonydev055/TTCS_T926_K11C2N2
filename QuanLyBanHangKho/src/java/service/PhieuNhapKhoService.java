package service;
import db.*;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import util.KetNoiDB;
/** S5-04. Nháp không đổi tồn; xác nhận/điều chỉnh ghi tồn và sổ kho nguyên tử. Phụ thuộc S5-03. */
public class PhieuNhapKhoService {
    public static BigDecimal quantity(Object raw,boolean zero){
        BigDecimal q=ChinhSachChietKhauService.decimal(raw);
        if(q.signum()<0||(!zero&&q.signum()==0)||q.scale()>6||q.compareTo(new BigDecimal("1000000000"))>0)throw new IllegalArgumentException("Số lượng không hợp lệ (tối đa 6 số lẻ)");
        return q;
    }
    public Map<String,Object> masters(long user,Collection<String> roles)throws SQLException{
        var out=new LinkedHashMap<>(new KhoService().list(user,roles));
        try(Connection c=KetNoiDB.getConnection()){
            out.put("suppliers",TruyVanDB.rows(c,"SELECT id,code,name FROM suppliers WHERE active ORDER BY name"));
            var ps=TruyVanDB.rows(c,"SELECT id,sku,name,base_unit,lot_managed FROM products WHERE active ORDER BY sku");
            for(var p:ps)p.put("units",TruyVanDB.rows(c,"SELECT id,name,factor FROM product_units WHERE product_id=? AND active ORDER BY factor,id",p.get("id")));
            out.put("products",ps);return out;
        }
    }
    public List<Map<String,Object>> list(long user,Collection<String> roles)throws SQLException{
        try(Connection c=KetNoiDB.getConnection()){
            String scope=KhoService.manager(roles)?"":" AND EXISTS(SELECT 1 FROM user_warehouses a WHERE a.warehouse_id=r.warehouse_id AND a.user_id=?)";
            return TruyVanDB.rows(c,"SELECT r.id,r.code,r.status,r.document_number,r.receipt_date::text AS receipt_date,r.direction,r.warehouse_id,w.name AS warehouse_name,s.name AS supplier_name FROM stock_receipts r JOIN warehouses w ON w.id=r.warehouse_id JOIN suppliers s ON s.id=r.supplier_id WHERE true"+scope+" ORDER BY r.id DESC LIMIT 100",KhoService.manager(roles)?new Object[]{}:new Object[]{user});
        }
    }
    public Map<String,Object> detail(long id,long user,Collection<String> roles)throws SQLException{
        try(Connection c=KetNoiDB.getConnection()){
            var row=TruyVanDB.one(c,"SELECT * FROM stock_receipts WHERE id=?",id);
            if(row==null)throw new NoSuchElementException("Không tìm thấy phiếu nhập");KhoService.access(c,((Number)row.get("warehouse_id")).longValue(),user,roles);
            row.put("lines",TruyVanDB.rows(c,"SELECT l.*,p.sku,p.name,u.name AS unit_name FROM stock_receipt_lines l JOIN products p ON p.id=l.product_id JOIN product_units u ON u.id=l.unit_id WHERE receipt_id=? ORDER BY l.id",id));return row;
        }
    }
    public long save(Long id,Long source,long user,Collection<String> roles,Map<String,Object> in)throws SQLException{
        long warehouse=KhoService.id(in.get("warehouseId")),supplier=KhoService.id(in.get("supplierId"));
        String number=KhoService.text(in.get("documentNumber"),"Số chứng từ",80,true),note=KhoService.text(in.get("note"),"Ghi chú/lý do",1000,source!=null);
        LocalDate date;try{date=LocalDate.parse(Objects.toString(in.get("date"),""));}catch(Exception e){throw new IllegalArgumentException("Ngày nhập không hợp lệ");}
        if(date.isAfter(LocalDate.now()))throw new IllegalArgumentException("Ngày nhập không được ở tương lai");
        int direction=source==null?1:("REMOVE".equals(in.get("direction"))?-1:"ADD".equals(in.get("direction"))?1:0);
        if(direction==0)throw new IllegalArgumentException("Chọn điều chỉnh tăng hoặc giảm");
        if(!(in.get("lines") instanceof List<?> lines)||lines.isEmpty()||lines.size()>200)throw new IllegalArgumentException("Phiếu cần từ 1 đến 200 dòng");
        try(Connection c=KetNoiDB.getConnection()){c.setAutoCommit(false);try{
            KhoService.access(c,warehouse,user,roles);KhoService.actor(c,user);
            if(TruyVanDB.one(c,"SELECT id FROM suppliers WHERE id=? AND active FOR SHARE",supplier)==null)throw new IllegalArgumentException("Nhà cung cấp không hợp lệ");
            if(source!=null){var old=TruyVanDB.one(c,"SELECT warehouse_id,status FROM stock_receipts WHERE id=?",source);if(old==null||!"POSTED".equals(old.get("status"))||((Number)old.get("warehouse_id")).longValue()!=warehouse)throw new IllegalArgumentException("Phiếu gốc phải đã xác nhận và thuộc kho điều chỉnh");}
            long result;
            if(id==null){result=TruyVanDB.id(TruyVanDB.one(c,"INSERT INTO stock_receipts(code,supplier_id,warehouse_id,document_number,receipt_date,note,direction,source_receipt_id,created_by) VALUES('PN-'||nextval('stock_receipts_id_seq'),?,?,?,?,?,?,?,?) RETURNING id",supplier,warehouse,number,date,note,direction,source,user));}
            else{var old=TruyVanDB.one(c,"SELECT * FROM stock_receipts WHERE id=? FOR UPDATE",id);if(old==null)throw new NoSuchElementException("Không tìm thấy phiếu");KhoService.access(c,((Number)old.get("warehouse_id")).longValue(),user,roles);if(!"DRAFT".equals(old.get("status")))throw new IllegalArgumentException("Phiếu đã xác nhận không sửa được");if(old.get("source_receipt_id")!=null)throw new IllegalArgumentException("Lập phiếu điều chỉnh mới thay vì đổi phiếu gốc");TruyVanDB.update(c,"UPDATE stock_receipts SET supplier_id=?,warehouse_id=?,document_number=?,receipt_date=?,note=? WHERE id=?",supplier,warehouse,number,date,note,id);TruyVanDB.update(c,"DELETE FROM stock_receipt_lines WHERE receipt_id=?",id);result=id;}
            for(Object raw:lines){
                if(!(raw instanceof Map<?,?> l))throw new IllegalArgumentException("Dòng hàng không hợp lệ");
                long product=KhoService.id(l.get("productId")),unit=KhoService.id(l.get("unitId")),location=KhoService.id(l.get("locationId"));
                BigDecimal qty=quantity(l.get("quantity"),false);
                var p=TruyVanDB.one(c,"SELECT p.lot_managed,u.factor FROM products p JOIN product_units u ON u.product_id=p.id AND u.id=? AND u.active WHERE p.id=? AND p.active FOR SHARE OF p,u",unit,product);
                if(p==null)throw new IllegalArgumentException("Sản phẩm/đơn vị tính không hợp lệ");
                if(TruyVanDB.one(c,"SELECT id FROM warehouse_locations WHERE id=? AND warehouse_id=? AND active FOR SHARE",location,warehouse)==null)throw new IllegalArgumentException("Vị trí không thuộc kho nhập");
                BigDecimal base=qty.multiply((BigDecimal)p.get("factor"));if(base.stripTrailingZeros().scale()>0)throw new IllegalArgumentException("Số lượng quy đổi phải là số nguyên đơn vị cơ sở");
                String lot=KhoService.text(l.get("lotNumber"),"Số lô",80,Boolean.TRUE.equals(p.get("lot_managed")));LocalDate expiry=null;
                String expiryText=Objects.toString(l.get("expiryDate"),"");if(!expiryText.isEmpty()){try{expiry=LocalDate.parse(expiryText);}catch(Exception e){throw new IllegalArgumentException("Hạn sử dụng không hợp lệ");}}
                if(Boolean.TRUE.equals(p.get("lot_managed"))&&expiry==null)throw new IllegalArgumentException("Hàng quản lý lô phải có hạn sử dụng");
                if(direction>0&&expiry!=null&&expiry.isBefore(date))throw new IllegalArgumentException("Hàng đã hết hạn tại ngày nhập");
                TruyVanDB.update(c,"INSERT INTO stock_receipt_lines(receipt_id,product_id,unit_id,quantity,conversion_factor,location_id,lot_number,expiry_date) VALUES(?,?,?,?,?,?,?,?)",result,product,unit,qty,p.get("factor"),location,lot,expiry);
            }
            c.commit();return result;
        }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
    }
    public void confirm(long id,long user,Collection<String> roles)throws SQLException{
        try(Connection c=KetNoiDB.getConnection()){c.setAutoCommit(false);try{
            var r=TruyVanDB.one(c,"SELECT * FROM stock_receipts WHERE id=? FOR UPDATE",id);if(r==null)throw new NoSuchElementException("Không tìm thấy phiếu nhập");
            long wh=((Number)r.get("warehouse_id")).longValue();KhoService.access(c,wh,user,roles);KhoService.actor(c,user);
            if(!"DRAFT".equals(r.get("status")))throw new IllegalArgumentException("Phiếu đã xác nhận");
            var ls=TruyVanDB.rows(c,"SELECT * FROM stock_receipt_lines WHERE receipt_id=? ORDER BY product_id,id",id);if(ls.isEmpty())throw new IllegalArgumentException("Phiếu không có hàng");
            for(var l:ls){long product=((Number)l.get("product_id")).longValue();StockLedgerDB.lockStock(c,wh,product);long bin=StockLedgerDB.bin(c,wh,product,l.get("location_id"),String.valueOf(l.get("lot_number")),l.get("expiry_date"));
                StockLedgerDB.change(c,wh,product,bin,((BigDecimal)l.get("base_quantity")).multiply(new BigDecimal(r.get("direction").toString())),"RECEIPT",id,TruyVanDB.id(l),user,String.valueOf(r.get("note")));}
            TruyVanDB.update(c,"UPDATE stock_receipts SET status='POSTED',posted_at=CURRENT_TIMESTAMP WHERE id=?",id);c.commit();
        }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
    }
    public void lotMode(long id,long user,Collection<String> roles,boolean managed)throws SQLException{
        if(!KhoService.manager(roles))throw new SecurityException("Chỉ quản lý kho được đổi chế độ quản lý lô");
        try(Connection c=KetNoiDB.getConnection()){c.setAutoCommit(false);try{KhoService.actor(c,user);if(TruyVanDB.update(c,"UPDATE products SET lot_managed=? WHERE id=?",managed,id)==0)throw new NoSuchElementException("Không tìm thấy sản phẩm");c.commit();}catch(SQLException|RuntimeException e){c.rollback();throw e;}}
    }
}
