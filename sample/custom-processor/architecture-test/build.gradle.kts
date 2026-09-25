plugins {
    alias(libs.plugins.kotlinJvm)
    id("me.tbsten.katachi")
    alias(libs.plugins.kotlinPluginSerialization)
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
    systemProperty(
        "katachi.snapshot.update",
        providers.systemProperty("katachi.snapshot.update").getOrElse("false"),
    )
}

dependencies {
    testImplementation(libs.katachi)
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsCore)

    testImplementation(sampleLibs.junitJupiter)
}

katachi {
    architecture = "com.example.projectArchitecture"
    processors {
        register("roleFileCount", "com.example.processors.RoleFileCount")
        register("roleTable", "com.example.processors.RoleTable") {
            arg("sortBy", "Name")
        }
        register("roleDocCoverage", "com.example.processors.RoleDocCoverage")
        docs {
            outputDir = rootProject.layout.projectDirectory.dir("docs")
            rootTitle = "自作プロセッサのサンプル"
            rootDescription = "katachi の定義を読む processor を、利用者が自分で書く方法だけを見せるサンプル。" +
                    "このページ以下はすべて `--processor=docs` が生成したもので、手では書かない。"
        }
    }
}
