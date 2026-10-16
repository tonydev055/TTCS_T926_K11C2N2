package service;

import db.TruyVanDB;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import util.KetNoiDB;

/** S7-01 integration test. Run only against a disposable database whose URL contains "s7_test". */
public final class SalesInvoiceIntegrationTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        String url=System.getenv("DB_URL");
        if(url==null||!url.contains("s7_test"))throw new IllegalStateException("Test S7-01 chỉ được chạy trên database tạm s7_test");
        Fixture f=fixture();
        HoaDonService service=new HoaDonService();
        Map<String,Object> list=service.list();
        check(((List<?>)list.get("candidates")).size()==3,"Ba phiếu giao phải đủ điều kiện");

        Map<String,Object> first=service.issue(f.actor,Map.of(
            "periodFrom",LocalDate.now().minusDays(1).toString(),"periodTo",LocalDate.now().plusDays(1).toString(),
            "note","Kiểm thử gộp phiếu giao","orderIds",List.of(f.order1,f.order2)));
        check("HD-000001".equals(first.get("invoice_number")),"Số hóa đơn đầu tiên phải liên tục từ 1");
        check(((List<?>)first.get("deliveries")).size()==2,"Phải gộp hai phiếu giao cùng đại lý");
        check(decimal(first.get("subtotal")).compareTo(new BigDecimal("1440.00"))==0,"Tạm tính phải theo lượng thực giao");
        check(decimal(first.get("discount_total")).compareTo(new BigDecimal("40.00"))==0,"Chiết khấu phải phân bổ theo lượng thực giao");
        check(decimal(first.get("total_amount")).compareTo(new BigDecimal("1400.00"))==0,"Tổng hóa đơn phải theo lượng thực giao, không theo lượng đặt");
        BigDecimal delivered=((List<Map<String,Object>>)first.get("lines")).stream().map(x->decimal(x.get("delivered_quantity"))).reduce(BigDecimal.ZERO,BigDecimal::add);
        check(delivered.compareTo(new BigDecimal("9"))==0,"Hóa đơn phải lưu 9 đơn vị thực giao thay vì 15 đơn vị đặt");

        Map<String,Object> adjusted=service.adjust(((Number)first.get("id")).longValue(),f.actor,Map.of("amount","-100.00","reason","Giảm do hàng lỗi sau giao"));
        check(((List<?>)adjusted.get("adjustments")).size()==1,"Phải lập được chứng từ điều chỉnh");
        check("DC-000001".equals(((Map<?,?>)((List<?>)adjusted.get("adjustments")).get(0)).get("adjustment_number")),"Số chứng từ điều chỉnh phải tự sinh");
        immutable(((Number)first.get("id")).longValue());

        installFailureTrigger();
        boolean failed=false;
        try { service.issue(f.actor,Map.of("periodFrom",LocalDate.now().minusDays(1).toString(),"periodTo",LocalDate.now().plusDays(1).toString(),"note","ROLLBACK_TEST","orderIds",List.of(f.order3))); }
        catch(SQLException expected){failed=true;}
        check(failed,"Lỗi sau lúc cấp số phải rollback toàn bộ hóa đơn");
        check(counter("INVOICE")==1,"Rollback không được làm nhảy số hóa đơn");
        removeFailureTrigger();
        Map<String,Object> second=service.issue(f.actor,Map.of("periodFrom",LocalDate.now().minusDays(1).toString(),"periodTo",LocalDate.now().plusDays(1).toString(),"note","Sau rollback","orderIds",List.of(f.order3)));
        check("HD-000002".equals(second.get("invoice_number")),"Hóa đơn sau rollback phải nhận số kế tiếp, không nhảy cóc");
        System.out.println("S7-01 integration: "+checks+" checks passed on disposable database.");
    }

    private static Fixture fixture() throws Exception {
        try(Connection c=KetNoiDB.getConnection()){
            c.setAutoCommit(false);
            TruyVanDB.update(c,"SET LOCAL session_replication_role='replica'");
            long actor=TruyVanDB.id(TruyVanDB.one(c,"INSERT INTO users(username,email,full_name,password_hash) VALUES('s7.accountant','s7@test.local','Kế toán kiểm thử','x') RETURNING id"));
            long group=TruyVanDB.id(TruyVanDB.one(c,"INSERT INTO customer_groups(code,name) VALUES('S7TEST','Nhóm kiểm thử') RETURNING id"));
            long customer=TruyVanDB.id(TruyVanDB.one(c,"INSERT INTO customers(code,name,tax_code,customer_group_id,region,phone) VALUES('DL-S7','Đại lý kiểm thử','0312345678',?,'TP.HCM','0900000000') RETURNING id",group));
            long category=TruyVanDB.id(TruyVanDB.one(c,"INSERT INTO categories(code,name) VALUES('S7CAT','Nhóm S7') RETURNING id"));
            long product=TruyVanDB.id(TruyVanDB.one(c,"INSERT INTO products(sku,name,category_id,base_unit) VALUES('S7-SKU','Sản phẩm kiểm thử',?,'Cái') RETURNING id",category));
            long unit=TruyVanDB.id(TruyVanDB.one(c,"INSERT INTO product_units(product_id,name,factor) VALUES(?,'Cái',1) RETURNING id",product));
            TruyVanDB.update(c,"INSERT INTO invoice_number_counters(document_type,last_number) VALUES('INVOICE',0),('ADJUSTMENT',0)");
            long o1=order(c,"S7-DH-001",actor,customer,product,unit,"10","4","1000","100");
            long o2=order(c,"S7-DH-002",actor,customer,product,unit,"5","5","1000","0");
            long o3=order(c,"S7-DH-003",actor,customer,product,unit,"2","2","400","0");
            TruyVanDB.update(c,"SET LOCAL session_replication_role='origin'");
            c.commit();return new Fixture(actor,o1,o2,o3);
        }
    }

    private static long order(Connection c,String code,long actor,long customer,long product,long unit,String quantity,String delivered,String total,String discount)throws Exception{
        long id=TruyVanDB.id(TruyVanDB.one(c,"INSERT INTO orders(code,status,agent_id,created_by,subtotal,discount_total,payable,delivered_at) VALUES(?,'DELIVERED',?,?,0,0,0,CURRENT_TIMESTAMP) RETURNING id",code,customer,actor));
        TruyVanDB.update(c,"INSERT INTO order_lines(order_id,product_id,unit_id,quantity,conversion_factor,base_price,discount_amount,line_total,list_price,floor_price,price_overridden,delivered_base_quantity) VALUES(?,?,?,?,1,0,?,?,0,0,FALSE,?)",id,product,unit,new BigDecimal(quantity),new BigDecimal(discount),new BigDecimal(total),new BigDecimal(delivered));
        TruyVanDB.update(c,"INSERT INTO delivery_confirmations(order_id,outcome,recipient_name,delivered_at,proof_image_path,created_by) VALUES(?,'SUCCESS','Người nhận',CURRENT_TIMESTAMP,'test.png',?)",id,actor);
        return id;
    }

    private static void immutable(long invoice)throws Exception{
        try(Connection c=KetNoiDB.getConnection()){
            boolean update=false,delete=false;
            try{TruyVanDB.update(c,"UPDATE invoices SET note='sửa trái phép' WHERE id=?",invoice);}catch(SQLException e){update="P0001".equals(e.getSQLState());}
            try{TruyVanDB.update(c,"DELETE FROM invoices WHERE id=?",invoice);}catch(SQLException e){delete="P0001".equals(e.getSQLState());}
            check(update&&delete,"Hóa đơn đã phát hành phải chặn cả sửa và xóa");
        }
    }

    private static void installFailureTrigger()throws Exception{try(Connection c=KetNoiDB.getConnection();Statement s=c.createStatement()){s.execute("CREATE FUNCTION s7_test_fail() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN IF NEW.note=''ROLLBACK_TEST'' THEN RAISE EXCEPTION ''forced test failure''; END IF; RETURN NEW; END'");s.execute("CREATE TRIGGER s7_test_fail BEFORE INSERT ON invoices FOR EACH ROW EXECUTE FUNCTION s7_test_fail()");}}
    private static void removeFailureTrigger()throws Exception{try(Connection c=KetNoiDB.getConnection();Statement s=c.createStatement()){s.execute("DROP TRIGGER s7_test_fail ON invoices");s.execute("DROP FUNCTION s7_test_fail()");}}
    private static long counter(String type)throws Exception{try(Connection c=KetNoiDB.getConnection()){return ((Number)TruyVanDB.one(c,"SELECT last_number FROM invoice_number_counters WHERE document_type=?",type).get("last_number")).longValue();}}
    private static BigDecimal decimal(Object value){return (BigDecimal)value;}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    private record Fixture(long actor,long order1,long order2,long order3){}
}

