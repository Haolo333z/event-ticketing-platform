package com.haolo.ticketing.payments;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.haolo.ticketing.shared.ApiException;
import com.haolo.ticketing.bookings.BookingTransactions;

@Service
public class PaymentMaintenance {
    public record Pass(int candidates,int processed,int failed){}
    private static final Logger log=LoggerFactory.getLogger(PaymentMaintenance.class);
    private final JdbcTemplate jdbc;
    private final PaymentSimulatorService simulator;
    private final PaymentResultProcessor processor;
    private final int batch;
    private final BookingTransactions transactions;
    @Value("${messaging.enabled:false}") private boolean messaging;
    public PaymentMaintenance(JdbcTemplate jdbc,PaymentSimulatorService simulator,PaymentResultProcessor processor,BookingTransactions transactions,@Value("${EXPIRY_BATCH_SIZE:50}")int batch){this.jdbc=jdbc;this.simulator=simulator;this.processor=processor;this.transactions=transactions;this.batch=batch;}
    public Pass runOnce(){
        var rows=jdbc.queryForList("SELECT id,booking_id,status FROM ticketing.payments WHERE (status='SCHEDULED' AND result_due_at<=clock_timestamp()) OR(?=false AND recorded_at IS NOT NULL AND processed_at IS NULL) ORDER BY created_at,id LIMIT ?",messaging,batch);
        int processed=0,failed=0;
        for(var row:rows){UUID payment=(UUID)row.get("id"),booking=(UUID)row.get("booking_id");
            try{long deadline=transactions.newDeadline();if(!"SCHEDULED".equals(row.get("status"))||simulator.recordDueUntil(booking,payment,deadline)){if(!messaging)processed+=processor.processUntil(booking,payment,deadline);}}
            catch(ApiException ex){failed++;log.warn("Payment recovery transaction failed bookingId={} paymentResultId={} code={}",booking,payment,ex.code);}
        }
        return new Pass(rows.size(),processed,failed);
    }
}
