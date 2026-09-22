package com.eligo.server.security

import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

@Component
class JwtUserPrincipalConverter : Converter<Jwt, AbstractAuthenticationToken> {

    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        return try {
            val userId = jwt.subject!!.toLong()
            val sessionKey = jwt.getClaimAsString("sid")
            if (userId <= 0 || sessionKey == null || sessionKey.isBlank()) {
                throw IllegalArgumentException("JWT 用户身份声明无效")
            }
            UsernamePasswordAuthenticationToken(UserPrincipal(userId, sessionKey), jwt, emptyList())
        } catch (exception: RuntimeException) {
            throw IllegalArgumentException("JWT 用户身份声明无效", exception)
        }
    }
}
