package com.eligo.server.organization

import java.util.function.Function

import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.database.Stage2TestDatabaseCleaner
import com.eligo.server.integration.wechat.WechatRestClientFactory
import com.eligo.server.organization.service.OrganizationAccessService
import com.eligo.server.organization.vo.PublicOrganizationDetailView
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest(properties = [
    "eligo.security.jwt.signing-key-base64=IB8eHRwbGhkYFxYVFBMSERAPDg0MCwoJCAcGBQQDAgE=",
    "eligo.security.sensitive-data.encryption-key-base64=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
    "eligo.security.sensitive-data.lookup-key-base64=Hx4dHBsaGRgXFhUUExIREA8ODQwLCgkIBwYFBAMCAQA="
])
@ActiveProfiles("it")
@EnabledIfSystemProperty(named = "eligo.database.tests", matches = "true")
class OrganizationPublicDatabaseIntegrationTests {

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var service: OrganizationAccessService

    @MockitoBean
    lateinit var redis: StringRedisTemplate

    @MockitoBean
    lateinit var wechatRestClientFactory: WechatRestClientFactory

    @BeforeEach
    fun prepareDatabase() {
        Stage2TestDatabaseCleaner.cleanAllStage2Tables(jdbcTemplate)
        insertOrganization(1)
    }

    @Test
    fun readsActiveOrganizationWithOnlyPublicFields() {
        val result = service.getPublicOrganization(ORGANIZATION_ID)

        assertThat(result.organizationId).isEqualTo(ORGANIZATION_ID.toString())
        assertThat(result.name).isEqualTo("公开测试企业")
        assertThat(result.avatar).isNull()
        assertThat(result.summary).isEqualTo("公开测试简介")
        assertThat(result.region.provinceName).isEqualTo("广东省")
        assertThat(result.region.districtCode).isEqualTo("440305")
        assertThat(result.addressDetail).isEqualTo("公开测试地址")
    }

    @Test
    fun doesNotExposeDisabledOrMissingOrganization() {
        jdbcTemplate.update(
            "UPDATE organizations SET status=2 WHERE id=?", ORGANIZATION_ID)

        assertThatThrownBy { service.getPublicOrganization(ORGANIZATION_ID) }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  exception -> (exception as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND)
        assertThatThrownBy { service.getPublicOrganization(9972002L) }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  exception -> (exception as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND)
    }

    private fun insertOrganization(status: Int) {
        jdbcTemplate.update(
            """
            INSERT INTO organizations (
                id, name, summary, province_code, province_name,
                city_code, city_name, district_code, district_name,
                address_detail, contact_phone_ciphertext,
                contact_phone_lookup_hash, contact_phone_last_four,
                status, version, created_at, updated_at
            ) VALUES (?, '公开测试企业', '公开测试简介', '44', '广东省',
                '4403', '深圳市', '440305', '南山区', '公开测试地址', X'01',
                UNHEX(SHA2('organization-public-it', 256)), '0001', ?, 0,
                UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, ORGANIZATION_ID, status)
    }

    companion object {
        private const val ORGANIZATION_ID = 9972001L
    }
}
