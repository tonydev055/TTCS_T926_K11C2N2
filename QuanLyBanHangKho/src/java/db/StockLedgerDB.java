package db;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
/** S5-04: mọi thay đổi tồn/bin và bút toán cùng connection của giao dịch nghiệp vụ. */
public final class StockLedgerDB {
    private StockLedgerDB(){}
    public static void lockStock(Connection c,long warehouse,long product)throws SQLException{
        TruyVanDB.update(c,"INSERT INTO inventory(warehouse_id,product_id) VALUES(?,?) ON CONFLICT(warehouse_id,product_id) DO NOTHING",warehouse,product);
        TruyVanDB.one(c,"SELECT id FROM inventory WHERE warehouse_id=? AND product_id=? FOR UPDATE",warehouse,product);
    }
    public static long bin(Connection c,long warehouse,long product,Object location,String lot,Object expiry)throws SQLException{
        return TruyVanDB.id(TruyVanDB.one(c,"INSERT INTO stock_bins(warehouse_id,product_id,location_id,lot_number,expiry_date) VALUES(?,?,?,?,?) ON CONFLICT(warehouse_id,location_id,product_id,lot_number,expiry_date) DO UPDATE SET lot_number=EXCLUDED.lot_number RETURNING id",warehouse,product,location,lot,expiry));
    }
    public static void change(Connection c,long warehouse,long product,long bin,BigDecimal delta,String kind,long doc,long line,long actor,String reason)throws SQLException{
        lockStock(c,warehouse,product);
        var total=TruyVanDB.one(c,"SELECT on_hand,reserved FROM inventory WHERE warehouse_id=? AND product_id=?",warehouse,product);
        var bucket=TruyVanDB.one(c,"SELECT on_hand FROM stock_bins WHERE id=? AND warehouse_id=? AND product_id=? FOR UPDATE",bin,warehouse,product);
        if(bucket==null)throw new IllegalArgumentException("Lô/vị trí không thuộc sản phẩm và kho");
        BigDecimal next=((BigDecimal)total.get("on_hand")).add(delta),nextBin=((BigDecimal)bucket.get("on_hand")).add(delta);
        if(nextBin.signum()<0||next.compareTo((BigDecimal)total.get("reserved"))<0)throw new IllegalArgumentException("Không đủ tồn khả dụng hoặc số lượng lô/vị trí");
        TruyVanDB.update(c,"UPDATE stock_bins SET on_hand=? WHERE id=?",nextBin,bin);
        TruyVanDB.update(c,"UPDATE inventory SET on_hand=?,updated_at=CURRENT_TIMESTAMP WHERE warehouse_id=? AND product_id=?",next,warehouse,product);
        TruyVanDB.update(c,"INSERT INTO stock_movements(warehouse_id,product_id,bin_id,delta,kind,document_id,line_id,actor_id,reason) VALUES(?,?,?,?,?,?,?,?,?)",warehouse,product,bin,delta,kind,doc,line,actor,reason);
    }
}
