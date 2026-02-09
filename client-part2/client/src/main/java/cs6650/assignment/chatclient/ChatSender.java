package cs6650.assignment.chatclient;

// 这些就是你刚才报错缺少的 Import
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;

import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

@ClientEndpoint // 解决 Cannot resolve symbol 'ClientEndpoint'
public class ChatSender implements Runnable {
    private final String serverUri;
    private final BlockingQueue<ChatMessage> queue;
    private final MetricsCollector metrics;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatSender(String serverUri, BlockingQueue<ChatMessage> queue, MetricsCollector metrics) {
        this.serverUri = serverUri;
        this.queue = queue;
        this.metrics = metrics;
    }

    @Override
    public void run() {
        // 解决 Cannot resolve symbol 'WebSocketContainer'
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();

        // 解决 Cannot resolve symbol 'Session'
        try (Session session = container.connectToServer(this, new URI(serverUri))) {
            while (true) {
                // 3秒拿不到消息就退出，防止死锁导致 printSummary 跑不到
                ChatMessage msg = queue.poll(3, TimeUnit.SECONDS);
                if (msg == null) break;

                long startTime = System.nanoTime();
                boolean success = false;

                try {
                    String payload = objectMapper.writeValueAsString(msg);
                    // 解决 Cannot resolve method 'getBasicRemote'
                    session.getBasicRemote().sendText(payload);
                    success = true;
                } catch (Exception e) {
                    // 打印详细错误方便你查为什么 Server 拒收消息
                    System.err.println("Send Error: " + e.getMessage());
                } finally {
                    long latencyNanos = System.nanoTime() - startTime;
                    metrics.record(
                            System.currentTimeMillis(),
                            latencyNanos,
                            success,
                            msg.getMessageType(),
                            msg.getRoomId()
                    );
                }
            }
        } catch (Exception e) {
            System.err.println("Connection Error: " + e.getMessage());
        }
    }
}