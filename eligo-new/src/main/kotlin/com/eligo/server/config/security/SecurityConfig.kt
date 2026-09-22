package com.eligo.server.config.security

import com.eligo.server.common.api.Result
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.common.error.ErrorCode
import com.eligo.server.security.AccountRestrictionFilter
import com.eligo.server.security.DevAuthenticationFilter
import com.eligo.server.security.JwtUserPrincipalConverter
import com.eligo.server.security.SensitiveDataProperties
import com.eligo.server.security.SessionAuthenticationFilter
import jakarta.servlet.DispatcherType
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.JwtValidationException
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain
import tools.jackson.databind.ObjectMapper
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.spec.SecretKeySpec

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(
    JwtProperties::class,
    SensitiveDataProperties::class,
    SecurityModeProperties::class
)
class SecurityConfig {

    @Bean
    @Throws(Exception::class)
    fun securityFilterChain(
        http: HttpSecurity,
        objectMapper: ObjectMapper,
        securityModeProperties: SecurityModeProperties,
        jwtUserPrincipalConverter: JwtUserPrincipalConverter,
        sessionAuthenticationFilter: SessionAuthenticationFilter,
        accountRestrictionFilter: AccountRestrictionFilter,
        bearerTokenResolver: BearerTokenResolver
    ): SecurityFilterChain {
        // 测试模式：放行所有请求，注入固定测试用户，不做 JWT/会话/账号限制校验。
        if (!securityModeProperties.enabled) {
            return devSecurityFilterChain(http, securityModeProperties)
        }

        http
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
            .requestCache { it.disable() }
            .sessionManagement { session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { authorize ->
                authorize
                    .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/users/me/profile").authenticated()
                    .requestMatchers(HttpMethod.GET, *PUBLIC_GET_ENDPOINTS).permitAll()
                    .requestMatchers(HttpMethod.POST, *PUBLIC_POST_ENDPOINTS).permitAll()
                    .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated()
            }
            .exceptionHandling { exceptions ->
                exceptions
                    .authenticationEntryPoint { _, response, _ ->
                        writeErrorResponse(response, objectMapper, CommonErrorCode.AUTHENTICATION_REQUIRED)
                    }
                    .accessDeniedHandler { _, response, _ ->
                        writeErrorResponse(response, objectMapper, CommonErrorCode.ACCESS_DENIED)
                    }
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2
                    .bearerTokenResolver(bearerTokenResolver)
                    .jwt { jwt -> jwt.jwtAuthenticationConverter(jwtUserPrincipalConverter) }
                    .authenticationEntryPoint { _, response, _ ->
                        writeErrorResponse(response, objectMapper, AccountUserFileErrorCode.ACCESS_TOKEN_INVALID)
                    }
            }

        http.addFilterAfter(sessionAuthenticationFilter, BearerTokenAuthenticationFilter::class.java)
        http.addFilterAfter(accountRestrictionFilter, SessionAuthenticationFilter::class.java)

        return http.build()
    }

    @Throws(Exception::class)
    private fun devSecurityFilterChain(
        http: HttpSecurity,
        properties: SecurityModeProperties
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
            .requestCache { it.disable() }
            .sessionManagement { session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { authorize ->
                authorize
                    .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                    .anyRequest().permitAll()
            }

        http.addFilterBefore(
            DevAuthenticationFilter(properties),
            BearerTokenAuthenticationFilter::class.java
        )

        return http.build()
    }

    @Bean
    fun bearerTokenResolver(): BearerTokenResolver = DefaultBearerTokenResolver()

    @Bean
    fun jwtDecoder(properties: JwtProperties, objectMapper: ObjectMapper): JwtDecoder {
        val signingKey = SecretKeySpec(
            Base64.getDecoder().decode(properties.signingKeyBase64),
            "HmacSHA256"
        )
        val decoder = NimbusJwtDecoder.withSecretKey(signingKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build()
        val validator: OAuth2TokenValidator<Jwt> = DelegatingOAuth2TokenValidator(
            JwtIssuerValidator(properties.issuer),
            JwtTimestampValidator(properties.clockSkew),
            userPrincipalClaimsValidator()
        )
        decoder.setJwtValidator(validator)
        return JwtDecoder { token ->
            val jwt = decoder.decode(token)
            validateRawUserPrincipalClaimTypes(token, objectMapper)
            jwt
        }
    }

    private fun validateRawUserPrincipalClaimTypes(token: String, objectMapper: ObjectMapper) {
        try {
            val segments = token.split(".")
            if (segments.size != 3) {
                throw invalidUserPrincipalClaimsException()
            }
            val claims = objectMapper.readTree(Base64.getUrlDecoder().decode(segments[1]))
            if (claims == null || !claims.isObject
                || !claims.path("sub").isTextual
                || !claims.path("sid").isTextual
            ) {
                throw invalidUserPrincipalClaimsException()
            }
        } catch (exception: IllegalArgumentException) {
            throw invalidUserPrincipalClaimsException()
        }
    }

    private fun userPrincipalClaimsValidator(): OAuth2TokenValidator<Jwt> = OAuth2TokenValidator { jwt ->
        val subject: Any? = jwt.getClaim("sub")
        val sessionKey: Any? = jwt.getClaim("sid")
        if (subject !is String || sessionKey !is String) {
            return@OAuth2TokenValidator invalidUserPrincipalClaims()
        }
        try {
            if (subject.toLong() <= 0 || sessionKey.isBlank()) {
                return@OAuth2TokenValidator invalidUserPrincipalClaims()
            }
            OAuth2TokenValidatorResult.success()
        } catch (exception: NumberFormatException) {
            invalidUserPrincipalClaims()
        }
    }

    private fun invalidUserPrincipalClaims(): OAuth2TokenValidatorResult =
        OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "JWT 用户身份声明无效", null))

    private fun invalidUserPrincipalClaimsException(): JwtValidationException = JwtValidationException(
        "JWT 用户身份声明无效",
        listOf(OAuth2Error("invalid_token", "JWT 用户身份声明无效", null))
    )

    @Throws(IOException::class)
    private fun writeErrorResponse(
        response: HttpServletResponse,
        objectMapper: ObjectMapper,
        errorCode: ErrorCode
    ) {
        response.status = errorCode.httpStatus.value()
        response.characterEncoding = StandardCharsets.UTF_8.name()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        objectMapper.writeValue(response.outputStream, Result.failure<Any>(errorCode))
    }

    companion object {
        private val PUBLIC_GET_ENDPOINTS = arrayOf(
            "/api/v1/system/ping",
            "/api/v1/agreements/current",
            "/api/v1/agreements/*",
            "/api/v1/activities",
            "/api/v1/activities/*",
            "/api/v1/activities/*/comments",
            "/api/v1/posts",
            "/api/v1/posts/*",
            "/api/v1/users/*/profile",
            "/api/v1/users/*/posts",
            "/api/v1/organizations/*",
            "/api/v1/organizations/*/posts",
            "/api/v1/feed/recommended",
            "/api/v1/files/*/content",
            "/actuator/health",
            "/actuator/health/**"
        )

        private val PUBLIC_POST_ENDPOINTS = arrayOf(
            "/api/v1/auth/wechat-login",
            "/api/v1/auth/refresh"
        )
    }
}
