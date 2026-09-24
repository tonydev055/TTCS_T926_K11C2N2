package dao;
import java.sql.*; import java.util.*;
public class SupplierDAO extends BaseDAO {
    public List<Map<String,Object>> findAll() throws SQLException { return query("SELECT * FROM suppliers ORDER BY id DESC"); }
    public Map<String,Object> findById(long id)throws SQLException{var x=query("SELECT * FROM suppliers WHERE id=?",id);return x.isEmpty()?null:x.get(0);}
    public int delete(long id)throws SQLException{return update("DELETE FROM suppliers WHERE id=?",id);}
}
