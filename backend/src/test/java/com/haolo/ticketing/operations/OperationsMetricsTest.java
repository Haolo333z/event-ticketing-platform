package com.haolo.ticketing.operations;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OperationsMetricsTest {
    @Test void boundedCacheFailsVisiblyAndRecoversWithoutReturningStaleOrFalseZero(){
        var reads=new AtomicInteger();var clock=new AtomicLong();
        java.util.function.Supplier<Map<String,Double>> query=()->switch(reads.incrementAndGet()){
            case 1 -> Map.of("overdue.count",2d);
            case 2 -> throw new IllegalStateException("Private marker must not be logged");
            default -> Map.of("overdue.count",1d);
        };
        var registry=new SimpleMeterRegistry();try{
            var collector=new OperationsMetrics(query,1000,clock::get);collector.bindTo(registry);
            var overdue=registry.get("ticketing.holds.overdue").gauge();var available=registry.get("ticketing.metrics.snapshot.available").gauge();var age=registry.get("ticketing.metrics.snapshot.age").gauge();
            assertEquals(2,overdue.value());clock.set(500_000_000);assertEquals(2,overdue.value());assertEquals(0.5,age.value());assertEquals(1,reads.get());
            clock.set(1_100_000_000);assertTrue(Double.isNaN(overdue.value()));assertEquals(0,available.value());assertEquals(1.1,age.value(),0.001);
            clock.set(2_200_000_000L);assertEquals(1,overdue.value());assertEquals(1,available.value());assertEquals(0,age.value());assertEquals(3,reads.get());
        }finally{registry.close();}
    }
}
