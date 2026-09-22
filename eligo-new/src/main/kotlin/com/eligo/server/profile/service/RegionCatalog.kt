package com.eligo.server.profile.service

interface RegionCatalog {

    fun resolve(provinceCode: String?, cityCode: String?, districtCode: String?): Region

    fun isDistrictCode(districtCode: String?): Boolean {
        return districtCode != null &&
            districtCode.matches(Regex("[0-9]{6}([0-9]{3})?"))
    }

    data class Region(
        val provinceCode: String?,
        val provinceName: String?,
        val cityCode: String?,
        val cityName: String?,
        val districtCode: String?,
        val districtName: String?
    )
}
