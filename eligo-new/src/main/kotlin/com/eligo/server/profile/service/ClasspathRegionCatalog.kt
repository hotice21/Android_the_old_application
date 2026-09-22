package com.eligo.server.profile.service

import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

@Component
class ClasspathRegionCatalog(private val mapper: ObjectMapper) : RegionCatalog {

    private val provinces = mutableMapOf<String, Province>()
    private val cities = mutableMapOf<String, City>()
    private val districts = mutableMapOf<String, District>()

    init {
        try {
            ClassPathResource("regions/cn-regions.json").inputStream.use { input ->
                val root = mapper.readTree(input)
                for (provinceNode in root.path("regions")) {
                    val province = Province(
                        provinceNode.path("code").asText(),
                        provinceNode.path("name").asText()
                    )
                    putUnique(provinces, province.code, province)
                    loadCities(provinceNode, province)
                }
            }
        } catch (exception: Exception) {
            throw IllegalStateException("行政区划快照加载失败", exception)
        }
    }

    private fun loadCities(provinceNode: JsonNode, province: Province) {
        for (cityNode in provinceNode.path("children")) {
            val city = City(
                cityNode.path("code").asText(),
                cityNode.path("name").asText(),
                province.code
            )
            putUnique(cities, city.code, city)
            loadDistricts(cityNode, city)
        }
    }

    private fun loadDistricts(cityNode: JsonNode, city: City) {
        for (districtNode in cityNode.path("children")) {
            val district = District(
                districtNode.path("code").asText(),
                districtNode.path("name").asText(),
                city.code
            )
            putUnique(districts, district.code, district)
        }
    }

    override fun resolve(
        provinceCode: String?,
        cityCode: String?,
        districtCode: String?
    ): RegionCatalog.Region {
        val province = provinces[provinceCode]
        val city = cities[cityCode]
        val district = districts[districtCode]
        if (province == null || city == null || district == null ||
            city.provinceCode != province.code ||
            district.cityCode != city.code
        ) {
            throw BusinessException(CommonErrorCode.VALIDATION_FAILED)
        }
        return RegionCatalog.Region(
            province.code,
            province.name,
            city.code,
            city.name,
            district.code,
            district.name
        )
    }

    override fun isDistrictCode(districtCode: String?): Boolean {
        return districts.containsKey(districtCode)
    }

    private fun <T> putUnique(map: MutableMap<String, T>, code: String, value: T) {
        if (map.putIfAbsent(code, value) != null) {
            throw IllegalStateException("行政区划编码重复: $code")
        }
    }

    private data class Province(val code: String, val name: String)

    private data class City(val code: String, val name: String, val provinceCode: String)

    private data class District(val code: String, val name: String, val cityCode: String)
}
