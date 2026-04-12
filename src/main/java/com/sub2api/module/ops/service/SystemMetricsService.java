package com.sub2api.module.ops.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.time.LocalDateTime;

/**
 * 系统指标服务
 * 提供 CPU、内存、JVM 等系统指标的实时监控
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
public class SystemMetricsService {

    @Value("${spring.application.name:unknown}")
    private String applicationName;

    /**
     * 系统指标数据
     */
    @Data
    public static class SystemMetrics {
        private String applicationName;
        private long uptimeSeconds;
        private CpuMetrics cpu;
        private MemoryMetrics memory;
        private JvmMetrics jvm;
        private ThreadMetrics threads;
        private LocalDateTime timestamp;
    }

    @Data
    public static class CpuMetrics {
        private double systemLoadAverage;
        private int availableProcessors;
        private double processCpuUsage;
    }

    @Data
    public static class MemoryMetrics {
        private long totalPhysicalMemory;
        private long freePhysicalMemory;
        private long usedPhysicalMemory;
        private double usagePercent;
    }

    @Data
    public static class JvmMetrics {
        private long heapUsed;
        private long heapMax;
        private long heapCommitted;
        private double heapUsagePercent;
        private long nonHeapUsed;
        private long nonHeapMax;
        private long metaspaceUsed;
        private long metaspaceMax;
    }

    @Data
    public static class ThreadMetrics {
        private int peak;
        private int daemon;
        private int total;
        private long totalStarted;
    }

    /**
     * 获取系统指标
     */
    public SystemMetrics getMetrics() {
        SystemMetrics metrics = new SystemMetrics();
        metrics.setApplicationName(applicationName);
        metrics.setTimestamp(LocalDateTime.now());

        // 运行时间
        metrics.setUptimeSeconds(getUptimeSeconds());

        // CPU 指标
        metrics.setCpu(getCpuMetrics());

        // 内存指标
        metrics.setMemory(getMemoryMetrics());

        // JVM 指标
        metrics.setJvm(getJvmMetrics());

        // 线程指标
        metrics.setThreads(getThreadMetrics());

        return metrics;
    }

    /**
     * 获取 CPU 指标
     */
    private CpuMetrics getCpuMetrics() {
        CpuMetrics cpu = new CpuMetrics();
        try {
            cpu.setSystemLoadAverage(ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage());
            cpu.setAvailableProcessors(ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors());

            // Process CPU usage requires sun.management.ManagementFactory
            try {
                com.sun.management.OperatingSystemMXBean osBean = (com.sun.management.OperatingSystemMXBean)
                        ManagementFactory.getOperatingSystemMXBean();
                cpu.setProcessCpuUsage(osBean.getProcessCpuLoad() * 100);
            } catch (Exception e) {
                cpu.setProcessCpuUsage(-1);
            }
        } catch (Exception e) {
            log.warn("Failed to get CPU metrics: {}", e.getMessage());
        }
        return cpu;
    }

    /**
     * 获取内存指标
     */
    private MemoryMetrics getMemoryMetrics() {
        MemoryMetrics memory = new MemoryMetrics();
        try {
            com.sun.management.OperatingSystemMXBean osBean = (com.sun.management.OperatingSystemMXBean)
                    ManagementFactory.getOperatingSystemMXBean();

            long total = osBean.getTotalMemorySize();
            long free = osBean.getFreeMemorySize();
            long used = total - free;

            memory.setTotalPhysicalMemory(total);
            memory.setFreePhysicalMemory(free);
            memory.setUsedPhysicalMemory(used);
            memory.setUsagePercent(total > 0 ? (double) used / total * 100 : 0);

        } catch (Exception e) {
            log.warn("Failed to get memory metrics: {}", e.getMessage());
        }
        return memory;
    }

    /**
     * 获取简化的内存指标（用于兼容）
     */
    private MemoryMetrics getSimpleMemoryMetrics() {
        MemoryMetrics memory = new MemoryMetrics();
        try {
            com.sun.management.OperatingSystemMXBean osBean = (com.sun.management.OperatingSystemMXBean)
                    ManagementFactory.getOperatingSystemMXBean();

            long total = osBean.getTotalMemorySize();
            long free = osBean.getFreeMemorySize();
            long used = total - free;

            memory.setTotalPhysicalMemory(total);
            memory.setFreePhysicalMemory(free);
            memory.setUsedPhysicalMemory(used);
            memory.setUsagePercent(total > 0 ? (double) used / total * 100 : 0);

        } catch (Exception e) {
            log.warn("Failed to get memory metrics: {}", e.getMessage());
        }
        return memory;
    }

    /**
     * 获取 JVM 指标
     */
    private JvmMetrics getJvmMetrics() {
        JvmMetrics jvm = new JvmMetrics();
        try {
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();

            jvm.setHeapUsed(heapUsage.getUsed());
            jvm.setHeapMax(heapUsage.getMax());
            jvm.setHeapCommitted(heapUsage.getCommitted());
            jvm.setNonHeapUsed(nonHeapUsage.getUsed());
            jvm.setNonHeapMax(nonHeapUsage.getMax());

            if (heapUsage.getMax() > 0) {
                jvm.setHeapUsagePercent((double) heapUsage.getUsed() / heapUsage.getMax() * 100);
            }

            // Metaspace
            try {
                jvm.setMetaspaceUsed(getMetaspaceUsed());
                jvm.setMetaspaceMax(getMetaspaceMax());
            } catch (Exception e) {
                jvm.setMetaspaceUsed(-1);
                jvm.setMetaspaceMax(-1);
            }

        } catch (Exception e) {
            log.warn("Failed to get JVM metrics: {}", e.getMessage());
        }
        return jvm;
    }

    /**
     * 获取线程指标
     */
    private ThreadMetrics getThreadMetrics() {
        ThreadMetrics threads = new ThreadMetrics();
        try {
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            threads.setPeak(threadBean.getPeakThreadCount());
            threads.setDaemon(threadBean.getDaemonThreadCount());
            threads.setTotal(threadBean.getThreadCount());
            threads.setTotalStarted(threadBean.getTotalStartedThreadCount());
        } catch (Exception e) {
            log.warn("Failed to get thread metrics: {}", e.getMessage());
        }
        return threads;
    }

    /**
     * 获取运行时间（秒）
     */
    private long getUptimeSeconds() {
        return ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
    }

    /**
     * 获取 Metaspace 使用量
     */
    private long getMetaspaceUsed() {
        try {
            for (var mgmt : ManagementFactory.getMemoryPoolMXBeans()) {
                if ("Metaspace".equals(mgmt.getName())) {
                    return mgmt.getUsage().getUsed();
                }
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    /**
     * 获取 Metaspace 最大值
     */
    private long getMetaspaceMax() {
        try {
            for (var mgmt : ManagementFactory.getMemoryPoolMXBeans()) {
                if ("Metaspace".equals(mgmt.getName())) {
                    return mgmt.getUsage().getMax();
                }
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    /**
     * 获取健康状态
     */
    public String getHealthStatus() {
        try {
            SystemMetrics metrics = getMetrics();

            // 检查堆内存使用率
            if (metrics.getJvm().getHeapUsagePercent() > 90) {
                return "critical";
            }
            if (metrics.getJvm().getHeapUsagePercent() > 75) {
                return "warning";
            }

            // 检查线程数
            if (metrics.getThreads().getTotal() > 1000) {
                return "warning";
            }

            return "healthy";
        } catch (Exception e) {
            log.warn("Failed to get health status: {}", e.getMessage());
            return "unknown";
        }
    }
}
