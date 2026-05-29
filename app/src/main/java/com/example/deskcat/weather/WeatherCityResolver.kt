package com.example.deskcat.weather

object WeatherCityResolver {
    fun resolve(input: String): WeatherLocation? {
        val normalized = input.trim()
        if (normalized.isBlank()) return cityMap[DEFAULT_CITY]
        if (isLocationId(normalized) || isCoordinate(normalized)) {
            return WeatherLocation(
                id = normalized,
                name = normalized,
                adm1 = "",
                adm2 = "",
            )
        }
        val key = normalized
            .removeSuffix("市")
            .removeSuffix("区")
            .removeSuffix("县")
        return cityMap[normalized] ?: cityMap[key]
    }

    fun supportedCityHint(): String {
        return "支持常见城市名，也可以直接输入和风天气 LocationID。"
    }

    private fun isLocationId(input: String): Boolean {
        return input.length in 6..12 && input.all { it.isDigit() }
    }

    private fun isCoordinate(input: String): Boolean {
        val parts = input.split(",")
        if (parts.size != 2) return false
        return parts[0].trim().toDoubleOrNull() != null && parts[1].trim().toDoubleOrNull() != null
    }

    private val cityMap = mapOf(
        "北京" to WeatherLocation("101010100", "北京", "北京", "北京"),
        "上海" to WeatherLocation("101020100", "上海", "上海", "上海"),
        "天津" to WeatherLocation("101030100", "天津", "天津", "天津"),
        "重庆" to WeatherLocation("101040100", "重庆", "重庆", "重庆"),
        "广州" to WeatherLocation("101280101", "广州", "广东", "广州"),
        "深圳" to WeatherLocation("101280601", "深圳", "广东", "深圳"),
        "珠海" to WeatherLocation("101280701", "珠海", "广东", "珠海"),
        "佛山" to WeatherLocation("101280800", "佛山", "广东", "佛山"),
        "东莞" to WeatherLocation("101281601", "东莞", "广东", "东莞"),
        "杭州" to WeatherLocation("101210101", "杭州", "浙江", "杭州"),
        "宁波" to WeatherLocation("101210401", "宁波", "浙江", "宁波"),
        "温州" to WeatherLocation("101210701", "温州", "浙江", "温州"),
        "南京" to WeatherLocation("101190101", "南京", "江苏", "南京"),
        "苏州" to WeatherLocation("101190401", "苏州", "江苏", "苏州"),
        "无锡" to WeatherLocation("101190201", "无锡", "江苏", "无锡"),
        "常州" to WeatherLocation("101191101", "常州", "江苏", "常州"),
        "成都" to WeatherLocation("101270101", "成都", "四川", "成都"),
        "绵阳" to WeatherLocation("101270401", "绵阳", "四川", "绵阳"),
        "武汉" to WeatherLocation("101200101", "武汉", "湖北", "武汉"),
        "长沙" to WeatherLocation("101250101", "长沙", "湖南", "长沙"),
        "西安" to WeatherLocation("101110101", "西安", "陕西", "西安"),
        "郑州" to WeatherLocation("101180101", "郑州", "河南", "郑州"),
        "洛阳" to WeatherLocation("101180901", "洛阳", "河南", "洛阳"),
        "济南" to WeatherLocation("101120101", "济南", "山东", "济南"),
        "青岛" to WeatherLocation("101120201", "青岛", "山东", "青岛"),
        "烟台" to WeatherLocation("101120501", "烟台", "山东", "烟台"),
        "沈阳" to WeatherLocation("101070101", "沈阳", "辽宁", "沈阳"),
        "大连" to WeatherLocation("101070201", "大连", "辽宁", "大连"),
        "哈尔滨" to WeatherLocation("101050101", "哈尔滨", "黑龙江", "哈尔滨"),
        "长春" to WeatherLocation("101060101", "长春", "吉林", "长春"),
        "石家庄" to WeatherLocation("101090101", "石家庄", "河北", "石家庄"),
        "唐山" to WeatherLocation("101090501", "唐山", "河北", "唐山"),
        "太原" to WeatherLocation("101100101", "太原", "山西", "太原"),
        "呼和浩特" to WeatherLocation("101080101", "呼和浩特", "内蒙古", "呼和浩特"),
        "包头" to WeatherLocation("101080201", "包头", "内蒙古", "包头"),
        "合肥" to WeatherLocation("101220101", "合肥", "安徽", "合肥"),
        "芜湖" to WeatherLocation("101220301", "芜湖", "安徽", "芜湖"),
        "福州" to WeatherLocation("101230101", "福州", "福建", "福州"),
        "厦门" to WeatherLocation("101230201", "厦门", "福建", "厦门"),
        "泉州" to WeatherLocation("101230501", "泉州", "福建", "泉州"),
        "南昌" to WeatherLocation("101240101", "南昌", "江西", "南昌"),
        "九江" to WeatherLocation("101240201", "九江", "江西", "九江"),
        "昆明" to WeatherLocation("101290101", "昆明", "云南", "昆明"),
        "贵阳" to WeatherLocation("101260101", "贵阳", "贵州", "贵阳"),
        "南宁" to WeatherLocation("101300101", "南宁", "广西", "南宁"),
        "柳州" to WeatherLocation("101300301", "柳州", "广西", "柳州"),
        "海口" to WeatherLocation("101310101", "海口", "海南", "海口"),
        "三亚" to WeatherLocation("101310201", "三亚", "海南", "三亚"),
        "兰州" to WeatherLocation("101160101", "兰州", "甘肃", "兰州"),
        "银川" to WeatherLocation("101170101", "银川", "宁夏", "银川"),
        "西宁" to WeatherLocation("101150101", "西宁", "青海", "西宁"),
        "乌鲁木齐" to WeatherLocation("101130101", "乌鲁木齐", "新疆", "乌鲁木齐"),
        "拉萨" to WeatherLocation("101140101", "拉萨", "西藏", "拉萨"),
        "香港" to WeatherLocation("101320101", "香港", "香港", "香港"),
        "澳门" to WeatherLocation("101330101", "澳门", "澳门", "澳门"),
        "台北" to WeatherLocation("101340101", "台北", "台湾", "台北"),
    )
}
