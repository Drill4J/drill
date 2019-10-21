package com.epam.drill.e2e

import com.epam.drill.common.*
import com.epam.drill.testdata.*
import io.kotlintest.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.junit.*


class AgentRegistrationTest : AbstractE2ETest() {

    @Test(timeout = 10000)
    fun `Agent should be registered`() {
        val it = 0
        createSimpleAppWithUIConnection(uiStreamDebug = true, agentStreamDebug = false) {
            connectAgent(AgentWrap("ag$it", "0.1.$it")) { ui, agent ->
                ui.getAgent()?.status shouldBe AgentStatus.NOT_REGISTERED
                agent.getServiceConfig()?.sslPort shouldBe sslPort
                register("ag$it").first shouldBe HttpStatusCode.OK
                ui.getAgent()?.status shouldBe AgentStatus.BUSY

                agent.`get-set-packages-prefixes`()
//                                delay(1500)
                agent.`get-load-classes-data`()
//                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
            }
        }
    }

}