package db;

import java.sql.*;
import java.util.*;
import util.KetNoiDB;
import service.ChinhSachChietKhauService;

public class ChinhSachChietKhauDB extends CoSoDB {
    public List<Map<String,Object>> findAll() throws SQLException {
        // One query gives a consistent snapshot of policies and tiers.
        var rows = query("SELECT p.id,p.name,p.scope,COALESCE(p.product_id,p.category_id)::text tid,"+
            "COALESCE(s.sku || ' — ' || s.name,c.name) tname,p.dtype,p.active,t.qty_from,t.value "+
            "FROM discount_policies p LEFT JOIN products s ON s.id=p.product_id "+
            "LEFT JOIN categories c ON c.id=p.category_id JOIN discount_tiers t ON t.policy_id=p.id ORDER BY p.id,t.qty_from");
        Map<Long,Map<String,Object>> policies = new LinkedHashMap<>();
        for (var row : rows) {
            long id=((Number)row.get("id")).longValue();
            var policy=policies.computeIfAbsent(id,k -> {
                var copy=new LinkedHashMap<>(row); copy.remove("qty_from"); copy.remove("value");
                copy.put("tiers",new ArrayList<Map<String,Object>>()); return copy;
            });
            @SuppressWarnings("unchecked") var tiers=(List<Map<String,Object>>)policy.get("tiers");
            tiers.add(Map.of("q",row.get("qty_from"),"v",row.get("value")));
        }
        return new ArrayList<>(policies.values());
    }
    public Map<String,Object> masters() throws SQLException {
        return Map.of("skus",query("SELECT id::text id,sku || ' — ' || name AS name,category_id::text gid FROM products WHERE active ORDER BY sku"),
            "groups",query("SELECT id::text id,name FROM categories ORDER BY name"));
    }
    public long save(Long id, Map<String,Object> input) throws SQLException {
        var data=ChinhSachChietKhauService.validate(input);
        try (Connection c=KetNoiDB.getConnection()) {
            c.setAutoCommit(false);
            try {
                String sql=id==null
                    ? "INSERT INTO discount_policies(name,scope,product_id,category_id,dtype,active) VALUES(?,?,?,?,?,?) RETURNING id"
                    : "UPDATE discount_policies SET name=?,scope=?,product_id=?,category_id=?,dtype=?,active=?,updated_at=CURRENT_TIMESTAMP WHERE id=? RETURNING id";
                long saved;
                try (PreparedStatement p=c.prepareStatement(sql)) {
                    p.setString(1,(String)data.get("name")); p.setString(2,(String)data.get("scope"));
                    long target=Long.parseLong((String)data.get("tid"));
                    p.setObject(3,data.get("scope").equals("SKU") ? target : null,Types.BIGINT);
                    p.setObject(4,data.get("scope").equals("GROUP") ? target : null,Types.BIGINT);
                    p.setString(5,(String)data.get("dtype")); p.setBoolean(6,(Boolean)data.get("active"));
                    if (id!=null) p.setLong(7,id);
                    try (ResultSet r=p.executeQuery()) {
                        if (!r.next()) throw new NoSuchElementException("Không tìm thấy chính sách");
                        saved=r.getLong(1);
                    }
                }
                try (PreparedStatement p=c.prepareStatement("DELETE FROM discount_tiers WHERE policy_id=?")) {
                    p.setLong(1,saved); p.executeUpdate();
                }
                try (PreparedStatement p=c.prepareStatement("INSERT INTO discount_tiers(policy_id,qty_from,value) VALUES(?,?,?)")) {
                    for (Object item:(List<?>)data.get("tiers")) {
                        Map<?,?> t=(Map<?,?>)item; p.setLong(1,saved);
                        p.setInt(2,((Number)t.get("q")).intValue()); p.setBigDecimal(3,ChinhSachChietKhauService.decimal(t.get("v"))); p.addBatch();
                    }
                    p.executeBatch();
                }
                c.commit(); return saved;
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        }
    }
    public boolean delete(long id) throws SQLException {
        return update("DELETE FROM discount_policies WHERE id=?",id)>0;
    }
    public Map<String,Object> quote(Map<String,Object> input) throws SQLException {
        long product=ChinhSachChietKhauService.positiveId(input.get("productId"));
        long qty=ChinhSachChietKhauService.positiveId(input.get("quantity"));
        if (qty>Integer.MAX_VALUE) throw new IllegalArgumentException("Số lượng quá lớn");
        var rows=query("SELECT category_id FROM products WHERE id=? AND active",product);
        if (rows.isEmpty()) throw new NoSuchElementException("Sản phẩm không tồn tại hoặc đã ngừng hoạt động");
        return ChinhSachChietKhauService.quote(findAll(),product,((Number)rows.get(0).get("category_id")).longValue(),
            (int)qty,ChinhSachChietKhauService.decimal(input.get("unitPrice")));
    }
}
