package com.eligo.server.activity

import com.eligo.server.activity.dto.PersonalActivityCreateRequest
import com.eligo.server.activity.dto.PersonalActivityUpdateRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class ActivityContactFieldContractTests {

    @Test
    fun personalCreateCarriesGenderContactQrAndNullRefundPolicy() {
        val request = PersonalActivityCreateRequest(
            "徒步活动",
            "HIKING",
            "活动说明",
            null,
            listOf<String>(),
            Instant.parse("2026-08-19T00:00:00Z"),
            Instant.parse("2026-08-20T00:00:00Z"),
            Instant.parse("2026-08-21T00:00:00Z"),
            Instant.parse("2026-08-22T00:00:00Z"),
            "440305",
            "活动地址",
            capacity = 20,
            signupDetails = "报名说明",
            organizerMessage = "组织者说明",
            registrationGender = "FEMALE",
            organizerPhone = "13800000000",
            organizerWechat = "organizer-wechat",
            organizerWechatQrFileId = "9001",
            refundPolicy = null
        )

        assertThat(request.registrationGender).isEqualTo("FEMALE")
        assertThat(request.organizerPhone).isEqualTo("13800000000")
        assertThat(request.organizerWechat).isEqualTo("organizer-wechat")
        assertThat(request.organizerWechatQrFileId).isEqualTo("9001")
        assertThat(request.refundPolicy).isNull()
    }

    @Test
    fun personalUpdateCarriesUnlimitedGenderAndNullRefundPolicy() {
        val request = PersonalActivityUpdateRequest(
            3,
            "徒步活动",
            "HIKING",
            "活动说明",
            null,
            listOf<String>(),
            null,
            null,
            null,
            null,
            "440305",
            "活动地址",
            capacity = 20,
            signupDetails = "报名说明",
            organizerMessage = "组织者说明",
            registrationGender = "UNLIMITED",
            organizerPhone = null,
            organizerWechat = null,
            organizerWechatQrFileId = null,
            refundPolicy = null
        )

        assertThat(request.registrationGender).isEqualTo("UNLIMITED")
        assertThat(request.organizerPhone).isNull()
        assertThat(request.organizerWechat).isNull()
        assertThat(request.organizerWechatQrFileId).isNull()
        assertThat(request.refundPolicy).isNull()
    }
}
