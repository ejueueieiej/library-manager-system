# -*- coding: utf-8 -*-
"""交付自检：几何产物是否良构 + 前端是否真的接上了。

重点不是「文件存在」，而是**两边是否一致**：
  docs/brand/svg/mark-d-shelf-books.svg        ==  library-web/public/brand/logo.svg
  docs/brand/svg/mark-d-shelf-books-compact.svg ==  library-web/public/brand/favicon.svg
不一致 = 有人手改了其中一边，前端显示的和设计稿不是同一个东西。
"""
import os
import re
from lxml import etree

HERE = os.path.dirname(os.path.abspath(__file__))
BRAND = os.path.dirname(HERE)
ROOT = os.path.dirname(os.path.dirname(BRAND))
SVGDIR = os.path.join(BRAND, "svg")
PREVIEW = os.path.join(BRAND, "preview.html")
APPDIR = os.path.join(ROOT, "library-web", "public", "brand")
INDEX = os.path.join(ROOT, "library-web", "index.html")
LAYOUT = os.path.join(ROOT, "library-web", "src", "layout", "MainLayout.vue")

fails = []


def check(name, ok, detail=""):
    print(f"{'ok  ' if ok else 'FAIL'} {name}{('  ' + detail) if detail else ''}")
    if not ok:
        fails.append(name)


# 1. 设计稿 SVG 良构
svg_files = sorted(f for f in os.listdir(SVGDIR) if f.endswith(".svg"))
for f in svg_files:
    try:
        etree.parse(os.path.join(SVGDIR, f))
        check(f"svg/{f}", True)
    except Exception as e:  # noqa: BLE001
        check(f"svg/{f}", False, str(e))
check("svg/ 只有定稿两个文件（无冗余方案）",
      svg_files == ["mark-d-shelf-books-compact.svg", "mark-d-shelf-books.svg"],
      str(svg_files))

# 2. preview.html 内联 SVG 良构
html = open(PREVIEW, encoding="utf-8").read()
inline = re.findall(r'<svg class="mk".*?</svg>', html, re.S)
bad = 0
for s in inline:
    try:
        etree.fromstring(s)
    except Exception:  # noqa: BLE001
        bad += 1
check(f"preview.html 内联 SVG 良构（{len(inline)} 个）", bad == 0)
check("preview.html 标签闭合完整",
      html.count("<script") == html.count("</script>")
      and html.count("<style") == html.count("</style>")
      and html.count("<section") == html.count("</section>"))

# 3. 前端资产存在且良构
for f in ("logo.svg", "favicon.svg"):
    p = os.path.join(APPDIR, f)
    try:
        etree.parse(p)
        check(f"public/brand/{f}", True)
    except Exception as e:  # noqa: BLE001
        check(f"public/brand/{f}", False, str(e))

# 4. 设计稿与前端资产逐字节一致（防手改漂移）
pairs = [
    ("mark-d-shelf-books.svg", "logo.svg"),
    ("mark-d-shelf-books-compact.svg", "favicon.svg"),
]
for src, dst in pairs:
    a = open(os.path.join(SVGDIR, src), "rb").read()
    b = open(os.path.join(APPDIR, dst), "rb").read()
    check(f"{src} == public/brand/{dst}", a == b,
          "" if a == b else "两份内容不一致，前端显示的不是设计稿")

# 5. 前端代码真的引用了它们
idx = open(INDEX, encoding="utf-8").read()
check("index.html 引用了 favicon", 'href="/brand/favicon.svg"' in idx)
lay = open(LAYOUT, encoding="utf-8").read()
check("MainLayout 引用了 logo.svg", 'src="/brand/logo.svg"' in lay)
check("MainLayout 已改品牌名", "星河图书" in lay)
check("MainLayout 不再残留黑底方块样式", "place-items: center" not in lay.split(".brand-mark")[1][:400]
      if ".brand-mark" in lay else False)

print()
print("RESULT:", "ALL PASS" if not fails else f"{len(fails)} 处失败 -> {fails}")
