package com.infolinks.services;

public interface InfoScheduler {
    void executeNow(Runnable runnable);
}
