package threadpool;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Рабочий поток, обрабатывающий задачи из своей очереди.
 * Завершается, если простаивает дольше заданного времени.
 */
public class QueueWorker implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(QueueWorker.class);

    private final BlockingQueue<Runnable> taskQueue;
    private final int workerId;
    private final String workerName;

    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    private final AtomicInteger corePoolSize;
    private final AtomicInteger currentPoolSize;
    private final MultiQueueExecutor executor;

    private volatile boolean running = true;
    private volatile boolean executing = false;

    /**
     * Конструктор рабочего потока.
     */
    public QueueWorker(BlockingQueue<Runnable> taskQueue, int workerId, String workerName,
                       long keepAliveTime, TimeUnit timeUnit, AtomicInteger corePoolSize,
                       AtomicInteger currentPoolSize, MultiQueueExecutor executor) {
        this.taskQueue = taskQueue;
        this.workerId = workerId;
        this.workerName = workerName;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.corePoolSize = corePoolSize;
        this.currentPoolSize = currentPoolSize;
        this.executor = executor;
    }

    @Override
    public void run() {
        try {
            while (running && !executor.isShutdown()) {
                Runnable task = taskQueue.poll(keepAliveTime, timeUnit);
                if (task != null) {
                    executing = true;
                    logger.info("[Worker] {} executes {}", workerName, task.toString());
                    try {
                        task.run();
                    } catch (Exception e) {
                        logger.error("[Worker] {} error executing task: {}", workerName, e.getMessage());
                    } finally {
                        executing = false;
                    }
                } else {
                    // Проверяем, можем ли завершиться (если превышаем corePoolSize)
                    if (currentPoolSize.get() > corePoolSize.get()) {
                        logger.info("[Worker] {} idle timeout, stopping.", workerName);
                        currentPoolSize.decrementAndGet();
                        running = false;
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("[Worker] {} was interrupted", workerName);
        } finally {
            currentPoolSize.decrementAndGet();
        }
    }

    public void stop() {
        this.running = false;
    }

    public int getQueueSize() {
        return taskQueue.size();
    }

    public boolean isIdle() {
        return !executing && taskQueue.isEmpty();
    }
}
