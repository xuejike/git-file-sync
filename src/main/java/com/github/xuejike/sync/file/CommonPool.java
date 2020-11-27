package com.github.xuejike.sync.file;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author xuejike
 * @date 2020/11/27
 */
public class CommonPool {
    static {
        pool = Executors.newScheduledThreadPool(1);
    }

    private static ScheduledExecutorService pool;

    public static ScheduledExecutorService getPool() {
        return pool;
    }
}
