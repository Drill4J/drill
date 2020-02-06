package com.epam.drill.plugin.api.message

import kotlinx.serialization.*

@Serializable
open class MessageWrapper(var pluginId: String, var drillMessage: DrillMessage)
