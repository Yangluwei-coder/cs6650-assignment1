package cs6650.assignment.chatclient;

import java.util.concurrent.*;

public class SimpleWebSocketClient {
    private static final int TOTAL_MESSAGES = 100_000;
    private static final int WARMUP_THREADS = 32;
    private static final int MAIN_THREADS = 32; // 降低到32防止Server崩溃

    public static void main(String[] args) throws Exception {
        MetricsCollector metrics = new MetricsCollector("results/metrics.csv");

        // --- WARMUP PHASE ---
        System.out.println("Starting Warmup...");
        runPhase(metrics, WARMUP_THREADS, 32000, false);
        System.out.println("Warmup Complete.");

        // --- MAIN PHASE ---
        System.out.println("Starting Main Phase...");
        long start = System.currentTimeMillis();
        metrics.enable(true);
        runPhase(metrics, MAIN_THREADS, TOTAL_MESSAGES, true);
        long end = System.currentTimeMillis();

        metrics.printSummary(end - start);
    }

    private static void runPhase(MetricsCollector metrics, int numThreads, int numMsg, boolean isMain) throws InterruptedException {
        BlockingQueue<ChatMessage> queue = new LinkedBlockingQueue<>(10000);

        // 启动生成器
        new Thread(new MessageGenerator(queue, numMsg)).start();

        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        for (int i = 0; i < numThreads; i++) {
            // 注意：每个线程应该发到不同的 Room 才能体现负载均衡
            pool.submit(new ChatSender("ws://localhost:8080/chat/room" + (i % 20), queue, metrics));
        }

        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.MINUTES);
    }
}