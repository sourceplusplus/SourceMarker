package com.sourceplusplus

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.sourceplusplus.portal.SourcePortal
import com.sourceplusplus.protocol.ProtocolAddress.Global.RefreshLogs
import com.sourceplusplus.protocol.portal.PageType
import com.sourceplusplus.protocol.portal.PortalConfiguration
import com.sourceplusplus.sourcemarker.SourceMarkerPlugin
import com.sourceplusplus.sourcemarker.SourceMarkerPlugin.vertx
import com.sourceplusplus.sourcemarker.settings.SourceMarkerConfig
import io.vertx.core.Promise
import io.vertx.core.json.Json
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.cli.common.toBooleanLenient
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class StandaloneLogsLiveView : LightJavaCodeInsightFixtureTestCase() {

    @BeforeEach
    public override fun setUp() {
        assumeTrue((System.getenv("STANDALONE_ENABLED")?.toBooleanLenient() ?: false))
        super.setUp()
    }

    override fun getTestDataPath() = "src/test/testData/spp-example-web-app/src/main/java"

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return object : DefaultLightProjectDescriptor() {
            override fun getSdk(): Sdk = JavaSdk.getInstance().createJdk(
                "jdk-1.8", "/usr/lib/jvm/java-1.8.0-openjdk-amd64/jre"
            )

            override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
                super.configureModule(module, model, contentEntry)
                model.getModuleExtension(LanguageLevelModuleExtension::class.java).languageLevel = LanguageLevel.JDK_1_8

                myFixture.copyDirectoryToProject("", "")
                val modulePath = testDataPath
                val moduleDir = LocalFileSystem.getInstance().findFileByPath(
                    modulePath.replace(File.separatorChar, '/')
                )!!
                PsiTestUtil.removeAllRoots(module, sdk)
                PsiTestUtil.addContentRoot(module, moduleDir)
                PsiTestUtil.addLibrary(
                    model,
                    "test-lib",
                    "src/test/testData/spp-example-web-app/lib/",
                    "spring-web-5.2.2.RELEASE.jar",
                    "spring-data-commons-2.2.3.RELEASE.jar",
                    "apm-toolkit-trace-8.5.0.jar",
                    "slf4j-api-1.7.29.jar"
                )
            }
        }
    }

    @Test
    fun test() {
        assumeTrue((System.getenv("STANDALONE_ENABLED")?.toBooleanLenient() ?: false))
        val projectSettings = PropertiesComponent.getInstance(project)
        val pluginConfig = SourceMarkerConfig(
            serviceHost = "https://localhost:5445",
            accessToken = "change-me",
            certificatePins = listOf(
                "47:09:B8:64:03:06:5C:1A:25:D5:9B:95:CD:0F:8B:DD:5C:BA:7C:89:48:F0:37:14:E2:21:9D:E1:45:64:11:2C"
            )
        )
        projectSettings.setValue("sourcemarker_plugin_config", Json.encode(pluginConfig))

        val className = "spp.example.webapp.controller.WebappController"
        val artifactName = "$className.getUser(long)"
        val portalUuid = "5471535f-2a5f-4ed2-bfaf-65345c59fd7b"
        println(
            "Portal UUID: " + SourcePortal.register(
                portalUuid,
                "null",
                artifactName,
                PortalConfiguration(
                    currentPage = PageType.LOGS,
                    external = true
                )
            )
        )
        val portal = SourcePortal.getPortal(portalUuid)!!

        vertx.sharedData().getLocalMap<String, Int>("portal")["bridge.port"] = 8888
        runBlocking {
            SourceMarkerPlugin.init(project)
        }

        myFixture.testHighlighting(
            false,
            false,
            false,
            "spp/example/webapp/controller/WebappController.java"
        )

        GlobalScope.launch(vertx.dispatcher()) {
            vertx.eventBus().send(RefreshLogs, portal)
        }

        runBlocking { Promise.promise<Nothing>().future().await() }
    }
}
