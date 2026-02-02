package dev.qingzhou.pushserver.service.impl;

import dev.qingzhou.push.api.model.ActionContext;
import dev.qingzhou.push.api.model.PluginMeta;
import dev.qingzhou.push.api.spi.PushPlugin;
import dev.qingzhou.push.api.spi.PushSender;
import dev.qingzhou.pushserver.grpc.PluginPacketDispatcher;
import dev.qingzhou.pushserver.plugin.GrpcPluginProxy;
import dev.qingzhou.pushserver.service.PluginManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class PluginManagerServiceImpl implements PluginManagerService {

    private final List<PushPlugin> localPlugins; 
    private final PluginPacketDispatcher packetDispatcher;
    private final PushSender pushSender; 

    private final Map<String, GrpcPluginProxy> remotePlugins = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("Found {} local plugins", localPlugins.size());
        for (PushPlugin plugin : localPlugins) {
             try {
                 plugin.init(pushSender);
                 log.info("Initialized local plugin: {}", plugin.getClass().getName());
             } catch (Exception e) {
                 log.error("Failed to init local plugin {}", plugin.getClass().getName(), e);
             }
        }
    }

    @Override
    public void registerRemotePlugin(String pluginKey, PluginMeta meta) {
        log.info("Registering remote plugin: {}", pluginKey);
        GrpcPluginProxy proxy = new GrpcPluginProxy(pluginKey, meta, packetDispatcher);
        remotePlugins.put(pluginKey, proxy);
    }

    @Override
    public void unregisterRemotePlugin(String pluginKey) {
        log.info("Unregistering remote plugin: {}", pluginKey);
        remotePlugins.remove(pluginKey);
    }

    @Override
    public List<PushPlugin> getAllPlugins() {
        List<PushPlugin> all = new ArrayList<>(localPlugins);
        all.addAll(remotePlugins.values());
        return all;
    }

    @Override
    public List<PushPlugin> getLocalPlugins() {
        return localPlugins;
    }

    @Override
    public void dispatch(ActionContext context) {
        for (PushPlugin plugin : getAllPlugins()) {
            try {
                if (plugin.supports(context)) {
                    // Try to log name if meta is available, else class name
                    String name = (plugin.getMeta() != null && plugin.getMeta().getName() != null) 
                            ? plugin.getMeta().getName() 
                            : plugin.getClass().getSimpleName();
                            
                    log.info("Dispatching event {} to plugin {}", context.getEventId(), name);
                    plugin.handle(context);
                    return; // Stop after first match
                }
            } catch (Exception e) {
                log.error("Plugin failed to handle event", e);
            }
        }
        log.warn("No plugin handled event {}", context.getEventId());
    }
}
