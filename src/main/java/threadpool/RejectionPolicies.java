package threadpool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.RejectedExecutionException;

/**
 * Стандартная стратегия обработки отклоненных задач - выбрасывание исключения.
 */
public class RejectionPolicies {

    private static final Logger logger = LoggerFactory.getLogger(RejectionPolicies.class);

    /**
     * Стратегия по умолчанию: отклонить задачу с исключением.
     */
    public static final RejectedExecutionHandler ABORT_POLICY = (task, executor) -> {
        logger.warn("[Rejected] Task {} was rejected due to overload!", task.toString());
        throw new RejectedExecutionException("Task rejected due to thread pool overload");
    };
}
