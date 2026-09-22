package com.eligo.server.recommendation

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.time.Duration
import java.util.HexFormat
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "eligo.recommendation")
class RecommendationProperties {

    var enabled: Boolean = false

    @field:NotBlank(message = "Qdrant 地址不能为空")
    var qdrantUrl: String = "http://127.0.0.1:6334"

    var qdrantApiKey: String? = null

    @field:NotBlank(message = "Qdrant 集合名称不能为空")
    var collection: String = "eligo_post_recommendation_bge_m3_v1"

    @field:Min(value = 1, message = "向量维度必须大于零")
    @field:Max(value = 4096, message = "向量维度不能超过 4096")
    var vectorSize: Int = 1024

    @field:NotBlank(message = "嵌入模型名称不能为空")
    var model: String = "bge-m3"

    @field:NotBlank(message = "嵌入模型版本不能为空")
    var modelVersion: String = "bge-m3-v1"

    @field:NotBlank(message = "Ollama 地址不能为空")
    var ollamaUrl: String = "http://127.0.0.1:11434"

    @field:Min(value = 1, message = "候选数量必须大于零")
    @field:Max(value = 1000, message = "候选数量不能超过 1000")
    var candidateLimit: Int = 200

    @field:Min(value = 1, message = "回溯天数必须大于零")
    @field:Max(value = 3650, message = "回溯天数不能超过 3650")
    var lookbackDays: Int = 90

    @field:NotNull(message = "快照有效期不能为空")
    var snapshotTtl: Duration = Duration.ofMinutes(10)

    @field:NotNull(message = "嵌入超时不能为空")
    var embeddingTimeout: Duration = Duration.ofSeconds(2)

    @field:NotNull(message = "Qdrant 超时不能为空")
    var qdrantTimeout: Duration = Duration.ofMillis(500)

    @field:Min(value = 1, message = "工作器批量大小必须大于零")
    @field:Max(value = 500, message = "工作器批量大小不能超过 500")
    var workerBatchSize: Int = 20

    @field:Min(value = 1, message = "最大尝试次数必须大于零")
    @field:Max(value = 100, message = "最大尝试次数不能超过 100")
    var maxAttempts: Int = 8

    @field:NotNull(message = "处理租约不能为空")
    var processingLease: Duration = Duration.ofMinutes(5)

    @field:NotNull(message = "失败重试冷却期不能为空")
    var failedRetryCooldown: Duration = Duration.ofHours(6)

    var workerEnabled: Boolean = false

    var backfillEnabled: Boolean = false

    fun indexIdentity(): String {
        val source = "$model\n$modelVersion\n$vectorSize\n$collection"
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(source.toByteArray(StandardCharsets.UTF_8)))
        } catch (exception: NoSuchAlgorithmException) {
            throw IllegalStateException("运行环境不支持 SHA-256", exception)
        }
    }

    @get:AssertTrue(message = "推荐相关持续时间必须全部大于零")
    val isPositiveDurations: Boolean
        get() = positive(snapshotTtl)
            && positive(embeddingTimeout)
            && positive(qdrantTimeout)
            && positive(processingLease)
            && positive(failedRetryCooldown)

    @get:AssertTrue(message = "启用回填时必须同时启用索引工作器")
    val isBackfillWorkerConfiguration: Boolean
        get() = !backfillEnabled || workerEnabled

    private fun positive(value: Duration?): Boolean {
        return value != null && !value.isZero && !value.isNegative
    }
}
