# filetidy

[![ci](https://github.com/elthereal-star/filetidy/actions/workflows/ci.yml/badge.svg)](https://github.com/elthereal-star/filetidy/actions/workflows/ci.yml)
[![native](https://github.com/elthereal-star/filetidy/actions/workflows/native.yml/badge.svg)](https://github.com/elthereal-star/filetidy/actions/workflows/native.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

一条命令把乱七八糟的下载文件夹收拾整齐：按可配置的 YAML 规则把文件自动归类到子文件夹。
支持 `--dry-run` 预览、`--watch` 持续监听、`undo` 一键撤销，还能常驻系统托盘、随开机自启。
可以打成不依赖 JVM 的原生可执行文件。

## 为什么写这个

下载文件夹是大多数人电脑里最乱的角落。市面上同类工具要么闭源要么过重，
这个项目的目标是：**一份 YAML 就能把整理规则固化下来，并且永远可撤销。**

## 快速开始

```bash
# 环境要求：JDK 21+，Maven 3.8+
mvn package        # 产出 target/filetidy.jar

# 预览整理计划（不移动任何文件）
java -jar target/filetidy.jar organize ~/Downloads --dry-run

# 实际整理（使用内置规则）
java -jar target/filetidy.jar organize ~/Downloads

# 想知道每个文件具体去哪，而不是只看分组汇总
java -jar target/filetidy.jar organize ~/Downloads -v

# 使用自定义规则
java -jar target/filetidy.jar organize ~/Downloads -c my-rules.yml

# 确认 YAML 到底被解析成了什么
java -jar target/filetidy.jar config -c my-rules.yml

# 后悔了：先看看能撤销什么，再撤销
java -jar target/filetidy.jar undo ~/Downloads --list
java -jar target/filetidy.jar undo ~/Downloads
```

想把「新下载的文件自动归类」变成常态，有两种方式：

```bash
# 方式一：前台监听，Ctrl+C 退出
java -jar target/filetidy.jar organize ~/Downloads --watch

# 方式二：常驻托盘，右键菜单可立即整理 / 暂停监听 / 打开目录 / 退出
java -jar target/filetidy.jar tray ~/Downloads

# 顺便让它开机自启（详见下方「开机自启」）
java -jar target/filetidy.jar autostart ~/Downloads --enable
```

## 子命令

| 命令 | 作用 |
| --- | --- |
| `organize <目录>` | 按规则整理一次 |
| `undo <目录>` | 撤销**最近一次**整理（`--list` 列历史批次，`--dry-run` 只看不动） |
| `config` | 打印当前生效的规则与选项，用来确认 YAML 写得对不对 |
| `tray <目录>` | 常驻系统托盘，后台自动整理新文件 |
| `autostart <目录>` | 查看 / 启用 / 取消开机自启 |

`organize` 的选项：

| 选项 | 说明 |
| --- | --- |
| `--dry-run` | 只打印整理计划，不移动文件 |
| `--watch` | 持续监听，新文件落盘自动整理 |
| `-c, --config <文件>` | 使用指定的 YAML 规则，缺省用内置规则 |
| `--no-duplicates` | 本次跳过重复文件检测 |
| `-v, --verbose` | 列出每个文件的去向 |
| `-q, --quiet` | 只在出错时输出 |

## 规则配置

规则是一份 YAML，按扩展名匹配，未命中的文件归入 `fallback` 目录：

```yaml
rules:
  - name: images
    target: 01-图片
    extensions: [jpg, jpeg, png, gif, webp]
  - name: documents
    target: 02-文档
    extensions: [pdf, doc, docx, xls, xlsx, md]
    datePattern: yyyy-MM        # 可选：再按修改时间分一层月份目录
fallback: 99-其他
skipHidden: true

# 可选：大文件单独归置
largeFiles:
  threshold: 100MB              # 支持 500k / 1.5GB / 100MB
  target: 90-大文件

# 可选：重复文件处理
duplicates:
  action: report                # report（只报告，缺省）| move（把多余副本挪走）
  keep: oldest                  # oldest（留最早的，缺省）| newest（留最新的）
  target: 98-重复文件            # action 为 move 时多余副本的去处
```

| 字段 | 说明 |
| --- | --- |
| `rules[].name` | 规则名，仅用于展示 |
| `rules[].target` | 命中后归入的子文件夹名 |
| `rules[].extensions` | 扩展名列表，不区分大小写，不带点 |
| `rules[].datePattern` | 可选。命中后追加一级日期子目录，取值是 `DateTimeFormatter` 的格式，如 `yyyy-MM`、`yyyy/MM`；按文件的**最后修改时间**计算 |
| `fallback` | 未命中任何规则时归入的文件夹 |
| `skipHidden` | 是否跳过隐藏文件，默认 `true` |
| `largeFiles.threshold` | 达到该大小（含等于）就不再按扩展名归类，直接进 `largeFiles.target` |
| `duplicates.action` | `report` 只报告（缺省）；`move` 把多余副本挪进 `duplicates.target` |
| `duplicates.keep` | 一组重复文件里保留哪一个：`oldest`（缺省）或 `newest` |
| `duplicates.target` | `action` 为 `move` 时多余副本的去处，缺省 `98-重复文件` |

内置规则见 [rules-default.yml](src/main/resources/rules-default.yml)，
其中 `largeFiles` 与 `duplicates` 以注释形式给出示例，需要时取消注释即可。

几条实际行为上的约定：

- **判定优先级**：大文件分流 → 扩展名规则 → `fallback`。
- **重复检测的比对范围**是「本次待整理的文件 + 已经归档到各目标目录里的文件」，
  所以「同一个文件下载了两次」也能被发现，即使第一次那份早就归档好了。
- **重复检测只会搬动本次待整理的文件**，已经归档的文件永远只报告、不移动，
  工具不会擅自改动你早就整理好的目录结构。空文件不参与重复判定。
- **`move` 模式下，`keep` 决定留下谁**：其余副本进 `duplicates.target`。
- 配置写坏时会在动手移动之前报错，并指出是第几条规则缺了什么字段。

## 平台支持

| 能力 | Windows | macOS | Linux |
| --- | :---: | :---: | :---: |
| `organize` / `undo` / `config` | ✅ | ✅ | ✅ |
| `tray` 托盘常驻 | ✅ | ✅ | ✅（需要桌面环境） |
| `autostart` 开机自启 | 启动文件夹 `.cmd` | `~/Library/LaunchAgents/dev.filetidy.plist` | `~/.config/autostart/filetidy.desktop` |
| 原生可执行文件 | ✅ | ✅ | ✅ |

没有桌面环境时（服务器、SSH 会话），`tray` 会自动退化成前台监听，
启动参数里加 `--no-fallback` 可以让它直接报错而不是退化。

### 开机自启

```bash
java -jar target/filetidy.jar autostart ~/Downloads          # 只看当前状态
java -jar target/filetidy.jar autostart ~/Downloads --enable # 启用
java -jar target/filetidy.jar autostart ~/Downloads --disable # 取消
```

两个实现细节：Windows 上优先用 `javaw.exe` 启动，避免每次登录闪出控制台窗口；
写进启动项的路径会经过 `toRealPath()` 规范化，所以不会出现
`C:\Users\BAISHA~1\...` 这种 8.3 短名。

> ⚠️ `--enable` 会往你的用户家目录写文件，属于对系统的持久改动。
> 如果之后把 jar 挪走或删掉，登录时会留下一个起不来的后台进程，
> 记得先用 `--disable` 关掉。

## 原生可执行文件

需要 **GraalVM JDK 21**（自带 `native-image` 工具），然后用 `native` profile 构建：

```bash
mvn -Pnative package

./target/filetidy --version                      # Windows 上是 target\filetidy.exe
./target/filetidy organize ~/Downloads --dry-run
```

产物是不依赖 JVM 的单个可执行文件，不需要先拉起 JVM，
也方便直接丢进 `PATH` 或配成开机自启。

原生镜像不会自己做反射，所以这些地方需要显式注册，仓库里都配好了：

- **picocli**：编译期由 `picocli-codegen` 注解处理器扫描全部 `@Command` 类，
  把反射配置生成到 `META-INF/native-image/picocli-generated/`。
- **SnakeYAML**：按 JavaBean setter 绑定 `TidyConfig` 及其内部的
  `Rule` / `LargeFiles` / `Duplicates`，见手写的 `reflect-config.json`。
- **Jackson**：读写历史记录的 `HistoryEntry` 这条 record。
- **内置规则**：`rules-default.yml` 走 `getResourceAsStream` 读取，
  必须在 `resource-config.json` 里保留，否则镜像里没有这份默认规则。

构建参数在 `native-image.properties`，用了 `--no-fallback`：
缺配置时宁可让构建失败，也不要悄悄产出一个仍然依赖 JVM 的兜底镜像。

## 设计要点

- **只扫描目录第一层**：已归档到子文件夹的文件不会被二次移动
- **永不覆盖**：目标位置已有同名文件时自动追加 ` (1)`、` (2)`
- **完整可撤销**：每次移动追加写入 `.filetidy-history.jsonl`，`undo` 按运行批次整体回滚
- **watch 模式全量重扫**：天然规避浏览器分块下载产生的重复事件和半截文件
- **隐藏文件判定跨平台一致**：以点开头的文件名在 Windows 上同样算隐藏，
  不会因为只认 DOS 隐藏属性而把 `.env` 之类的文件搬走；
  历史文件 `.filetidy-history.jsonl` 永远不会被当作整理对象
- **undo 不留垃圾**：撤销成功后，被搬空的目标文件夹会一并清理；
  因原位置被占用而没能回滚的记录会保留，处理掉冲突后重跑 `undo` 即可
- **输出对齐中文**：CJK 与全角字符按两列宽计算，中英混排的表格不会错位

## 退出码

| 退出码 | 含义 |
| --- | --- |
| `0` | 成功（包括「没有需要整理的文件」这种空跑） |
| `1` | 运行期错误：目录不存在、配置文件路径错误或内容不合法 |
| `2` | 命令行参数错误，例如漏掉 `<directory>` |

遇到这类错误只打印一行 `错误: 原因`，不会把 Java 堆栈甩出来。
需要排查程序缺陷时用 `FILETIDY_DEBUG=1` 重跑即可看到完整堆栈。

## 控制台输出

中文 Windows 下 `System.out` 默认跟随控制台代码页（GBK），
同一份输出在 cmd 里正常、在 Windows Terminal / Git Bash / IDEA 里却是乱码。
filetidy 启动时会判断终端类型：能正确显示 UTF-8 的终端自动切到 UTF-8，
老式 conhost 保持原生编码。需要手动干预时：

```bash
java -Dfiletidy.output.encoding=UTF-8 -jar target/filetidy.jar organize ~/Downloads
java -Dfiletidy.output.encoding=native   -jar target/filetidy.jar organize ~/Downloads
```

输出颜色同理：终端支持 ANSI 时高亮「已整理 / 警告 / 错误」，不支持时自动降级为纯文本。
设了 `NO_COLOR` 环境变量也会关掉颜色。

## 开发

```bash
mvn verify          # 编译 + 跑全部单元测试
mvn package         # 生成 target/filetidy.jar（shaded，可直接 java -jar 运行）
mvn -Pnative package # 生成原生可执行文件（需要 GraalVM JDK）
```

两条 GitHub Actions 流水线：

- `ci.yml`：ubuntu 与 windows 双平台跑 `mvn verify`，
  再对打好的 jar 做一次 `--version` / `organize --help` 冒烟。
- `native.yml`：用 GraalVM 构建原生二进制，构建完**真跑一遍**完整的
  `organize → undo` 往返，并校验退出码约定（可预期错误 `1` / 参数错误 `2`）。
  PR 上只构建 Linux（反射没注册这类问题各平台同源，一个平台即可暴露），
  手动触发或合并到 main 时铺开 Linux / Windows / macOS。

## 项目结构

```
src/main/java/dev/filetidy
├── Main.java                      # picocli 入口、退出码与异常兜底
├── FiletidyException.java         # 可预期的用户级错误
├── OrganizeCommand.java           # organize 子命令
├── UndoCommand.java               # undo 子命令
├── ConfigCommand.java             # config 子命令
├── TrayCommand.java               # tray 子命令
├── AutostartCommand.java          # autostart 子命令
├── config/TidyConfig.java         # YAML 规则绑定与校验
├── core/
│   ├── RuleEngine.java            # 生成整理计划（大文件 → 扩展名 → fallback）
│   ├── MovePlan.java
│   ├── TidyService.java           # 计划 → 执行 → 记录
│   ├── FileMover.java
│   ├── DuplicateDetector.java     # 内容哈希找重复
│   ├── DuplicateGroup.java
│   ├── DetectResult.java
│   ├── HistoryEntry.java
│   ├── HistoryStore.java
│   ├── RunSummary.java
│   ├── UndoService.java
│   └── UndoResult.java
├── autostart/
│   ├── AutostartManager.java      # 三个平台各自的启动项写法
│   └── LaunchCommand.java         # 拼装开机要执行的命令
├── tray/
│   ├── TrayApp.java               # 托盘图标、菜单与后台监听
│   └── TraySupport.java           # 托盘可用性探测
├── util/
│   ├── Ansi.java                  # 颜色降级
│   ├── ConsoleOutput.java         # 输出编码统一
│   ├── PlanReport.java            # 汇总输出
│   ├── Size.java                  # 100MB / 1.5GB 这类写法解析与格式化
│   └── TextWidth.java             # 中英混排宽度计算
└── watch/FolderWatcher.java
```

## Roadmap

- [x] 按文件日期归档（`2026-08/` 月份目录）
- [x] 按文件大小分流（大文件单独归置）
- [x] 重复文件检测（内容哈希）
- [x] GraalVM native image，免 JDK 运行
- [x] 开机自启与系统托盘（Windows/macOS/Linux）

## License

MIT，详见 [LICENSE](LICENSE)。
