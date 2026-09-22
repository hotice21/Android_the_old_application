package com.eligo.server.account.service

import com.eligo.server.account.entity.AccountSecurityEventEntity
import com.eligo.server.account.entity.UserEntity
import com.eligo.server.account.entity.UserPhoneBindingEntity
import com.eligo.server.account.mapper.AccountSecurityEventMapper
import com.eligo.server.account.mapper.UserMapper
import com.eligo.server.account.mapper.UserPhoneBindingMapper
import com.eligo.server.integration.wechat.AuthorizedPhone
import com.eligo.server.integration.wechat.WechatPhoneClient
import com.eligo.server.profile.service.ProfileCompletionUpdater
import com.eligo.server.security.SensitiveDataCodec
import com.eligo.server.security.UserPrincipal
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.concurrent.atomic.AtomicBoolean
import jakarta.annotation.Resource
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@SpringJUnitConfig(PhoneBindingTransactionBoundaryTests.TestConfiguration::class)
class PhoneBindingTransactionBoundaryTests {

    @Resource private lateinit var service: PhoneBindingService
    @Resource private lateinit var observations: TransactionObservations

    @Test
    fun wechatExchangeRunsOutsideTransactionAndDatabaseWritesRunInsideTransaction() {
        service.bindOrReplace(UserPrincipal(202L, "session-a"), "phone-code")

        assertThat(observations.externalCallInTransaction().get()).isFalse()
        assertThat(observations.bindingWriteInTransaction().get()).isTrue()
        assertThat(observations.eventWriteInTransaction().get()).isTrue()
        assertThat(observations.completionUpdateInTransaction().get()).isTrue()
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    class TestConfiguration {

        @Bean
        fun observations(): TransactionObservations {
            return TransactionObservations()
        }

        @Bean
        fun transactionManager(): PlatformTransactionManager {
            return TestTransactionManager()
        }

        @Bean
        fun users(): UserMapper {
            val users = mock(UserMapper::class.java)
            val user = UserEntity()
            user.id = 202L
            user.status = 1
            `when`(users.lockById(202L)).thenReturn(Optional.of(user))
            return users
        }

        @Bean
        fun bindings(observations: TransactionObservations): UserPhoneBindingMapper {
            val bindings = mock(UserPhoneBindingMapper::class.java)
            `when`(bindings.findActiveByUserId(202L)).thenReturn(Optional.empty())
            `when`(bindings.findActiveByCountryCodeAndLookupHash(any(), any()))
                .thenReturn(Optional.empty())
            `when`(bindings.insert(any<UserPhoneBindingEntity>())).thenAnswer {
                observations.bindingWriteInTransaction().set(
                    TransactionSynchronizationManager.isActualTransactionActive())
                1
            }
            return bindings
        }

        @Bean
        fun events(observations: TransactionObservations): AccountSecurityEventMapper {
            val events = mock(AccountSecurityEventMapper::class.java)
            `when`(events.insert(any<AccountSecurityEventEntity>())).thenAnswer {
                observations.eventWriteInTransaction().set(
                    TransactionSynchronizationManager.isActualTransactionActive())
                1
            }
            return events
        }

        @Bean
        fun codec(): SensitiveDataCodec {
            val codec = mock(SensitiveDataCodec::class.java)
            `when`(codec.lookupHash("phone:86:13800121234")).thenReturn(ByteArray(32))
            `when`(codec.encrypt("13800121234")).thenReturn("加密值")
            return codec
        }

        @Bean
        fun phoneMasker(): PhoneMasker {
            return PhoneMasker()
        }

        @Bean
        fun phoneLookupKey(): PhoneLookupKey {
            return PhoneLookupKey()
        }

        @Bean
        fun clock(): Clock {
            return Clock.fixed(Instant.parse("2026-07-22T08:00:00Z"), ZoneOffset.UTC)
        }

        @Bean
        fun wechatPhoneClient(observations: TransactionObservations): WechatPhoneClient {
            return object : WechatPhoneClient {
                override fun exchangePhoneCode(code: String): AuthorizedPhone {
                    observations.externalCallInTransaction().set(
                        TransactionSynchronizationManager.isActualTransactionActive())
                    return AuthorizedPhone("86", "13800121234")
                }
            }
        }

        @Bean
        fun profileCompletionUpdater(
                observations: TransactionObservations): ProfileCompletionUpdater {
            return object : ProfileCompletionUpdater {
                override fun recalculate(userId: Long): Boolean {
                    observations.completionUpdateInTransaction().set(
                        TransactionSynchronizationManager.isActualTransactionActive())
                    return true
                }
            }
        }

        @Bean
        fun phoneBindingTransactionService(
                bindings: UserPhoneBindingMapper,
                users: UserMapper,
                events: AccountSecurityEventMapper,
                codec: SensitiveDataCodec,
                phoneMasker: PhoneMasker,
                phoneLookupKey: PhoneLookupKey,
                profileCompletionUpdater: ProfileCompletionUpdater,
                clock: Clock): PhoneBindingTransactionService {
            return DefaultPhoneBindingTransactionService(
                    bindings,
                    users,
                    events,
                    codec,
                    phoneMasker,
                    phoneLookupKey,
                    profileCompletionUpdater,
                    clock)
        }

        @Bean
        fun phoneBindingService(
                wechatPhoneClient: WechatPhoneClient,
                transactions: PhoneBindingTransactionService): PhoneBindingService {
            return DefaultPhoneBindingService(wechatPhoneClient, transactions)
        }
    }

    class TransactionObservations {
        private val externalCallInTransaction = AtomicBoolean(true)
        private val bindingWriteInTransaction = AtomicBoolean(false)
        private val eventWriteInTransaction = AtomicBoolean(false)
        private val completionUpdateInTransaction = AtomicBoolean(false)

        fun externalCallInTransaction(): AtomicBoolean {
            return externalCallInTransaction
        }

        fun bindingWriteInTransaction(): AtomicBoolean {
            return bindingWriteInTransaction
        }

        fun eventWriteInTransaction(): AtomicBoolean {
            return eventWriteInTransaction
        }

        fun completionUpdateInTransaction(): AtomicBoolean {
            return completionUpdateInTransaction
        }
    }

    class TestTransactionManager : AbstractPlatformTransactionManager() {
        override fun doGetTransaction(): Any {
            return Any()
        }

        override fun doBegin(transaction: Any, definition: TransactionDefinition) {
        }

        override fun doCommit(status: DefaultTransactionStatus) {
        }

        override fun doRollback(status: DefaultTransactionStatus) {
        }
    }
}
