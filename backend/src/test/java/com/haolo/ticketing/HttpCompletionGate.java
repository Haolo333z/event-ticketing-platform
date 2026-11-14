package com.haolo.ticketing;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;

/** Test-only: flush a real response, then pause before the outer correlation filter logs completion. */
final class HttpCompletionGate implements Filter {
    private final AtomicReference<Pause> active=new AtomicReference<>();

    Pause pause(UUID correlation){
        var pause=new Pause(correlation.toString());
        if(!active.compareAndSet(null,pause))throw new IllegalStateException("HTTP completion already paused");
        return pause;
    }

    @Override public void doFilter(ServletRequest request,ServletResponse response,FilterChain chain)throws IOException,ServletException{
        var pause=active.get();
        boolean selected=pause!=null&&request instanceof HttpServletRequest http&&pause.correlation.equals(http.getHeader("X-Correlation-Id"));
        try{chain.doFilter(request,response);}
        finally{if(selected){
            response.flushBuffer();
            pause.flushed.countDown();
            try{if(!pause.released.await(10,TimeUnit.SECONDS))throw new ServletException("Test HTTP completion gate timed out");}
            catch(InterruptedException ex){Thread.currentThread().interrupt();throw new ServletException("Test HTTP completion interrupted",ex);}
        }}
    }

    final class Pause implements AutoCloseable {
        private final String correlation;
        private final CountDownLatch flushed=new CountDownLatch(1),released=new CountDownLatch(1);
        private Pause(String correlation){this.correlation=correlation;}
        void awaitResponseFlushed()throws InterruptedException{
            if(!flushed.await(10,TimeUnit.SECONDS))throw new AssertionError("Real HTTP response did not reach completion gate");
        }
        @Override public void close(){released.countDown();active.compareAndSet(this,null);}
    }
}
