package com.haolo.ticketing.payments;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import com.haolo.ticketing.bookings.*;
import com.haolo.ticketing.identity.*;
import com.haolo.ticketing.messaging.OutboxWriter;
import com.haolo.ticketing.shared.ApiException;

@Service
public class PaymentSimulatorService {
    public record Accepted(UUID paymentIntentId,String status){}
    public record Receipt(UUID bookingId,UUID paymentId,UUID paymentIntentId,boolean scheduled){}
    private final JdbcTemplate jdbc;
    private final BookingTransactions transactions;
    private final BookingLifecycle lifecycle;
    private final PaymentResultProcessor processor;
    private final OutboxWriter outbox;
    private final DemoSettings settings;
    @org.springframework.beans.factory.annotation.Value("${messaging.enabled:false}") private boolean messaging;
    public PaymentSimulatorService(JdbcTemplate jdbc,BookingTransactions transactions,BookingLifecycle lifecycle,PaymentResultProcessor processor,OutboxWriter outbox,DemoSettings settings){this.jdbc=jdbc;this.transactions=transactions;this.lifecycle=lifecycle;this.processor=processor;this.outbox=outbox;this.settings=settings;}
    public Accepted simulate(DemoPrincipal principal,UUID booking,SimulationInput input,UUID correlation){
        long deadline=transactions.newDeadline();
        var receipt=recordUntil(principal,booking,input,correlation,deadline);
        if(!receipt.scheduled()&&!messaging)processor.processUntil(booking,receipt.paymentId(),deadline);
        return new Accepted(receipt.paymentIntentId(),"ACCEPTED");
    }
    /** Record first; expiry and recovery can process this durable row even if the adapter fails next. */
    public Receipt record(DemoPrincipal principal,UUID booking,SimulationInput input,UUID correlation){
        return recordUntil(principal,booking,input,correlation,transactions.newDeadline());
    }
    private Receipt recordUntil(DemoPrincipal principal,UUID booking,SimulationInput input,UUID correlation,long deadline){
        if(!settings.safeProfile||principal==null||!principal.role().equals("CUSTOMER"))throw new ApiException(403,"FORBIDDEN","Mô phỏng chỉ dành cho khách demo local/test");
        return transactions.runUntil(()->{
            var current=lifecycle.lock(booking);
            if(!principal.accountId().equals(current.get("account_id")))throw ApiException.missing();
            var existing=jdbc.queryForList("SELECT id,scenario,status FROM ticketing.payments WHERE booking_id=? AND request_id=?",booking,input.requestId());
            if(!existing.isEmpty()){
                var result=existing.getFirst();
                if(!input.scenario().equals(result.get("scenario")))throw new ApiException(409,"IDEMPOTENCY_KEY_REUSED","requestId đã dùng cho kịch bản khác");
                return new Receipt(booking,(UUID)result.get("id"),(UUID)current.get("payment_intent_id"),"SCHEDULED".equals(result.get("status")));
            }
            UUID payment=UUID.randomUUID();boolean scheduled=input.scenario().equals("DELAY_TIMEOUT");
            if(scheduled)jdbc.update("INSERT INTO ticketing.payments(id,booking_id,request_id,scenario,status,result_due_at,correlation_id) VALUES (?,?,?,'DELAY_TIMEOUT','SCHEDULED',?::timestamptz+interval '1s',?)",payment,booking,input.requestId(),current.get("expires_at"),correlation);
            else{
                jdbc.update("INSERT INTO ticketing.payments(id,booking_id,request_id,scenario,status,recorded_at,correlation_id) VALUES (?,?,?,?,?,clock_timestamp(),?)",payment,booking,input.requestId(),input.scenario(),input.scenario(),correlation);
                resultEvent(booking,payment,correlation);
            }
            return new Receipt(booking,payment,(UUID)current.get("payment_intent_id"),scheduled);
        },deadline);
    }
    public boolean recordDue(UUID booking,UUID payment){return recordDueUntil(booking,payment,transactions.newDeadline());}
    public boolean recordDueUntil(UUID booking,UUID payment,long deadline){return transactions.runUntil(()->{
        lifecycle.lock(booking);
        var rows=jdbc.queryForList("SELECT *,result_due_at<=clock_timestamp() AS due FROM ticketing.payments WHERE id=? AND booking_id=?",payment,booking);
        if(rows.isEmpty())throw ApiException.missing();var result=rows.getFirst();
        if(!"SCHEDULED".equals(result.get("status")))return true;
        if(!Boolean.TRUE.equals(result.get("due")))return false;
        jdbc.update("UPDATE ticketing.payments SET status='SUCCESS',recorded_at=clock_timestamp() WHERE id=? AND status='SCHEDULED'",payment);
        resultEvent(booking,payment,(UUID)result.get("correlation_id"));return true;
    },deadline);}
    private void resultEvent(UUID booking,UUID payment,UUID correlation){
        Long version=jdbc.queryForObject("UPDATE ticketing.bookings SET aggregate_version=aggregate_version+1 WHERE id=? RETURNING aggregate_version",Long.class,booking);
        outbox.append(booking,version,"PaymentResultRecorded",correlation,Map.of("bookingId",booking.toString(),"paymentResultId",payment.toString()));
    }
}
