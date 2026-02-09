package cs6650.assignment.chatclient;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public class MetricsCollector {
    // 改用 Double 存储毫秒，保留精度
    private final List<Double> latencies = Collections.synchronizedList(new ArrayList<>());
    private final LongAdder success = new LongAdder();
    private final LongAdder failure = new LongAdder();
    private volatile boolean enabled = true;

    private final Map<String, LongAdder> roomCount = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> typeCount = new ConcurrentHashMap<>();
    private final BufferedWriter writer;

    public MetricsCollector(String csvPath) throws IOException {
        File file = new File(csvPath);
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) parentDir.mkdirs();

        writer = new BufferedWriter(new FileWriter(file));
        writer.write("timestamp,messageType,latencyMs,status,roomId\n");
    }

    public void enable(boolean enabled) { this.enabled = enabled; }

    // 接收纳秒差值，内部转毫秒
    public void record(long timestamp, long latencyNanos, boolean ok, String messageType, String roomId) {
        if (!enabled) return;

        double latencyMs = latencyNanos / 1_000_000.0;
        latencies.add(latencyMs);

        if (ok) success.increment();
        else failure.increment();

        roomCount.computeIfAbsent(roomId, k -> new LongAdder()).increment();
        typeCount.computeIfAbsent(messageType, k -> new LongAdder()).increment();

        try {
            // synchronized 确保多线程写入文件不乱序
            synchronized (writer) {
                writer.write(String.format("%d,%s,%.2f,%s,%s\n",
                        timestamp, messageType, latencyMs, ok ? "OK" : "FAIL", roomId));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void printSummary(long totalRuntimeMs) throws IOException {
        System.out.println("DEBUG: Looking for CSV at: " + new java.io.File("results/metrics.csv").getAbsolutePath());
        writer.flush();
        writer.close();

        if (latencies.isEmpty()) {
            System.out.println("No data collected.");
            return;
        }

        List<Double> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);

        int n = sorted.size();
        double mean = sorted.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double p95 = sorted.get((int) (n * 0.95));
        double p99 = sorted.get((int) (n * 0.99));
        double seconds = totalRuntimeMs / 1000.0;

        System.out.println("\n====== PERFORMANCE METRICS ======");
        System.out.println("Success: " + success.sum());
        System.out.println("Failure: " + failure.sum());
        System.out.println("Mean latency: " + String.format("%.2f", mean) + " ms");
        System.out.println("P99 latency: " + String.format("%.2f", p99) + " ms");
        System.out.println("Throughput: " + String.format("%.2f", (success.sum() / seconds)) + " msg/s");
    }
}