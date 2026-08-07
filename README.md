# filetidy

一条命令把乱七八糟的下载文件夹收拾整齐：按可配置的 YAML 规则把文件自动归类到子文件夹。
支持 `--dry-run` 预览、`--watch` 持续监听新文件、`undo` 一键撤销上一次整理。

## 为什么写这个

下载文件夹是大多数人电脑里最乱的角落。市面上同类工具要么闭源要么过重，
这个项目的目标是：**一个 jar 包 + 一份 YAML，就能把整理规则固化下来，并且永远可撤销。**

## 快速开始

```bash
# 环境要求：JDK 21+，Maven 3.8+
mvn package

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

内置规则见 [rules-default.yml](src/main/resources/rules-default.yml)。

## 设计要点

- **只扫描目录第一层**：已归档到子文件夹的文件不会被二次移动
- **永不覆盖**：目标位置已有同名文件时自动追加 ` (1)`、` (2)`
- **完整可撤销**：每次移动追加写入 `.filetidy-history.jsonl`，`undo` 按运行批次整体回滚
- **watch 模式全量重扫**：天然规避浏览器分块下载产生的重复事件和半截文件

## 项目结构

```
src/main/java/dev/filetidy
├── Main.java              # picocli 入口
├── OrganizeCommand.java   # organize 子命令
├── UndoCommand.java       # undo 子命令
├── config/TidyConfig.java # YAML 规则绑定
├── core/                  # 规则引擎、移动执行、历史与撤销
└── watch/FolderWatcher.java
```

## Roadmap

- [ ] 按文件日期归档（`2026-08/` 月份目录）
- [ ] 按文件大小分流（大文件单独归置）
- [ ] 重复文件检测（内容哈希）
- [ ] GraalVM native image，免 JDK 运行
- [ ] 开机自启与系统托盘（Windows/macOS）

## License

MIT
