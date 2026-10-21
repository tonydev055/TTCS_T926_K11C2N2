package service;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebListener;
import java.util.concurrent.*;
import java.util.logging.*;
@WebListener
public class HetHanGiuChoLifecycle implements ServletContextListener {
    private ScheduledExecutorService executor;
    public void contextInitialized(ServletContextEvent event){
        executor=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"khoflow-reservation-expiry");t.setDaemon(true);return t;});
        executor.scheduleWithFixedDelay(()->{try{new HetHanGiuChoService().expire();}catch(Exception e){Logger.getLogger(getClass().getName()).log(Level.WARNING,"Không thể xử lý giữ chỗ hết hạn; kiểm tra migration Sprint 5",e);}},5,60,TimeUnit.SECONDS);
    }
    public void contextDestroyed(ServletContextEvent event){if(executor!=null)executor.shutdownNow();}
}

