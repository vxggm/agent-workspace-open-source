# Agent Workspace 开源源码副本

源码快照：见 `app/build.gradle.kts` 中的 `APP_VERSION_NAME` / `APP_VERSION_CODE`，包名 `com.agentpad.workspace`。

## 目录范围

本目录是独立 Android Gradle 工程，包含 App Kotlin/Java/C 源码、XML、资源、Gradle 配置与 Wrapper。服务器后端工程、生产部署配置、历史交接、私有凭据、签名密钥、APK 及构建缓存均未收录。原工作区保持原样。

图片、字体、图标和项目模板属于运行资源。`app/src/main/jniLibs/` 保留已有 arm64 终端/proot 运行库；`gradle/wrapper/gradle-wrapper.jar` 是官方构建引导文件。这两类是既有工程所需的二进制依赖，并非新编译的交付包；目录并非所有文件都为纯文本。Rhino 改由 Maven Central 的 `org.mozilla:rhino:1.7.15.1` 解析，未复制本地依赖 JAR。

## 已替换的私有配置

- 用户系统后端基址、官方模型基址、收款链接使用 `https://example.com`。
- 市场构建凭证注入段中的固定 HMAC 密钥替换为 `https://example.com`；部署自建市场时请自行配置匹配的服务端凭证。
- 用户登录后动态保存的 token、用户自行输入的模型 Key、MCP/SSH 配置逻辑保留，副本未携带用户运行数据。
- 公共依赖仓库、上游源码引用、Android XML/SVG 命名空间和本机 127.0.0.1 服务地址保留。

## 环境与工具链下载链接（已保留）

为了让拿到源码的人**能直接装上运行环境**，以下下载地址在开源版中**保持原样、不做占位替换**（它们都是公开的 GitHub Release / 公开网盘分享，不含任何私有凭据）：

| 用途 | 来源 |
|---|---|
| 沙箱 Debian ARM64 rootfs | `gh-proxy.com` + `debuerreotype/docker-debian-artifacts`（官方 dist-arm64v8 slim rootfs） |
| 沙箱 rootfs 备用源 | `share.feijipan.com` 公开分享 |
| CPython 3.12（aarch64） | `astral-sh/python-build-standalone` Release |
| CPython 3.16 定制版 | `github.com/vxggm/cpython-aarch64-ci` Release |
| 工具链（JDK + Gradle + Android SDK 原生件等） | `github.com/vxggm/agent-toolchain-arm64` Release，走 `ghfast.top` 镜像 |

源码内对应位置：

- `ai/SandboxInstaller.kt`：`DEBIAN_SLIM_PROXY` / `DEBIAN_SLIM_FEIJI` / `PYTHON_BUNDLE` / `PYTHON316_*`
- `ai/ToolchainRelease.kt`：`MIRROR` 与 `assetUrl()`
- `ai/DexKitRelease.kt`、`ai/DshService.kt`、`DownloadEnvironmentActivity.kt`

这些地址都指向**公开可下载的发布资产**，无需自建即可使用。若要换成自己的镜像，改对应常量即可。

占位地址仅用于后端与市场部分；自建后端后相应联网功能才可使用。

## 构建

使用 JDK17、Android SDK37，Gradle Wrapper9.6.0，AGP9.4.0，Compose Kotlin 插件2.4.21。SDK 安装路径可通过 `ANDROID_HOME` 配置，或参照 `local.properties.example` 创建自己的 `local.properties`。

```sh
chmod +x gradlew
./gradlew :app:assembleDebug
```

发布签名从用户自己的 `local.properties` 或 `SIGNING_STORE_FILE` / `SIGNING_STORE_PASSWORD` / `SIGNING_KEY_ALIAS` / `SIGNING_KEY_PASSWORD` 环境变量读取。

## 上游与许可

RikkaHub、Operit、Termux 及其他依赖的来源与文件内声明保留。根目录 LICENSE 复制自现有 `app/src/main/assets/RikkaHub-LICENSE.txt`；该文件及各源码头部许可证说明请随分发保留。终端/proot 运行库的源码与构建资料参见上游项目。
