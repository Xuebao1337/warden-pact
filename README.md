
# 坚守者契约 · Warden Pact

Minecraft **Java 版 26.2 / NeoForge 26.2.0.95** 模组。用剑命中敌对生物，召唤一只属于你的友方坚守者助战。

## 安装

1. 在启动器中安装 **Minecraft 26.2 + NeoForge 26.2.0.95 或同一 26.2 分支的更新版本**。游戏使用 **Java 25**。
2. 将 `build/libs/wardenpact-1.0.0.jar` 放进这个游戏实例的 `mods` 文件夹。不要放入 `-sources.jar`。
3. 启动游戏。多人游戏时，客户端和服务器都要安装本模组。

本模组不能装到 Fabric、Forge、基岩版或其他 Minecraft 版本中。

## 玩法

- 默认任何带 `minecraft:swords` 标签的剑都能触发，包括原版剑和本模组的召唤剑。
- **左键命中僵尸、骷髅等敌对生物并造成伤害**，即可召唤；空挥、射箭、砍动物或玩家不会触发。
- 坚守者立即攻击你命中的怪物；继续用剑命中其他怪物，会指挥现有坚守者更换目标。
- 每位玩家最多一只。默认持续 **60 秒**，召唤冷却 **20 秒**，多次攻击不会刷新存活时间。
- 没有指定目标时，优先攻击主人周围 **24 格**内的敌对生物；没有敌人时走路跟随主人。
- 保留原版坚守者的近战和音波攻击。不会攻击玩家、已驯服的宠物或友方坚守者，也不会给玩家施加黑暗效果或妨碍睡觉。
- 附近必须有安全落脚点和约 3 格高的空间；空间不足会提示，且不消耗召唤冷却。
- 召唤物是临时助战单位，不掉落物品和经验，不写入世界存档。主人死亡、离线、切换维度，以及召唤物所在区块卸载或退出世界时会消失。
- 默认与原版坚守者一样，不能攻击原版坚守者。会把实现原版 `Enemy` 接口的怪物视为敌人，其中包括末影人等平时可能保持中立的怪物；不会主动伤害动物、村民等被动生物。

## 专属召唤剑

创造模式的“战斗用品”中可以找到 **坚守者召唤剑**，也可以开启作弊后使用：

```mcfunction
/give @s wardenpact:summoning_sword
```

在工作台同一列从上到下放置：

```text
回响碎片
下界合金剑
钻石
```

得到一把坚守者召唤剑。属性相当于下界合金剑，带原创青色像素贴图。此有序合成会消耗材料，不继承材料剑的附魔或自定义名称。

## 配置

首次进入世界后，编辑游戏实例的 `config/wardenpact-server.toml`，退出并重新进入世界使配置生效。也可将该文件放入某个世界的 `serverconfig` 目录，覆盖该世界的设置：

```toml
anySwordSummons = true
cooldownSeconds = 20
lifetimeSeconds = 60
defendRadius = 24
```

将 `anySwordSummons` 改为 `false` 后，只有专属召唤剑能够召唤。服务器由服主设置这些选项。

## 构建与开发

需要 **JDK 25**；JRE 或 Java 17 无法编译本项目。第一次构建需要网络下载 Gradle、Minecraft 和 NeoForge 依赖。

Windows 上可运行自动查找 JDK 25 的脚本：

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

已经正确配置 `JAVA_HOME` 时，使用标准 Gradle 命令：

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runGameTestServer
```

产物：`build/libs/wardenpact-1.0.0.jar`。

`src/gameTest` 包含服务端游戏测试，覆盖召唤与目标切换、数量和冷却限制、玩家和宠物保护、到期清理、实际击杀怪物、跟随，以及空间不足时的行为。测试模组和测试场地不会打包进正式模组。

主要代码在 `src/main/java/dev/wardenpact`；贴图、语言、合成表和物品标签在 `src/main/resources`。

项目以 NeoForge 官方 MDK 为基础，使用官方 Mojang 类名；游戏本体及其映射仍受各自许可证约束。项目新增代码和原创贴图使用 MIT 许可证。
