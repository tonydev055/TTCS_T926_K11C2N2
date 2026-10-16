package service;

import db.TruyVanDB;
import java.math.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import util.KetNoiDB;

/** S7-02: khóa đại lý, đối trừ nguyên tử và chỉ thêm bút toán hoàn tác. */
public final class ThanhToanService {
    public static BigDecimal money(Object value) {
        try {
            BigDecimal n=new BigDecimal(Objects.toString(value,""));
            if(n.scale()>2||n.precision()-n.scale()>16)throw new ArithmeticException();
            return n.setScale(2);
        }catch(RuntimeException e){throw new IllegalArgumentException("Số tiền không hợp lệ, tối đa 2 chữ số thập phân");}
    }
    public static LocalDate date(Object value) {
        try{LocalDate d=LocalDate.parse(Objects.toString(value,""));if(d.isAfter(LocalDate.now()))throw new IllegalArgumentException();return d;}
        catch(RuntimeException e){throw new IllegalArgumentException("Ngày thu không hợp lệ hoặc ở tương lai");}
    }
    public static void lockCustomer(Connection c,long customer)throws SQLException {
        if(TruyVanDB.one(c,"SELECT id FROM customers WHERE id=? FOR UPDATE",customer)==null)throw new NoSuchElementException("Không tìm thấy đại lý");
    }
    public Map<String,Object> list()throws SQLException {
        try(Connection c=KetNoiDB.getConnection()){
            var out=new LinkedHashMap<String,Object>();
            out.put("customers",TruyVanDB.rows(c,"SELECT c.id,c.code,c.name,COALESCE(sum(b.outstanding),0) outstanding FROM customers c LEFT JOIN receivable_invoice_balances b ON b.customer_id=c.id GROUP BY c.id ORDER BY c.code"));
            out.put("invoices",TruyVanDB.rows(c,"SELECT b.*,b.issued_at::date::text issue_date,b.due_date::text due_date,c.code customer_code,c.name customer_name FROM receivable_invoice_balances b JOIN customers c ON c.id=b.customer_id WHERE b.outstanding>0 ORDER BY b.issued_at,b.id"));
            out.put("payments",TruyVanDB.rows(c,"SELECT p.*,p.received_date::text received_date,c.code customer_code,c.name customer_name,u.full_name collector_name,(r.id IS NOT NULL) reversed,r.reason reversal_reason FROM receivable_payments p JOIN customers c ON c.id=p.customer_id JOIN users u ON u.id=p.received_by LEFT JOIN payment_reversals r ON r.payment_id=p.id ORDER BY p.id DESC LIMIT 500"));
            return out;
        }
    }
    public Map<String,Object> detail(long id)throws SQLException {try(Connection c=KetNoiDB.getConnection()){return detail(c,id);}}
    static Map<String,Object> detail(Connection c,long id)throws SQLException {
        var row=TruyVanDB.one(c,"SELECT p.*,p.received_date::text received_date,c.code customer_code,c.name customer_name,c.phone,c.region,u.full_name collector_name,(r.id IS NOT NULL) reversed,r.reason reversal_reason FROM receivable_payments p JOIN customers c ON c.id=p.customer_id JOIN users u ON u.id=p.received_by LEFT JOIN payment_reversals r ON r.payment_id=p.id WHERE p.id=?",id);
        if(row==null)throw new NoSuchElementException("Không tìm thấy khoản thu");
        row.put("allocations",TruyVanDB.rows(c,"SELECT a.invoice_id,i.invoice_number,a.amount FROM payment_allocations a JOIN invoices i ON i.id=a.invoice_id WHERE a.payment_id=? ORDER BY i.issued_at,i.id",id));return row;
    }
    public Map<String,Object> create(long actor,Map<String,Object> input)throws SQLException {
        long customer=KhoService.id(input.get("customerId"));BigDecimal amount=money(input.get("amount"));
        if(amount.signum()<=0)throw new IllegalArgumentException("Số tiền phải lớn hơn 0");
        LocalDate date=date(input.get("receivedDate"));String method=KhoService.text(input.get("paymentMethod"),"Hình thức thu",20,true);
        if(!Set.of("CASH","BANK_TRANSFER","OTHER").contains(method))throw new IllegalArgumentException("Hình thức thu không hợp lệ");
        String note=KhoService.text(input.get("note"),"Ghi chú",1000,false);UUID key;
        try{key=UUID.fromString(Objects.toString(input.get("requestKey"),""));}catch(RuntimeException e){throw new IllegalArgumentException("Mã yêu cầu không hợp lệ");}
        Long selected=input.get("invoiceId")==null?null:KhoService.id(input.get("invoiceId"));
        try(Connection c=KetNoiDB.getConnection()){c.setAutoCommit(false);try{
            lockCustomer(c,customer);KhoService.actor(c,actor);
            var previous=TruyVanDB.one(c,"SELECT * FROM receivable_payments WHERE request_key=?",key);
            if(previous!=null){
                Long previousTarget=previous.get("target_invoice_id")==null?null:((Number)previous.get("target_invoice_id")).longValue();
                if(!Objects.equals(previousTarget,selected))throw new IllegalArgumentException("Mã yêu cầu đã dùng với cách đối trừ khác");
                if(((Number)previous.get("customer_id")).longValue()!=customer||((Number)previous.get("received_by")).longValue()!=actor||((BigDecimal)previous.get("amount")).compareTo(amount)!=0||!method.equals(previous.get("payment_method"))||!date.equals(((java.sql.Date)previous.get("received_date")).toLocalDate())||!note.equals(previous.get("note")))throw new IllegalArgumentException("Mã yêu cầu đã dùng cho khoản thu khác");
                if(selected!=null){var allocations=TruyVanDB.rows(c,"SELECT invoice_id FROM payment_allocations WHERE payment_id=?",previous.get("id"));if(allocations.size()!=1||((Number)allocations.get(0).get("invoice_id")).longValue()!=selected)throw new IllegalArgumentException("Mã yêu cầu đã dùng với hóa đơn khác");}
                var result=detail(c,TruyVanDB.id(previous));c.commit();return result;
            }
            var balances=TruyVanDB.rows(c,"SELECT * FROM receivable_invoice_balances WHERE customer_id=? AND outstanding>0 AND issued_at::date<=?"+(selected==null?"":" AND id=?")+" ORDER BY issued_at,id",selected==null?new Object[]{customer,date}:new Object[]{customer,date,selected});
            BigDecimal available=balances.stream().map(x->(BigDecimal)x.get("outstanding")).reduce(BigDecimal.ZERO,BigDecimal::add);
            if(amount.compareTo(available)>0)throw new IllegalArgumentException("Số tiền vượt công nợ hóa đơn được chọn hoặc công nợ hợp lệ tại ngày thu");
            long id=TruyVanDB.id(TruyVanDB.one(c,"SELECT nextval('receivable_payments_id_seq') id"));
            TruyVanDB.update(c,"INSERT INTO receivable_payments(id,receipt_code,customer_id,amount,payment_method,received_date,received_by,note,request_key,target_invoice_id) VALUES(?,?,?,?,?,?,?,?,?,?)",id,"PT-"+String.format("%08d",id),customer,amount,method,date,actor,note,key,selected);
            BigDecimal remaining=amount;
            for(var balance:balances){if(remaining.signum()==0)break;BigDecimal applied=remaining.min((BigDecimal)balance.get("outstanding"));TruyVanDB.update(c,"INSERT INTO payment_allocations(payment_id,invoice_id,amount) VALUES(?,?,?)",id,balance.get("id"),applied);remaining=remaining.subtract(applied);}
            var result=detail(c,id);c.commit();return result;
        }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
    }
    public Map<String,Object> reverse(long payment,long actor,String reason)throws SQLException {
        reason=KhoService.text(reason,"Lý do điều chỉnh",1000,true);
        try(Connection c=KetNoiDB.getConnection()){c.setAutoCommit(false);try{
            var row=TruyVanDB.one(c,"SELECT customer_id FROM receivable_payments WHERE id=?",payment);if(row==null)throw new NoSuchElementException("Không tìm thấy khoản thu");
            lockCustomer(c,((Number)row.get("customer_id")).longValue());KhoService.actor(c,actor);
            if(TruyVanDB.one(c,"SELECT 1 FROM payment_reversals WHERE payment_id=?",payment)!=null)throw new IllegalArgumentException("Khoản thu đã được hoàn tác");
            TruyVanDB.update(c,"INSERT INTO payment_reversals(payment_id,reason,reversed_by) VALUES(?,?,?)",payment,reason,actor);
            var result=detail(c,payment);c.commit();return result;
        }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
    }
}
