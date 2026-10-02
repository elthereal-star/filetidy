# filetidy

[![ci](https://github.com/elthereal-star/filetidy/actions/workflows/ci.yml/badge.svg)](https://github.com/elthereal-star/filetidy/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

一条命令把乱七八糟的下载文件夹收拾整齐：按可配置的 YAML 规则把文件自动归类到子文件夹。
支持 `--dry-run` 预览、`--watch` 持续监听新文件、`undo` 一键撤销上一次整理。

## 为什么写这个

下载文件夹是大多数人电脑里最乱的角落。市面上同类工具要么闭源要么过重，
这个项目的目标是：**一个 jar 包 + 一份 YAML，就能把整理规则固化下来，并且永远可撤销。**

## 快速开始

```bash
# 环境要求：JDK 21+，Maven 3.8+
mvn package        # 产出 target/filetidy.jar

# 预览整理计划（不移动任何文件）
java -jar target/filetidy.jar organize ~/Downloads --dry-run

# 实际整理（使用内置规则）
java -jar target/filetidy.jar organize ~/Downloads

# 使用自定义规则
java -jar target/filetidy.jar organize ~/Downloads -c my-rules.yml

# 监听模式：新下载的文件自动归类
java -jar target/filetidy.jar organize ~/Downloads --watch

# 后悔了：撤销上一次整理
java -jar target/filetidy.jar undo ~/Downloads
```

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
fallback: 99-其他
skipHidden: true
```

| 字段 | 说明 |
| --- | --- |
| `rules[].name` | 规则名，仅用于展示 |
| `rules[].target` | 命中后归入的子文件夹名 |
| `rules[].extensions` | 扩展名列表，不区分大小写，不带点 |
| `fallback` | 未命中任何规则时归入的文件夹 |
| `skipHidden` | 是否跳过隐藏文件，默认 `true` |

内置规则见 [rules-default.yml](src/main/resources/rules-default.yml)。

配置写坏时会在动手移动之前报错，并指出是第几条规则缺了什么字段。

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

## 退出码

| 退出码 | 含义 |
| --- | --- |
| `0` | 成功（包括「没有需要整理的文件」这种空跑） |
| `1` | 运行期错误：目录不存在、配置文件路径错误或内容不合法 |
| `2` | 命令行参数错误，例如漏掉 `<directory>` |

遇到这类错误只打印一行 `错误: 原因`，不会把 Java 堆栈甩出来。
需要排查程序缺陷时用 `FILETIDY_DEBUG=1` 重跑即可看到完整堆栈。

## 控制台输出编码

中文 Windows 下 `System.out` 默认跟随控制台代码页（GBK），
同一份输出在 cmd 里正常、在 Windows Terminal / Git Bash / IDEA 里却是乱码。
filetidy 启动时会判断终端类型：能正确显示 UTF-8 的终端自动切到 UTF-8，
老式 conhost 保持原生编码。需要手动干预时：

```bash
java -Dfiletidy.output.encoding=UTF-8 -jar target/filetidy.jar organize ~/Downloads
java -Dfiletidy.output.encoding=native   -jar target/filetidy.jar organize ~/Downloads
```

## 开发

```bash
mvn verify      # 编译 + 跑全部单元测试
mvn package     # 生成 target/filetidy.jar（shaded，可直接 java -jar 运行）
```

CI 在 ubuntu 与 windows 两个平台上跑同一套 `mvn verify`，
并额外对打好的 jar 做一次 `--version` / `organize --help` 冒烟，
确保交付物本身能跑起来。

## 项目结构

```
src/main/java/dev/filetidy
├── Main.java                    # picocli 入口、退出码与异常兜底
├── FiletidyException.java       # 可预期的用户级错误
├── OrganizeCommand.java         # organize 子命令
├── UndoCommand.java             # undo 子命令
├── config/TidyConfig.java       # YAML 规则绑定与校验
├── core/                        # 规则引擎、移动执行、历史与撤销
│   ├── RuleEngine.java
│   ├── MovePlan.java
│   ├── FileMover.java
│   ├── HistoryEntry.java
│   ├── HistoryStore.java
│   ├── UndoService.java
│   └── UndoResult.java
├── util/ConsoleOutput.java      # 输出编码统一
└── watch/FolderWatcher.java
```

## Roadmap

- [ ] 按文件日期归档（`2026-08/` 月份目录）
- [ ] 按文件大小分流（大文件单独归置）
- [ ] 重复文件检测（内容哈希）
- [ ] GraalVM native image，免 JDK 运行
- [ ] 开机自启与系统托盘（Windows/macOS）

## License

MIT，详见 [LICENSE](LICENSE)。
