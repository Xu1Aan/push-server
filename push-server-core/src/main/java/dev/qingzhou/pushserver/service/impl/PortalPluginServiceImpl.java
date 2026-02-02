package dev.qingzhou.pushserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.qingzhou.pushserver.exception.PortalException;
import dev.qingzhou.pushserver.exception.PortalStatus;
import dev.qingzhou.pushserver.mapper.portal.PortalPluginMapper;
import dev.qingzhou.pushserver.model.dto.portal.PortalPluginCreateRequest;
import dev.qingzhou.pushserver.model.entity.portal.PortalPlugin;
import dev.qingzhou.pushserver.model.vo.portal.PortalPluginVo;
import dev.qingzhou.pushserver.service.PortalPluginService;
import dev.qingzhou.pushserver.utils.TokenUtils;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortalPluginServiceImpl implements PortalPluginService {

    private final PortalPluginMapper pluginMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createPlugin(PortalPluginCreateRequest request) {
        // 1. Check duplicate key
        if (pluginMapper.exists(new LambdaQueryWrapper<PortalPlugin>()
                .eq(PortalPlugin::getPluginKey, request.getPluginKey()))) {
            throw new PortalException(PortalStatus.CONFLICT, "Plugin key already exists");
        }

        // 2. Generate Token
        String token = TokenUtils.generateToken("sk_live_");

        // 3. Save
        PortalPlugin plugin = new PortalPlugin();
        plugin.setPluginKey(request.getPluginKey());
        plugin.setName(request.getName());
        plugin.setDescription(request.getDescription());
        plugin.setToken(token);
        plugin.setStatus(1); // Default Enabled
        plugin.setCreatedAt(System.currentTimeMillis());
        plugin.setUpdatedAt(System.currentTimeMillis());
        
        pluginMapper.insert(plugin);

        return token;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String resetToken(Integer id) {
        PortalPlugin plugin = pluginMapper.selectById(id);
        if (plugin == null) {
            throw new PortalException(PortalStatus.NOT_FOUND, "Plugin not found");
        }

        String newToken = TokenUtils.generateToken("sk_live_");
        plugin.setToken(newToken);
        plugin.setUpdatedAt(System.currentTimeMillis());
        pluginMapper.updateById(plugin);
        
        // TODO: If we have active gRPC connections for this plugin, we might want to terminate them.
        
        return newToken;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void switchStatus(Integer id, Integer status) {
        PortalPlugin plugin = pluginMapper.selectById(id);
        if (plugin == null) {
            throw new PortalException(PortalStatus.NOT_FOUND, "Plugin not found");
        }
        
        if (status != 0 && status != 1) {
             throw new PortalException(PortalStatus.BAD_REQUEST, "Invalid status");
        }

        plugin.setStatus(status);
        plugin.setUpdatedAt(System.currentTimeMillis());
        pluginMapper.updateById(plugin);

        // TODO: If disabled (status=0), disconnect active sessions.
    }

    @Override
    public List<PortalPluginVo> listPlugins() {
        List<PortalPlugin> list = pluginMapper.selectList(new LambdaQueryWrapper<PortalPlugin>()
                .orderByDesc(PortalPlugin::getCreatedAt));
        
        return list.stream().map(p -> PortalPluginVo.builder()
                .id(p.getId())
                .pluginKey(p.getPluginKey())
                .name(p.getName())
                .description(p.getDescription())
                .status(p.getStatus())
                .createdAt(p.getCreatedAt())
                .isConnected(false) // TODO: Check connection manager
                .build()).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePlugin(Integer id) {
        PortalPlugin plugin = pluginMapper.selectById(id);
        if (plugin == null) {
             throw new PortalException(PortalStatus.NOT_FOUND, "Plugin not found");
        }
        
        // TODO: Check if any Apps are using this plugin before deleting.
        
        pluginMapper.deleteById(id);
    }
}
