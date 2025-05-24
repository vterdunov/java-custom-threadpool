package demo;

import threadpool.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Демонстрационная программа для тестирования кастомного пула потоков.
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("=== Демонстрация работы CustomThreadPool ===");

        // Создаем пул с параметрами: core=2, max=4, queueSize=3, keepAlive=5сек, minSpare=1
        MultiQueueExecutor executor = new MultiQueueExecutor(
                2, 4, 3, 5, TimeUnit.SECONDS, 1,
                new CustomThreadFactory("DemoPool")
        );

        // Тест 1: Обычная загрузка
        logger.info("\n--- Тест 1: Обычная загрузка ---");
        for (int i = 1; i <= 5; i++) {
            final int taskId = i;
            executor.execute(new TestTask("Task-" + taskId, 2000));
        }

        sleep(3000);

        // Тест 2: Перегрузка (проверка rejection policy)
        logger.info("\n--- Тест 2: Тест перегрузки ---");
        for (int i = 6; i <= 15; i++) {
            final int taskId = i;
            try {
                executor.execute(new TestTask("Overload-Task-" + taskId, 1000));
            } catch (Exception e) {
                logger.error("Задача отклонена: {}", e.getMessage());
            }
        }

        sleep(2000);

        // Тест 3: Submit с Callable
        logger.info("\n--- Тест 3: Submit с Callable ---");
        Future<String> future1 = executor.submit(new TestCallable("Callable-1", 1500));
        Future<String> future2 = executor.submit(new TestCallable("Callable-2", 1000));

        try {
            logger.info("Результат Callable-1: {}", future1.get());
            logger.info("Результат Callable-2: {}", future2.get());
        } catch (Exception e) {
            logger.error("Ошибка получения результата: {}", e.getMessage());
        }

        // Тест 4: Проверка keepAliveTime
        logger.info("\n--- Тест 4: Проверка keepAliveTime (ждем 8 секунд) ---");
        logger.info("Текущее количество потоков: {}", executor.getWorkerCount());
        sleep(8000);
        logger.info("Количество потоков после timeout: {}", executor.getWorkerCount());

        // Завершение
        logger.info("\n--- Завершение работы пула ---");
        executor.shutdown();

        // Ждем завершения всех задач
        sleep(3000);
        logger.info("Демонстрация завершена.");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Тестовая задача Runnable.
     */
    static class TestTask implements Runnable {
        private final String name;
        private final long duration;

        public TestTask(String name, long duration) {
            this.name = name;
            this.duration = duration;
        }

        @Override
        public void run() {
            logger.info("[Task] {} started", name);
            try {
                Thread.sleep(duration);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("[Task] {} interrupted", name);
                return;
            }
            logger.info("[Task] {} completed", name);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Тестовая задача Callable.
     */
    static class TestCallable implements Callable<String> {
        private final String name;
        private final long duration;

        public TestCallable(String name, long duration) {
            this.name = name;
            this.duration = duration;
        }

        @Override
        public String call() throws Exception {
            logger.info("[Callable] {} started", name);
            Thread.sleep(duration);
            String result = name + " completed successfully";
            logger.info("[Callable] {} finished", name);
            return result;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
