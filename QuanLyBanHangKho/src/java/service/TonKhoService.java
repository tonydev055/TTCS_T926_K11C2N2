package service;
import db.TruyVanDB;
import java.sql.*;
import java.util.*;
import util.KetNoiDB;
/** S5-05: ba số tồn riêng biệt, lọc/tìm/phân trang trong kho được phép. Phụ thuộc S5-03. */
public class TonKhoService {
    public Map<String,Object> list(long user,Collection<String> roles,Map<String,String> filters)throws SQLException{
        String q=KhoService.text(filters.get("q"),"Từ khoá",100,false),state=Objects.toString(filters.get("state"),"");
        if(!Set.of("","AVAILABLE","EMPTY","RESERVED").contains(state))throw new IllegalArgumentException("Trạng thái tồn không hợp lệ");
        int page=number(filters.get("page"),1),size=Math.min(100,number(filters.get("pageSize"),20));
        try(Connection c=KetNoiDB.getConnection()){
            StringBuilder where=new StringBuilder(" WHERE w.active AND p.active");List<Object> args=new ArrayList<>();
            if(!KhoService.manager(roles)){where.append(" AND EXISTS(SELECT 1 FROM user_warehouses a WHERE a.warehouse_id=w.id AND a.user_id=?)");args.add(user);}
            if(filters.get("warehouseId")!=null&&!filters.get("warehouseId").isBlank()){long wh=KhoService.id(filters.get("warehouseId"));KhoService.access(c,wh,user,roles);where.append(" AND w.id=?");args.add(wh);}
            if(filters.get("categoryId")!=null&&!filters.get("categoryId").isBlank()){where.append(" AND p.category_id IN (WITH RECURSIVE cat(id) AS (SELECT id FROM categories WHERE id=? UNION SELECT c.id FROM categories c JOIN cat ON c.parent_id=cat.id) SELECT id FROM cat)");args.add(KhoService.id(filters.get("categoryId")));}
            if(!q.isEmpty()){where.append(" AND (p.sku ILIKE ? ESCAPE '\\' OR p.name ILIKE ? ESCAPE '\\')");String pattern="%"+q.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";args.add(pattern);args.add(pattern);}
            if(state.equals("AVAILABLE"))where.append(" AND COALESCE(i.on_hand-i.reserved,0)>0");
            if(state.equals("EMPTY"))where.append(" AND COALESCE(i.on_hand-i.reserved,0)<=0");
            if(state.equals("RESERVED"))where.append(" AND COALESCE(i.reserved,0)>0");
            String from=" FROM warehouses w CROSS JOIN products p LEFT JOIN inventory i ON i.warehouse_id=w.id AND i.product_id=p.id"+where;
            long count=((Number)TruyVanDB.one(c,"SELECT count(*) total"+from,args.toArray()).get("total")).longValue();args.add(size);args.add((page-1)*size);
            var rows=TruyVanDB.rows(c,"SELECT w.id AS warehouse_id,w.name AS warehouse_name,p.id AS product_id,p.sku,p.name,p.base_unit,p.category_id,COALESCE(i.on_hand,0) AS on_hand,COALESCE(i.reserved,0) AS reserved,COALESCE(i.on_hand-i.reserved,0) AS available"+from+" ORDER BY w.code,p.sku LIMIT ? OFFSET ?",args.toArray());
            return Map.of("items",rows,"total",count,"page",page,"pageSize",size);
        }
    }
    private int number(String v,int fallback){if(v==null||v.isBlank())return fallback;try{int n=Integer.parseInt(v);if(n<1||n>100000)throw new NumberFormatException();return n;}catch(Exception e){throw new IllegalArgumentException("Phân trang không hợp lệ");}}
    public Map<String,Object> masters(long user,Collection<String> roles)throws SQLException{
        var out=new LinkedHashMap<>(new KhoService().list(user,roles));try(Connection c=KetNoiDB.getConnection()){out.put("categories",TruyVanDB.rows(c,"SELECT id,name FROM categories ORDER BY name"));}return out;
    }
    /** Chi tiết lô/vị trí và bút toán; phụ thuộc S5-04. */
    public Map<String,Object> ledger(long wh,long product,long user,Collection<String> roles)throws SQLException{
        try(Connection c=KetNoiDB.getConnection()){KhoService.access(c,wh,user,roles);
            return Map.of("bins",TruyVanDB.rows(c,"SELECT b.*,COALESCE(l.name,'Chưa phân vị trí') location_name FROM stock_bins b LEFT JOIN warehouse_locations l ON l.id=b.location_id WHERE b.warehouse_id=? AND b.product_id=? ORDER BY b.expiry_date NULLS LAST,b.id",wh,product),
                "movements",TruyVanDB.rows(c,"SELECT delta,kind,document_id,reason,created_at::text AS created_at FROM stock_movements WHERE warehouse_id=? AND product_id=? ORDER BY id DESC LIMIT 100",wh,product));
        }
    }
}
