# -*- coding: utf-8 -*-
"""
星河图书 · LOGO 概念稿生成器
============================

为什么用脚本而不是手写 SVG：
  星芒（4 角凹边星）在 5 个方案里出现十几次，尺寸各异。手算控制点必错，
  这里统一用 sparkle() 生成，改半径就能重算，保证每个方案的星芒形状完全一致。

产出：
  docs/brand/svg/mark-*.svg      5 个独立矢量标（fill=currentColor，可黑可白）
  docs/brand/preview.html        评审页（内联 SVG，可切深浅底 / 字标语言 / 构造网格）
  docs/brand/README.md           概念说明与落地方式

坐标系统：所有标都画在 64x64 画布上，内容留 4~8 单位安全边距。
"""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
BRAND = os.path.dirname(HERE)                     # docs/brand
ROOT = os.path.dirname(os.path.dirname(BRAND))    # 仓库根目录
SVGDIR = os.path.join(BRAND, "svg")               # 设计稿留档
APPDIR = os.path.join(ROOT, "library-web", "public", "brand")   # 前端直接引用的资产
os.makedirs(SVGDIR, exist_ok=True)


# --------------------------------------------------------------------------
# 图形工具
# --------------------------------------------------------------------------
def rrect(x, y, w, h, r):
    """圆角矩形路径（半径自动收敛，避免 r > 边长一半时画崩）"""
    r = min(r, w / 2, h / 2)
    return (
        f"M{x + r:.2f} {y:.2f} H{x + w - r:.2f} "
        f"A{r:.2f} {r:.2f} 0 0 1 {x + w:.2f} {y + r:.2f} "
        f"V{y + h - r:.2f} A{r:.2f} {r:.2f} 0 0 1 {x + w - r:.2f} {y + h:.2f} "
        f"H{x + r:.2f} A{r:.2f} {r:.2f} 0 0 1 {x:.2f} {y + h - r:.2f} "
        f"V{y + r:.2f} A{r:.2f} {r:.2f} 0 0 1 {x + r:.2f} {y:.2f} Z"
    )


def sparkle(cx, cy, r):
    """四角星芒（凹边）。控制点比例取自成熟图标集的经典比例，缩放到任意 r 都不变形。"""
    a, b = 0.05 * r, 0.50 * r   # 靠近尖角的第一控制点
    c, d = 0.20 * r, 0.25 * r   # 靠近星心的第二控制点
    return (
        f"M{cx:.2f} {cy - r:.2f} "
        f"C{cx + a:.2f} {cy - b:.2f} {cx + c:.2f} {cy - d:.2f} {cx + r:.2f} {cy:.2f} "
        f"C{cx + c:.2f} {cy + d:.2f} {cx + a:.2f} {cy + b:.2f} {cx:.2f} {cy + r:.2f} "
        f"C{cx - a:.2f} {cy + b:.2f} {cx - c:.2f} {cy + d:.2f} {cx - r:.2f} {cy:.2f} "
        f"C{cx - c:.2f} {cy - d:.2f} {cx - a:.2f} {cy - b:.2f} {cx:.2f} {cy - r:.2f} Z"
    )


def dot(cx, cy, r):
    return f"M{cx - r:.2f} {cy:.2f} a{r:.2f} {r:.2f} 0 1 0 {2 * r:.2f} 0 a{r:.2f} {r:.2f} 0 1 0 {-2 * r:.2f} 0 Z"


# --------------------------------------------------------------------------
# 共用图形：翻开的书
#   · 书脊（中线）比外侧低 7 单位 —— 打开的书摊平后，书脊是整本书的最低点
#   · 页顶向外上扬，是「翻开的书」能被一眼认出的关键特征
# --------------------------------------------------------------------------
BOOK_L = "M31 33.1 C25 28.9 18 27.1 12 26.1 L12 48.6 C18 49.7 25 51.6 31 53.8 Z"
BOOK_R = "M33 33.1 C39 28.9 46 27.1 52 26.1 L52 48.6 C46 49.7 39 51.6 33 53.8 Z"

# 同一本书等比缩到 34 宽、并在 56x56 方块内居中（黑方块方案用）
TILE_L = ("M31.15 26.18 C26.05 22.61 20.10 21.08 15.00 20.23 "
          "L15.00 39.35 C20.10 40.29 26.05 41.90 31.15 43.77 Z")
TILE_R = ("M32.85 26.18 C37.95 22.61 43.90 21.08 49.00 20.23 "
          "L49.00 39.35 C43.90 40.29 37.95 41.90 32.85 43.77 Z")


# --------------------------------------------------------------------------
# 五个方案
#   fill-rule="evenodd" 是这里的核心手法：所有「浅色」部分都不是画上去的，
#   而是从实心块里挖空的负空间。这样文件里只有一种颜色（currentColor），
#   放白底、放黑底、放深色顶栏都不会出现「白块糊在黑底上」的翻车。
# --------------------------------------------------------------------------
MARKS = [
    dict(
        key="A", slug="spine-stairs", name="书脊星阶", tag="几何 · 抽象 · 平台感",
        idea="三根书脊自左向右递升，每根顶端留一道书脊标签，上方浮起由小到大的星芒。",
        notes=[
            "16px 缩到 1px 一根书脊仍然不糊，最耐缩",
            "书脊上的标签槽是「书」的唯一线索，去掉就退化成柱状图",
            "气质中性：不像一家书店，更像一个「系统」",
        ],
        body="".join([
            # 标签槽和书脊写在同一条路径里，靠 evenodd 从实心书脊中挖空
            f'<path fill-rule="evenodd" d="{rrect(10.5, 39, 9, 16, 3)} {rrect(12.5, 43.5, 5, 2.4, 1)} '
            f'{rrect(26.5, 31, 9, 24, 3)} {rrect(28.5, 35.5, 5, 2.4, 1)} '
            f'{rrect(42.5, 23, 9, 32, 3)} {rrect(44.5, 27.5, 5, 2.4, 1)}"/>',
            f'<path d="{sparkle(15, 34.5, 2.6)}"/>',
            f'<path d="{sparkle(31, 25, 4)}"/>',
            f'<path d="{sparkle(47, 15.5, 5.6)}"/>',
        ]),
    ),
    dict(
        key="B", slug="open-book-dust", name="开卷星尘", tag="象征 · 有故事 · 偏人文",
        idea="翻开的书页向外扬起，页缘一路散作星尘——书读开了，就成了星河。",
        notes=[
            "「书」的语义最明确，一眼认得出是图书",
            "星尘在 16px 会退化成噪点，小尺寸需切简版",
            "曲线多，气质偏人文，与黑白极简的 UI 有一点点张力",
        ],
        body="".join([
            f'<path d="{BOOK_L}"/>',
            f'<path d="{BOOK_R}"/>',
            f'<path d="{sparkle(10, 21.1, 2.4)}"/>',
            f'<path d="{sparkle(8, 15.6, 1.6)}"/>',
            f'<path d="{dot(6.5, 11.1, 1.0)}"/>',
            f'<path d="{sparkle(54, 21.1, 2.4)}"/>',
            f'<path d="{sparkle(56, 15.6, 1.6)}"/>',
            f'<path d="{dot(57.5, 11.1, 1.0)}"/>',
        ]),
    ),
    dict(
        key="C", slug="tile-book", name="黑方块书页", tag="系统内嵌 · 零成本接入",
        idea="沿用侧栏现有的 28px 黑圆角方块，把白「书」字换成镂空的开卷书页。",
        notes=[
            "直接替换 .brand-mark 的内容，UI 一行结构都不用改",
            "黑底白形，天然自带「应用图标」的体质，做 favicon 也顺手",
            "方块内空间小，星的点缀只能克制到几乎没有",
        ],
        body=(f'<path fill-rule="evenodd" d="{rrect(4, 4, 56, 56, 10)} {TILE_L} {TILE_R}"/>'),
    ),
    dict(
        key="D", slug="shelf-books", name="书架星芒", tag="具象 · 最易读 · 图书馆感",
        idea="两本立着、一本向左斜靠着，顶上一颗星——「书架」是这个方案唯一要说的东西。",
        notes=[
            "斜靠的那一本是全部关键：去掉它，三根竖条立刻退化成柱状图",
            "斜书绕「底边中点」旋转后回落 1.09，保证三本书踩在同一条地平线上",
            "书缝统一留 5 单位：小于 4 时 16px 下三本书会糊成一坨",
        ],
        body="".join([
            # -0.94 / +1.25 是整体重心的居中补偿（画完之后量出来的墨迹包围盒偏了这么多）
            '<g transform="translate(-0.94 1.25)">',
            f'<path fill-rule="evenodd" d="{rrect(11, 28, 9, 26, 2)} {rrect(25, 22, 9, 32, 2)}"/>',
            # 绕底边中点旋转，再上移 1.09 抵消旋转造成的下沉
            f'<g transform="translate(0 -1.09) rotate(-14 50.5 54)"><path d="{rrect(46, 24, 9, 30, 2)}"/></g>',
            f'<path d="{sparkle(48, 13, 5.5)}"/>',
            '</g>',
        ]),
    ),
    dict(
        key="E", slug="star-bookmark", name="星形书签", tag="符号 · 最易识别 · 最省墨",
        idea="一枚实心书签，中间镂空出一颗星——星河被夹在书里，等你翻到那一页。",
        notes=[
            "整体轮廓是单一实心块，16px 依然是个干净的「书签」",
            "星芒半径压在 6/64：再大会把书签两侧的肩线啃断，整块就散了",
            "书签＝借阅／藏书，语义与「图书馆管理系统」咬得最准",
        ],
        body=(
            f'<path fill-rule="evenodd" d="'
            f'M25 8 H39 A4 4 0 0 1 43 12 V54 L32 45 L21 54 V12 A4 4 0 0 1 25 8 Z '
            f'{sparkle(32, 23, 6)}"/>'
        ),
    ),
]


# --------------------------------------------------------------------------
# 小尺寸简版（光学尺寸变体）
#   16px 下主标的星芒只有 1.4px 半径，等于一个噪点；书缝 5 单位只有 1.25px，三本会粘连。
#   所以 ≤20px 改用这一版：去掉星芒、整组放大 1.3 倍（书缝随之变成 6.5 单位 ≈ 1.6px）。
#   这是光学补偿，不是把主标缩小——两者共用同一套几何，只是取舍不同。
# --------------------------------------------------------------------------
COMPACT = dict(
    key="D", slug="shelf-books-compact", name="书架星芒 · 小尺寸简版", tag="≤20px 专用",
    idea="主标放大 1.3 倍并去掉星芒，只留下三本书的轮廓。",
    notes=["供 favicon / 16~20px 使用", "与主标共用同一套几何，只是不做细部"],
    body=(
        '<g transform="translate(-0.935 -6.545) translate(32.935 38.545) scale(1.3) '
        'translate(-32.935 -38.545)">'
        f'<path fill-rule="evenodd" d="{rrect(11, 28, 9, 26, 2)} {rrect(25, 22, 9, 32, 2)}"/>'
        f'<g transform="translate(0 -1.09) rotate(-14 50.5 54)"><path d="{rrect(46, 24, 9, 30, 2)}"/></g>'
        '</g>'
    ),
)


# --------------------------------------------------------------------------
# SVG 产物
# --------------------------------------------------------------------------
def svg_file(mark):
    body = mark["body"].replace("@@MID@@", "cut-" + mark["slug"])
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" '
        'width="64" height="64" fill="currentColor" role="img" '
        f'aria-label="{mark["name"]}">\n'
        f'  <title>{mark["name"]} · 星河图书</title>\n'
        f'  {body}\n'
        '</svg>\n'
    )


_uid = [0]


def svg_inline(mark):
    """内联版：交给外层 CSS 控制尺寸与颜色（currentColor 跟着 color 走）"""
    _uid[0] += 1
    body = mark["body"].replace("@@MID@@", f'cut-{mark["slug"]}-{_uid[0]}')
    return f'<svg class="mk" viewBox="0 0 64 64" fill="currentColor" aria-hidden="true">{body}</svg>'


FINAL = next(m for m in MARKS if m["key"] == "D")   # 定稿方案
EXTRAS = [COMPACT]                                  # 定稿配套的小尺寸简版
CONCEPTS = MARKS + EXTRAS                           # 全部方向，仅 --all 时才落盘


def write_svgs(all_concepts=False):
    """默认只产出定稿（主标 + 简版）；--all 才把被否掉的四个方向也写出来。

    被否掉的方向只是不落盘，定义仍留在本文件里：需要回看时跑一次 --all 即可，
    不用重新画一遍。
    """
    # 方案改名后会留下同名旧文件，先清掉 mark-*.svg，避免 svg/ 里出现幽灵方案
    for f in sorted(os.listdir(SVGDIR)):
        if f.startswith("mark-") and f.endswith(".svg"):
            os.remove(os.path.join(SVGDIR, f))
    marks = CONCEPTS if all_concepts else ([FINAL] + EXTRAS)
    for m in marks:
        path = os.path.join(SVGDIR, f'mark-{m["key"].lower()}-{m["slug"]}.svg')
        with open(path, "w", encoding="utf-8") as f:
            f.write(svg_file(m))
    print(f"svg -> {SVGDIR} ({len(marks)} 个)")


def write_app_assets():
    """产出前端直接引用的两个文件。前端只认这两个，其余都留在 docs/brand。"""
    os.makedirs(APPDIR, exist_ok=True)
    for name, mark in (("logo.svg", FINAL), ("favicon.svg", COMPACT)):
        with open(os.path.join(APPDIR, name), "w", encoding="utf-8") as f:
            f.write(svg_file(mark))
    print("app ->", APPDIR)


# --------------------------------------------------------------------------
# 评审页
# --------------------------------------------------------------------------
CSS = """
*{box-sizing:border-box}
html,body{margin:0}
body{
  --font:"MiSans VF","HarmonyOS Sans SC","MiSans","Source Han Sans SC","Noto Sans SC",
         "PingFang SC","Microsoft YaHei",system-ui,sans-serif;
  --mono:"JetBrains Mono","SF Mono","Cascadia Mono",Consolas,monospace;
  --ink:#000; --ink-2:#3d3d3d; --ink-3:#7a7a7a; --ink-4:#9a9a9a;
  --paper:#fff; --canvas:#f7f7f7; --subtle:#fafafa;
  --line:#ededed; --line-2:#e2e2e2; --line-hover:#c9c9c9; --gridline:rgba(0,0,0,.10);
  font-family:var(--font); font-size:13.5px; line-height:1.6; color:var(--ink);
  background:var(--canvas); -webkit-font-smoothing:antialiased;
}
body.theme-dark{
  --ink:#fff; --ink-2:#d4d4d4; --ink-3:#9a9a9a; --ink-4:#7a7a7a;
  --paper:#111; --canvas:#000; --subtle:#171717;
  --line:#242424; --line-2:#2e2e2e; --line-hover:#4a4a4a; --gridline:rgba(255,255,255,.14);
}
.mono{font-family:var(--mono);letter-spacing:0}
.wrap{max-width:1320px;margin:0 auto;padding:44px 32px 80px;display:flex;flex-direction:column;gap:26px}

/* ---------- 页头 ---------- */
.top{display:flex;gap:40px;align-items:flex-start;flex-wrap:wrap}
.top-l{flex:1 1 460px;min-width:300px}
.kicker{font-size:10.5px;color:var(--ink-4);letter-spacing:.14em}
h1{margin:8px 0 0;font-size:30px;font-weight:600;letter-spacing:-.03em;text-wrap:balance}
.lede{margin:10px 0 0;font-size:13.5px;color:var(--ink-2);max-width:62ch;text-wrap:pretty}
.tools{flex:0 0 auto;display:flex;flex-direction:column;gap:10px;padding-top:6px}
.seg{display:flex;border:1px solid var(--line-2);border-radius:5px;overflow:hidden}
.seg .lab{font-size:10.5px;color:var(--ink-4);padding:0 9px;display:flex;align-items:center;
  border-right:1px solid var(--line-2);background:var(--subtle);letter-spacing:.04em}
.seg button{font:inherit;font-size:12px;color:var(--ink-3);background:none;border:0;border-right:1px solid var(--line-2);
  padding:5px 12px;cursor:pointer;transition:background 120ms cubic-bezier(.4,0,.2,1),color 120ms}
.seg button:last-child{border-right:0}
.seg button:hover{background:var(--subtle);color:var(--ink)}
.seg button.on{background:var(--ink);color:var(--paper)}

/* ---------- 设计前提 ---------- */
.rail{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:1px;
  background:var(--line-2);border:1px solid var(--line-2);border-radius:8px;overflow:hidden}
.rail div{background:var(--paper);padding:11px 14px}
.rail .k{font-size:10.5px;color:var(--ink-4);letter-spacing:.06em}
.rail .v{font-size:12.5px;color:var(--ink-2);margin-top:2px}

/* ---------- 方案卡（已随定稿收档，样式保留给 .sz / .lock 复用） ---------- */
.mk{width:100%;height:100%;display:block}
.sz{display:flex;flex-direction:column;align-items:center;gap:7px}
.szbox{width:var(--n);height:var(--n);position:relative}
.szlab{font-size:9.5px;color:var(--ink-4)}
/* 构造网格：64 单位画布切 8×8 格，格子边长随标记尺寸走，安全区 = 四周 1/16 */
.bigmark::before{content:"";position:absolute;inset:0;opacity:0;transition:opacity 120ms;
  background-image:linear-gradient(to right,var(--gridline) 1px,transparent 1px),
                   linear-gradient(to bottom,var(--gridline) 1px,transparent 1px);
  background-size:calc(var(--n)/8) calc(var(--n)/8);
  outline:1px dashed var(--line-hover);outline-offset:calc(var(--n)/16)}
body.gridon .bigmark::before{opacity:1}
.bigmark svg{position:relative;z-index:1}
.lock{display:flex;align-items:center;gap:var(--gap,11px)}
.lock-mark{width:var(--m,30px);height:var(--m,30px);flex:none}
.lock-words{display:flex;flex-direction:column;line-height:1.22;min-width:0}
.lock-cn{font-size:var(--cn,19px);font-weight:600;letter-spacing:-.02em}
.lock-en{font-size:var(--en,9.5px);color:var(--ink-4);letter-spacing:.15em;margin-top:3px;font-family:var(--mono)}
body.word-cn .lock-en{display:none}
body.word-en .lock-cn{display:none}
body.word-en .lock-en{font-size:15px;font-weight:600;letter-spacing:.02em;color:var(--ink);margin-top:0;font-family:var(--font)}

/* ---------- 应用验证 ---------- */
.apps{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:18px;align-items:start}
.panel{background:var(--paper);border:1px solid var(--line-2);border-radius:8px;padding:16px;
  display:flex;flex-direction:column;gap:14px}
.cap{font-size:10px;color:var(--ink-4);letter-spacing:.12em}
.side{border:1px solid var(--line-2);border-radius:8px;overflow:hidden;background:var(--paper);width:100%;max-width:260px}
.side-inner{padding:18px 12px 14px;display:flex;flex-direction:column;gap:22px}
.brand{display:flex;align-items:center;gap:10px;padding:0 6px}
.brand-mark{width:28px;height:28px;flex:none;display:block}
.brand-text{display:flex;flex-direction:column;line-height:1.25;min-width:0}
.brand-name{font-size:14px;font-weight:600;letter-spacing:-.01em}
.brand-sub{font-size:10.5px;color:var(--ink-4);letter-spacing:.04em;font-family:var(--mono)}
.navs{display:flex;flex-direction:column}
.nav-label{font-size:11px;color:var(--ink-4);letter-spacing:.06em;padding:0 8px 8px}
.nav-item{display:flex;align-items:center;gap:8px;padding:7px 8px;font-size:13.5px;color:var(--ink-3);
  border-left:2px solid transparent}
.nav-item.is-active{color:var(--ink);font-weight:500;border-left-color:var(--ink)}
.soon{margin-left:auto;font-size:10.5px;color:var(--ink-4);font-family:var(--mono)}
.tabbar{display:flex;align-items:flex-end;gap:4px;background:var(--canvas);border:1px solid var(--line-2);
  border-bottom:0;border-radius:8px 8px 0 0;padding:7px 7px 0}
.tab{display:flex;align-items:center;gap:8px;background:var(--paper);border:1px solid var(--line-2);
  border-bottom:0;border-radius:6px 6px 0 0;padding:6px 10px;font-size:12px;color:var(--ink-2)}
.tab-fav{width:16px;height:16px;flex:none}
.tab-x{color:var(--ink-4);font-size:11px;margin-left:2px}
.tab-plus{color:var(--ink-4);font-size:14px;padding:0 8px 6px}
.tabpage{border:1px solid var(--line-2);border-top:0;border-radius:0 0 8px 8px;height:64px;background:var(--paper)}
.strip{background:#000;border-radius:8px;padding:24px 18px;display:flex;justify-content:space-around;
  align-items:flex-start;gap:14px;flex-wrap:wrap}
.strip .item{display:flex;flex-direction:column;align-items:center;gap:9px;color:#fff}
.strip .mk{width:40px;height:40px}
.strip .lbl{font-size:9.5px;color:#7a7a7a;font-family:var(--mono)}
.foot{border-top:1px solid var(--line-2);padding-top:18px;display:grid;
  grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:20px}
.foot h3{margin:0 0 8px;font-size:12px;font-weight:600;letter-spacing:.02em}
.foot p{margin:0;font-size:12px;color:var(--ink-3);line-height:1.75}
.foot code{font-family:var(--mono);font-size:11.5px;background:var(--subtle);border:1px solid var(--line);
  border-radius:4px;padding:1px 4px;color:var(--ink-2)}
"""

# 定稿规范段的样式（单独一段，方便和评审页主体样式分开看）
CSS_SPEC = """
.finalwrap{border:1px solid var(--ink);border-radius:8px;background:var(--paper);overflow:hidden}
.finalhead{display:flex;align-items:baseline;gap:12px;flex-wrap:wrap;padding:16px 18px;
  border-bottom:1px solid var(--line-2)}
.badge{font-size:10px;font-family:var(--mono);letter-spacing:.1em;background:var(--ink);
  color:var(--paper);border-radius:4px;padding:2px 7px}
.finalhead h2{margin:0;font-size:18px;font-weight:600;letter-spacing:-.02em}
.finalhead p{margin:0;font-size:12.5px;color:var(--ink-3);flex:1 1 320px;min-width:200px;text-wrap:pretty}
.specgrid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:1px;
  background:var(--line-2)}
.specgrid .panel{border:0;border-radius:0;background:var(--paper)}
.ladder{display:flex;align-items:flex-end;gap:16px;flex-wrap:wrap;padding:6px 0}
.clearwrap{display:flex;align-items:center;gap:22px;flex-wrap:wrap}
.clearbox{position:relative;width:128px;height:128px;flex:none;border:1px dashed var(--line-hover);
  display:flex;align-items:center;justify-content:center}
.clearbox .inner{width:64px;height:64px}
.clearnote{font-size:11.5px;color:var(--ink-3);line-height:1.8}
.clearnote b{font-weight:600;color:var(--ink)}
.clearnote code{font-family:var(--mono);font-size:11px;background:var(--subtle);
  border:1px solid var(--line);border-radius:4px;padding:1px 4px;color:var(--ink-2)}
.lockrow{display:flex;align-items:center;gap:28px;flex-wrap:wrap;padding:4px 0}
.others h3{margin:0;font-size:13px;font-weight:600;letter-spacing:.02em}
.others>*+*{margin-top:12px}
"""

JS = """
const KEY = 'xinghe-logo-prefs';
let prefs = Object.assign({theme:'light', word:'both', grid:'off'},
                          JSON.parse(localStorage.getItem(KEY) || '{}'));

function apply() {
  document.body.classList.toggle('theme-dark', prefs.theme === 'dark');
  document.body.classList.toggle('word-cn',    prefs.word === 'cn');
  document.body.classList.toggle('word-en',    prefs.word === 'en');
  document.body.classList.toggle('gridon',     prefs.grid === 'on');
  document.querySelectorAll('.seg').forEach(seg => {
    const g = seg.dataset.group;
    seg.querySelectorAll('button').forEach(b => b.classList.toggle('on', b.dataset.v === prefs[g]));
  });
  localStorage.setItem(KEY, JSON.stringify(prefs));
}

document.querySelectorAll('.seg button').forEach(btn => {
  btn.addEventListener('click', () => {
    prefs[btn.parentElement.dataset.group] = btn.dataset.v;
    apply();
  });
});
apply();
"""


def seg(group, label, options):
    btns = "".join(f'<button data-v="{v}">{t}</button>' for v, t in options)
    return f'<div class="seg" data-group="{group}"><span class="lab mono">{label}</span>{btns}</div>'


def final_section(d):
    """定稿规范段：尺寸阶梯 / 留白安全区 / 小尺寸简版 / 组合锁定"""
    ladder = "".join(
        f'<div class="sz"><div class="szbox{" bigmark" if n == 128 else ""}" style="--n:{n}px">'
        f'{svg_inline(d)}</div><span class="szlab mono">{n}</span></div>'
        for n in (128, 96, 64, 48, 32, 24, 16)
    )
    compact_ladder = "".join(
        f'<div class="sz"><div class="szbox" style="--n:{n}px">{svg_inline(COMPACT)}</div>'
        f'<span class="szlab mono">{n}</span></div>'
        for n in (64, 48, 32, 24, 16)
    )
    locks = "".join(
        f'<div class="lock" style="--m:{m}px;--cn:{cn}px;--en:{en}px;--gap:{gap}px">'
        f'<span class="lock-mark">{svg_inline(d)}</span>'
        f'<span class="lock-words"><span class="lock-cn">星河图书</span>'
        f'<span class="lock-en">GALAXY LIBRARY</span></span></div>'
        for m, cn, en, gap in ((48, 30, 14, 16), (34, 21, 10, 12), (28, 17, 8.5, 10))
    )
    return f"""  <section class="finalwrap">
    <div class="finalhead">
      <span class="badge mono">定稿</span>
      <h2>D · {d['name']}</h2>
      <p>{d['idea']} 选中理由：具象程度最高，不用解释就能认出是「书架」；整体是负空间构成，
      单色文件在深浅底之间直接通用，不需要第二套。</p>
    </div>
    <div class="specgrid">
      <div class="panel">
        <div class="cap mono">主标 · 尺寸阶梯</div>
        <div class="ladder">{ladder}</div>
        <div class="clearnote">主标最小 <b>24px</b>，再小请换简版。</div>
      </div>
      <div class="panel">
        <div class="cap mono">留白 · 安全区</div>
        <div class="clearwrap">
          <div class="clearbox"><div class="inner">{svg_inline(d)}</div></div>
          <div class="clearnote">四周留白 ≥ <b>0.5 × 标记高度</b>。<br>
          28px 的侧栏标，四周至少空 14px；<br>低于这个值就会和相邻文字黏在一起。</div>
        </div>
      </div>
      <div class="panel">
        <div class="cap mono">小尺寸简版 · ≤20px</div>
        <div class="ladder">{compact_ladder}</div>
        <div class="clearnote">去掉星芒、整组放大 <b>1.3×</b>，
        书缝从 1.25px 拉到 1.6px，16px 下三本书才分得开。</div>
      </div>
      <div class="panel">
        <div class="cap mono">组合锁定 · 中文主 + 英文小字 / 600</div>
        <div class="lockrow">{locks}</div>
        <div class="clearnote">中文 600 字重、字距 -.02em；英文用等宽体、字距 .15em，
        与现有侧栏 <code>LIBRARY · v1.0</code> 的排版语言一致。</div>
      </div>
    </div>
  </section>
"""


def build_html():
    by_key = {m["key"]: m for m in MARKS}
    d = by_key["D"]

    final = final_section(d)

    side = f"""        <div class="side"><div class="side-inner">
          <div class="brand">
            <span class="brand-mark">{svg_inline(d)}</span>
            <span class="brand-text">
              <span class="brand-name">星河图书</span>
              <span class="brand-sub">GALAXY · v1.0</span>
            </span>
          </div>
          <div class="navs">
            <div class="nav-label">管理</div>
            <div class="nav-item is-active">图书管理</div>
            <div class="nav-item">借阅记录<span class="soon">2.0</span></div>
            <div class="nav-item">用户管理<span class="soon">2.0</span></div>
          </div>
        </div></div>"""

    tab = f"""        <div class="tabbar">
          <div class="tab">
            <span class="tab-fav">{svg_inline(COMPACT)}</span>
            <span>星河图书 · 图书管理系统</span>
            <span class="tab-x">×</span>
          </div>
          <div class="tab-plus">+</div>
        </div>
        <div class="tabpage"></div>
        <div class="cap mono" style="margin-top:2px">标签页用的是简版（16px 下星芒会糊成一个点）</div>"""

    strip_items = "".join(
        f'<div class="item"><span class="mk">{svg_inline(m)}</span>'
        f'<span class="lbl">{lab}</span></div>'
        for m, lab in ((d, "主标 40"), (d, "主标 24"), (COMPACT, "简版 16"))
    )

    return f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>星河图书 · LOGO 定稿与规范</title>
<style>{CSS}{CSS_SPEC}</style>
</head>
<body>
<div class="wrap">

  <header class="top">
    <div class="top-l">
      <div class="kicker mono">LIBRARY MANAGER SYSTEM · BRAND IDENTITY</div>
      <h1>星河图书 · LOGO 定稿</h1>
      <p class="lede">方向已定为 <b>D 书架星芒</b>，纯黑白、无品牌色，与项目现有的 B 版设计系统
      （灰阶 / 1px 发丝线 / 无阴影 / 圆角 4·5·8）严格一致。下面是尺寸阶梯、留白规则、
      小尺寸简版，以及它在侧栏、标签页、深色底上的实际表现。</p>
    </div>
    <div class="tools">
      {seg('theme', '底色', [('light', '浅底'), ('dark', '深底')])}
      {seg('word', '字标', [('cn', '中文'), ('both', '中英'), ('en', '英文')])}
      {seg('grid', '网格', [('off', '关'), ('on', '开')])}
    </div>
  </header>

  <section class="rail">
    <div><div class="k mono">COLOR</div><div class="v">纯黑白灰，无品牌色</div></div>
    <div><div class="k mono">MIN SIZE</div><div class="v">主标 24px / 简版 16px</div></div>
    <div><div class="k mono">CLEAR SPACE</div><div class="v">四周 ≥ 0.5 × 标记高度</div></div>
    <div><div class="k mono">TECHNIQUE</div><div class="v">负空间镂空，单色文件</div></div>
  </section>

{final}
  <section class="apps">
    <div class="panel">
      <div class="cap mono">应用 · 侧栏 BRAND（28px）</div>
{side}
      <div class="cap mono" style="margin-top:-2px">把现有 .brand-mark 的黑方块换成这个标，
      文字改「星河图书」即可，结构不动</div>
    </div>
    <div class="panel">
      <div class="cap mono">应用 · 浏览器标签 / FAVICON（16px）</div>
{tab}
    </div>
    <div class="panel">
      <div class="cap mono">应用 · 深色底反白</div>
      <div class="strip">{strip_items}</div>
      <div class="cap mono" style="margin-top:-2px">同一份文件换 color 就变白，不需要第二套资源</div>
    </div>
  </section>

  <footer class="foot">
    <div>
      <h3>文件位置</h3>
      <p>矢量源文件在 <code>docs/brand/svg/</code>，坐标写在
      <code>tools/gen_logo.py</code> 里，改完重跑脚本，不要手改 SVG——
      星芒控制点与居中补偿都是算出来的。</p>
    </div>
    <div>
      <h3>落地方式</h3>
      <p>把 <code>mark-d-shelf-books.svg</code> 与 <code>mark-d-shelf-books-compact.svg</code>
      复制到 <code>library-web/public/brand/</code>；<code>index.html</code> 加
      <code>&lt;link rel="icon" href="/brand/favicon.svg"&gt;</code>；
      侧栏 <code>MainLayout.vue</code> 的 <code>.brand-mark</code> 换成该 SVG。</p>
    </div>
    <div>
      <h3>还差一步</h3>
      <p>网页里的字标用真实 HTML 文字渲染（更清晰、可被搜索），所以不需要转曲；
      但若要做印刷 / 对外物料，需要按最终字体把「星河图书」描边转曲，
      再产出一份独立的 lockup SVG。</p>
    </div>
  </footer>

</div>
<script>{JS}</script>
</body>
</html>
"""


def build_readme():
    lines = [
        "# 星河图书 · LOGO 定稿与规范",
        "",
        "**定稿：D 书架星芒**（纯黑白 / 中文主 + 英文小字 / 600 字重）。",
        "",
        "所有方案都遵守 `library-web/src/styles/theme.css` 里 B 版「黑白强对比」的约束：",
        "纯灰阶、零品牌色、1px 发丝线、无阴影、圆角 4/5/8。",
        "",
        "## 怎么看",
        "",
        "浏览器打开 `docs/brand/preview.html`。右上角三个开关：",
        "",
        "- **底色**：浅底 / 深底，验证标在两种背景下的表现",
        "- **字标**：中文 / 中英 / 英文，看三种字标组合的节奏",
        "- **网格**：打开后显示 64×64 构造网格与安全区虚线",
        "",
        "选择会记在 localStorage，刷新不丢。",
        "",
        "## 使用规则",
        "",
        "| 项 | 值 |",
        "|---|---|",
        "| 主标最小尺寸 | 24px |",
        "| 简版最小尺寸 | 16px（favicon） |",
        "| 四周留白 | ≥ 0.5 × 标记高度（28px 标 = 至少 14px） |",
        "| 颜色 | 单色 `currentColor`，深浅底同一份文件 |",
        "| 字标 | 中文 600 / 字距 -.02em；英文等宽 / 字距 .15em |",
        "",
        "**什么时候用简版**：标记小于 20px 时。主标的星芒在 16px 下只有约 1.4px 半径，",
        "等于一个噪点，书缝也会糊到 1.25px；简版去掉星芒并把整组放大 1.3 倍，书缝回到 1.6px。",
        "",
        "## 为什么是「书架」",
        "",
        "被否掉的方向（抽象书脊柱、开卷散星尘、黑方块书页、镂空星形书签）都不差，",
        "但它们要么在 16px 下散架，要么需要靠文字补语义。书架是唯一一个**不用解释**的：",
        "具象程度最高、单色文件最稳、负空间构成在深浅底之间直接通用。",
        "需要回看那几个方向，跑 `python tools/gen_logo.py --all` 会把它们一并生成。",
        "",
        "## 文件",
        "",
        "```",
        "docs/brand/",
        "├─ preview.html                          定稿规范页（内联 SVG，可切深浅底 / 开构造网格）",
        "├─ README.md                             本文件",
        "├─ svg/",
        f"│  ├─ mark-d-{FINAL['slug']}.svg          定稿主标（设计稿留档）",
        "│  └─ mark-d-shelf-books-compact.svg     小尺寸简版",
        "└─ tools/",
        "   ├─ gen_logo.py                        生成器：改坐标后重跑，勿手改 SVG",
        "   └─ check_output.py                    交付自检：XML 良构 + 结构完整性",
        "",
        "library-web/public/brand/                ← 前端真正引用的位置（由生成器同步）",
        "├─ logo.svg                               侧栏品牌标（= mark-d-shelf-books.svg）",
        "└─ favicon.svg                            标签页图标（= 简版）",
        "```",
        "",
        "## 技术要点",
        "",
        "- 所有标画在 64×64 画布上，内容留 4~8 单位安全边距",
        "- 「浅色」部分一律用 `fill-rule=\"evenodd\"` 从实心块里挖空，不是另外画一个浅色块。",
        "  因此文件里只有一种颜色（`currentColor`），放黑底、白底、深色顶栏都不会出现白块糊边。",
        "- 星芒用一个参数化的四角凹边星函数生成，所有方案里的星形状完全一致。",
        "- 斜靠的那本书绕「底边中点」旋转后再上移 1.09，保证三本书踩在同一条地平线上；",
        "  整组再平移 (-0.94, +1.25) 做重心居中。这两个数是量出来的，不是估的。",
        "",
        "## 前端接入（已完成）",
        "",
        "| 文件 | 改动 |",
        "|---|---|",
        "| `library-web/public/brand/logo.svg` | 新增，侧栏品牌标 |",
        "| `library-web/public/brand/favicon.svg` | 新增，标签页图标 |",
        "| `library-web/index.html` | `<title>` 改「星河图书 · 图书管理系统」+ 加 favicon link |",
        "| `MainLayout.vue` | `.brand-mark` 的「书」字换成 `<img src=\"/brand/logo.svg\">`；"
        "`.brand-name` 改「星河图书」；`.brand-mark` 去掉黑底（logo 自带形状） |",
        "",
        "改 logo 的正确姿势：改 `tools/gen_logo.py` 里的坐标 → 重跑脚本（会同时刷新 "
        "`docs/brand/svg/` 与 `library-web/public/brand/`）→ 前端自动生效。",
        "",
        "网页里的字标用真实 HTML 文字渲染（清晰、可选中、可搜索），无需转曲；",
        "印刷 / 对外物料才需要按最终字体描边转曲，另出 lockup SVG。",
        "",
    ]
    return "\n".join(lines)


if __name__ == "__main__":
    import sys

    all_concepts = "--all" in sys.argv
    write_svgs(all_concepts=all_concepts)
    write_app_assets()
    with open(os.path.join(BRAND, "preview.html"), "w", encoding="utf-8") as f:
        f.write(build_html())
    with open(os.path.join(BRAND, "README.md"), "w", encoding="utf-8") as f:
        f.write(build_readme())
    print("html ->", os.path.join(BRAND, "preview.html"))
