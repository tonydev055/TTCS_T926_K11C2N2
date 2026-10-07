package db;

import java.sql.*;
import java.util.*;

public class LichSuGiaDB {

    public Map<String, Object> findByProduct(Connection c, long productId) throws SQLException {
        var product = TruyVanDB.one(
            c,
            "SELECT id,sku,name,base_unit FROM products WHERE id=?",
            productId
        );
        if (product == null) throw new NoSuchElementException("Không tìm thấy sản phẩm");
        var history = TruyVanDB.rows(
            c,
            "SELECT id,old_price,new_price,changed_by_name,changed_by_email,effective_at,changed_at," +
                "price_list_id,price_list_name,price_list_version,customer_group_name " +
                "FROM price_history WHERE product_id=? " +
                "ORDER BY effective_at DESC,changed_at DESC,id DESC LIMIT 500",
            productId
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("product", product);
        result.put("items", history);
        result.put("readOnly", true);
        return result;
    }
}
