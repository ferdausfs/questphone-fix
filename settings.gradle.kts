// Optional local maven mirror (inject via gradle property `ecc.local.maven`) and
// optional mirror-first resolution (inject via gradle property `ecc.use.mirrors`) —
// used in constrained/rate-limited build environments; harmless when unset, so
// regular contributors always resolve from the canonical repositories below.
// (Each block re-reads the system properties: pluginManagement has its own scope.)

pluginManagement {
    val localMavenRepo: String? = System.getProperty("ecc.local.maven")
    val useMirrors: Boolean = System.getProperty("ecc.use.mirrors") != null
    repositories {
        if (localMavenRepo != null) {
            maven { url = uri(localMavenRepo) }
        }
        if (useMirrors) {
            // Aliyun proxies google()/mavenCentral()/gradlePluginPortal and stays
            // reachable when the primary repos rate-limit (HTTP 429).
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
            maven("https://maven.aliyun.com/repository/gradle-plugin")
        }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    val localMavenRepo: String? = System.getProperty("ecc.local.maven")
    val useMirrors: Boolean = System.getProperty("ecc.use.mirrors") != null
    repositories {
        if (localMavenRepo != null) {
            maven { url = uri(localMavenRepo) }
        }
        if (useMirrors) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
        }
        google()
        mavenCentral()
        maven {
            setUrl("https://jitpack.io")
        }
    }
}

rootProject.name = "launcher"
include(":app")
include(":core")
include(":data")
include(":ai")
include(":backend")
