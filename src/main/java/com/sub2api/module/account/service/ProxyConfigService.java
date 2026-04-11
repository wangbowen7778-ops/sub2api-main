package com.sub2api.module.account.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sub2api.module.account.mapper.ProxyMapper;
import com.sub2api.module.account.model.entity.Proxy;
import com.sub2api.module.common.exception.BusinessException;
import com.sub2api.module.common.model.enums.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 代理配置服务
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProxyConfigService extends ServiceImpl<ProxyMapper, Proxy> {

    private final ProxyMapper proxyMapper;

    /**
     * 根据ID查询代理
     */
    public Proxy findById(Long id) {
        Proxy proxy = getById(id);
        if (proxy == null || proxy.getDeletedAt() != null) {
            return null;
        }
        return proxy;
    }

    /**
     * 查询所有活跃代理
     */
    public java.util.List<Proxy> listActive() {
        return list(new LambdaQueryWrapper<Proxy>()
                .eq(Proxy::getStatus, "active")
                .isNull(Proxy::getDeletedAt));
    }

    /**
     * 创建代理配置
     */
    @Transactional(rollbackFor = Exception.class)
    public Proxy createProxy(Proxy proxy) {
        if (proxy.getStatus() == null) {
            proxy.setStatus("active");
        }
        proxy.setCreatedAt(LocalDateTime.now());
        proxy.setUpdatedAt(LocalDateTime.now());

        if (!save(proxy)) {
            throw new BusinessException(ErrorCode.FAIL, "创建代理配置失败");
        }

        log.info("创建代理配置: id={}, name={}", proxy.getId(), proxy.getName());
        return proxy;
    }

    /**
     * 更新代理配置
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateProxy(Proxy proxy) {
        Proxy existing = findById(proxy.getId());
        if (existing == null) {
            throw new BusinessException(ErrorCode.PROXY_NOT_FOUND);
        }
        proxy.setUpdatedAt(LocalDateTime.now());
        updateById(proxy);
        log.info("更新代理配置: id={}", proxy.getId());
    }

    /**
     * 软删除代理配置
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteProxy(Long proxyId) {
        Proxy updateProxy = new Proxy();
        updateProxy.setId(proxyId);
        updateProxy.setDeletedAt(LocalDateTime.now());
        updateProxy.setUpdatedAt(LocalDateTime.now());
        updateById(updateProxy);
        log.info("删除代理配置: id={}", proxyId);
    }

    /**
     * 获取代理连接字符串
     */
    public String getProxyUrl(Proxy proxy) {
        if (proxy == null) {
            return null;
        }
        StringBuilder url = new StringBuilder();
        url.append(proxy.getProtocol()).append("://");
        if (proxy.getUsername() != null && !proxy.getUsername().isEmpty()) {
            url.append(proxy.getUsername()).append(":").append(proxy.getPassword()).append("@");
        }
        url.append(proxy.getHost()).append(":").append(proxy.getPort());
        return url.toString();
    }
}
