package com.sub2api.module.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Email Queue Service
 * 异步邮件队列服务 - 将邮件发送任务放入队列异步执行
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
public class EmailQueueService {

    private final EmailService emailService;

    private static final int DEFAULT_WORKERS = 3;
    private static final int QUEUE_CAPACITY = 100;

    private final ExecutorService executorService;
    private final BlockingQueue<EmailTask> taskQueue;

    private volatile boolean running = true;

    /**
     * 邮件任务类型
     */
    public enum TaskType {
        VERIFY_CODE("verify_code"),
        PASSWORD_RESET("password_reset");

        private final String value;

        TaskType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 邮件任务
     */
    public static class EmailTask {
        private final String email;
        private final String siteName;
        private final TaskType taskType;
        private final String resetURL;

        public EmailTask(String email, String siteName, TaskType taskType) {
            this(email, siteName, taskType, null);
        }

        public EmailTask(String email, String siteName, TaskType taskType, String resetURL) {
            this.email = email;
            this.siteName = siteName;
            this.taskType = taskType;
            this.resetURL = resetURL;
        }

        public String getEmail() {
            return email;
        }

        public String getSiteName() {
            return siteName;
        }

        public TaskType getTaskType() {
            return taskType;
        }

        public String getResetURL() {
            return resetURL;
        }
    }

    public EmailQueueService(EmailService emailService) {
        this(emailService, DEFAULT_WORKERS);
    }

    public EmailQueueService(EmailService emailService, int workers) {
        this.emailService = emailService;
        this.taskQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

        this.executorService = Executors.newFixedThreadPool(workers, r -> {
            Thread t = new Thread(r, "email-queue-worker");
            t.setDaemon(true);
            return t;
        });
    }

    @PostConstruct
    public void init() {
        int workers = DEFAULT_WORKERS;
        for (int i = 0; i < workers; i++) {
            executorService.submit(new Worker(i));
        }
        log.info("EmailQueueService started with {} workers", workers);
    }

    @PreDestroy
    public void stop() {
        running = false;
        executorService.shutdownNow();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("EmailQueueService workers did not terminate in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("EmailQueueService stopped");
    }

    /**
     * 将验证码发送任务加入队列
     *
     * @param email    邮箱
     * @param siteName 站点名称
     * @return 是否成功加入队列
     */
    public boolean enqueueVerifyCode(String email, String siteName) {
        return enqueue(new EmailTask(email, siteName, TaskType.VERIFY_CODE));
    }

    /**
     * 将密码重置邮件任务加入队列
     *
     * @param email    邮箱
     * @param siteName 站点名称
     * @param resetURL 重置链接
     * @return 是否成功加入队列
     */
    public boolean enqueuePasswordReset(String email, String siteName, String resetURL) {
        return enqueue(new EmailTask(email, siteName, TaskType.PASSWORD_RESET, resetURL));
    }

    /**
     * 加入队列
     */
    private boolean enqueue(EmailTask task) {
        if (!running) {
            log.warn("EmailQueueService is stopped, rejecting task");
            return false;
        }

        boolean offered = taskQueue.offer(task);
        if (offered) {
            log.debug("Enqueued email task: type={}, email={}", task.getTaskType(), task.getEmail());
        } else {
            log.warn("Email queue is full, rejecting task: type={}, email={}", task.getTaskType(), task.getEmail());
        }
        return offered;
    }

    /**
     * 工作线程
     */
    private class Worker implements Runnable {
        private final int id;

        Worker(int id) {
            this.id = id;
        }

        @Override
        public void run() {
            log.info("EmailQueue worker {} started", id);

            while (running) {
                try {
                    EmailTask task = taskQueue.poll(1, TimeUnit.SECONDS);
                    if (task != null) {
                        processTask(task);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("EmailQueue worker {} encountered error: {}", id, e.getMessage());
                }
            }

            log.info("EmailQueue worker {} stopped", id);
        }

        private void processTask(EmailTask task) {
            try {
                switch (task.getTaskType()) {
                    case VERIFY_CODE:
                        processVerifyCode(task);
                        break;
                    case PASSWORD_RESET:
                        processPasswordReset(task);
                        break;
                    default:
                        log.warn("Unknown task type: {}", task.getTaskType());
                }
            } catch (Exception e) {
                log.error("Failed to process email task: type={}, email={}, error={}",
                        task.getTaskType(), task.getEmail(), e.getMessage());
            }
        }

        private void processVerifyCode(EmailTask task) {
            try {
                emailService.sendVerifyCode(task.getEmail(), task.getSiteName());
                log.info("Sent verify code email: email={}", task.getEmail());
            } catch (Exception e) {
                log.error("Failed to send verify code: email={}, error={}", task.getEmail(), e.getMessage());
            }
        }

        private void processPasswordReset(EmailTask task) {
            try {
                if (task.getResetURL() != null) {
                    emailService.sendPasswordResetEmailWithCooldown(task.getEmail(), task.getSiteName(), task.getResetURL());
                } else {
                    emailService.sendPasswordResetEmail(task.getEmail(), task.getSiteName(), "");
                }
                log.info("Sent password reset email: email={}", task.getEmail());
            } catch (Exception e) {
                log.error("Failed to send password reset email: email={}, error={}", task.getEmail(), e.getMessage());
            }
        }
    }

    /**
     * 获取队列状态
     */
    public Map<String, Object> getStatus() {
        return Map.of(
                "running", running,
                "queue_size", taskQueue.size(),
                "queue_capacity", QUEUE_CAPACITY
        );
    }
}
