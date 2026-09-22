package com.eligo.server.contract

import java.util.function.Function

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern

class OpenApiContractTest {

    private companion object {
        val HTTP_METHODS = setOf("get", "post", "put", "patch", "delete")
    }

    @Test
    fun sharedContractIsValidYamlAndHasUniqueOperations() {
        val contract = Path.of("docs", "api", "openapi.yaml")
        assertThat(contract).exists().isRegularFile

        val document: Map<String, Any>
        Files.newInputStream(contract).use { input ->
            document = loadMap(input)
        }

        assertThat(document["openapi"]).isEqualTo("3.0.3")
        val info = map(document["info"])
        assertThat(info)
            .containsEntry("version", "1.7.0")
            .containsEntry("x-m2-contract-status", "IMPLEMENTED")
            .containsEntry("x-m3-contract-status", "IMPLEMENTED")
            .containsEntry("x-m4-contract-status", "IMPLEMENTED")
            .containsEntry("x-m4.1-contract-status", "IMPLEMENTED")

        val paths = map(document["paths"])
        assertThat(paths)
            .hasSize(65)
            .containsKeys(
                "/api/v1/users/me/capabilities",
                "/api/v1/users/me/organizations",
                "/api/v1/organizations/{organizationId}",
                "/api/v1/activities",
                "/api/v1/activities/map",
                "/api/v1/activities/{activityId}",
                "/api/v1/activities/{activityId}/organizer/wechat-qr",
                "/api/v1/users/me/activities",
                "/api/v1/organizations/{organizationId}/activities",
                "/api/v1/users/me/activities/{activityId}",
                "/api/v1/organizations/{organizationId}/activities/{activityId}",
                "/api/v1/users/me/managed-activities",
                "/api/v1/users/me/managed-activities/{activityId}",
                "/api/v1/activities/{activityId}/publication",
                "/api/v1/activities/{activityId}/cancellation",
                "/api/v1/activities/{activityId}/participation",
                "/api/v1/activities/{activityId}/participants",
                "/api/v1/activities/{activityId}/participants/{userId}",
                "/api/v1/users/me/participations",
                "/api/v1/activities/{activityId}/favorite",
                "/api/v1/activities/{activityId}/comments",
                "/api/v1/activities/{activityId}/comments/{commentId}",
                "/api/v1/users/me/favorite-activities",
                "/api/v1/follows/users/{userId}",
                "/api/v1/follows/organizations/{organizationId}",
                "/api/v1/users/me/following",
                "/api/v1/users/me/followers",
                "/api/v1/posts",
                "/api/v1/posts/{postId}",
                "/api/v1/feed/following",
                "/api/v1/feed/recommended"
            )

        val operationIds = HashSet<String>()
        var operationCount = 0
        for (pathItemValue in paths.values) {
            val pathItem = map(pathItemValue)
            for (method in HTTP_METHODS) {
                if (!pathItem.containsKey(method)) {
                    continue
                }
                operationCount++
                val operation = map(pathItem[method])
                assertThat(operation["operationId"]).isInstanceOf(String::class.java)
                assertThat(operationIds.add(operation["operationId"] as String))
                    .`as`("operationId must be unique")
                    .isTrue()
            }
        }

        assertThat(operationCount).isEqualTo(80)
        assertThat(operationIds)
            .contains(
                "getMyCapabilities",
                "listMyOrganizations",
                "getPublicOrganization",
                "listPublicActivities",
                "listActivitiesOnMap",
                "getPublicActivity",
                "getActivityOrganizerWechatQr",
                "favoriteActivity",
                "getActivityFavoriteState",
                "unfavoriteActivity",
                "listMyFavoriteActivities",
                "createActivityComment",
                "listActivityComments",
                "deleteActivityComment",
                "createMyActivity",
                "createOrganizationActivity",
                "updateMyActivity",
                "updateOrganizationActivity",
                "listMyManagedActivities",
                "getMyManagedActivity",
                "publishActivity",
                "cancelActivity",
                "deleteActivityDraft",
                "joinActivity",
                "cancelActivityParticipation",
                "listActivityParticipants",
                "removeActivityParticipant",
                "listMyParticipations",
                "getPublicUserProfile",
                "followUser",
                "unfollowUser",
                "followOrganization",
                "unfollowOrganization",
                "listMyFollowing",
                "listMyFollowers",
                "createMyPostDraft",
                "createOrganizationPostDraft",
                "replaceMyPostDraft",
                "replaceOrganizationPostDraft",
                "publishPost",
                "getPost",
                "deletePost",
                "listMyManagedPosts",
                "getMyManagedPost",
                "listPublicPosts",
                "listUserPosts",
                "listOrganizationPosts",
                "listFollowingFeed",
                "listRecommendedFeed"
            )

        assertImplementedOperation(paths, "/api/v1/organizations/{organizationId}", "get")
        assertImplementedOperation(paths, "/api/v1/activities", "get")
        assertImplementedOperation(paths, "/api/v1/activities/map", "get")
        assertAllowsAnonymous(paths, "/api/v1/activities/map", "get")
        assertImplementedOperation(paths, "/api/v1/activities/{activityId}", "get")
        assertImplementedOperation(paths, "/api/v1/users/me/activities", "post")
        assertImplementedOperation(
            paths, "/api/v1/organizations/{organizationId}/activities", "post"
        )
        assertImplementedOperation(paths, "/api/v1/users/me/activities/{activityId}", "put")
        assertImplementedOperation(
            paths,
            "/api/v1/organizations/{organizationId}/activities/{activityId}",
            "put"
        )
        assertImplementedOperation(paths, "/api/v1/users/me/managed-activities", "get")
        assertImplementedOperation(
            paths, "/api/v1/users/me/managed-activities/{activityId}", "get"
        )
        assertImplementedOperation(paths, "/api/v1/activities/{activityId}/publication", "put")
        assertImplementedOperation(paths, "/api/v1/activities/{activityId}/cancellation", "put")
        assertImplementedOperation(paths, "/api/v1/activities/{activityId}", "delete")
        assertImplementedOperation(
            paths, "/api/v1/activities/{activityId}/organizer/wechat-qr", "get"
        )

        assertLocalReferencesResolve(document, document)
        assertPathTemplateParametersAreDeclared(document, paths)
    }

    @Test
    fun m2LocationContractDefinesCoordinateAndBoundedMapQuery() {
        val document = loadDocument()
        val paths = map(document["paths"])
        val mapOperation = map(
            map(paths["/api/v1/activities/map"])["get"]
        )

        assertThat(mapOperation["operationId"]).isEqualTo("listActivitiesOnMap")
        val mapParameters = parametersOf(mapOperation).map { map(it) }
        for (parameterName in listOf(
            "minLatitude", "maxLatitude", "minLongitude", "maxLongitude"
        )) {
            val parameter = mapParameters.stream()
                .filter { candidate -> parameterName == candidate["name"] }
                .findFirst()
                .orElseThrow()
            assertThat(parameter).containsEntry("required", false)
        }
        assertThat(mapParameters).extracting(Function {  it["name"]  })
            .contains("userLatitude", "userLongitude", "radiusMeters")
        val limit = parametersOf(mapOperation).stream()
            .map { map(it) }
            .filter { "limit" == it["name"] }
            .findFirst()
            .orElseThrow()
        assertThat(map(limit["schema"]))
            .containsEntry("default", 100)
            .containsEntry("minimum", 1)
            .containsEntry("maximum", 200)

        val schemas = map(map(document["components"])["schemas"])
        val latitude = map(schemas["ActivityLatitude"])
        assertThat(latitude)
            .containsEntry("type", "number")
            .containsEntry("minimum", -90)
            .containsEntry("maximum", 90)
            .containsEntry("multipleOf", 0.0000001)
            .containsEntry("x-coordinate-reference-system", "GCJ-02")
        assertThat(latitude["description"] as String)
            .contains("GCJ-02", "国测局")
            .doesNotContain("WGS84")
        val longitude = map(schemas["ActivityLongitude"])
        assertThat(longitude)
            .containsEntry("type", "number")
            .containsEntry("minimum", -180)
            .containsEntry("maximum", 180)
            .containsEntry("multipleOf", 0.0000001)
            .containsEntry("x-coordinate-reference-system", "GCJ-02")
        assertThat(longitude["description"] as String)
            .contains("GCJ-02", "国测局")
            .doesNotContain("WGS84")
        assertThat(mapOperation["description"] as String)
            .contains(
                "GCJ-02", "视窗模式", "附近模式", "四个边界",
                "radiusMeters", "distanceMeters ASC", "互斥"
            )

        for (schemaName in listOf(
            "PersonalActivityCreateRequest",
            "OrganizationActivityCreateRequest",
            "PersonalActivityUpdateRequest",
            "OrganizationActivityUpdateRequest"
        )) {
            val requestSchema = map(schemas[schemaName])
            val properties = map(requestSchema["properties"])
            assertThat(requestSchema["description"] as String)
                .contains("latitude", "longitude", "同时为空", "同时有值", "GCJ-02")
            assertThat(properties).containsKeys("latitude", "longitude")
            assertThat(map(properties["latitude"])).containsEntry("nullable", true)
            assertThat(map(properties["longitude"])).containsEntry("nullable", true)
            assertSchemaReference(
                map(properties["latitude"]),
                "#/components/schemas/ActivityLatitude"
            )
            assertSchemaReference(
                map(properties["longitude"]),
                "#/components/schemas/ActivityLongitude"
            )
        }
        assertThat(map(schemas["PersonalActivityUpdateRequest"])["description"] as String)
            .contains("PUBLISHED", "不得为空")
        assertThat(map(schemas["OrganizationActivityUpdateRequest"])["description"] as String)
            .contains("PUBLISHED", "不得为空")

        for (schemaName in listOf(
            "PublicActivitySummary",
            "ManagedActivitySummary",
            "ParticipationActivitySummary"
        )) {
            val schema = map(schemas[schemaName])
            val properties = map(schema["properties"])
            assertThat(schema["required"]).asList().contains("latitude", "longitude")
            assertThat(map(properties["latitude"])).containsEntry("nullable", true)
            assertThat(map(properties["longitude"])).containsEntry("nullable", true)
        }

        val mapItem = map(schemas["ActivityMapItem"])
        val mapItemProperties = map(mapItem["properties"])
        assertThat(mapItem["required"]).asList().contains("latitude", "longitude")
        assertThat(mapItem["required"]).asList().contains("distanceMeters")
        assertThat(map(mapItemProperties["latitude"])).doesNotContainKey("nullable")
        assertThat(map(mapItemProperties["longitude"])).doesNotContainKey("nullable")
        assertThat(map(mapItemProperties["distanceMeters"]))
            .containsEntry("type", "integer")
            .containsEntry("format", "int64")
            .containsEntry("minimum", 0)
            .containsEntry("nullable", true)

        val mapView = map(schemas["ActivityMap"])
        assertThat(mapView["required"]).isEqualTo(listOf("items", "truncated"))
        assertThat(map(mapView["properties"])).containsKeys("items", "truncated")
        val resultAllOf = map(schemas["ActivityMapResult"])["allOf"] as List<*>
        val resultEnvelope = map(resultAllOf[1])
        assertSchemaReference(
            map(map(resultEnvelope["properties"])["data"]),
            "#/components/schemas/ActivityMap"
        )
    }

    @Test
    fun m2PublicActivityContractDefinesKeywordSearch() {
        val document = loadDocument()
        val paths = map(document["paths"])
        val operation = map(
            map(paths["/api/v1/activities"])["get"]
        )
        val parameters = parametersOf(operation).map { map(it) }
        assertThat(parameters).extracting(Function {  it["name"]  })
            .contains("keyword")
        val keyword = parameters.stream()
            .filter { "keyword" == it["name"] }
            .findFirst()
            .orElseThrow()

        assertThat(keyword)
            .containsEntry("in", "query")
            .containsEntry("required", false)
        assertThat(map(keyword["schema"]))
            .containsEntry("type", "string")
            .containsEntry("maxLength", 20)
        assertThat(keyword["description"] as String)
            .contains("标题", "描述", "包含")
    }

    @Test
    fun m2ActivityContractDefinesTopicsAcrossWritesFiltersAndReads() {
        val document = loadDocument()
        val paths = map(document["paths"])
        val schemas = map(map(document["components"])["schemas"])

        val listOperation = map(
            map(paths["/api/v1/activities"])["get"]
        )
        val topicParameter = parametersOf(listOperation).stream()
            .map { map(it) }
            .filter { "topic" == it["name"] }
            .findFirst()
            .orElseThrow()
        assertThat(topicParameter).containsEntry("required", false)
        assertThat(map(topicParameter["schema"]))
            .containsEntry("type", "string")
            .containsEntry("minLength", 1)
            .containsEntry("maxLength", 20)
        assertThat(listOperation["description"] as String)
            .contains("topic", "精确", "且")

        val activityTopics = map(schemas["ActivityTopics"])
        assertThat(activityTopics)
            .containsEntry("type", "array")
            .containsEntry("maxItems", 5)
        assertThat(map(activityTopics["items"]))
            .containsEntry("type", "string")
            .containsEntry("minLength", 1)
            .containsEntry("maxLength", 20)

        for (schemaName in listOf(
            "PersonalActivityCreateRequest",
            "OrganizationActivityCreateRequest",
            "PersonalActivityUpdateRequest",
            "OrganizationActivityUpdateRequest"
        )) {
            val properties = map(map(schemas[schemaName])["properties"])
            assertSchemaReference(
                map(properties["topics"]),
                "#/components/schemas/ActivityTopics"
            )
        }
        for (schemaName in listOf(
            "PublicActivitySummary", "ManagedActivitySummary", "ActivityMapItem"
        )) {
            val schema = map(schemas[schemaName])
            assertThat((schema["required"] as List<*>).contains("topics")).isTrue()
            assertSchemaReference(
                map(map(schema["properties"])["topics"]),
                "#/components/schemas/ActivityTopics"
            )
        }
    }

    @Test
    fun m3ActivityFavoriteContractDefinesIdempotentStateAndPersonalPage() {
        val document = loadDocument()
        val paths = map(document["paths"])
        val schemas = map(map(document["components"])["schemas"])

        val favoritePath = map(
            paths["/api/v1/activities/{activityId}/favorite"]
        )
        assertThat(favoritePath).containsKeys("put", "get", "delete")
        assertThat(map(favoritePath["put"])["description"] as String)
            .contains("资料完整", "公开", "幂等")
        assertThat(map(favoritePath["get"])["description"] as String)
            .contains("公开可见", "避免泄露")
        assertThat(map(favoritePath["delete"])["description"] as String)
            .contains("不要求活动当前仍公开", "幂等")

        val pageOperation = map(
            map(paths["/api/v1/users/me/favorite-activities"])["get"]
        )
        assertThat(pageOperation["description"] as String)
            .contains("favoritedAt DESC", "favoriteId DESC", "M2")
        assertPageShape(schemas, "FavoriteActivityPage")
        val favoriteItemProperties = map(
            map(schemas["FavoriteActivityItem"])["properties"]
        )
        assertSchemaReference(
            map(favoriteItemProperties["activity"]),
            "#/components/schemas/PublicActivitySummary"
        )
        val stateResultAllOf = map(
            schemas["FavoriteStateResult"]
        )["allOf"] as List<*>
        val stateEnvelope = map(stateResultAllOf[1])
        assertSchemaReference(
            map(map(stateEnvelope["properties"])["data"]),
            "#/components/schemas/FavoriteState"
        )
    }

    @Test
    fun m3ActivityCommentContractDefinesOneLevelRepliesAndDeletedPlaceholders() {
        val document = loadDocument()
        val paths = map(document["paths"])
        val schemas = map(map(document["components"])["schemas"])

        val commentsPath = map(
            paths["/api/v1/activities/{activityId}/comments"]
        )
        val create = map(commentsPath["post"])
        val list = map(commentsPath["get"])
        assertThat(create["description"] as String)
            .contains("一级回复", "Unicode", "11711", "Idempotency-Key")
        assertThat(list["description"] as String)
            .contains("匿名", "createdAt ASC", "deleted=true",
                "content=null", "author=null")
        assertAllowsAnonymous(
            paths, "/api/v1/activities/{activityId}/comments", "get"
        )

        val deletion = map(map(paths[
            "/api/v1/activities/{activityId}/comments/{commentId}"
        ])["delete"])
        assertThat(deletion["description"] as String)
            .contains("作者", "发起者", "软删除", "幂等", "清空正文")

        val request = map(schemas["ActivityCommentCreateRequest"])
        assertThat(map(map(request["properties"])["content"]))
            .containsEntry("minLength", 1)
            .containsEntry("maxLength", 500)
        assertPageShape(schemas, "ActivityCommentPage")
        val comment = map(schemas["ActivityComment"])
        assertThat(comment["required"]).isEqualTo(listOf(
            "commentId", "activityId", "parentCommentId", "deleted",
            "content", "author", "createdAt", "deletedAt"
        ))
    }

    @Test
    fun m2ActivityContractDefinesPlaceNameAcrossWritesAndReads() {
        val document = loadDocument()
        val schemas = map(map(document["components"])["schemas"])
        val placeName = map(schemas["ActivityPlaceName"])

        assertThat(placeName)
            .containsEntry("type", "string")
            .containsEntry("maxLength", 100)
            .containsEntry("nullable", true)
        assertThat(placeName["description"] as String)
            .contains("集合地点", "发布", "历史")

        for (schemaName in listOf(
            "PersonalActivityCreateRequest",
            "OrganizationActivityCreateRequest",
            "PersonalActivityUpdateRequest",
            "OrganizationActivityUpdateRequest"
        )) {
            val schema = map(schemas[schemaName])
            val property = map(map(schema["properties"])["placeName"])
            assertSchemaReference(property, "#/components/schemas/ActivityPlaceName")
            assertThat(schema["description"] as String)
                .contains("placeName", "发布", "不得为空")
        }

        for (schemaName in listOf(
            "PublicActivitySummary",
            "ActivityMapItem",
            "ManagedActivitySummary"
        )) {
            val schema = map(schemas[schemaName])
            assertThat(schema["required"]).asList().contains("placeName")
            assertSchemaReference(
                map(map(schema["properties"])["placeName"]),
                "#/components/schemas/ActivityPlaceName"
            )
        }
    }

    @Test
    fun m2ActivityContractDefinesGcj02CoordinateSystemField() {
        val document = loadDocument()
        val schemas = map(map(document["components"])["schemas"])
        val coordinateSystem = map(schemas["ActivityCoordinateSystem"])

        assertThat(coordinateSystem)
            .containsEntry("type", "string")
            .containsEntry("nullable", true)
        assertThat(coordinateSystem["enum"]).isEqualTo(listOf("GCJ-02"))
        assertThat(coordinateSystem["description"] as String)
            .contains("GCJ-02", "有坐标", "无坐标")

        for (schemaName in listOf(
            "PersonalActivityCreateRequest",
            "OrganizationActivityCreateRequest",
            "PersonalActivityUpdateRequest",
            "OrganizationActivityUpdateRequest"
        )) {
            val schema = map(schemas[schemaName])
            assertSchemaReference(
                map(map(schema["properties"])["coordinateSystem"]),
                "#/components/schemas/ActivityCoordinateSystem"
            )
            assertThat(schema["description"] as String)
                .contains("coordinateSystem", "GCJ-02", "无坐标")
        }

        for (schemaName in listOf(
            "PublicActivitySummary",
            "ActivityMapItem",
            "ManagedActivitySummary"
        )) {
            val schema = map(schemas[schemaName])
            assertThat(schema["required"]).asList().contains("coordinateSystem")
            assertSchemaReference(
                map(map(schema["properties"])["coordinateSystem"]),
                "#/components/schemas/ActivityCoordinateSystem"
            )
        }
    }

    @Test
    fun m2PublicActivityContractDefinesNearbyDistanceQuery() {
        val document = loadDocument()
        val paths = map(document["paths"])
        val operation = map(
            map(paths["/api/v1/activities"])["get"]
        )
        val parameters = parametersOf(operation).map { map(it) }

        assertThat(parameters).extracting(Function {  it["name"]  })
            .contains("userLatitude", "userLongitude", "radiusMeters")
        val radius = parameters.stream()
            .filter { "radiusMeters" == it["name"] }
            .findFirst()
            .orElseThrow()
        assertThat(map(radius["schema"]))
            .containsEntry("minimum", 1)
            .containsEntry("maximum", 50000)
        assertThat(operation["description"] as String)
            .contains(
                "Haversine",
                "distanceMeters ASC",
                "userLatitude",
                "userLongitude",
                "radiusMeters",
                "筛选条件",
                "400"
            )

        val schemas = map(map(document["components"])["schemas"])
        val summary = map(schemas["PublicActivitySummary"])
        assertThat(summary["required"]).asList().contains("distanceMeters")
        assertThat(map(map(summary["properties"])["distanceMeters"]))
            .containsEntry("type", "integer")
            .containsEntry("format", "int64")
            .containsEntry("minimum", 0)
            .containsEntry("nullable", true)
    }

    fun m4ContractDefinesFollowDraftPublicationAndFeedBoundaries() {
        val document = loadDocument()
        val info = map(document["info"])
        val paths = map(document["paths"])

        assertThat(info)
            .containsEntry("version", "1.7.0")
            .containsEntry("x-m4-contract-status", "IMPLEMENTED")
        assertThat(paths)
            .containsKeys(
                "/api/v1/users/{userId}/profile",
                "/api/v1/follows/users/{userId}",
                "/api/v1/follows/organizations/{organizationId}",
                "/api/v1/users/{userId}/follow-state",
                "/api/v1/organizations/{organizationId}/follow-state",
                "/api/v1/users/me/following",
                "/api/v1/users/me/followers",
                "/api/v1/users/me/posts",
                "/api/v1/organizations/{organizationId}/posts",
                "/api/v1/users/me/posts/{postId}",
                "/api/v1/organizations/{organizationId}/posts/{postId}",
                "/api/v1/posts/{postId}/publication",
                "/api/v1/posts/{postId}",
                "/api/v1/users/me/managed-posts",
                "/api/v1/users/me/managed-posts/{postId}",
                "/api/v1/posts",
                "/api/v1/users/{userId}/posts",
                "/api/v1/organizations/{organizationId}/posts",
                "/api/v1/feed/following",
                "/api/v1/feed/recommended"
            )
            .doesNotContainKeys(
                "/api/v1/users/search",
                "/api/v1/post-mentions",
                "/api/v1/posts/{postId}/likes",
                "/api/v1/posts/{postId}/comments"
            )

        assertImplementedOperation(paths, "/api/v1/users/{userId}/profile", "get")
        assertImplementedOperation(paths, "/api/v1/follows/users/{userId}", "put")
        assertImplementedOperation(paths, "/api/v1/follows/users/{userId}", "delete")
        assertImplementedOperation(
            paths, "/api/v1/follows/organizations/{organizationId}", "put"
        )
        assertImplementedOperation(
            paths, "/api/v1/follows/organizations/{organizationId}", "delete"
        )
        assertImplementedOperation(paths, "/api/v1/users/{userId}/follow-state", "get")
        assertImplementedOperation(
            paths, "/api/v1/organizations/{organizationId}/follow-state", "get"
        )
        assertImplementedOperation(paths, "/api/v1/users/me/following", "get")
        assertImplementedOperation(paths, "/api/v1/users/me/followers", "get")
        assertImplementedOperation(paths, "/api/v1/users/me/posts", "post")
        assertImplementedOperation(
            paths, "/api/v1/organizations/{organizationId}/posts", "post"
        )
        assertImplementedOperation(paths, "/api/v1/users/me/posts/{postId}", "put")
        assertImplementedOperation(
            paths, "/api/v1/organizations/{organizationId}/posts/{postId}", "put"
        )
        assertImplementedOperation(paths, "/api/v1/posts/{postId}/publication", "put")
        assertImplementedOperation(paths, "/api/v1/posts/{postId}", "get")
        assertImplementedOperation(paths, "/api/v1/posts/{postId}", "delete")
        assertImplementedOperation(paths, "/api/v1/users/me/managed-posts", "get")
        assertImplementedOperation(paths, "/api/v1/users/me/managed-posts/{postId}", "get")
        assertImplementedOperation(paths, "/api/v1/posts", "get")
        assertImplementedOperation(paths, "/api/v1/users/{userId}/posts", "get")
        assertImplementedOperation(
            paths, "/api/v1/organizations/{organizationId}/posts", "get"
        )
        assertImplementedOperation(paths, "/api/v1/feed/following", "get")
        assertImplementedOperation(paths, "/api/v1/feed/recommended", "get")

        assertAllowsAnonymous(paths, "/api/v1/users/{userId}/profile", "get")
        assertAllowsAnonymous(paths, "/api/v1/posts", "get")
        assertAllowsAnonymous(paths, "/api/v1/feed/recommended", "get")
        val recommendedFeed =
            map(map(paths["/api/v1/feed/recommended"])["get"])
        assertThat(recommendedFeed)
            .containsEntry("operationId", "listRecommendedFeed")
            .containsEntry("x-implementation-status", "IMPLEMENTED")
        assertThat(recommendedFeed["description"] as String)
            .contains(
                "匿名访问",
                "Ollama",
                "bge-m3",
                "Qdrant",
                "PUBLISHED + PUBLIC",
                "FOLLOWERS_ONLY",
                "PRIVATE",
                "DRAFT",
                "HIDDEN",
                "DELETED",
                "Redis",
                "following"
            )
        assertThat(parameterReferences(recommendedFeed))
            .contains(
                "#/components/parameters/RecommendedCursor",
                "#/components/parameters/RecommendedLimit"
            )
            .doesNotContain("#/components/parameters/FilterBoundCursor")
        assertHasParameterReference(
            paths,
            "/api/v1/users/me/posts",
            "post",
            "#/components/parameters/IdempotencyKey"
        )
        assertHasParameterReference(
            paths,
            "/api/v1/organizations/{organizationId}/posts",
            "post",
            "#/components/parameters/IdempotencyKey"
        )

        val components = map(document["components"])
        val schemas = map(components["schemas"])
        assertThat(schemas)
            .containsKeys(
                "PostStatus",
                "PostVisibility",
                "PostAuthorType",
                "PostDraftCreateRequest",
                "PostDraftReplaceRequest",
                "PublicPost",
                "ManagedPostDetail",
                "PublicPostPage",
                "ManagedPostPage",
                "FollowingPage",
                "FollowerPage"
            )
            .doesNotContainKeys("PostMention", "PostReaction", "PostComment")
        assertThat(map(schemas["PostStatus"])["enum"])
            .isEqualTo(listOf("DRAFT", "PUBLISHED", "DELETED", "HIDDEN"))
        assertThat(map(schemas["PostVisibility"])["enum"])
            .isEqualTo(listOf("PUBLIC", "FOLLOWERS_ONLY", "PRIVATE"))

        val create = map(schemas["PostDraftCreateRequest"])
        val createProperties = map(create["properties"])
        assertThat(create).containsEntry("additionalProperties", false)
        assertThat(createProperties)
            .containsKeys("title", "content", "visibility", "activityId", "mediaFileIds")
            .doesNotContainKeys("mentions", "topicIds", "location", "declaration")
        assertThat(map(createProperties["title"]))
            .containsEntry("maxLength", 20)
            .containsEntry("nullable", true)
        assertThat(map(createProperties["content"]))
            .containsEntry("maxLength", 5000)
            .containsEntry("nullable", true)
        assertThat(map(createProperties["mediaFileIds"]))
            .containsEntry("maxItems", 5)
            .containsEntry("uniqueItems", true)

        val replacement = map(schemas["PostDraftReplaceRequest"])
        assertThat(replacement["required"]).asList().contains("version")
        assertPageShape(schemas, "PublicPostPage")
        assertPageShape(schemas, "ManagedPostPage")
        assertPageShape(schemas, "FollowingPage")
        assertPageShape(schemas, "FollowerPage")

        val organizationProfile =
            map(map(paths["/api/v1/organizations/{organizationId}"])["get"])
        assertThat(organizationProfile)
            .containsEntry("x-m4-extension-status", "IMPLEMENTED")
        val organizationDetail = map(schemas["PublicOrganizationDetail"])
        assertThat(map(organizationDetail["properties"])).containsKey("followerCount")
        assertThat(organizationDetail["required"])
            .asList()
            .contains("followerCount")

        val fileContent = map(map(paths[
            "/api/v1/files/{fileId}/content"
        ])["get"])
        val fileDescription = fileContent["description"] as String
        assertThat(fileDescription)
            .contains(
                "POST",
                "PUBLIC",
                "FOLLOWERS_ONLY",
                "PRIVATE",
                "个人作者本人",
                "企业当前有效且唯一的 OWNER",
                "所有 POST",
                "no-store"
            )
            .doesNotContain("public, max-age=300")
        var cacheControl = map(map(map(fileContent["responses"])["200"])["headers"])
        cacheControl = map(cacheControl["Cache-Control"])
        assertThat(cacheControl["example"]).isEqualTo("no-store")
    }

    @Test
    fun m4ContractClosesCapabilityModerationAndLifecycleGaps() {
        val document = loadDocument()
        val paths = map(document["paths"])
        val schemas = map(map(document["components"])["schemas"])

        assertThat(map(schemas["UserCapability"])["enum"])
            .asList()
            .contains("FOLLOW")
        val capabilities =
            map(map(paths["/api/v1/users/me/capabilities"])["get"])
        assertThat(capabilities)
            .containsEntry("x-m4-extension-status", "IMPLEMENTED")
        assertThat(capabilities["description"] as String)
            .contains("FOLLOW", "资料完成")
        var capabilityExamples = map(map(map(map(capabilities["responses"])["200"])["content"])["application/json"])
        capabilityExamples = map(capabilityExamples["examples"])
        val personalPublisherData = map(map(map(
            capabilityExamples["personalPublisher"]
        )["value"])["data"])
        val organizationOwnerData = map(map(map(
            capabilityExamples["organizationOwner"]
        )["value"])["data"])
        assertThat(personalPublisherData["capabilities"])
            .asList()
            .contains("FOLLOW")
        assertThat(organizationOwnerData["capabilities"])
            .asList()
            .contains("FOLLOW")

        val followUser =
            map(map(paths["/api/v1/follows/users/{userId}"])["put"])
        val unfollowUser =
            map(map(paths["/api/v1/follows/users/{userId}"])["delete"])
        val followOrganization = map(map(paths[
            "/api/v1/follows/organizations/{organizationId}"
        ])["put"])
        val unfollowOrganization = map(map(paths[
            "/api/v1/follows/organizations/{organizationId}"
        ])["delete"])
        assertErrorResponses(followUser, "401", "404", "409")
        assertErrorResponses(followOrganization, "401", "404", "409")
        assertThat(followUser["description"] as String)
            .contains("FOLLOW", "11201/409", "重复关注", "目标用户")
            .contains("11801/409")
        assertThat(followOrganization["description"] as String)
            .contains("FOLLOW", "11201/409", "重复关注", "目标企业")
        assertThat(unfollowUser["description"] as String)
            .contains("资料未完成", "仍允许", "不泄露")
        assertThat(unfollowOrganization["description"] as String)
            .contains("资料未完成", "仍允许", "不泄露")

        val personalCreation =
            map(map(paths["/api/v1/users/me/posts"])["post"])
        val organizationCreation = map(map(paths[
            "/api/v1/organizations/{organizationId}/posts"
        ])["post"])
        val personalReplacement = map(map(paths[
            "/api/v1/users/me/posts/{postId}"
        ])["put"])
        val organizationReplacement = map(map(paths[
            "/api/v1/organizations/{organizationId}/posts/{postId}"
        ])["put"])
        val publication = map(map(paths[
            "/api/v1/posts/{postId}/publication"
        ])["put"])
        val deletion =
            map(map(paths["/api/v1/posts/{postId}"])["delete"])
        assertThat(personalCreation["description"] as String)
            .contains(
                "PUBLISH_PERSONAL_POST",
                "同键同请求",
                "200",
                "11901/409",
                "11904/409",
                "PUBLISHED",
                "CANCELLED",
                "ENDED"
            )
        assertThat(map(personalCreation["responses"])).containsKeys("200", "201")
        assertThat(map(organizationCreation["responses"])).containsKeys("200", "201")
        assertThat(personalCreation["x-error-precedence"])
            .isEqualTo(
                listOf(
                    "PROFILE_AND_CAPABILITY_AUTHORIZATION",
                    "IDEMPOTENCY_REPLAY_OR_CONFLICT",
                    "CONTENT_AND_REFERENCE_VALIDATION",
                    "CREATE_DRAFT"
                )
            )
        assertThat(organizationCreation["x-error-precedence"])
            .isEqualTo(
                listOf(
                    "PROFILE_COMPLETENESS_11201",
                    "ORGANIZATION_WRITE_AUTHORIZATION_404",
                    "IDEMPOTENCY_REPLAY_OR_CONFLICT",
                    "CONTENT_AND_REFERENCE_VALIDATION",
                    "CREATE_DRAFT"
                )
            )
        assertThat(personalReplacement["description"] as String)
            .contains("PUBLISH_PERSONAL_POST", "PUBLISHED", "CANCELLED", "ENDED")
        assertThat(organizationCreation["description"] as String)
            .contains("DEV_ENABLED", "PUBLISH_ORGANIZATION_POST", "OWNER")
        assertThat(organizationReplacement["description"] as String)
            .contains("DEV_ENABLED", "PUBLISH_ORGANIZATION_POST", "OWNER")
        assertThat(personalReplacement["x-error-precedence"])
            .isEqualTo(
                listOf(
                    "RESOURCE_MANAGE_VISIBILITY_404",
                    "PROFILE_AND_CAPABILITY_AUTHORIZATION",
                    "VERSION_11903",
                    "STATUS_11902",
                    "CONTENT_AND_REFERENCE_VALIDATION",
                    "REPLACE_DRAFT"
                )
            )
        assertThat(organizationReplacement["x-error-precedence"])
            .isEqualTo(
                listOf(
                    "RESOURCE_MANAGE_VISIBILITY_404",
                    "PROFILE_COMPLETENESS_11201",
                    "ORGANIZATION_WRITE_AUTHORIZATION_404",
                    "VERSION_11903",
                    "STATUS_11902",
                    "CONTENT_AND_REFERENCE_VALIDATION",
                    "REPLACE_DRAFT"
                )
            )
        assertThat(publication)
            .containsEntry("operationId", "publishPost")
            .containsEntry("summary", "发布动态草稿")
        assertThat(publication["description"] as String)
            .contains(
                "DRAFT",
                "PUBLISHED",
                "HIDDEN",
                "内部平台",
                "11902/409",
                "PUBLISH_PERSONAL_POST",
                "DEV_ENABLED",
                "PUBLISH_ORGANIZATION_POST"
            )
        assertThat(publication["x-error-precedence"])
            .isEqualTo(
                listOf(
                    "RESOURCE_MANAGE_VISIBILITY_404",
                    "PUBLISHED_IDEMPOTENCY",
                    "WRITE_AUTHORIZATION",
                    "STATUS_11902",
                    "CONTENT_AND_REFERENCE_VALIDATION",
                    "PUBLISH_POST"
                )
            )
        assertThat(deletion["description"] as String)
            .contains(
                "个人动态只校验已认证登录的作者本人",
                "不依赖资料完成度或 PUBLISH_PERSONAL_POST",
                "DEV_ENABLED",
                "PUBLISH_ORGANIZATION_POST",
                "OWNER"
            )
            .doesNotContain("个人动态由具备 PUBLISH_PERSONAL_POST 的作者本人操作")
        assertThat(map(deletion["responses"])).doesNotContainKey("409")
        assertThat(deletion["x-error-precedence"])
            .isEqualTo(
                listOf(
                    "RESOURCE_MANAGE_VISIBILITY_404",
                    "DELETED_404",
                    "ORGANIZATION_WRITE_AUTHORIZATION_404",
                    "DELETE_POST"
                )
            )

        val managedList =
            map(map(paths["/api/v1/users/me/managed-posts"])["get"])
        val managedDetail = map(map(paths[
            "/api/v1/users/me/managed-posts/{postId}"
        ])["get"])
        assertThat(managedList["description"] as String)
            .contains("DEV_DISABLED", "仍可读取", "不可写")
        assertThat(managedDetail["description"] as String)
            .contains("DEV_DISABLED", "仍可读取", "不可写")

        val postDetail =
            map(map(paths["/api/v1/posts/{postId}"])["get"])
        val publicPosts =
            map(map(paths["/api/v1/posts"])["get"])
        val userPosts =
            map(map(paths["/api/v1/users/{userId}/posts"])["get"])
        val organizationPosts = map(map(paths[
            "/api/v1/organizations/{organizationId}/posts"
        ])["get"])
        val followingFeed =
            map(map(paths["/api/v1/feed/following"])["get"])
        assertThat(postDetail["description"] as String)
            .contains(
                "只返回 PUBLISHED",
                "DRAFT",
                "HIDDEN",
                "DELETED",
                "个人作者本人",
                "企业当前有效且唯一的 OWNER",
                "作者",
                "不可用",
                "404",
                "可管理动态"
            )
        assertThat(publicPosts["description"] as String)
            .contains("作者", "公开可用", "历史动态")
        assertThat(userPosts["description"] as String)
            .contains("作者账号不可用", "404")
        assertThat(organizationPosts["description"] as String)
            .contains("企业不可用", "404")
        assertThat(followingFeed["description"] as String)
            .contains("作者", "不可用", "保留关注关系", "不分发")

        val deactivation =
            map(map(paths["/api/v1/account/deactivation"])["post"])
        val dataExport =
            map(map(paths["/api/v1/account/data-exports"])["post"])
        assertThat(deactivation).containsEntry("x-m4-extension-status", "IMPLEMENTED")
        assertThat(deactivation["description"] as String)
            .contains(
                "实际注销",
                "关注关系",
                "DRAFT",
                "DELETED",
                "PUBLISHED",
                "HIDDEN",
                "ACCOUNT_DEACTIVATED",
                "企业动态"
            )
        assertThat(dataExport).containsEntry("x-m4-extension-status", "IMPLEMENTED")
        assertThat(dataExport["description"] as String)
            .contains(
                "关注",
                "粉丝",
                "本人创作",
                "他人私密内容",
                "企业动态",
                "企业动态图片"
            )

        val following =
            map(map(paths["/api/v1/users/me/following"])["get"])
        val followers =
            map(map(paths["/api/v1/users/me/followers"])["get"])
        assertThat(following["description"] as String)
            .contains("不可用", "排除", "关系保留")
        assertThat(followers["description"] as String)
            .contains("不可用", "排除", "关系保留")

        val postDraft = map(schemas["PostDraftCreateRequest"])
        val postDraftProperties = map(postDraft["properties"])
        assertThat(map(postDraftProperties["activityId"])["description"] as String)
            .contains("PUBLISHED", "CANCELLED", "ENDED", "11906/409")

        val parameters = map(map(document["components"])["parameters"])
        assertThat(parameters).containsKey("FilterBoundCursor")
        assertThat(map(parameters["Cursor"])["description"] as String)
            .doesNotContain("筛选", "排序", "HTTP 400")
        assertThat(map(parameters["FilterBoundCursor"])["description"] as String)
            .contains("筛选", "排序", "HTTP 400")
        assertThat(map(parameters["RecommendedCursor"]))
            .containsEntry("name", "cursor")
            .containsEntry("in", "query")
            .containsEntry("required", false)
        assertThat(map(parameters["RecommendedCursor"])["description"] as String)
            .contains("用户", "Redis 快照", "10 分钟", "10001/400")
            .doesNotContain("FilterBoundCursor")
        assertThat(map(map(parameters["RecommendedCursor"])["schema"]))
            .containsEntry("maxLength", 512)
        assertThat(map(parameters["RecommendedLimit"]))
            .containsEntry("name", "limit")
            .containsEntry("in", "query")
            .containsEntry("required", false)
        assertThat(map(map(parameters["RecommendedLimit"])["schema"]))
            .containsEntry("minimum", 1)
            .containsEntry("maximum", 20)
            .containsEntry("default", 20)

        assertThat(parameterReferences(following))
            .contains("#/components/parameters/FilterBoundCursor")
        assertThat(parameterReferences(followers))
            .contains("#/components/parameters/FilterBoundCursor")
        assertThat(parameterReferences(
            map(map(paths["/api/v1/users/me/managed-posts"])["get"])
        ))
            .contains("#/components/parameters/FilterBoundCursor")
        assertThat(parameterReferences(publicPosts))
            .contains("#/components/parameters/FilterBoundCursor")
        assertThat(parameterReferences(userPosts))
            .contains("#/components/parameters/FilterBoundCursor")
        assertThat(parameterReferences(organizationPosts))
            .contains("#/components/parameters/FilterBoundCursor")
        assertThat(parameterReferences(followingFeed))
            .contains("#/components/parameters/FilterBoundCursor")

        assertThat(parameterReferences(
            map(map(paths["/api/v1/activities"])["get"])
        ))
            .contains("#/components/parameters/Cursor")
            .doesNotContain("#/components/parameters/FilterBoundCursor")
        assertThat(parameterReferences(map(
            map(paths["/api/v1/users/me/managed-activities"])["get"]
        )))
            .contains("#/components/parameters/Cursor")
            .doesNotContain("#/components/parameters/FilterBoundCursor")
        assertThat(parameterReferences(map(map(
            paths["/api/v1/activities/{activityId}/participants"]
        )["get"])))
            .contains("#/components/parameters/Cursor")
            .doesNotContain("#/components/parameters/FilterBoundCursor")
        assertThat(parameterReferences(map(
            map(paths["/api/v1/users/me/participations"])["get"]
        )))
            .contains("#/components/parameters/Cursor")
            .doesNotContain("#/components/parameters/FilterBoundCursor")
    }

    private fun parameterReferences(operation: Map<String, Any>): List<String> {
        return parametersOf(operation).stream()
            .map { map(it) }
            .map { it["\$ref"] as String? }
            .filter { it != null }
            .map { it!! }
            .toList()
    }

    @Test
    fun m3ContractDefinesMinimalParticipationOperationsAndSafeViews() {
        val document = loadDocument()
        val paths = map(document["paths"])

        assertThat(paths)
            .doesNotContainKeys(
                "/api/v1/users/me/activities/{activityId}/participation",
                "/api/v1/organizations/{organizationId}/activities/{activityId}/participation"
            )

        val participationPath =
            map(paths["/api/v1/activities/{activityId}/participation"])
        val join = map(participationPath["put"])
        val cancellation = map(participationPath["delete"])
        val participantList =
            map(map(paths["/api/v1/activities/{activityId}/participants"])["get"])
        val removal = map(map(paths[
            "/api/v1/activities/{activityId}/participants/{userId}"
        ])["delete"])
        val myParticipations =
            map(map(paths["/api/v1/users/me/participations"])["get"])
        assertThat(join).doesNotContainKey("requestBody")
        assertThat(cancellation).doesNotContainKey("requestBody")
        assertThat(removal).doesNotContainKey("requestBody")
        assertThat(join["description"] as String)
            .contains(
                "个人活动",
                "企业活动",
                "当前登录用户",
                "重新报名",
                "活动发起者",
                "joinedAt",
                "当前时间",
                "cancelledAt",
                "terminatedAt",
                "terminationReason",
                "原子",
                "11201",
                "11709",
                "不能再次报名",
                "409"
            )
        assertThat(cancellation["description"] as String)
            .contains(
                "活动开始前",
                "活动开始后",
                "活动正常结束",
                "ACTIVE",
                "重复取消",
                "TERMINATED",
                "200",
                "原子"
            )
            .contains("participantCount")
        assertThat(removal["description"] as String)
            .contains(
                "OWNER",
                "REMOVED_BY_OWNER",
                "participantCount",
                "原子",
                "11708"
            )
        assertThat(removal["description"] as String)
            .contains(
                "DRAFT",
                "HIDDEN",
                "无管理权限",
                "404",
                "endsAt",
                "11706",
                "11707"
            )
        assertThat(join["x-error-precedence"])
            .isEqualTo(
                listOf(
                    "RESOURCE_VISIBILITY",
                    "ACTIVE_IDEMPOTENCY",
                    "REMOVED_BY_OWNER_11709",
                    "PROFILE_INCOMPLETE_11201",
                    "ACTIVITY_CANCELLED_11706",
                    "ACTIVITY_ENDED_11707",
                    "ACTIVITY_STARTED_11705",
                    "REGISTRATION_NOT_STARTED_11701",
                    "REGISTRATION_ENDED_11702",
                    "OWNER_SELF_11704",
                    "CAPACITY_FULL_11703",
                    "GENDER_RESTRICTION_11710"
                )
            )
        assertThat(cancellation["x-error-precedence"])
            .isEqualTo(
                listOf(
                    "PARTICIPATION_NOT_FOUND_404",
                    "INACTIVE_IDEMPOTENCY",
                    "ACTIVITY_CANCELLED_11706",
                    "ACTIVITY_ENDED_11707",
                    "ACTIVITY_STARTED_11705",
                    "CANCEL_PARTICIPATION"
                )
            )
        assertThat(removal["x-error-precedence"])
            .isEqualTo(
                listOf(
                    "MANAGE_VISIBILITY_404",
                    "PARTICIPATION_NOT_FOUND_404",
                    "REMOVED_IDEMPOTENCY",
                    "PARTICIPATION_NOT_REMOVABLE_11708",
                    "ACTIVITY_CANCELLED_11706",
                    "ACTIVITY_ENDED_11707",
                    "REMOVE_PARTICIPANT"
                )
            )
        assertErrorResponses(join, "401", "404", "409")
        assertThat(map(join["responses"])).doesNotContainKey("403")
        assertErrorResponses(cancellation, "401", "404", "409")
        assertErrorResponses(removal, "401", "404", "409")
        val activityCancellation =
            map(map(paths["/api/v1/activities/{activityId}/cancellation"])["put"])
        assertThat(activityCancellation)
            .containsEntry("x-m3-extension-status", "IMPLEMENTED")
        assertThat(activityCancellation["description"] as String)
            .contains(
                "ACTIVE",
                "TERMINATED",
                "ACTIVITY_CANCELLED",
                "participantCount",
                "归零",
                "endsAt",
                "保留",
                "原子"
            )
        val accountDeactivation =
            map(map(paths["/api/v1/account/deactivation"])["post"])
        assertThat(accountDeactivation)
            .containsEntry("x-m2-extension-status", "IMPLEMENTED")
            .containsEntry("x-m3-extension-status", "IMPLEMENTED")
        assertThat(accountDeactivation["description"] as String)
            .contains(
                "ACTIVE",
                "正常结束",
                "有效结束",
                "个人活动发起者",
                "企业当前有效且唯一的 OWNER",
                "DRAFT",
                "CANCELLED",
                "ENDED",
                "11501",
                "409"
            )
        assertErrorResponses(accountDeactivation, "409")
        assertThat(participantList["description"] as String)
            .doesNotContain("Figma", "前端", "预览", "三个")
        assertThat(myParticipations["description"] as String)
            .doesNotContain("评价")
        assertImplementedOperation(paths, "/api/v1/activities/{activityId}/participation", "put")
        assertImplementedOperation(paths, "/api/v1/activities/{activityId}/participation", "delete")
        assertImplementedOperation(paths, "/api/v1/activities/{activityId}/participants", "get")
        assertImplementedOperation(
            paths,
            "/api/v1/activities/{activityId}/participants/{userId}",
            "delete"
        )
        assertImplementedOperation(paths, "/api/v1/users/me/participations", "get")

        assertHasParameterReference(
            paths,
            "/api/v1/activities/{activityId}/participants",
            "get",
            "#/components/parameters/Cursor"
        )
        assertHasParameterReference(
            paths,
            "/api/v1/activities/{activityId}/participants",
            "get",
            "#/components/parameters/ParticipationLimit"
        )
        assertNamedParameterSchemaReference(
            paths,
            "/api/v1/users/me/participations",
            "get",
            "status",
            "#/components/schemas/ParticipationStatus"
        )

        val components = map(document["components"])
        val parameters = map(components["parameters"])
        assertThat(map(parameters["ParticipationLimit"])["description"] as String)
            .doesNotContain("前端", "预览", "三个")

        val activityDetail =
            map(map(paths["/api/v1/activities/{activityId}"])["get"])
        assertThat(activityDetail)
            .containsEntry("x-m3-extension-status", "IMPLEMENTED")
        assertAllowsAnonymous(paths, "/api/v1/activities/{activityId}", "get")
        assertThat(activityDetail["security"]).isInstanceOf(List::class.java)
        assertThat(activityDetail["security"] as List<*>)
            .anySatisfy { item ->
                assertThat(map(item))
                    .containsKey("bearerAuth")
            }

        val schemas = map(components["schemas"])
        assertThat(map(schemas["UserCapability"])["enum"])
            .asList()
            .contains(
                "PARTICIPATE",
                "PUBLISH_PERSONAL_ACTIVITY",
                "PUBLISH_ORGANIZATION_ACTIVITY",
                "MANAGE_ORGANIZATION"
            )
        val participationStatus = map(schemas["ParticipationStatus"])
        assertThat(participationStatus["enum"])
            .isEqualTo(listOf("ACTIVE", "CANCELLED", "TERMINATED"))
        assertThat(participationStatus["description"] as String)
            .contains("有效", "未取消", "未终止", "活动正常结束", "ACTIVE")
        assertThat(map(schemas["ParticipationTerminationReason"])["enum"])
            .isEqualTo(listOf("ACTIVITY_CANCELLED", "REMOVED_BY_OWNER"))

        val participation = map(schemas["ActivityParticipation"])
        val participationProperties = map(participation["properties"])
        assertThat(participationProperties)
            .containsKeys(
                "participationId",
                "activityId",
                "userId",
                "status",
                "joinedAt",
                "cancelledAt",
                "terminatedAt",
                "terminationReason",
                "participantCount"
            )
            .doesNotContainKeys("remark", "organizationId", "ownerType")
        assertThat(participation["required"])
            .asList()
            .contains("userId", "terminationReason")

        val myParticipation = map(schemas["MyParticipationItem"])
        val myParticipationProperties = map(myParticipation["properties"])
        assertThat(myParticipationProperties).containsKeys("userId", "terminationReason")
        assertThat(myParticipation["required"])
            .asList()
            .contains("userId", "terminationReason")

        val responses = map(components["responses"])
        val errorDescription = map(responses["Error"])["description"] as String
        assertThat(errorDescription)
            .contains(
                "11701 报名尚未开始",
                "11702 报名已经截止",
                "11703 活动名额已满",
                "11704 活动发起者不能报名自己的活动",
                "11705 活动已经开始",
                "11706 活动已取消",
                "11707 活动已结束",
                "11708 当前参与记录不可由发起者移除",
                "11709 已被活动发起者移除，不能再次报名",
                "11710 不符合活动报名性别限制"
            )

        val participant = map(schemas["ActivityParticipantSummary"])
        val participantProperties = map(participant["properties"])
        assertThat(participantProperties)
            .containsKeys("userId", "nickname", "avatar", "joinedAt")
            .doesNotContainKeys(
                "remark",
                "bio",
                "phone",
                "email",
                "birthDate",
                "gender",
                "region"
            )

        val publicActivityDetail = map(schemas["PublicActivityDetail"])
        val publicActivityDetailAllOf = publicActivityDetail["allOf"] as List<*>
        val publicActivityDetailExtension =
            map(publicActivityDetailAllOf[1])
        val publicActivityDetailProperties =
            map(publicActivityDetailExtension["properties"])
        assertThat(publicActivityDetailProperties)
            .containsKeys(
                "myParticipationStatus",
                "viewerIsOwner",
                "registrationGender",
                "organizerPhone",
                "organizerWechat",
                "organizerWechatQr",
                "refundPolicy"
            )

        assertPageShape(schemas, "ActivityParticipantPage")
        assertPageShape(schemas, "MyParticipationPage")
        assertLocalReferencesResolve(document, document)
    }

    @Test
    fun m2ContractSeparatesPublicReadsAndOwnerWriteCommands() {
        val document = loadDocument()
        val paths = map(document["paths"])

        assertAllowsAnonymous(paths, "/api/v1/organizations/{organizationId}", "get")
        assertAllowsAnonymous(paths, "/api/v1/activities", "get")
        assertAllowsAnonymous(paths, "/api/v1/activities/{activityId}", "get")
        assertAllowsAnonymous(paths, "/api/v1/files/{fileId}/content", "get")

        assertHasParameterReference(
            paths,
            "/api/v1/users/me/activities",
            "post",
            "#/components/parameters/IdempotencyKey"
        )
        assertHasParameterReference(
            paths,
            "/api/v1/organizations/{organizationId}/activities",
            "post",
            "#/components/parameters/IdempotencyKey"
        )

        val publication =
            map(map(paths["/api/v1/activities/{activityId}/publication"])["put"])
        val cancellation =
            map(map(paths["/api/v1/activities/{activityId}/cancellation"])["put"])
        val deletion =
            map(map(paths["/api/v1/activities/{activityId}"])["delete"])
        val publicList =
            map(map(paths["/api/v1/activities"])["get"])
        val publicDetail =
            map(map(paths["/api/v1/activities/{activityId}"])["get"])
        val personalCreation =
            map(map(paths["/api/v1/users/me/activities"])["post"])
        val organizationCreation = map(map(paths[
            "/api/v1/organizations/{organizationId}/activities"
        ])["post"])
        val personalUpdate = map(map(paths[
            "/api/v1/users/me/activities/{activityId}"
        ])["put"])
        val organizationUpdate = map(map(paths[
            "/api/v1/organizations/{organizationId}/activities/{activityId}"
        ])["put"])

        assertThat(publication).doesNotContainKey("requestBody")
        assertThat(cancellation).doesNotContainKey("requestBody")
        assertThat(deletion).doesNotContainKey("requestBody")
        assertThat(publication["description"] as String)
            .contains(
                "DRAFT", "PUBLISHED", "重复", "先保存", "资料完成度", "11201",
                "尚未到期", "endsAt", "11602"
            )
        assertThat(cancellation["description"] as String)
            .contains("DRAFT", "CANCELLED", "重复", "不重复写生命周期事件")
        assertThat(deletion["description"] as String)
            .contains("DRAFT", "物理删除", "404", "409", "24 小时")
        assertThat(personalCreation["description"] as String)
            .contains("11601", "11605", "原幂等请求", "已删除")
        assertThat(organizationCreation["description"] as String)
            .contains("11601", "11605", "原幂等请求", "已删除")
        assertThat(personalUpdate["description"] as String)
            .contains("原 endsAt", "替换后的 endsAt", "11602", "409")
        assertThat(organizationUpdate["description"] as String)
            .contains("原 endsAt", "替换后的 endsAt", "11602", "409")
        assertThat(publicList["description"] as String)
            .contains("endsAt", "PUBLISHED", "ENDED")
        assertThat(publicDetail["description"] as String)
            .contains("endsAt", "status", "registrationStatus", "ENDED")
        val deletionResponses = map(deletion["responses"])
        assertThat(map(deletionResponses["200"]))
            .containsEntry("\$ref", "#/components/responses/VoidSuccess")
        assertThat(map(paths["/api/v1/activities/{activityId}/cancellation"]))
            .doesNotContainKey("post")

        val components = map(document["components"])
        val parameters = map(components["parameters"])
        val idempotencyKey =
            map(map(parameters["IdempotencyKey"])["schema"])
        assertThat(idempotencyKey)
            .containsEntry("minLength", 8)
            .containsEntry("maxLength", 128)
            .containsEntry("pattern", "^[A-Za-z0-9._:-]+$")
        assertThat(map(parameters["IdempotencyKey"])["description"] as String)
            .contains("草稿删除", "24 小时")

        val errorDescription = map(map(components["responses"])["Error"])["description"] as String
        assertThat(errorDescription).contains("11605", "幂等请求对应的活动草稿已删除")

        val activityLimit =
            map(map(parameters["ActivityLimit"])["schema"])
        assertThat(activityLimit)
            .containsEntry("minimum", 1)
            .containsEntry("maximum", 50)
            .containsEntry("default", 20)

        assertNamedParameterSchemaReference(
            paths,
            "/api/v1/activities",
            "get",
            "categoryCode",
            "#/components/schemas/ActivityCategory"
        )
        assertNamedParameterSchemaReference(
            paths,
            "/api/v1/activities",
            "get",
            "regionCode",
            "#/components/schemas/ActivityRegionCode"
        )

        val imageUpload =
            map(map(paths["/api/v1/files/images"])["post"])
        val fileMetadata =
            map(map(paths["/api/v1/files/{fileId}"])["get"])
        val fileContent =
            map(map(paths["/api/v1/files/{fileId}/content"])["get"])
        assertThat(imageUpload).containsEntry("x-m2-extension-status", "IMPLEMENTED")
        assertThat(fileMetadata).containsEntry("x-m2-extension-status", "IMPLEMENTED")
        assertThat(fileContent).containsEntry("x-m2-extension-status", "IMPLEMENTED")
        assertThat(imageUpload["description"] as String)
            .doesNotContain("M2 用途扩展实现前")
        assertThat(fileContent["description"] as String)
            .contains(
                "ACTIVITY", "DRAFT", "HIDDEN", "404",
                "匿名读取使用 no-store", "上传者预览使用 private"
            )
            .doesNotContain("ACTIVITY 文件内容响应固定使用")
    }

    @Test
    fun m2ContractKeepsRequestModelsAndKeyConstraintsExplicit() {
        val document = loadDocument()
        val schemas = map(map(document["components"])["schemas"])

        val activityStatus = map(schemas["ActivityStatus"])
        val publicStatus = map(schemas["PublicActivityStatus"])
        val category = map(schemas["ActivityCategory"])
        val region = map(schemas["ActivityRegionCode"])
        val filePurpose = map(schemas["FilePurpose"])
        assertThat(activityStatus["enum"])
            .isEqualTo(listOf("DRAFT", "PUBLISHED", "CANCELLED", "ENDED", "HIDDEN"))
        assertThat(publicStatus["enum"])
            .isEqualTo(listOf("PUBLISHED", "CANCELLED", "ENDED"))
        assertThat(category["enum"])
            .isEqualTo(
                listOf(
                    "HIKING",
                    "CAMPING",
                    "MOUNTAINEERING",
                    "RUNNING",
                    "CYCLING",
                    "FITNESS",
                    "BALL_SPORTS",
                    "WATER_SPORTS",
                    "TRAVEL",
                    "FOOD",
                    "MUSIC",
                    "MOVIE",
                    "READING",
                    "PHOTOGRAPHY",
                    "BOARD_GAMES",
                    "GAMING",
                    "PETS",
                    "PARENT_CHILD",
                    "VOLUNTEERING",
                    "OTHER"
                )
            )
        assertThat(region)
            .containsEntry("type", "string")
            .containsEntry("minLength", 6)
            .containsEntry("maxLength", 9)
            .containsEntry("pattern", "^[0-9]{6}([0-9]{3})?$")
        assertThat(region["description"] as String)
            .contains(
                "区县",
                "districtCode",
                "AreaCity-JsSpider-StatsGov",
                "2025.251231.260403"
            )
        assertThat(filePurpose["enum"])
            .isEqualTo(listOf("AVATAR", "ACTIVITY", "ACTIVITY_CONTACT_QR", "POST"))
        assertThat(schemas)
            .doesNotContainKeys("ActivityTargetStatus", "ActivityCancellationRequest")

        val personalCreate =
            map(schemas["PersonalActivityCreateRequest"])
        val organizationCreate =
            map(schemas["OrganizationActivityCreateRequest"])
        val personalUpdate =
            map(schemas["PersonalActivityUpdateRequest"])
        val organizationUpdate =
            map(schemas["OrganizationActivityUpdateRequest"])

        assertThat(personalCreate)
            .containsEntry("additionalProperties", false)
            .containsEntry("required", listOf("title"))
        assertThat(organizationCreate)
            .containsEntry("additionalProperties", false)
            .containsEntry("required", listOf("title"))
        assertThat(personalUpdate)
            .containsEntry("additionalProperties", false)
            .containsEntry("required", listOf("version", "title"))
        assertThat(organizationUpdate)
            .containsEntry("additionalProperties", false)
            .containsEntry("required", listOf("version", "title"))

        val personalCreateProperties =
            map(personalCreate["properties"])
        val organizationCreateProperties =
            map(organizationCreate["properties"])
        val personalUpdateProperties =
            map(personalUpdate["properties"])
        val organizationUpdateProperties =
            map(organizationUpdate["properties"])

        assertThat(personalCreateProperties)
            .containsKeys(
                "signupDetails",
                "organizerMessage",
                "registrationGender",
                "organizerPhone",
                "organizerWechat",
                "organizerWechatQrFileId",
                "refundPolicy"
            )
            .doesNotContainKeys(
                "targetStatus",
                "ownerType",
                "ownerId",
                "operatorUserId",
                "userId"
            )
        assertThat(personalUpdateProperties)
            .containsKeys(
                "signupDetails",
                "organizerMessage",
                "registrationGender",
                "organizerPhone",
                "organizerWechat",
                "organizerWechatQrFileId",
                "refundPolicy"
            )
            .doesNotContainKeys(
                "targetStatus",
                "ownerType",
                "ownerId",
                "operatorUserId",
                "userId"
            )
        assertThat(organizationCreateProperties)
            .containsKeys(
                "registrationGender",
                "organizerPhone",
                "organizerWechat",
                "organizerWechatQrFileId",
                "refundPolicy"
            )
            .doesNotContainKeys(
                "targetStatus",
                "signupDetails",
                "organizerMessage",
                "ownerType",
                "ownerId",
                "operatorUserId",
                "userId"
            )
        assertThat(organizationUpdateProperties)
            .containsKeys(
                "registrationGender",
                "organizerPhone",
                "organizerWechat",
                "organizerWechatQrFileId",
                "refundPolicy"
            )
            .doesNotContainKeys(
                "targetStatus",
                "signupDetails",
                "organizerMessage",
                "ownerType",
                "ownerId",
                "operatorUserId",
                "userId"
            )

        assertCommonActivityWriteConstraints(personalCreateProperties)
        assertCommonActivityWriteConstraints(organizationCreateProperties)
        assertCommonActivityWriteConstraints(personalUpdateProperties)
        assertCommonActivityWriteConstraints(organizationUpdateProperties)

        val publicSummaryProperties =
            map(map(schemas["PublicActivitySummary"])["properties"])
        val managedSummaryProperties =
            map(map(schemas["ManagedActivitySummary"])["properties"])
        assertSchemaReference(
            map(publicSummaryProperties["categoryCode"]),
            "#/components/schemas/ActivityCategory"
        )
        assertSchemaReference(
            map(publicSummaryProperties["regionCode"]),
            "#/components/schemas/ActivityRegionCode"
        )
        assertSchemaReference(
            map(managedSummaryProperties["categoryCode"]),
            "#/components/schemas/ActivityCategory"
        )
        assertSchemaReference(
            map(managedSummaryProperties["regionCode"]),
            "#/components/schemas/ActivityRegionCode"
        )
        assertThat(map(publicSummaryProperties["title"]))
            .containsEntry("minLength", 1)
            .containsEntry("maxLength", 20)
        assertThat(map(managedSummaryProperties["title"]))
            .containsEntry("minLength", 1)
            .containsEntry("maxLength", 20)

        assertThat(map(personalCreateProperties["signupDetails"]))
            .containsEntry("maxLength", 2000)
        assertThat(map(personalCreateProperties["organizerMessage"]))
            .containsEntry("maxLength", 1000)

        val file = map(schemas["File"])
        assertThat((file["required"] as List<*>).contains("purpose")).isTrue()
        assertThat(map(file["properties"])).containsKey("purpose")

        assertPageShape(schemas, "PublicActivityPage")
        assertPageShape(schemas, "ManagedActivityPage")
    }

    @Test
    fun profileContractMakesEmailOptionalAndKeepsInterestsRequired() {
        val contract = Path.of("docs", "api", "openapi.yaml")
        val document: Map<String, Any>
        Files.newInputStream(contract).use { input ->
            document = loadMap(input)
        }

        val schemas =
            map(map(document["components"])["schemas"])
        val updateProfile = map(schemas["UpdateProfileRequest"])
        val profileRequired = updateProfile["required"] as List<*>
        val email =
            map(map(updateProfile["properties"])["email"])
        val updateInterests = map(schemas["UpdateInterestsRequest"])
        val interestTagIds =
            map(map(updateInterests["properties"])["interestTagIds"])

        assertThat(profileRequired.contains("email")).isFalse()
        assertThat(email)
            .containsEntry("nullable", true)
            .containsEntry("format", "email")
            .containsEntry("maxLength", 254)
        assertThat(interestTagIds)
            .containsEntry("minItems", 1)
            .containsEntry("maxItems", 20)
            .containsEntry("uniqueItems", true)
    }

    private fun loadDocument(): Map<String, Any> {
        val contract = Path.of("docs", "api", "openapi.yaml")
        Files.newInputStream(contract).use { input ->
            return loadMap(input)
        }
    }

    private fun assertImplementedOperation(
        paths: Map<String, Any>, path: String, method: String
    ) {
        val operation = map(map(paths[path])[method])
        assertThat(operation)
            .`as`("已实现操作必须标记 IMPLEMENTED: %s %s", method, path)
            .containsEntry("x-implementation-status", "IMPLEMENTED")
    }

    private fun assertPlannedOperation(
        paths: Map<String, Any>, path: String, method: String
    ) {
        val operation = map(map(paths[path])[method])
        assertThat(operation)
            .`as`("规划操作必须标记 PLANNED: %s %s", method, path)
            .containsEntry("x-implementation-status", "PLANNED")
    }

    private fun assertCommonActivityWriteConstraints(
        properties: Map<String, Any>
    ) {
        assertThat(map(properties["title"]))
            .containsEntry("minLength", 1)
            .containsEntry("maxLength", 20)
        assertSchemaReference(
            map(properties["categoryCode"]),
            "#/components/schemas/ActivityCategory"
        )
        assertThat(map(properties["description"])).containsEntry("maxLength", 5000)
        assertThat(map(properties["mediaFileIds"]))
            .containsEntry("maxItems", 5)
            .containsEntry("uniqueItems", true)
        assertSchemaReference(
            map(properties["regionCode"]),
            "#/components/schemas/ActivityRegionCode"
        )
        assertThat(map(properties["addressDetail"])).containsEntry("maxLength", 512)
        assertThat(map(properties["capacity"])).containsEntry("minimum", 1)
    }

    private fun assertSchemaReference(
        schema: Map<String, Any>, expectedReference: String
    ) {
        if (schema.containsKey("\$ref")) {
            assertThat(schema["\$ref"]).isEqualTo(expectedReference)
            return
        }
        assertThat(schema["allOf"]).isInstanceOf(List::class.java)
        val allOf = schema["allOf"] as List<*>
        assertThat(allOf)
            .anySatisfy { item ->
                assertThat(map(item)["\$ref"])
                    .isEqualTo(expectedReference)
            }
    }

    private fun assertAllowsAnonymous(
        paths: Map<String, Any>, path: String, method: String
    ) {
        val operation = map(map(paths[path])[method])
        val securityValue = operation["security"]
        assertThat(securityValue).isInstanceOf(List::class.java)
        val security = securityValue as List<*>
        assertThat(
            security.isEmpty() ||
                security.any { item ->
                    item is Map<*, *> && item.isEmpty()
                }
        )
            .`as`("公开读取必须允许匿名访问: %s %s", method, path)
            .isTrue()
    }

    private fun assertHasParameterReference(
        paths: Map<String, Any>, path: String, method: String, expectedReference: String
    ) {
        val operation = map(map(paths[path])[method])
        assertThat(operation["parameters"]).isInstanceOf(List::class.java)
        val parameters = operation["parameters"] as List<*>
        assertThat(parameters)
            .anySatisfy { parameter ->
                assertThat(map(parameter)["\$ref"])
                    .isEqualTo(expectedReference)
            }
    }

    private fun assertNamedParameterSchemaReference(
        paths: Map<String, Any>,
        path: String,
        method: String,
        parameterName: String,
        expectedReference: String
    ) {
        val operation = map(map(paths[path])[method])
        assertThat(operation["parameters"]).isInstanceOf(List::class.java)
        val parameters = operation["parameters"] as List<*>
        val parameter =
            parameters.stream()
                .map { map(it) }
                .filter { parameterName == it["name"] }
                .findFirst()
                .orElseThrow {
                    AssertionError("缺少查询参数: $parameterName")
                }
        assertSchemaReference(map(parameter["schema"]), expectedReference)
    }

    private fun assertPageShape(schemas: Map<String, Any>, schemaName: String) {
        val page = map(schemas[schemaName])
        val properties = map(page["properties"])
        assertThat(page["required"])
            .isEqualTo(listOf("items", "nextCursor", "hasMore"))
        assertThat(properties).containsKeys("items", "nextCursor", "hasMore")
    }

    private fun assertErrorResponses(
        operation: Map<String, Any>, vararg expectedStatuses: String
    ) {
        val responses = map(operation["responses"])
        for (status in expectedStatuses) {
            assertThat(responses)
                .`as`("操作必须声明 HTTP %s", status)
                .containsKey(status)
            assertThat(map(responses[status]))
                .containsEntry("\$ref", "#/components/responses/Error")
        }
    }

    private fun assertPathTemplateParametersAreDeclared(
        document: Map<String, Any>, paths: Map<String, Any>
    ) {
        val templatePattern = Pattern.compile("\\{([^}]+)}")
        for ((pathKey, pathItemValue) in paths) {
            val templateNames = LinkedHashSet<String>()
            val matcher = templatePattern.matcher(pathKey)
            while (matcher.find()) {
                templateNames.add(matcher.group(1))
            }
            if (templateNames.isEmpty()) {
                continue
            }

            val pathItem = map(pathItemValue)
            val pathParameters = parametersOf(pathItem)
            for (method in HTTP_METHODS) {
                if (!pathItem.containsKey(method)) {
                    continue
                }
                val availableParameters = ArrayList<Any>(pathParameters)
                availableParameters.addAll(parametersOf(map(pathItem[method])))
                for (templateName in templateNames) {
                    assertThat(availableParameters)
                        .`as`("路径模板参数必须声明且必填: %s %s", method, pathKey)
                        .anySatisfy { parameterValue ->
                            val parameter = resolveParameter(
                                map(parameterValue), document
                            )
                            assertThat(parameter)
                                .containsEntry("name", templateName)
                                .containsEntry("in", "path")
                                .containsEntry("required", true)
                        }
                }
            }
        }
    }

    private fun parametersOf(container: Map<String, Any>): List<Any> {
        val parameters = container["parameters"] ?: return listOf()
        assertThat(parameters).isInstanceOf(List::class.java)
        return ArrayList((parameters as List<*>).filterNotNull())
    }

    private fun resolveParameter(
        parameter: Map<String, Any>, document: Map<String, Any>
    ): Map<String, Any> {
        val reference = parameter["\$ref"]
        if (reference !is String || !reference.startsWith("#/")) {
            return parameter
        }
        var target: Any = document
        for (segment in reference.substring(2).split("/")) {
            target = map(target)[segment.replace("~1", "/").replace("~0", "~")]!!
        }
        return map(target)
    }

    private fun assertLocalReferencesResolve(
        node: Any?, document: Map<String, Any>
    ) {
        if (node is Map<*, *>) {
            val currentMap = map(node)
            val reference = currentMap["\$ref"]
            if (reference is String && reference.startsWith("#/")) {
                var target: Any = document
                for (segment in reference.substring(2).split("/")) {
                    val targetMap = map(target)
                    val key = segment.replace("~1", "/").replace("~0", "~")
                    assertThat(targetMap)
                        .`as`("本地引用必须存在: %s", reference)
                        .containsKey(key)
                    target = targetMap[key]!!
                }
            }
            currentMap.values.forEach { value -> assertLocalReferencesResolve(value, document) }
            return
        }
        if (node is List<*>) {
            node.forEach { value -> assertLocalReferencesResolve(value, document) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadMap(input: InputStream): Map<String, Any> {
        val loaderOptions = LoaderOptions()
        loaderOptions.isAllowDuplicateKeys = false
        val value = Yaml(SafeConstructor(loaderOptions)).load<Any>(input)
        assertThat(value).isInstanceOf(Map::class.java)
        return value as Map<String, Any>
    }

    @Suppress("UNCHECKED_CAST")
    private fun map(value: Any?): Map<String, Any> {
        assertThat(value).isInstanceOf(Map::class.java)
        return value as Map<String, Any>
    }
}
