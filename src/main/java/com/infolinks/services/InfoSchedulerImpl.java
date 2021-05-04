package com.infolinks.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class InfoSchedulerImpl implements InfoScheduler {

    private final static Logger logger = LogManager.getLogger(CrawlerServiceImpl.class);

    //    @Value("${scheduler.thread.pool.size}")
//    private Integer schedulerPoolSize=50;
    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(50);

    @Override
    public void scheduleNow(Runnable runnable) {
        executorService.schedule(runnable, 0, TimeUnit.MILLISECONDS);
    }

}
