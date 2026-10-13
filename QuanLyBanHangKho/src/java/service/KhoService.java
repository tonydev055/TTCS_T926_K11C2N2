package service;
import db.TruyVanDB;
import java.sql.*;
import java.util.*;
import security.PhanQuyen;
import util.KetNoiDB;

/** S5-03: nhân viên chỉ thao tác kho được phân công; người quản lý kho cấu hình mọi kho. */
public class KhoService {
    public static boolean manager(Collection<String> roles){return PhanQuyen.allows(roles,"warehouses.manage");}
    public static void access(Connection c,long warehouse,long actor,Collection<String> roles)throws SQLException{
        var w=TruyVanDB.one(c,"SELECT id,active FROM warehouses WHERE id=?",warehouse);
        if(w==null||(!manager(roles)&&TruyVanDB.one(c,"SELECT 1 FROM user_warehouses WHERE user_id=? AND warehouse_id=?",actor,warehouse)==null))throw new NoSuchElementException("Không tìm thấy kho trong phạm vi được phân công");
        if(!Boolean.TRUE.equals(w.get("active")))throw new IllegalArgumentException("Kho đã ngừng hoạt động");
    }
    public static long id(Object v){return ChinhSachChietKhauService.positiveId(v);}
    public static String text(Object v,String label,int max,boolean required){
        if(v!=null&&!(v instanceof String))throw new IllegalArgumentException(label+" không hợp lệ");
        String s=Objects.toString(v,"").trim();if((required&&s.isEmpty())||s.length()>max)throw new IllegalArgumentException(label+" phải có từ "+(required?1:0)+" đến "+max+" ký tự");return s;
    }
    public static boolean active(Object v){if(!(v instanceof Boolean))throw new IllegalArgumentException("Trạng thái phải là true hoặc false");return (Boolean)v;}
    public static void actor(Connection c,long user)throws SQLException{TrangThaiDonHang.setContext(c,user,"");}
    public Map<String,Object> list(long user,Collection<String> roles)throws SQLException{
        try(Connection c=KetNoiDB.getConnection()){
            String scope=manager(roles)?"":" WHERE EXISTS(SELECT 1 FROM user_warehouses a WHERE a.warehouse_id=w.id AND a.user_id=?)";
            var ws=TruyVanDB.rows(c,"SELECT w.*,COALESCE(u.full_name,'') AS manager_name FROM warehouses w LEFT JOIN users u ON u.id=w.manager_id"+scope+" ORDER BY w.code",manager(roles)?new Object[]{}:new Object[]{user});
            List<Map<String,Object>> locations=new ArrayList<>();for(var w:ws)locations.addAll(TruyVanDB.rows(c,"SELECT * FROM warehouse_locations WHERE warehouse_id=? ORDER BY code",w.get("id")));
            Map<String,Object> out=new LinkedHashMap<>();out.put("warehouses",ws);out.put("locations",locations);out.put("canManage",manager(roles));
            out.put("managers",manager(roles)?TruyVanDB.rows(c,"SELECT DISTINCT u.id,u.full_name FROM users u JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id WHERE u.active AND r.code='WH_MANAGER' ORDER BY u.full_name"):List.of());return out;
        }
    }
    public long save(Long warehouse,long user,Collection<String> roles,Map<String,Object> in)throws SQLException{
        if(!manager(roles))throw new SecurityException("Chỉ người có quyền quản lý kho được khai báo kho");
        String code=text(in.get("code"),"Mã kho",32,true).toUpperCase(Locale.ROOT),name=text(in.get("name"),"Tên kho",150,true),address=text(in.get("address"),"Địa chỉ",500,true);
        if(!code.matches("[A-Z0-9][A-Z0-9_.-]*"))throw new IllegalArgumentException("Mã kho không hợp lệ");
        Long managerId=in.get("managerId")==null?null:id(in.get("managerId"));boolean enabled=active(in.get("active"));
        try(Connection c=KetNoiDB.getConnection()){c.setAutoCommit(false);try{actor(c,user);
            if(managerId==null||TruyVanDB.one(c,"SELECT u.id FROM users u JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id WHERE u.id=? AND u.active AND r.code='WH_MANAGER'",managerId)==null)throw new IllegalArgumentException("Vui lòng chọn người phụ trách có vai trò Quản lý kho");
            if(warehouse!=null&&TruyVanDB.one(c,"SELECT id FROM warehouses WHERE id=? FOR UPDATE",warehouse)==null)throw new NoSuchElementException("Không tìm thấy kho");
            if(!enabled&&warehouse!=null&&TruyVanDB.one(c,"SELECT 1 FROM inventory WHERE warehouse_id=? AND (on_hand>0 OR reserved>0) LIMIT 1",warehouse)!=null)throw new IllegalArgumentException("Kho còn tồn hoặc hàng giữ chỗ, chưa thể ngừng hoạt động");
            var row=warehouse==null?TruyVanDB.one(c,"INSERT INTO warehouses(code,name,address,manager_id,active) VALUES(?,?,?,?,?) RETURNING id",code,name,address,managerId,enabled):TruyVanDB.one(c,"UPDATE warehouses SET code=?,name=?,address=?,manager_id=?,active=? WHERE id=? RETURNING id",code,name,address,managerId,enabled,warehouse);
            long result=TruyVanDB.id(row);TruyVanDB.update(c,"INSERT INTO user_warehouses(user_id,warehouse_id) VALUES(?,?) ON CONFLICT DO NOTHING",managerId,result);c.commit();return result;
        }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
    }
    public long location(long warehouse,Long location,long user,Collection<String> roles,Map<String,Object> in)throws SQLException{
        if(!manager(roles))throw new SecurityException("Chỉ người có quyền quản lý kho được khai báo vị trí");
        String code=text(in.get("code"),"Mã vị trí",40,true).toUpperCase(Locale.ROOT),name=text(in.get("name"),"Tên vị trí",150,true);boolean enabled=active(in.get("active"));
        try(Connection c=KetNoiDB.getConnection()){c.setAutoCommit(false);try{access(c,warehouse,user,roles);actor(c,user);
            var row=location==null?TruyVanDB.one(c,"INSERT INTO warehouse_locations(warehouse_id,code,name,active) VALUES(?,?,?,?) RETURNING id",warehouse,code,name,enabled):TruyVanDB.one(c,"UPDATE warehouse_locations SET code=?,name=?,active=? WHERE id=? AND warehouse_id=? RETURNING id",code,name,enabled,location,warehouse);
            if(row==null)throw new NoSuchElementException("Không tìm thấy vị trí của kho");c.commit();return TruyVanDB.id(row);
        }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
    }
}
