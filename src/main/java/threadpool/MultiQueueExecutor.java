package threadpool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Кастомный пул потоков с несколькими очередями задач.
 * Реализует интерфейс CustomExecutor.
 */
public class MultiQueueExecutor implements CustomExecutor {

    private static final Logger logger = LoggerFactory.getLogger(MultiQueueExecutor.class);

    private final int corePoolSize;
    private final int maxPoolSize;
    private final int queueSize;
    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    private final int minSpareThreads;
    private final RejectedExecutionHandler rejectedExecutionHandler;
    private final CustomThreadFactory threadFactory;

    private final List<QueueWorker> workers;
    private final List<BlockingQueue<Runnable>> taskQueues;
    private final List<Thread> threads;

    private final AtomicInteger queueIndex = new AtomicInteger(0);
    private final AtomicInteger currentPoolSize = new AtomicInteger(0);
    private final AtomicInteger corePoolSizeAtomic;

    private volatile boolean isShutdown = false;
    private final ReentrantLock lock = new ReentrantLock();

    public MultiQueueExecutor(int corePoolSize, int maxPoolSize, int queueSize,
                              long keepAliveTime, TimeUnit timeUnit, int minSpareThreads) {
        this(corePoolSize, maxPoolSize, queueSize, keepAliveTime, timeUnit, minSpareThreads,
             new CustomThreadFactory("CustomPool"));
    }

    public MultiQueueExecutor(int corePoolSize, int maxPoolSize, int queueSize,
                              long keepAliveTime, TimeUnit timeUnit, int minSpareThreads,
                              CustomThreadFactory threadFactory) {
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.queueSize = queueSize;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.minSpareThreads = minSpareThreads;
        this.rejectedExecutionHandler = RejectionPolicies.ABORT_POLICY;
        this.threadFactory = threadFactory;
        this.corePoolSizeAtomic = new AtomicInteger(corePoolSize);

        this.taskQueues = new ArrayList<>();
        this.workers = new ArrayList<>();
        this.threads = new ArrayList<>();

        logger.info("[Pool] Initializing thread pool: core={}, max={}, queueSize={}, keepAlive={} {}, minSpare={}",
                corePoolSize, maxPoolSize, queueSize, keepAliveTime, timeUnit.name(), minSpareThreads);

        // Создаем базовые потоки
        for (int i = 0; i < corePoolSize; i++) {
            createWorker();
        }
    }

    private void createWorker() {
        lock.lock();
        try {
            if (currentPoolSize.get() >= maxPoolSize) {
                return;
            }

            int workerId = currentPoolSize.getAndIncrement();
            BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(queueSize);
            String workerName = "CustomPool-worker-" + (workerId + 1);

            QueueWorker worker = new QueueWorker(queue, workerId, workerName,
                    keepAliveTime, timeUnit, corePoolSizeAtomic, currentPoolSize, this);

            Thread thread = threadFactory.newThread(worker);

            taskQueues.add(queue);
            workers.add(worker);
            threads.add(thread);

            thread.start();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void execute(Runnable command) {
        if (isShutdown) {
            rejectedExecutionHandler.rejectedExecution(command, this);
            return;
        }

        // Пытаемся найти очередь с местом
        BlockingQueue<Runnable> selectedQueue = null;
        int attempts = taskQueues.size();
        int startIndex = queueIndex.getAndIncrement() % Math.max(1, taskQueues.size());

        for (int i = 0; i < attempts; i++) {
            int index = (startIndex + i) % taskQueues.size();
            BlockingQueue<Runnable> queue = taskQueues.get(index);
            if (queue.remainingCapacity() > 0) {
                selectedQueue = queue;
                logger.info("[Pool] Task accepted into queue #{}: {}", index, command.toString());
                break;
            }
        }

        if (selectedQueue != null && selectedQueue.offer(command)) {
            // Проверяем, нужно ли создать дополнительные потоки
            checkAndCreateAdditionalWorkers();
            return;
        }

        // Если все очереди заполнены, пытаемся создать новый поток
        if (currentPoolSize.get() < maxPoolSize) {
            createWorker();
            // Попытаемся еще раз добавить задачу
            execute(command);
            return;
        }

        // Очереди переполнены и достигнут максимум потоков
        rejectedExecutionHandler.rejectedExecution(command, this);
    }

    private void checkAndCreateAdditionalWorkers() {
        // Проверяем minSpareThreads
        int idleWorkers = 0;
        for (QueueWorker worker : workers) {
            if (worker.isIdle()) {
                idleWorkers++;
            }
        }

        if (idleWorkers < minSpareThreads && currentPoolSize.get() < maxPoolSize) {
            createWorker();
        }
    }

    @Override
    public <T> Future<T> submit(Callable<T> callable) {
        if (isShutdown) {
            FutureTask<T> futureTask = new FutureTask<>(callable);
            rejectedExecutionHandler.rejectedExecution(futureTask, this);
            return futureTask;
        }

        FutureTask<T> futureTask = new FutureTask<>(callable);
        execute(futureTask);
        return futureTask;
    }

    @Override
    public void shutdown() {
        isShutdown = true;
        logger.info("[Pool] Shutdown initiated - no new tasks will be accepted");
    }

    @Override
    public void shutdownNow() {
        isShutdown = true;
        logger.warn("[Pool] ShutdownNow initiated - interrupting all workers");

        for (Thread thread : threads) {
            thread.interrupt();
        }

        for (QueueWorker worker : workers) {
            worker.stop();
        }
    }

    public boolean isShutdown() {
        return isShutdown;
    }

    public int getWorkerCount() {
        return currentPoolSize.get();
    }


}
