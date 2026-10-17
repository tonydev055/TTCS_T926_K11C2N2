package service;
import jakarta.servlet.*;import jakarta.servlet.annotation.WebListener;import java.util.concurrent.*;import java.util.logging.*;
@WebListener public final class NhacNoLifecycle implements ServletContextListener {
 private ScheduledExecutorService worker;
 public void contextInitialized(ServletContextEvent event){worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"khoflow-payment-reminders");t.setDaemon(true);return t;});worker.scheduleWithFixedDelay(()->{try{new NhacNoService().daily();}catch(Exception e){Logger.getLogger(getClass().getName()).log(Level.WARNING,"Không thể tạo thông báo nhắc nợ hằng ngày",e);}},15,60,TimeUnit.SECONDS);}
 public void contextDestroyed(ServletContextEvent event){if(worker!=null)worker.shutdownNow();}
}
