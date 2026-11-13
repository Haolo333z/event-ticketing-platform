package com.haolo.ticketing.operations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Only fixed stage/outcome labels and validated metadata belong in operational logs. */
public final class TraceLog {
    private static final Logger LOG=LoggerFactory.getLogger(TraceLog.class);
    private TraceLog(){}
    public static void event(String stage,String outcome,String consumer){
        var entry=(stage.endsWith("failed")||stage.endsWith("rejected"))?LOG.atWarn():LOG.atInfo();
        entry.addKeyValue("stage",stage).addKeyValue("outcome",outcome);
        if(consumer!=null)entry.addKeyValue("consumer",consumer);
        entry.log("Event processing");
    }
    public static void http(String method,String route,int status){
        LOG.atInfo().addKeyValue("stage","http_request").addKeyValue("method",method)
            .addKeyValue("route",route).addKeyValue("status",status).log("HTTP request completed");
    }
}
