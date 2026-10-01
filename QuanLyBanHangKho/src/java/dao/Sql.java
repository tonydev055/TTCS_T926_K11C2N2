package dao;
import java.sql.*;
import java.util.*;
public final class Sql {
 private Sql(){}
 public static List<Map<String,Object>> rows(Connection c,String sql,Object... values)throws SQLException{
  try(PreparedStatement p=c.prepareStatement(sql)){bind(p,values);try(ResultSet rs=p.executeQuery()){List<Map<String,Object>> out=new ArrayList<>();ResultSetMetaData meta=rs.getMetaData();while(rs.next()){Map<String,Object> row=new LinkedHashMap<>();for(int i=1;i<=meta.getColumnCount();i++)row.put(meta.getColumnLabel(i),rs.getObject(i));out.add(row);}return out;}}
 }
 public static Map<String,Object> one(Connection c,String sql,Object... values)throws SQLException{var rows=rows(c,sql,values);return rows.isEmpty()?null:rows.get(0);}
 public static int update(Connection c,String sql,Object... values)throws SQLException{try(PreparedStatement p=c.prepareStatement(sql)){bind(p,values);return p.executeUpdate();}}
 private static void bind(PreparedStatement p,Object[] values)throws SQLException{for(int i=0;i<values.length;i++)p.setObject(i+1,values[i]);}
 public static long id(Map<String,Object> row){return ((Number)row.get("id")).longValue();}
}
