package com.eligo.server.config.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 安全总开关与开发模式配置。
 *
 * - [enabled] = true：生产模式，JWT 鉴权、会话校验、账号限制全部生效。
 * - [enabled] = false：测试模式（仅用于开发/测试阶段），所有请求放行，
 *   未携带令牌的请求以 [devUserId] 对应的测试用户身份执行，
 *   无需微信登录即可手动测试全部接口。
 *
 * 生产环境（prod profile）必须保持 enabled=true。
 */
@ConfigurationProperties(prefix = "eligo.security")
data class SecurityModeProperties(
    val enabled: Boolean = true,
    val devUserId: Long = 1L,
    val devSessionKey: String = "dev-session",
    val devAdmin: Boolean = false
) {
    init {
        if (devUserId <= 0) {
            throw IllegalArgumentException("开发模式测试用户 ID 必须大于 0")
        }
        if (devSessionKey.isBlank()) {
            throw IllegalArgumentException("开发模式测试会话 Key 不能为空")
        }
    }
}
