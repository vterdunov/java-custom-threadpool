package threadpool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Кастомная фабрика потоков с логированием создания и завершения потоков.
 */
public class CustomThreadFactory implements ThreadFactory {

    private static final Logger logger = LoggerFactory.getLogger(CustomThreadFactory.class);

    private final String poolName;
    private final AtomicInteger threadNumber = new AtomicInteger(1);

    public CustomThreadFactory(String poolName) {
        this.poolName = poolName;
    }

    @Override
    public Thread newThread(Runnable r) {
        String threadName = poolName + "-worker-" + threadNumber.getAndIncrement();
        logger.info("[ThreadFactory] Creating new thread: {}", threadName);

        Thread thread = new Thread(() -> {
            try {
                r.run();
            } finally {
                logger.info("[Worker] {} terminated.", Thread.currentThread().getName());
            }
        }, threadName);

        thread.setDaemon(false);
        return thread;
    }
}
