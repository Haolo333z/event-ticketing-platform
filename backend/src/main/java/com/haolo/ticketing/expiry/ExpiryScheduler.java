package com.haolo.ticketing.expiry;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="expiry.enabled",havingValue="true",matchIfMissing=true)
public class ExpiryScheduler {
    private static final Logger log=LoggerFactory.getLogger(ExpiryScheduler.class);
    private final ExpiryService service;
    public ExpiryScheduler(ExpiryService service){this.service=service;}
    @Scheduled(fixedDelayString="${EXPIRY_INTERVAL_SECONDS:5}",initialDelayString="${EXPIRY_INTERVAL_SECONDS:5}",timeUnit=TimeUnit.SECONDS)
    public void tick(){
        var result=service.runOnce();
        if(result.candidates()>0)log.info("Expiry pass candidates={} expired={} deferredPayment={} busy={} failed={}",result.candidates(),result.expired(),result.deferred(),result.busy(),result.failed());
    }
}
