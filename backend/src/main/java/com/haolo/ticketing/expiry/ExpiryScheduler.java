package com.haolo.ticketing.expiry;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.haolo.ticketing.payments.PaymentMaintenance;

@Component
@ConditionalOnProperty(name="expiry.enabled",havingValue="true",matchIfMissing=true)
public class ExpiryScheduler {
    private static final Logger log=LoggerFactory.getLogger(ExpiryScheduler.class);
    private final ExpiryService service;
    private final PaymentMaintenance payments;
    public ExpiryScheduler(ExpiryService service,PaymentMaintenance payments){this.service=service;this.payments=payments;}
    @Scheduled(fixedDelayString="${EXPIRY_INTERVAL_SECONDS:5}",initialDelayString="${EXPIRY_INTERVAL_SECONDS:5}",timeUnit=TimeUnit.SECONDS)
    public void tick(){
        var paymentPass=payments.runOnce();
        if(paymentPass.candidates()>0)log.info("Payment pass candidates={} processed={} failed={}",paymentPass.candidates(),paymentPass.processed(),paymentPass.failed());
        var result=service.runOnce();
        if(result.candidates()>0)log.info("Expiry pass candidates={} expired={} processedPayments={} busy={} failed={}",result.candidates(),result.expired(),result.processedPayments(),result.busy(),result.failed());
    }
}
