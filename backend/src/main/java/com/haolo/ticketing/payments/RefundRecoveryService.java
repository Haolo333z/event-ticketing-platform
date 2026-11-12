package com.haolo.ticketing.payments;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import com.haolo.ticketing.bookings.BookingTransactions;
import com.haolo.ticketing.identity.DemoSettings;

/** Local fake refund: no external payment provider or money movement. */
@Service
public class RefundRecoveryService {
    private final JdbcTemplate jdbc;private final BookingTransactions tx;private final DemoSettings settings;private final int failures;
    public RefundRecoveryService(JdbcTemplate jdbc,BookingTransactions tx,DemoSettings settings,@Value("${messaging.refund-simulated-failures:0}")int failures){this.jdbc=jdbc;this.tx=tx;this.settings=settings;this.failures=failures;}
    public int runOnce(){
        if(!settings.safeProfile)return 0;
        int completed=0;
        for(UUID id:jdbc.queryForList("SELECT id FROM ticketing.payment_compensations WHERE status IN ('PENDING','FAILED') AND attempt_count<3 AND next_attempt_at<=clock_timestamp() ORDER BY created_at,id LIMIT 50",UUID.class)){
            try{if(attempt(id))completed++;}
            catch(RuntimeException ex){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Refund transaction failed compensationId={} type={}",id,ex.getClass().getSimpleName());}
        }
        return completed;
    }
    public boolean attempt(UUID id){
        if(!settings.safeProfile)throw new IllegalStateException("Refund simulator requires local/test");
        return tx.run(()->{
            var rows=jdbc.queryForList("SELECT *,next_attempt_at<=clock_timestamp() AS due FROM ticketing.payment_compensations WHERE id=? FOR UPDATE",id);
            if(rows.isEmpty())return false;var row=rows.getFirst();int count=((Number)row.get("attempt_count")).intValue();
            if(row.get("status").equals("COMPLETED")||count>=3||!Boolean.TRUE.equals(row.get("due")))return false;
            int valid=jdbc.queryForObject("SELECT count(*) FROM ticketing.payments p JOIN ticketing.bookings b ON b.id=p.booking_id WHERE p.id=? AND b.id=? AND p.status='SUCCESS' AND p.processed_at IS NOT NULL AND b.status IN ('EXPIRED','REFUND_REQUIRED','CANCELLED','PAYMENT_FAILED') AND NOT EXISTS(SELECT 1 FROM ticketing.tickets t WHERE t.booking_id=b.id)",Integer.class,row.get("payment_id"),row.get("booking_id"));
            int next=count+1;boolean success=valid==1&&next>failures;String outcome=success?"COMPLETED":"FAILED";
            jdbc.update("INSERT INTO ticketing.compensation_attempts(compensation_id,attempt,outcome,detail) VALUES (?,?,?,?)",id,next,outcome,valid!=1?"Compensation requires reconciliation":success?"Simulated refund completed":"Simulated transient refund failure");
            jdbc.update("UPDATE ticketing.payment_compensations SET status=?,attempt_count=?,processed_at=CASE WHEN ? THEN clock_timestamp() ELSE NULL END,next_attempt_at=clock_timestamp()+make_interval(secs=>power(2,?)::int) WHERE id=?",outcome,next,success,count,id);
            return success;
        });
    }
}
