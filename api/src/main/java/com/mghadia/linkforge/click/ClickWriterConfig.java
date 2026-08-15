package com.mghadia.linkforge.click;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ClickWriterConfig {

    /**
     * The pool that owns click writes.
     *
     * <p>Three choices here are deliberate and worth defending:
     *
     * <ul>
     *   <li><b>The queue is bounded.</b> An unbounded queue does not remove backpressure, it just
     *       converts it into heap growth: if the database slows down, an unbounded queue grows
     *       until the process dies, taking redirects down with it.
     *   <li><b>The rejection policy aborts rather than runs in the caller.</b>
     *       {@code CallerRunsPolicy} would push a database insert back onto the Tomcat thread —
     *       exactly the blocking the async path exists to avoid — so a saturated pool would slow
     *       every redirect instead of shedding analytics.
     *   <li><b>The pool is small.</b> Each worker holds a database connection while it inserts;
     *       sizing the pool above the connection pool just moves the queue.
     * </ul>
     *
     * <p>The resulting trade is explicit: under sustained overload LinkForge loses click rows and
     * keeps serving redirects. Analytics are lossy; the redirect is not.
     */
    @Bean(name = "clickExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor clickExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("click-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // On shutdown, finish what is already queued rather than dropping it.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
