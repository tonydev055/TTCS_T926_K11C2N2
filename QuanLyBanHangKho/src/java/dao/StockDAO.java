package dao;
import java.sql.*; import java.util.*;
public class StockDAO extends BaseDAO {
    public List<Map<String,Object>> findAll() throws SQLException { return query("SELECT * FROM stock_transactions ORDER BY id DESC"); }
    public Map<String,Object> findById(long id)throws SQLException{var x=query("SELECT * FROM stock_transactions WHERE id=?",id);return x.isEmpty()?null:x.get(0);}
    public int delete(long id)throws SQLException{return update("DELETE FROM stock_transactions WHERE id=?",id);}
}
