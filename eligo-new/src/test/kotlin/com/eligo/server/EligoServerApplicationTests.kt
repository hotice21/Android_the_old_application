package com.eligo.server

import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.test.context.ActiveProfiles
import javax.sql.DataSource

@SpringBootTest
@ActiveProfiles("test")
class EligoServerApplicationTests {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    fun contextLoadsWithoutExternalDependencies() {
        assertThat(applicationContext.environment.activeProfiles).containsExactly("test")
        assertThat(applicationContext.getBeansOfType(DataSource::class.java)).isEmpty()
        assertThat(applicationContext.getBeansOfType(RedisConnectionFactory::class.java)).isEmpty()
        assertThat(applicationContext.getBeansOfType(Flyway::class.java)).isEmpty()
        assertThat(applicationContext.getBeansOfType(UserDetailsService::class.java)).isEmpty()
        assertThat(
            applicationContext.environment.getProperty(
                "spring.flyway.enabled", Boolean::class.java
            )
        ).isFalse()
    }
}
