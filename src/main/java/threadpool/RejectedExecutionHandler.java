package threadpool;

/**
 * Интерфейс для обработки задач, которые не могут быть выполнены пулом потоков.
 */
public interface RejectedExecutionHandler {

    /**
     * Обрабатывает отклоненную задачу.
     *
     * @param task задача, которая была отклонена
     * @param executor пул потоков, который отклонил задачу
     */
    void rejectedExecution(Runnable task, MultiQueueExecutor executor);
}
