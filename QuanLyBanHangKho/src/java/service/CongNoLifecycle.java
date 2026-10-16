package service;
import jakarta.servlet.*;import jakarta.servlet.annotation.WebListener;import java.util.concurrent.*;import java.util.logging.*;
@WebListener public final class CongNoLifecycle implements ServletContextListener {
 private ScheduledExecutorService executor;
 public void contextInitialized(ServletContextEvent event){executor=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"khoflow-credit-risk");t.setDaemon(true);return t;});executor.scheduleWithFixedDelay(()->{try{new CanhBaoCongNoService().daily();}catch(Exception e){Logger.getLogger(getClass().getName()).log(Level.WARNING,"Không thể đánh giá công nợ định kỳ",e);}},10,60,TimeUnit.SECONDS);}
 public void contextDestroyed(ServletContextEvent event){if(executor!=null)executor.shutdownNow();}
}
