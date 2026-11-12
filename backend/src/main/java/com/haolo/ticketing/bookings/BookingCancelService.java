package com.haolo.ticketing.bookings;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import com.haolo.ticketing.payments.PaymentResultProcessor;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.shared.ApiException;

@Service
public class BookingCancelService {
    private final BookingTransactions transactions;
    private final BookingLifecycle lifecycle;
    private final PaymentResultProcessor processor;
    private final BookingQueryService queries;
    public BookingCancelService(BookingTransactions transactions,BookingLifecycle lifecycle,PaymentResultProcessor processor,BookingQueryService queries){this.transactions=transactions;this.lifecycle=lifecycle;this.processor=processor;this.queries=queries;}
    public BookingQueryService.Booking cancel(DemoPrincipal principal,UUID id,UUID correlation){
        if(principal==null||!principal.role().equals("CUSTOMER"))throw new ApiException(403,"FORBIDDEN","Chỉ khách sở hữu booking được hủy");
        boolean allowed=transactions.run(()->{
            var booking=lifecycle.lock(id);if(!principal.accountId().equals(booking.get("account_id")))throw ApiException.missing();
            processor.processLockedBooking(id);booking=lifecycle.lock(id);
            if(booking.get("status").equals("CANCELLED"))return true;
            if(!booking.get("status").equals("PENDING_PAYMENT")||Boolean.TRUE.equals(booking.get("due")))return false;
            lifecycle.releaseAndTransition(id,"CANCELLED",correlation);return true;
        });
        // Any valid payment transition above remains committed even when cancellation is rejected.
        if(!allowed)throw new ApiException(409,"INVALID_BOOKING_STATE","Booking không còn ở trạng thái cho phép hủy");
        return queries.get(principal,id);
    }
}
