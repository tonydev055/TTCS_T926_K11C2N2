package service;

import db.TruyVanDB;
import util.KetNoiDB;
import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.*;

/** S8-10: sổ đầy đủ hóa đơn, điều chỉnh, thu tiền và hoàn tác; không suy số dư từ trang hiện tại. */
public final class SoChiTietCongNoService {
    public Map<String,Object> customers(String keyword)throws SQLException{
        String q=KhoService.text(keyword,"Từ khóa đại lý",150,false);
        try(Connection c=KetNoiDB.getConnection()){
            var rows=TruyVanDB.rows(c,"SELECT id,code,name FROM customers WHERE strpos(lower(code),lower(?))>0 OR strpos(lower(name),lower(?))>0 ORDER BY code,id LIMIT 101",q,q);
            boolean more=rows.size()>100;if(more)rows.remove(100);return Map.of("customers",rows,"hasMore",more);
        }
    }
    public Map<String,Object> report(long customer,String fromText,String toText)throws SQLException{
        KhoService.id(customer);LocalDate from,to;
        try{from=LocalDate.parse(Objects.toString(fromText,""));to=LocalDate.parse(Objects.toString(toText,""));}
        catch(RuntimeException e){throw new IllegalArgumentException("Chọn ngày bắt đầu và kết thúc hợp lệ");}
        if(from.isAfter(to)||to.isAfter(LocalDate.now())||ChronoUnit.DAYS.between(from,to)>3660)throw new IllegalArgumentException("Kỳ báo cáo tối đa 10 năm, không sau hôm nay");
        try(Connection c=KetNoiDB.getConnection()){
            c.setAutoCommit(false);c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            var party=TruyVanDB.one(c,"SELECT id,code,name,region FROM customers WHERE id=?",customer);
            if(party==null)throw new NoSuchElementException("Không tìm thấy đại lý");
            BigDecimal opening=(BigDecimal)TruyVanDB.one(c,"SELECT COALESCE(sum(amount),0) amount FROM receivable_ledger_events WHERE customer_id=? AND event_date<?",customer,from).get("amount");
            var events=TruyVanDB.rows(c,"SELECT event_date::text event_date,recorded_at::text recorded_at,reference,kind,amount FROM receivable_ledger_events WHERE customer_id=? AND event_date BETWEEN ? AND ? ORDER BY event_date,recorded_at,kind,reference,amount LIMIT 50001",customer,from,to);
            if(events.size()>50000)throw new IllegalArgumentException("Sổ vượt 50.000 phát sinh; hãy thu hẹp khoảng ngày");
            BigDecimal balance=opening,debit=BigDecimal.ZERO,credit=BigDecimal.ZERO;
            for(var event:events){var amount=(BigDecimal)event.get("amount");var increase=amount.max(BigDecimal.ZERO);var decrease=amount.negate().max(BigDecimal.ZERO);debit=debit.add(increase);credit=credit.add(decrease);balance=balance.add(amount);event.put("debit",increase);event.put("credit",decrease);event.put("balance",balance);}
            c.commit();return Map.of("customer",party,"from",from.toString(),"to",to.toString(),"items",events,"openingBalance",opening,"debit",debit,"credit",credit,"closingBalance",balance);
        }
    }
}
