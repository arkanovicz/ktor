/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.velocity

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.util.*
import org.apache.velocity.app.*
import org.apache.velocity.tools.*
import org.apache.velocity.tools.config.*

private val ENGINE_CONFIG_KEY = "ENGINE_CONFIG"

public fun EasyFactoryConfiguration.engine(configure : VelocityEngine.() -> Unit) {
    data(ENGINE_CONFIG_KEY, configure)
}

/**
 * VelocityTools ktor feature. Populates model with standard Velocity tools.
 */
public class VelocityTools(private val toolManager : ToolManager) : Velocity(toolManager.velocityEngine) {

    /**
     * A companion object for installing feature
     */
    public companion object Feature : ApplicationFeature<ApplicationCallPipeline, EasyFactoryConfiguration, VelocityTools> {
        override val key: AttributeKey<VelocityTools> = AttributeKey<VelocityTools>("velocityTools")

        override fun install(pipeline: ApplicationCallPipeline, config: EasyFactoryConfiguration.() -> Unit): VelocityTools {
            val factoryConfig = EasyFactoryConfiguration().apply(config)
            val engineConfig = factoryConfig. getData(ENGINE_CONFIG_KEY)
            factoryConfig.removeData(engineConfig)
            val engine = VelocityEngine().apply(engineConfig.value as VelocityEngine.() -> Unit)
            val toolManager = ToolManager().apply {
                configure(factoryConfig)
                velocityEngine = engine
            }
            val feature = VelocityTools(toolManager)
            pipeline.sendPipeline.intercept(ApplicationSendPipeline.Transform) { value ->
                if (value is VelocityContent) {
                    val response = feature.process(value)
                    proceedWith(response)
                }
            }
            return feature
        }
    }

    override fun process(content: VelocityContent): VelocityOutgoingContent {
        return VelocityOutgoingContent(
            toolManager.velocityEngine.getTemplate(content.template),
            toolManager.createContext().also { it.putAll(content.model) },
            content.etag,
            content.contentType
        )
    }
}