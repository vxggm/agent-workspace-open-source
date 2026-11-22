plugins {
    // AGP 9.4 起，根 build.gradle.kts 里必须用 `apply false`。
    // 直接在根项目应用会报：
    //   "Android Gradle Plugin has been applied at the root build file.
    //    Please consider not applying in the root (use `apply false`)."
    // 真正的应用发生在 app/build.gradle.kts 的 plugins { id("com.android.application") }。
    id("com.android.application") version "9.4.0" apply false
    // Kotlin 2.4.21 —— 与 RikkaHub 3.1.1 完全对齐（其 libs.versions.toml:5）。
    // 只有 2.4.x 的 compose 编译器才能读 material3 1.5.0-beta02 的元数据。
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    // kotlinx.serialization 插件：为了照搬 RikkaHub 3.2.0 的 ai 模块
    // （其 libs.versions.toml:5 kotlin=2.4.21，:194 kotlin-serialization 用同一个版本）。
    // 我们的 Kotlin 刚好也是 2.4.21，版本完全匹配，不会出现当年 Room/KSP
    // 那种「AGP 内置 Kotlin 与 KSP 版本打架」的问题。
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21" apply false
}
