# 星河图书 · LOGO 定稿与规范

**定稿：D 书架星芒**（纯黑白 / 中文主 + 英文小字 / 600 字重）。

所有方案都遵守 `library-web/src/styles/theme.css` 里 B 版「黑白强对比」的约束：
纯灰阶、零品牌色、1px 发丝线、无阴影、圆角 4/5/8。

## 怎么看

浏览器打开 `docs/brand/preview.html`。右上角三个开关：

- **底色**：浅底 / 深底，验证标在两种背景下的表现
- **字标**：中文 / 中英 / 英文，看三种字标组合的节奏
- **网格**：打开后显示 64×64 构造网格与安全区虚线

选择会记在 localStorage，刷新不丢。

## 使用规则

| 项 | 值 |
|---|---|
| 主标最小尺寸 | 24px |
| 简版最小尺寸 | 16px（favicon） |
| 四周留白 | ≥ 0.5 × 标记高度（28px 标 = 至少 14px） |
| 颜色 | 单色 `currentColor`，深浅底同一份文件 |
| 字标 | 中文 600 / 字距 -.02em；英文等宽 / 字距 .15em |

**什么时候用简版**：标记小于 20px 时。主标的星芒在 16px 下只有约 1.4px 半径，
等于一个噪点，书缝也会糊到 1.25px；简版去掉星芒并把整组放大 1.3 倍，书缝回到 1.6px。

## 为什么是「书架」

被否掉的方向（抽象书脊柱、开卷散星尘、黑方块书页、镂空星形书签）都不差，
但它们要么在 16px 下散架，要么需要靠文字补语义。书架是唯一一个**不用解释**的：
具象程度最高、单色文件最稳、负空间构成在深浅底之间直接通用。
需要回看那几个方向，跑 `python tools/gen_logo.py --all` 会把它们一并生成。

## 文件

```
docs/brand/
├─ preview.html                          定稿规范页（内联 SVG，可切深浅底 / 开构造网格）
├─ README.md                             本文件
├─ svg/
│  ├─ mark-d-shelf-books.svg          定稿主标（设计稿留档）
│  └─ mark-d-shelf-books-compact.svg     小尺寸简版
└─ tools/
   ├─ gen_logo.py                        生成器：改坐标后重跑，勿手改 SVG
   └─ check_output.py                    交付自检：XML 良构 + 结构完整性

library-web/public/brand/                ← 前端真正引用的位置（由生成器同步）
├─ logo.svg                               侧栏品牌标（= mark-d-shelf-books.svg）
└─ favicon.svg                            标签页图标（= 简版）
```

## 技术要点

- 所有标画在 64×64 画布上，内容留 4~8 单位安全边距
- 「浅色」部分一律用 `fill-rule="evenodd"` 从实心块里挖空，不是另外画一个浅色块。
  因此文件里只有一种颜色（`currentColor`），放黑底、白底、深色顶栏都不会出现白块糊边。
- 星芒用一个参数化的四角凹边星函数生成，所有方案里的星形状完全一致。
- 斜靠的那本书绕「底边中点」旋转后再上移 1.09，保证三本书踩在同一条地平线上；
  整组再平移 (-0.94, +1.25) 做重心居中。这两个数是量出来的，不是估的。

## 前端接入（已完成）

| 文件 | 改动 |
|---|---|
| `library-web/public/brand/logo.svg` | 新增，侧栏品牌标 |
| `library-web/public/brand/favicon.svg` | 新增，标签页图标 |
| `library-web/index.html` | `<title>` 改「星河图书 · 图书管理系统」+ 加 favicon link |
| `MainLayout.vue` | `.brand-mark` 的「书」字换成 `<img src="/brand/logo.svg">`；`.brand-name` 改「星河图书」；`.brand-mark` 去掉黑底（logo 自带形状） |

改 logo 的正确姿势：改 `tools/gen_logo.py` 里的坐标 → 重跑脚本（会同时刷新 `docs/brand/svg/` 与 `library-web/public/brand/`）→ 前端自动生效。

网页里的字标用真实 HTML 文字渲染（清晰、可选中、可搜索），无需转曲；
印刷 / 对外物料才需要按最终字体描边转曲，另出 lockup SVG。
