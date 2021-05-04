package com.infolinks.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class InfoSchedulerImpl implements InfoScheduler {

    private final static Logger logger = LogManager.getLogger(CrawlerServiceImpl.class);

    private ScheduledExecutorService executorService;

    @Value("${scheduler.thread.pool.size}")
    private Integer schedulerPoolSize;


    @PostConstruct
    private void init() {
        executorService = Executors.newScheduledThreadPool(schedulerPoolSize);
    }

    @Override
    public void scheduleNow(Runnable runnable) {
        executorService.schedule(runnable, 0, TimeUnit.MILLISECONDS);
    }

}
