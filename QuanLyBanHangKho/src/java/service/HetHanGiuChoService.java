package service;
import db.*;
import java.sql.*;
import java.util.*;
import java.util.logging.*;
import util.KetNoiDB;
public class HetHanGiuChoService {
    /** S5-06: mỗi đơn được khóa riêng; nhả tồn và chuyển Chờ duyệt cùng giao dịch. Người thực hiện ghi là Hệ thống. */
    public int expire()throws SQLException{
        int count=0;List<Long> failed=new ArrayList<>();
        for(int i=0;i<100;i++){
            Long id=null;
            try(Connection c=KetNoiDB.getConnection()){c.setAutoCommit(false);try{
                // Bỏ qua đơn vừa lỗi trong lượt này để một đơn hỏng không chặn các đơn hết hạn phía sau.
                var order=TruyVanDB.one(c,"SELECT o.id FROM orders o WHERE o.status='APPROVED' AND NOT (o.id = ANY(?::bigint[])) AND EXISTS(SELECT 1 FROM stock_reservations r WHERE r.order_id=o.id AND r.status='ACTIVE' AND r.expires_at<=CURRENT_TIMESTAMP) ORDER BY o.id FOR UPDATE OF o SKIP LOCKED LIMIT 1",c.createArrayOf("bigint",failed.toArray()));
                if(order==null){c.rollback();break;}
                id=TruyVanDB.id(order);
                TruyVanDB.one(c,"SELECT set_config('app.actor_id','',true),set_config('app.status_reason','Hết hạn giữ chỗ: trả đơn về Chờ duyệt và nhả tồn',true)");
                GiuChoTonDB.release(c,id);
                TruyVanDB.update(c,"UPDATE orders SET status='PENDING_APPROVAL',approval_flags=approval_flags||'[{\"code\":\"RESERVATION_EXPIRED\",\"message\":\"Đã hết hạn giữ chỗ; cần duyệt lại để giữ tồn\"}]'::jsonb WHERE id=?",id);
                c.commit();count++;
            }catch(SQLException|RuntimeException e){
                c.rollback();
                if(id==null)throw e;
                failed.add(id);
                Logger.getLogger(getClass().getName()).log(Level.WARNING,"Không xử lý được hết hạn giữ chỗ của đơn "+id,e);
            }}
        }
        return count;
    }
    public Map<String,Object> settings()throws SQLException{try(Connection c=KetNoiDB.getConnection()){return TruyVanDB.one(c,"SELECT hold_hours FROM stock_reservation_settings WHERE id=1");}}
    public void settings(int hours,long user,Collection<String> roles)throws SQLException{
        if(!KhoService.manager(roles))throw new SecurityException("Chỉ quản lý kho được đổi thời hạn giữ chỗ");
        if(hours<1||hours>168)throw new IllegalArgumentException("Thời hạn giữ chỗ phải từ 1 đến 168 giờ");
        try(Connection c=KetNoiDB.getConnection()){TruyVanDB.update(c,"UPDATE stock_reservation_settings SET hold_hours=? WHERE id=1",hours);}
    }
    public List<Map<String,Object>> list(long user,Collection<String> roles)throws SQLException{
        Long scope=DonHangService.scope(user,roles);try(Connection c=KetNoiDB.getConnection()){
            return TruyVanDB.rows(c,"SELECT o.id,o.code,o.status,k.name AS customer_name,w.name AS warehouse_name,p.sku,p.name,r.quantity,r.expires_at::text AS expires_at FROM stock_reservations r JOIN orders o ON o.id=r.order_id JOIN customers k ON k.id=o.agent_id JOIN warehouses w ON w.id=r.warehouse_id JOIN products p ON p.id=r.product_id WHERE r.status='ACTIVE'"+(scope==null?"":" AND k.sales_rep_id=?")+" ORDER BY r.expires_at,o.id LIMIT 200",scope==null?new Object[]{}:new Object[]{scope});
        }
    }
}

