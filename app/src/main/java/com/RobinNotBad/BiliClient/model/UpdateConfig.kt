package com.RobinNotBad.BiliClient.model

data class UpdateConfig(
    var versionCode: Int = 0,
    var versionName: String? = null,
    var description: String? = null,
    var downloadUrl: String? = null,
    var isForceUpdate: Boolean = false
)