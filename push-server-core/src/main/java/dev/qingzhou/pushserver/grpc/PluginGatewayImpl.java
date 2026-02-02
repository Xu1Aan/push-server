package dev.qingzhou.pushserver.grpc;

import dev.qingzhou.push.api.grpc.*;
import dev.qingzhou.push.api.grpc.PluginGatewayGrpc.PluginGatewayImplBase;
import dev.qingzhou.pushserver.model.entity.portal.PortalPlugin;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import dev.qingzhou.pushserver.service.PushService;
import dev.qingzhou.pushserver.service.PluginManagerService;
import dev.qingzhou.push.api.model.PluginMeta;
import dev.qingzhou.push.api.model.ConfigField;
import dev.qingzhou.push.api.model.ConfigType;
import dev.qingzhou.push.api.model.SelectOption;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PluginGatewayImpl extends PluginGatewayImplBase {

    private final PluginConnectionManager connectionManager;
    private final GrpcAdapter grpcAdapter;
    private final PushService pushService;
    private final PluginManagerService pluginManagerService;

    @Override
    public StreamObserver<PluginUpstreamPacket> connect(StreamObserver<PlatformDownstreamPacket> responseObserver) {
        
        PortalPlugin plugin = PluginAuthInterceptor.PLUGIN_CONTEXT_KEY.get();
        if (plugin == null) {
            // Should be caught by interceptor, but safety check
            responseObserver.onError(io.grpc.Status.UNAUTHENTICATED.asRuntimeException());
            return new StreamObserver<>() {
                @Override public void onNext(PluginUpstreamPacket value) {}
                @Override public void onError(Throwable t) {}
                @Override public void onCompleted() {}
            };
        }

        final String pluginKey = plugin.getPluginKey();
        log.info("New connection stream from plugin: {}", pluginKey);

        return new StreamObserver<PluginUpstreamPacket>() {
            @Override
            public void onNext(PluginUpstreamPacket packet) {
                try {
                    handlePacket(pluginKey, packet, responseObserver);
                } catch (Exception e) {
                    log.error("Error handling packet from {}", pluginKey, e);
                }
            }

            @Override
            public void onError(Throwable t) {
                log.warn("Stream error for plugin {}: {}", pluginKey, t.getMessage());
                cleanup();
            }

            @Override
            public void onCompleted() {
                log.info("Stream completed for plugin {}", pluginKey);
                cleanup();
                responseObserver.onCompleted();
            }
            
            private void cleanup() {
                connectionManager.unregister(pluginKey);
                pluginManagerService.unregisterRemotePlugin(pluginKey);
            }
        };
    }
    
    private void handlePacket(String pluginKey, PluginUpstreamPacket packet, StreamObserver<PlatformDownstreamPacket> responseObserver) {
        if (packet.hasRegister()) {
             // Register logic
             RegisterRequest req = packet.getRegister();
             if (!req.getPluginKey().equals(pluginKey)) {
                 log.warn("Plugin Key mismatch in register packet. Token says {}, packet says {}", pluginKey, req.getPluginKey());
             }
             
             // Convert RegisterRequest to PluginMeta
             PluginMeta meta = PluginMeta.builder()
                     .id(pluginKey)
                     .version(req.getPluginVersion())
                     .maxConcurrency(req.getMaxConcurrency())
                     .build();
             
             // Convert ConfigDefinitions if needed... (simplified here)
             
             connectionManager.register(pluginKey, responseObserver);
             pluginManagerService.registerRemotePlugin(pluginKey, meta);
             
             responseObserver.onNext(PlatformDownstreamPacket.newBuilder()
                     .setHeader(PacketHeader.newBuilder()
                             .setTraceId(packet.getHeader().getTraceId())
                             .setTimestamp(System.currentTimeMillis())
                             .build())
                     .setRegisterAck(RegisterResponse.newBuilder().setSuccess(true).build())
                     .build());
                     
        } else if (packet.hasHeartbeat()) {
            // Heartbeat logic - just log for now
        } else if (packet.hasPushRequest()) {
            dev.qingzhou.pushserver.model.dto.openapi.PushRequest coreReq = grpcAdapter.toCorePushRequest(packet.getPushRequest());
            
            try {
                pushService.push(coreReq);
                
                responseObserver.onNext(PlatformDownstreamPacket.newBuilder()
                     .setHeader(PacketHeader.newBuilder()
                             .setTraceId(packet.getHeader().getTraceId())
                             .setTimestamp(System.currentTimeMillis())
                             .build())
                     .setPushResponse(PushResponse.newBuilder().setSuccess(true).build())
                     .build());
            } catch (Exception e) {
                log.error("Push failed for plugin {}", pluginKey, e);
                responseObserver.onNext(PlatformDownstreamPacket.newBuilder()
                     .setHeader(PacketHeader.newBuilder()
                             .setTraceId(packet.getHeader().getTraceId())
                             .setTimestamp(System.currentTimeMillis())
                             .build())
                     .setPushResponse(PushResponse.newBuilder()
                             .setSuccess(false)
                             .setErrorMsg(e.getMessage())
                             .build())
                     .build());
            }
        }
    }
}
