package service;
import java.sql.SQLException;
import java.util.*;
/** S7-09: chứng từ của khoản thu S7-02; không tạo nguồn công nợ riêng. */
public final class PhieuThuService {
 @SuppressWarnings("unchecked") public List<Map<String,Object>> list()throws SQLException{return (List<Map<String,Object>>)new ThanhToanService().list().get("payments");}
 public Map<String,Object> detail(long id)throws SQLException{return new ThanhToanService().detail(id);}
}
