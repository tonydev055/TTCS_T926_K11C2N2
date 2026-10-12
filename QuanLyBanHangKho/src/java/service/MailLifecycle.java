package service;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebListener;
import java.util.concurrent.*;

@WebListener
public class MailLifecycle implements ServletContextListener {
    private ScheduledExecutorService worker;
    public void contextInitialized(ServletContextEvent event) {
        if(ThuDienTuService.developmentMailbox())return;
        SmtpService.settings(System.getenv());
        worker=Executors.newSingleThreadScheduledExecutor(r->{
            Thread thread=new Thread(r,"khoflow-mail");thread.setDaemon(true);return thread;
        });
        worker.scheduleWithFixedDelay(MailOutboxWorker::tick,5,2,TimeUnit.SECONDS);
    }
    public void contextDestroyed(ServletContextEvent event) {
        if(worker!=null) {
            worker.shutdownNow();
            try{worker.awaitTermination(45,TimeUnit.SECONDS);}
            catch(InterruptedException e){Thread.currentThread().interrupt();}
        }
    }
}
