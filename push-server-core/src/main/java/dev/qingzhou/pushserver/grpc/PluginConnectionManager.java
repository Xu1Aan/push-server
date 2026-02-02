package dev.qingzhou.pushserver.grpc;

import dev.qingzhou.push.api.grpc.PlatformDownstreamPacket;
import io.grpc.stub.StreamObserver;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PluginConnectionManager {

    // Key: Plugin Key, Value: Response Observer (to send data to plugin)
    private final Map<String, StreamObserver<PlatformDownstreamPacket>> connections = new ConcurrentHashMap<>();

    public void register(String pluginKey, StreamObserver<PlatformDownstreamPacket> observer) {
        log.info("Plugin connected: {}", pluginKey);
        connections.put(pluginKey, observer);
    }

    public void unregister(String pluginKey) {
        if (pluginKey != null) {
            log.info("Plugin disconnected: {}", pluginKey);
            connections.remove(pluginKey);
        }
    }

    public StreamObserver<PlatformDownstreamPacket> get(String pluginKey) {
        return connections.get(pluginKey);
    }
    
    public boolean isConnected(String pluginKey) {
        return connections.containsKey(pluginKey);
    }
}
