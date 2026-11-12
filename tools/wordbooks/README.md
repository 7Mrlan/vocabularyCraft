# 词书数据

`build_wordbooks.py` 生成 `app/src/main/assets/wordbooks/` 下的三本词书。
**只保留词形**，不带任何释义、音标或例句——词典正文运行时抓取，不进仓库。

| 书 | 词数 | 来源与排序 |
|---|---|---|
| `core3000.txt` | 3000 | NGSL 1.2（2809 词，CC BY-SA 4.0，Browne/Culligan/Phillips），不足 3000 的部分按 COCA 词频补齐 |
| `cet2500.txt` | 2500 | CET-4 与 CET-6 大纲词表合并，按 COCA 词频取前 2500 |
| `kaoyan3000.txt` | 3000 | 考研大纲词表，按 COCA 词频取前 3000 |

跨书重复是**有意保留**的：三本书面向不同考试，同一词在另一本书里再次出现时由学习队列跳过，
而不是从词表里删掉。

## 重新生成

```bash
python3 tools/wordbooks/build_wordbooks.py
```

脚本把原始文件缓存在 `tools/wordbooks/.cache/`（已 gitignore），只把生成的词表提交进仓库。
原始文件来自：

- NGSL 1.2 stats — https://www.newgeneralservicelist.com/new-general-service-list
- CET-4 / CET-6 / 考研 / COCA 20000 — https://github.com/mahavivo/english-wordlists

NGSL 采用 CC BY-SA 4.0，应用内首页底部与 README 都要保留署名。
