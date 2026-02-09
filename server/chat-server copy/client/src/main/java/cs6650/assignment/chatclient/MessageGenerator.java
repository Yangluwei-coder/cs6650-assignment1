package cs6650.assignment.chatclient;

import java.time.Instant;
import java.util.Random;
import java.util.concurrent.BlockingQueue;

public class MessageGenerator implements Runnable {

    private static final int TOTAL_ROOMS = 20;
    private static final Random random = new Random();

    private final BlockingQueue<ChatMessage> queue;
    private final int totalMessages;

    private static final String[] MESSAGE_POOL = {
            "Hello!", "How are you?", "Good morning", "Good night",
            "Nice to meet you", "What's up?", "Random chat",
            "Test message", "CS6650 rocks", "WebSocket load test",
            "Concurrency is fun", "Distributed systems",
            "Message queue test", "Warmup phase", "Main phase",
            "JOIN room", "LEAVE room", "Another message",
            "Stress testing", "Performance matters",
            // 👉 至少 50 条，你可以继续补
    };

    public MessageGenerator(BlockingQueue<ChatMessage> queue, int totalMessages) {
        this.queue = queue;
        this.totalMessages = totalMessages;
    }

    @Override
    public void run() {
        try {
            for (int i = 0; i < totalMessages; i++) {

                int userId = random.nextInt(100_000) + 1;
                String username = "user" + userId;
                String message = MESSAGE_POOL[random.nextInt(MESSAGE_POOL.length)];
                String roomId = "room" + (random.nextInt(TOTAL_ROOMS) + 1);

                String messageType;
                int chance = random.nextInt(100);
                if (chance < 90) messageType = "TEXT";
                else if (chance < 95) messageType = "JOIN";
                else messageType = "LEAVE";

                ChatMessage chatMessage = new ChatMessage(
                        userId,
                        username,
                        message,
                        Instant.now().toString(),
                        messageType,
                        roomId
                );

                queue.put(chatMessage); // BlockingQueue，线程安全
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
