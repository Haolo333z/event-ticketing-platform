package com.haolo.ticketing.payments;

import java.sql.Timestamp;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.haolo.ticketing.bookings.*;
import com.haolo.ticketing.inventory.InventoryConfirmation;
import com.haolo.ticketing.tickets.TicketService;
import com.haolo.ticketing.shared.ApiException;

@Service
public class PaymentResultProcessor {
    private static final Logger log=LoggerFactory.getLogger(PaymentResultProcessor.class);
    private final JdbcTemplate jdbc;
    private final BookingTransactions transactions;
    private final BookingLifecycle lifecycle;
    private final InventoryConfirmation inventory;
    private final TicketService tickets;
    public PaymentResultProcessor(JdbcTemplate jdbc,BookingTransactions transactions,BookingLifecycle lifecycle,InventoryConfirmation inventory,TicketService tickets){this.jdbc=jdbc;this.transactions=transactions;this.lifecycle=lifecycle;this.inventory=inventory;this.tickets=tickets;}
    /** Trusted local callback adapter; validate linkage and durable result, never accept a client status/time. */
    public int process(UUID booking,UUID payment){return processUntil(booking,payment,transactions.newDeadline());}
    public int processUntil(UUID booking,UUID payment,long deadline){return transactions.runUntil(()->{
        lifecycle.lock(booking);
        var rows=jdbc.queryForList("SELECT booking_id,status,recorded_at FROM ticketing.payments WHERE id=?",payment);
        if(rows.isEmpty())throw ApiException.missing();var result=rows.getFirst();
        if(!booking.equals(result.get("booking_id"))||result.get("recorded_at")==null||!Set.of("SUCCESS","FAILURE").contains(result.get("status")))throw ApiException.invalid();
        return processLockedBooking(booking);
    },deadline);}
    @Transactional(propagation=Propagation.MANDATORY)
    public int processLockedBooking(UUID booking){
        var initial=lifecycle.lock(booking);
        var results=jdbc.queryForList("SELECT * FROM ticketing.payments WHERE booking_id=? AND recorded_at IS NOT NULL AND processed_at IS NULL ORDER BY CASE WHEN status='SUCCESS' AND recorded_at<=? THEN 0 ELSE 1 END,recorded_at,id",booking,initial.get("expires_at"));
        for(var result:results){
            var current=lifecycle.lock(booking);String state=(String)current.get("status");
            UUID payment=(UUID)result.get("id"),correlation=(UUID)result.get("correlation_id");
            boolean success="SUCCESS".equals(result.get("status"));
            boolean timely=((Timestamp)result.get("recorded_at")).compareTo((Timestamp)current.get("expires_at"))<=0;
            if(state.equals("PENDING_PAYMENT")){
                if(success&&timely){
                    inventory.confirm(booking);lifecycle.transition(booking,"CONFIRMED",correlation,Map.of());tickets.issue(booking);
                }else if(!success){
                    lifecycle.releaseAndTransition(booking,"PAYMENT_FAILED",correlation);
                }else{
                    lifecycle.releaseAndTransition(booking,"EXPIRED",correlation);
                    UUID compensation=compensate(booking,payment,"LATE_SUCCESS");
                    lifecycle.transition(booking,"REFUND_REQUIRED",correlation,Map.of("paymentId",payment.toString(),"compensationId",compensation.toString()));
                }
            }else if(success&&Set.of("EXPIRED","REFUND_REQUIRED","CANCELLED","PAYMENT_FAILED").contains(state)){
                UUID compensation=compensate(booking,payment,"SUCCESS_AFTER_"+state);
                if(state.equals("EXPIRED"))lifecycle.transition(booking,"REFUND_REQUIRED",correlation,Map.of("paymentId",payment.toString(),"compensationId",compensation.toString()));
            }else if(!success&&state.equals("CONFIRMED")){
                log.warn("Payment reconciliation required bookingId={} paymentResultId={} status=FAILURE bookingStatus=CONFIRMED",booking,payment);
            }
            jdbc.update("UPDATE ticketing.payments SET processed_at=clock_timestamp() WHERE id=? AND processed_at IS NULL",payment);
        }
        return results.size();
    }
    private UUID compensate(UUID booking,UUID payment,String reason){
        jdbc.update("INSERT INTO ticketing.payment_compensations(id,booking_id,payment_id,reason) VALUES (?,?,?,?) ON CONFLICT(payment_id) DO NOTHING",UUID.randomUUID(),booking,payment,reason);
        return jdbc.queryForObject("SELECT id FROM ticketing.payment_compensations WHERE payment_id=?",UUID.class,payment);
    }
}
