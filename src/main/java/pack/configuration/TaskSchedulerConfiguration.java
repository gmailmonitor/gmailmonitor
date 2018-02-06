package pack.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import pack.LoggingInitializer;

/**
 * Created by User on 11/13/2017.
 */
@Configuration
public class TaskSchedulerConfiguration {

    @Autowired LoggingInitializer loggingInitializer; // Placed in multiple @Configuration beans in attempt to obtain an early initialization

    private static final Logger log = LoggerFactory.getLogger((new Object(){}).getClass().getEnclosingClass());

    @Bean
    public ThreadPoolTaskScheduler threadPoolTaskScheduler(){
        log.info("Creating task scheduler");
        ThreadPoolTaskScheduler threadPoolTaskScheduler
                = new ThreadPoolTaskScheduler();
        threadPoolTaskScheduler.setPoolSize(5);
        threadPoolTaskScheduler.setThreadNamePrefix(
                "ThreadPoolTaskScheduler");
        return threadPoolTaskScheduler;
    }
}
