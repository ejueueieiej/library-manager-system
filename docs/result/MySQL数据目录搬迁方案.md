# MySQL 数据目录搬迁方案

> 目标：把 MySQL 的数据文件从 C 盘挪到别的地方（或只挪某几个表）。
> 结论先说：**能改，有 4 种粒度，但对你现在的数据量来说收益极小 —— 详见第五节。**

---

## 一、现状实测（你这台机器）

| 项 | 值 |
|---|---|
| MySQL 版本 | 8.0.45 |
| 数据目录 | `C:\ProgramData\MySQL\MySQL Server 8.0\Data\` |
| 配置文件 | `C:\ProgramData\MySQL\MySQL Server 8.0\my.ini` |
| **配置文件是写死的** | 服务定义里：`mysqld.exe --defaults-file="...\my.ini" MySQL80` |
| 服务名 | `MySQL80`（自动启动） |
| **服务运行账号** | **`NT AUTHORITY\NetworkService`** ★ 搬迁成败的关键 |
| 每表一个 `.ibd` | `innodb_file_per_table = ON` |
| 页大小 | 16384 字节（16 KB） |
| 表名大小写 | `lower_case_table_names = 1`（Windows 默认） |

### 数据目录里到底装了什么（约 170 MB）

| 内容 | 大小 | 说明 |
|---|---|---|
| `#innodb_redo`（redo log） | **100 MB** | `innodb_redo_log_capacity=100M`，8.0.30+ 的默认值，**固定预分配** |
| `mysql` 系统库 | 26 MB | 系统表空间 |
| `innodb_undo_001` / `_002` | 16 + 16 MB | undo 表空间 |
| `innodb_temporary` | 12 MB | 临时表空间 |
| `library_system`（**你的库**） | **0.92 MB** | 6 张表，见下 |
| binlog（72 个文件） | ~0.2 MB | 30 天过期，目前很小 |
| slow log / error log | 几十 KB | —— |

**你的 `library_system` 在整包里的占比：0.92 MB / 170 MB ≈ 0.5%。**

单表物理大小：`book` 192 KB、`notice` 160 KB、`borrow_record` 160 KB、`sys_user` 144 KB、
`notice_read` 144 KB、`book_category` 128 KB。

---

## 二、方案 A：搬**整个** datadir（最彻底）

### 步骤

```powershell
# ① 停服务（管理员 PowerShell）
net stop MySQL80

# ② 连同权限一起复制（★ 必须 /COPYALL，别用资源管理器拖拽）
robocopy "C:\ProgramData\MySQL\MySQL Server 8.0\Data" "D:\MySQLData" /E /COPYALL
```

```ini
; ③ 改 my.ini（C:\ProgramData\MySQL\MySQL Server 8.0\my.ini）
;    注意是【这个】文件 —— 服务定义里 --defaults-file 写死了它，
;    你在别处新建 my.ini 是【不生效】的（这是最常见的困惑）
datadir=D:/MySQLData
```

```powershell
# ④ 确认 NetworkService 有完全控制权（/COPYALL 通常已带上，这步是保险）
icacls "D:\MySQLData" /grant "NT AUTHORITY\NETWORK SERVICE:(OI)(CI)F" /T

# ⑤ 起服务
net start MySQL80
```

### ★★ 最大的坑

服务是以 **`NT AUTHORITY\NetworkService`** 身份运行的。
用资源管理器**拖拽复制**时，新目录的权限是从**目标盘的父目录**继承的（通常只给 Users 读），
NetworkService 拿不到写权限 → **服务起不来**，错误日志里是：

```
[ERROR] InnoDB: Can't create/write to file '...' (OS errno 13 - Permission denied)
```

`/COPYALL` 或第 ④ 步的 `icacls` 就是专门防这个的。

### 其他注意

- **旧的 `Data` 目录先别删**，等确认服务起来了、能连、数据都在，再删。
- 搬完在 MySQL 里验证：`SELECT @@datadir;` 应该显示新路径。
- `secure-file-priv="C:/ProgramData/MySQL/MySQL Server 8.0/Uploads"` 是**独立路径**，不受影响。
- **不要**把 datadir 放到网络盘 / OneDrive / 同步目录里 —— 文件锁和延迟会让 MySQL 出各种诡异问题。

---

## 三、方案 B：只搬**某几个表**（`DATA DIRECTORY`）

```sql
-- 目标目录必须【先手动建好】，MySQL 不会替你创建
ALTER TABLE notice DATA DIRECTORY = 'D:/MySQLData/notice';
```

- 前提：`innodb_file_per_table = ON` ✅（你已经是）
- 效果：该表的 `.ibd` 搬到新位置，原 datadir 里留一个 `.isl` 指针文件
- 粒度最细，但：
  - 每个表要**单独**执行一遍
  - MySQL 8.0 对分区表 / `CREATE TABLE ... LIKE` 场景有额外限制
  - 路径必须绝对路径、目录必须可写

> **对你的场景价值≈0**：你的表每张才 0.13～0.19 MB，搬走省不出任何有意义的空间。
> 这个方案适合"某张大表单独放到高速盘"的场景，不是省空间用的。

---

## 四、方案 C：Windows 目录联接（junction）—— **最省心**

不改任何 MySQL 配置，让 MySQL 以为路径没变：

```powershell
# ① 停服务
net stop MySQL80

# ② 整个目录搬走（/MOVE 搬完删源；不用 /MOVE 就手动删）
robocopy "C:\ProgramData\MySQL\MySQL Server 8.0\Data" "D:\MySQLData" /E /COPYALL /MOVE

# ③ 在原路径建一个目录联接指向新位置
mklink /J "C:\ProgramData\MySQL\MySQL Server 8.0\Data" "D:\MySQLData"

# ④ 起服务
net start MySQL80
```

**优点**

- `my.ini` 里的 `datadir` **一个字都不用改** → 不存在"改错文件不生效"的问题
- 权限不用重配（junction 对应用程序完全透明）
- 随时可以改回来（删掉 junction，把目录搬回原位）

**缺点**

- 需要管理员权限建 junction
- C 盘上留一个"空壳"目录（占 0 字节）
- 少数备份/同步工具不认 junction

---

## 五、方案 D：不搬，直接**降固定开销**（性价比最高）

| 改什么 | 怎么改 | 省多少 |
|---|---|---|
| redo log | `my.ini` 里 `innodb_redo_log_capacity=100M` → `32M`（最小 `8M`） | **~68 MB** |
| binlog | `my.ini` 加 `skip-log-bin` | 现在只有 0.2 MB，但以后不再涨（30 天过期） |
| slow log | `slow-query-log=1` → `0`（不用它排查时） | 几十 KB |

> ⚠️ redo 调太小会让 InnoDB 频繁 checkpoint，**写入性能下降**。32 MB 是安全的折中，
> 别为了省几十兆调到 8 MB。

---

## 六、实话：值得搬吗？

| 动作 | 省下 | 风险 |
|---|---|---|
| 搬 `library_system`（方案 B） | **0.92 MB** | 中（可能搬坏） |
| 搬整个 datadir（方案 A / C） | **~170 MB** | 中高（权限/服务起不来） |
| 只改 redo（方案 D） | **~68 MB** | 低（一行配置） |
| 删掉 `node_modules` 重装 | **~100 MB** | 低 |
| 清 `C:\Windows\Temp`、`C:\Program Files\MySQL` 冗余 | 未知但通常更大 | 低 |

结论：

1. **为了省空间**：先改 redo（方案 D，一行省 68 MB），比搬库划算得多。
2. **为了"数据文件跟着项目走"或方便查看**：用方案 C（junction），最不容易翻车。
3. **方案 B 单独搬 `library_system` 是最不划算的** —— 干活最多、省得最少。
4. 真正吃 C 盘的是 **MySQL 程序本体**（`C:\Program Files\MySQL`，通常 500 MB～1 GB）
   和 Windows 自己 —— 想彻底解决就得把 MySQL 整体装到 D 盘（导出数据 → 卸载 → 重装到 D → 导回）。
