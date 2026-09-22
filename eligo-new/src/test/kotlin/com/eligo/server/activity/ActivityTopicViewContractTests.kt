package com.eligo.server.activity

import com.eligo.server.activity.vo.ActivityMapItemView
import com.eligo.server.activity.vo.ManagedActivityDetailView
import com.eligo.server.activity.vo.ManagedActivitySummaryView
import com.eligo.server.activity.vo.PublicActivityDetailView
import com.eligo.server.activity.vo.PublicActivitySummaryView
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ActivityTopicViewContractTests {

    @Test
    fun allActivityViewsExposeTopics() {
        for (viewType in listOf(
            PublicActivitySummaryView::class.java,
            PublicActivityDetailView::class.java,
            ManagedActivitySummaryView::class.java,
            ManagedActivityDetailView::class.java,
            ActivityMapItemView::class.java
        )) {
            assertThat(viewType.declaredMethods.map { it.name })
                .contains("getTopics")
        }
    }
}
