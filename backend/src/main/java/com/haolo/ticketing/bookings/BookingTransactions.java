package com.haolo.ticketing.bookings;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import com.haolo.ticketing.shared.ApiException;

/** One transaction per attempt; callers only receive a result after commit or rollback. */
@Component
public class BookingTransactions {
    private final PlatformTransactionManager manager;
    private final BookingPolicy policy;
    private final JdbcTemplate jdbc;
    public BookingTransactions(PlatformTransactionManager manager,BookingPolicy policy,JdbcTemplate jdbc){this.manager=manager;this.policy=policy;this.jdbc=jdbc;}
    public <T> T run(Supplier<T> command){
        long deadline=System.nanoTime()+policy.totalMs()*1_000_000L;
        for(int attempt=0;attempt<3;attempt++){
            long remaining=(deadline-System.nanoTime())/1_000_000L;
            if(remaining<policy.poolMs()+1000)throw busy();
            var tx=new TransactionTemplate(manager);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            tx.setTimeout((int)Math.min(policy.attemptMs()/1000,(remaining-policy.poolMs())/1000));
            try{return tx.execute(status->{
                jdbc.execute("SET LOCAL lock_timeout='"+policy.lockMs()+"ms'");
                jdbc.execute("SET LOCAL statement_timeout='"+policy.statementMs()+"ms'");
                return command.get();
            });}
            catch(ApiException ex){throw ex;}
            catch(RuntimeException ex){
                String state=sqlState(ex);
                if(("40P01".equals(state)||"40001".equals(state))&&attempt<2){
                    try{Thread.sleep((50L<<attempt)+ThreadLocalRandom.current().nextInt(20));}
                    catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw busy();}
                    continue;
                }
                if(state!=null&&(Set.of("55P03","57014","40P01","40001").contains(state)||state.startsWith("08")))throw busy();
                if(ex instanceof CannotCreateTransactionException||ex instanceof TransactionTimedOutException||ex instanceof org.springframework.dao.TransientDataAccessException)throw busy();
                throw new ApiException(500,"INTERNAL_ERROR","Không thể hoàn tất giao dịch đặt vé");
            }
        }
        throw busy();
    }
    public static String sqlState(Throwable ex){for(Throwable t=ex;t!=null;t=t.getCause())if(t instanceof java.sql.SQLException sql)return sql.getSQLState();return null;}
    private static ApiException busy(){return new ApiException(503,"BOOKING_BUSY","Hệ thống đang bận; hãy thử lại cùng mã yêu cầu");}
}
