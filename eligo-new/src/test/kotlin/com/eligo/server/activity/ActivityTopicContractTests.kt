package com.eligo.server.activity

import com.eligo.server.activity.dto.OrganizationActivityCreateRequest
import com.eligo.server.activity.dto.OrganizationActivityUpdateRequest
import com.eligo.server.activity.dto.PersonalActivityCreateRequest
import com.eligo.server.activity.dto.PersonalActivityUpdateRequest
import jakarta.validation.constraints.Size
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ActivityTopicContractTests {

    @Test
    fun allActivityWriteRequestsCarryAtMostFiveTopics() {
        for (requestType in listOf(
            PersonalActivityCreateRequest::class.java,
            OrganizationActivityCreateRequest::class.java,
            PersonalActivityUpdateRequest::class.java,
            OrganizationActivityUpdateRequest::class.java
        )) {
            val topicsField = requestType.getDeclaredField("topics")

            assertThat(topicsField.genericType.typeName)
                .contains("java.util.List<java.lang.String>")
            assertThat(
                topicsField.getAnnotation(Size::class.java).max
            ).isEqualTo(5)
        }
    }
}
