# 生成「亮色模式」资源（values-notnight / drawable-notnight 等），并在标准输出打印 Kotlin 亮色色表片段
# （贴进 ColorScheme.LIGHT_PALETTES）。用法：在仓库根目录 `python tools/appearance/gen_light_mode.py`。
#
# 原则：values/ 与 drawable/ 下的现有资源 = 暗色（一字不动）；亮色只放进 -notnight 覆盖。
# 新增浅色图标后重跑本脚本即可生成它的亮色版（不想变色的加进 EXCLUDE）。
import os, re

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "main", "res") + os.sep
os.makedirs(ROOT + "values-notnight", exist_ok=True)
os.makedirs(ROOT + "drawable-notnight", exist_ok=True)

# ---------------------------------------------------------------- 亮色色表（XML 与 Kotlin 同源）
# 字段：primary deep plight secondary | t1 t2 t3 | ripple link info share [success gold]
# bg / surface / card / cardA / card2 / border / divider 不在这里写死，由下方「米白基底」统一计算。
# plight = 弹窗按钮等「浅底上的强调文字」用色，对卡片底 ≥ 3:1（原各主题的浅强调色在白底上只有 2:1 左右）。
L = {
    "pink": dict(primary=0xFFFF6699, deep=0xFFE84B85, plight=0xFFE84B85, secondary=0xFFFFB3CA,
                 t1=0xFF2B2026, t2=0xFF6B5862, t3=0xFF9A8892,
                 ripple=0x30FF6699, link=0xFF1A8FD8, info=0xFF1890FF, share=0xFFE84B85),
    "zhihu": dict(primary=0xFF056DE8, deep=0xFF0354B5, plight=0xFF2F7FE6, secondary=0xFF6DB0FF,
                  t1=0xFF15202E, t2=0xFF4F5F73, t3=0xFF8090A3,
                  ripple=0x30056DE8, link=0xFF0A63C9, info=0xFF056DE8, share=0xFF0354B5),
    # 爱奇艺绿在浅底上对比度只有 1.9:1，亮色模式下主色整体压深一档
    "iqiyi": dict(primary=0xFF00A84E, deep=0xFF008A3F, plight=0xFF00873E, secondary=0xFF33D47A,
                  t1=0xFF13241A, t2=0xFF4A6656, t3=0xFF7C9686,
                  ripple=0x3000A84E, link=0xFF008A3F, info=0xFF00A84E, share=0xFF008A3F),
    "purple_fantasy": dict(primary=0xFF7B2CBF, deep=0xFF5A1F8F, plight=0xFF8A45C8, secondary=0xFFBA7FE8,
                           t1=0xFF231A2E, t2=0xFF5D4D70, t3=0xFF8E80A0,
                           ripple=0x307B2CBF, link=0xFF6A22AD, info=0xFF7B2CBF, share=0xFF5A1F8F),
    # 五彩斑斓：强调色黄 #FFE66D 在浅底上看不见（弹窗按钮文字就用它），换成深琥珀
    "rainbow": dict(primary=0xFFF25C5C, deep=0xFFC94444, plight=0xFFB87D00, secondary=0xFFFF8E8E,
                    t1=0xFF2D1D1D, t2=0xFF6D5454, t3=0xFFA08787,
                    ripple=0x30F25C5C, link=0xFF1F8FB0, info=0xFFF25C5C, share=0xFFE07B1F, gold=0xFFE6A800),
    "classic_gray": dict(primary=0xFF6E6EE6, deep=0xFF5555C8, plight=0xFF6464DC, secondary=0xFFA3A3FD,
                         t1=0xFF1E1E2A, t2=0xFF595968, t3=0xFF8A8A9A,
                         ripple=0x306E6EE6, link=0xFF1E88C8, info=0xFF1E88C8, share=0xFF1E88C8),
    # 经典终端·亮：「纸质终端」——旧纸底 + 半透明米白卡，延续暗色版「半透明卡片」的骨架；
    # 分隔线 / 描边沿用暗色版的思路，用半透明黑叠在纸上
    "terminal": dict(primary=0xFFFF6699, deep=0xFFE84B85, plight=0xFFD9437A, secondary=0xFFFFB3CA,
                     t1=0xFF2A2326, t2=0xFF6B6266, t3=0xFF958B90, border=0x33000000, divider=0x26000000,
                     ripple=0x26000000, link=0xFF1E88C8, info=0xFF1E88C8, share=0xFF1E88C8, success=0xFF2E9E44),
}

# ---------------------------------------------------------------- 米白基底
# 亮色的背景 / 表面 / 卡片统一用「米白」：暖调浅色（CIELAB b* ≈ +4～+7），不是纯白。
# 与暗色对称：暗色 6 套主题共用 #1B1B24 / #24242E / #2A2A35，亮色 6 套主题也共用同一套米白，
# 只按主题色相微偏一点（偏完仍是米白，主题感主要靠主色、光晕、玻璃质感、正文色）；
# 经典终端是更深、更暖一档的「旧纸」（对应暗色版比其它主题更深的纯黑）。
# 想整体调暖 / 调冷：改 CREAM 的 b*；想加强 / 减弱主题偏色：改 TINT。
CREAM = {  # 层级 → (L*, a*, b*)
    "bg": (92.5, 0.8, 6.5), "surface": (95.3, 0.6, 5.5), "card": (97.6, 0.4, 4.2),
    "card2": (94.2, 0.7, 6.0), "border": (88.0, 1.0, 7.0), "divider": (90.5, 0.9, 6.5),
}
TINT_SCALE = {"bg": 1.0, "surface": 0.6, "card": 0.35, "card2": 0.6, "border": 0.8, "divider": 0.8}
TINT = {  # 主题 → 在背景层上的 (Δa*, Δb*)；越往上的层（卡片）偏得越少
    "pink": (2.2, -1.0), "zhihu": (-0.6, -2.2), "iqiyi": (-2.2, 0.6),
    "purple_fantasy": (1.6, -2.2), "rainbow": (1.6, 1.6), "classic_gray": (0.6, -2.0),
}
OLD_PAPER = {"bg": (89.5, 1.4, 9.5), "surface": (94.0, 1.0, 7.5), "card": (97.2, 0.6, 5.5), "card2": (95.0, 0.9, 7.0)}


def lab_to_rgb(L_, a, b):
    """CIELAB（D65）→ sRGB 0xRRGGBB。"""
    fy = (L_ + 16) / 116
    fx, fz = fy + a / 500, fy - b / 200

    def finv(t):
        return t ** 3 if t ** 3 > 0.008856 else (t - 16 / 116) / 7.787

    X, Y, Z = 0.95047 * finv(fx), finv(fy), 1.08883 * finv(fz)
    lin = (3.2406 * X - 1.5372 * Y - 0.4986 * Z,
           -0.9689 * X + 1.8758 * Y + 0.0415 * Z,
           0.0557 * X - 0.2040 * Y + 1.0570 * Z)

    def gam(v):
        v = max(0.0, min(1.0, v))
        v = 12.92 * v if v <= 0.0031308 else 1.055 * v ** (1 / 2.4) - 0.055
        return round(v * 255)

    r, g, b_ = (gam(v) for v in lin)
    return (r << 16) | (g << 8) | b_


for k, p in L.items():
    if k == "terminal":
        for layer, lab in OLD_PAPER.items():
            p[layer] = 0xFF000000 | lab_to_rgb(*lab)
        p["card"] = 0xCC000000 | (p["card"] & 0xFFFFFF)   # 半透明卡片（暗色版 #CC262626 的对应）
        p["cardA"] = p["card"]
    else:
        da, db = TINT[k]
        for layer, (l_, a, b) in CREAM.items():
            s_ = TINT_SCALE[layer]
            p[layer] = 0xFF000000 | lab_to_rgb(l_, a + da * s_, b + db * s_)
        p["cardA"] = 0xE6000000 | (p["card"] & 0xFFFFFF)

# 所有主题共用的浅色件（设置开关行底、搜索历史下拉、进度条槽）用不偏色的米白
NEUTRAL_CARD = lab_to_rgb(*CREAM["card"])
SURFACE_DARKER = 0xFF000000 | lab_to_rgb(*CREAM["border"])

def c(v):
    return "#%08X" % v

# ---------------------------------------------------------------- values-notnight/colors_light.xml
out = ['<?xml version="1.0" encoding="utf-8"?>',
       '<!-- 亮色模式（uiMode 非夜间）下的颜色覆盖。values/ 里的同名颜色 = 暗色，本文件只放要变的。',
       '     名字里的 _dark 是历史命名（原只有暗色），这里刻意沿用，好让 themes.xml / drawable 一行不改就切换。',
       '     ⚠️ 与 ColorScheme.kt 的 LIGHT 色表必须同步（同一份数据由 tools/appearance/gen_light_mode.py 生成）。',
       '     ⚠️ 不要在这里覆盖 terminal_bgblack / bgblack：冷启动窗口由系统按系统 uiMode 绘制，覆盖会让暗色用户也闪白屏。 -->',
       '<resources>']

def fam(prefix, p, on_surface, extra=()):
    pre = prefix + "_" if prefix else ""
    key = prefix or "pink"
    rows = [
        # 白底上的强调文字（弹窗按钮）/ 主色按钮上的文字 / 开关「关」态滑块
        ((prefix or "pink") + "_accent", p["plight"]), (key + "_btn_text", 0xFFFFFFFF), (key + "_switch_thumb", p["t2"]),
        (pre + "background_dark", p["bg"]), (pre + "surface_dark", p["surface"]),
        (pre + "card_dark", p["card"]), (pre + "card_dark_secondary", p["card2"]),
        (pre + "text_primary_dark", p["t1"]), (pre + "text_secondary_dark", p["t2"]),
        (pre + "text_tertiary_dark", p["t3"]), (pre + "divider_dark", p["divider"]),
        (on_surface, p["t1"]),
    ]
    return rows + list(extra)

blocks = [
    ("B站粉", fam("", L["pink"], "on_surface_bili")),
    ("知乎蓝", fam("zhihu", L["zhihu"], "on_surface_zhihu")),
    ("爱奇艺绿（主色压深，白底对比度）", fam("iqiyi", L["iqiyi"], "on_surface_iqiyi", [
        ("iqiyi_brand", L["iqiyi"]["primary"]), ("iqiyi_deep", L["iqiyi"]["deep"]),
        ("iqiyi_light", L["iqiyi"]["secondary"])])),
    ("紫色空灵", fam("purple_fantasy", L["purple_fantasy"], "on_surface_purple")),
    ("五彩斑斓（黄色强调色换琥珀）", fam("rainbow", L["rainbow"], "on_surface_rainbow", [
        ("rainbow_brand", L["rainbow"]["primary"]), ("rainbow_deep", L["rainbow"]["deep"])])),
    ("经典灰（主色压深）", fam("classic_gray", L["classic_gray"], "on_surface_classic_gray", [
        ("classic_gray_brand", L["classic_gray"]["primary"]), ("classic_gray_deep", L["classic_gray"]["deep"]),
        ("classic_gray_light", L["classic_gray"]["secondary"])])),
    ("经典终端·纸质终端", [
        ("terminal_window_bg", L["terminal"]["bg"]), ("terminal_textwhite", L["terminal"]["t1"]),
        ("terminal_text_primary", L["terminal"]["t1"]), ("terminal_text_secondary", L["terminal"]["t2"]),
        ("terminal_text_tertiary", L["terminal"]["t3"]), ("terminal_card_bg", L["terminal"]["card"]),
        ("terminal_surface", L["terminal"]["surface"]), ("terminal_color_ripple", L["terminal"]["ripple"]),
        ("terminal_text_transparent", 0x61000000), ("terminal_divider", L["terminal"]["divider"]),
        ("terminal_link", L["terminal"]["link"]), ("on_surface_terminal", L["terminal"]["t1"]),
        ("terminal_accent", L["terminal"]["plight"])]),
    ("通用（图标 / 分隔线 / 进度槽 / 叠加层）", [
        ("text_primary", 0xFF3A3438), ("text_secondary", 0xFF6B5F66),
        ("list_divider", 0x1F000000), ("surface_darker", SURFACE_DARKER), ("text_transparent", 0x30000000),
        ("hint_on_bg", 0x61000000), ("overlay_fill_weak", 0x0A000000),
        ("overlay_stroke_weak", 0x26000000), ("overlay_text_weak", 0x8A000000)]),
]
for title, rows in blocks:
    out.append(f"    <!-- {title} -->")
    out += [f'    <color name="{n}">{c(v)}</color>' for n, v in rows]
out.append("</resources>\n")
open(ROOT + "values-notnight/colors_light.xml", "w").write("\n".join(out))

# 暗色侧新增的语义色（原布局里的字面量）
open(ROOT + "values/colors_mode.xml", "w").write('''<?xml version="1.0" encoding="utf-8"?>
<!-- 亮 / 暗两套模式都要用到、原先写死在布局里的颜色。本文件 = 暗色取值（与原字面量完全一致），
     亮色取值在 values-notnight/colors_light.xml。 -->
<resources>
    <!-- 经典终端主题的窗口底色（原直接用 terminal_bgblack；闪屏仍用 terminal_bgblack，保持冷启动纯黑） -->
    <color name="terminal_window_bg">#000000</color>
    <!-- 深色底上的输入提示文字（原 #80ffffff） -->
    <color name="hint_on_bg">#80FFFFFF</color>
    <!-- 投票卡片：底 / 描边 / 次要文字（原 #1AFFFFFF / #33FFFFFF / #88FFFFFF） -->
    <color name="overlay_fill_weak">#1AFFFFFF</color>
    <color name="overlay_stroke_weak">#33FFFFFF</color>
    <color name="overlay_text_weak">#88FFFFFF</color>
    <!-- 主色按钮上的文字、开关「关」态滑块：暗色下就是原来的正文色（别名，观感不变）；
         亮色下正文色变深，按钮文字改白、滑块改中灰，见 values-notnight/colors_light.xml -->
    <color name="pink_btn_text">@color/text_primary_dark</color>
    <color name="zhihu_btn_text">@color/zhihu_text_primary_dark</color>
    <color name="iqiyi_btn_text">@color/iqiyi_text_primary_dark</color>
    <color name="purple_fantasy_btn_text">@color/purple_fantasy_text_primary_dark</color>
    <color name="rainbow_btn_text">@color/rainbow_text_primary_dark</color>
    <color name="classic_gray_btn_text">@color/classic_gray_text_primary_dark</color>
    <color name="pink_switch_thumb">@color/text_primary_dark</color>
    <color name="zhihu_switch_thumb">@color/zhihu_text_primary_dark</color>
    <color name="iqiyi_switch_thumb">@color/iqiyi_text_primary_dark</color>
    <color name="purple_fantasy_switch_thumb">@color/purple_fantasy_text_primary_dark</color>
    <color name="rainbow_switch_thumb">@color/rainbow_text_primary_dark</color>
    <color name="classic_gray_switch_thumb">@color/classic_gray_text_primary_dark</color>
    <!-- 压在视频 / 图片上的图标与文字：两种模式都必须是浅色（不进 notnight 覆盖） -->
    <color name="overlay_icon">#EBE0E2</color>
    <color name="overlay_text">#FFFFFF</color>
</resources>
''')

# ---------------------------------------------------------------- 主题父类按模式切换
open(ROOT + "values/themes_base.xml", "w").write('''<?xml version="1.0" encoding="utf-8"?>
<!-- 7 套主题的公共父类：暗色 = Material 暗色基底（与改动前完全一致）。
     亮色模式下由 values-notnight/themes_base.xml 换成 Material 亮色基底，
     于是 textColorPrimary / colorOnSurface 等未在主题里显式设置的属性也跟着变深。 -->
<resources>
    <style name="Base.Theme.BiliTerminal" parent="Theme.MaterialComponents.NoActionBar" />
</resources>
''')
open(ROOT + "values-notnight/themes_base.xml", "w").write('''<?xml version="1.0" encoding="utf-8"?>
<!-- 亮色模式的主题父类，见 values/themes_base.xml。 -->
<resources>
    <style name="Base.Theme.BiliTerminal" parent="Theme.MaterialComponents.Light.NoActionBar" />
</resources>
''')

# ---------------------------------------------------------------- 玻璃质感·亮色
def mix(a, b, t):
    ca = [(a >> s) & 255 for s in (16, 8, 0)]
    cb = [(b >> s) & 255 for s in (16, 8, 0)]
    return sum(round(x + (y - x) * t) << s for x, y, s in zip(ca, cb, (16, 8, 0)))

g = ['<?xml version="1.0" encoding="utf-8"?>',
     '<!-- 亮色模式的「玻璃质感」：以米白卡片色为底的磨砂（72% 不透明、向主色偏 4%）+ 主色亮边；按钮 = 主色 14% 淡底 + 主色 60% 描边。 -->',
     '<resources>',
     '    <color name="glass_btn_bg_disabled">#0F000000</color>',
     '    <color name="glass_btn_secondary">#0F000000</color>']
for k in ["pink", "zhihu", "iqiyi", "purple_fantasy", "rainbow", "classic_gray", "terminal"]:
    p = L[k]; rgb = p["primary"] & 0xFFFFFF
    g += [f'    <color name="glass_card_{k}">#B8{mix(p["card"] & 0xFFFFFF, rgb, 0.04):06X}</color>',
          f'    <color name="glass_stroke_{k}">#40{rgb:06X}</color>',
          f'    <color name="glass_ripple_{k}">#26{rgb:06X}</color>',
          f'    <color name="glass_btn_bg_fill_{k}">#24{rgb:06X}</color>',
          f'    <color name="glass_btn_stroke_{k}">#99{rgb:06X}</color>',
          f'    <color name="glass_btn_text_{k}">{c(p["t1"])}</color>']
g.append("</resources>\n")
open(ROOT + "values-notnight/colors_glass.xml", "w").write("\n".join(g))

# ---------------------------------------------------------------- drawable-notnight：图标改深色
ICON_DARK = "#3A3438"
EXCLUDE = {  # 压在主色 / 视频 / 通知上的白图标，以及靠颜色覆盖就能变色的 8 个 v21 着色图标
    "send", "article_fav_cover", "btn_player_play", "btn_player_pause", "player_tv_white", "icon_debug",
    "icon_audio_only_off", "icon_auto_next_off", "icon_player_lock", "icon_player_unlock", "ic_episode_playing",
    "icon_announcement", "icon_download", "icon_home", "icon_nav_dynamic", "icon_nav_media",
    "icon_nav_trending", "icon_update", "icon_sort_filter",
}
LIGHT_RE = re.compile(r'((?:fillColor|strokeColor|tint)=")(#(?:[0-9a-fA-F]{2})?(?:ebe0e2|EBE0E2|fff|FFF|ffffff|FFFFFF)|@android:color/white)(")')
made, multi = [], []
for f in sorted(os.listdir(ROOT + "drawable")):
    name = f[:-4]
    # *_overlay：压在视频 / 图片上的固定浅色版本，两种模式都不能变
    if not f.endswith(".xml") or name in EXCLUDE or name.endswith("_overlay"):
        continue
    s = open(ROOT + "drawable/" + f).read()
    if not LIGHT_RE.search(s):
        continue
    def rep(m):
        v = m.group(2)
        alpha = v[1:3] if v.startswith("#") and len(v) == 9 else ""
        return m.group(1) + "#" + alpha + ICON_DARK[1:] + m.group(3)
    s2 = LIGHT_RE.sub(rep, s)
    others = set(re.findall(r'(?:fillColor|strokeColor|tint)="([^"]+)"', s2)) - {ICON_DARK, "#dd3A3438", "#DD3A3438"}
    if others:
        multi.append((name, others))
    s2 = s2.replace('<?xml version="1.0" encoding="utf-8"?>', '<?xml version="1.0" encoding="utf-8"?>\n<!-- 亮色模式版本（tools/appearance/gen_light_mode.py 生成）：浅色填充 → 深色 ' + ICON_DARK + '。改图标时 drawable/ 与本目录两份一起改。 -->', 1)
    if not s2.startswith("<?xml"):
        s2 = '<!-- 亮色模式版本（tools/appearance/gen_light_mode.py 生成）：浅色填充 → 深色 ' + ICON_DARK + '。改图标时 drawable/ 与本目录两份一起改。 -->\n' + s2
    open(ROOT + "drawable-notnight/" + f, "w").write(s2)
    made.append(name)

# API 21+：亮色图标再按主题正文色着色（?android:attr/colorForeground = 各主题 text_primary），
# 于是知乎蓝的图标偏冷、B站粉的偏暖，与正文同色；API 19/20 不支持 drawable 里的 ?attr，回落上面的中性深灰。
# 与仓库里 drawable-v21 那 7 个图标是同一套做法（见 docs/architecture-map.md 的 API 兼容表）。
os.makedirs(ROOT + "drawable-notnight-v21", exist_ok=True)
for f in os.listdir(ROOT + "drawable-notnight-v21"):
    os.remove(ROOT + "drawable-notnight-v21/" + f)
for name in made:
    s3 = open(ROOT + "drawable-notnight/" + name + ".xml").read()
    s3 = re.sub(r'\s+android:tint="[^"]*"', "", s3)
    # 插在 xmlns 声明之后；个别图标 xmlns 不在第一位，就插在 <vector 之后（属性顺序对 aapt 无影响）
    if re.search(r'<vector\s+xmlns:android="[^"]+"', s3):
        s3 = re.sub(r'(<vector\s+xmlns:android="[^"]+")', r'\1\n    android:tint="?android:attr/colorForeground"', s3, count=1)
    else:
        s3 = re.sub(r'(<vector)(\s)', r'\1 android:tint="?android:attr/colorForeground"\2', s3, count=1)
    assert 'android:tint="?android:attr/colorForeground"' in s3, name
    s3 = s3.replace("浅色填充 → 深色 " + ICON_DARK + "。", "API 21+ 版：按主题正文色着色（?android:attr/colorForeground）。")
    open(ROOT + "drawable-notnight-v21/" + name + ".xml", "w").write(s3)

# 中性灰 shape 背景的亮色版
SHAPES = {
    "background_grey_cardview": ("#0F000000", None, None),
    "background_edittext": ("#08000000", "2dp", "#1F000000"),
    "background_edittext_privatemsg": ("#0A000000", "2dp", "#24000000"),
    "background_edittext_setting": (f"#CC{NEUTRAL_CARD:06X}", "@dimen/card_stroke", "#1A000000"),
    "background_searchbar": ("#0A000000", "2dp", "#33000000"),
    "background_searchhistory": (f"#F2{NEUTRAL_CARD:06X}", "2dp", "#26000000"),
}
for name, (solid, sw, sc) in SHAPES.items():
    stroke = f'''
    <stroke
        android:width="{sw}"
        android:color="{sc}" />''' if sw else ""
    open(ROOT + f"drawable-notnight/{name}.xml", "w").write(f'''<?xml version="1.0" encoding="utf-8"?>
<!-- 亮色模式版本（tools/appearance/gen_light_mode.py 生成）：原 #262626 系深灰底在浅色页面上是一块黑斑，改为极淡的黑色叠加（自动吸收主题底色）或米白卡片色。 -->
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="{solid}" />{stroke}
    <corners android:radius="@dimen/card_round" />
</shape>
''')

print("亮色图标:", len(made)); print(" ".join(made))
print("含其它颜色、需人工复核:", multi)

# ---------------------------------------------------------------- Kotlin 亮色色表片段
kt = []
order = [("BilibiliPink", "pink"), ("ZhihuBlue", "zhihu"), ("IQIYIGreen", "iqiyi"), ("PurpleFantasy", "purple_fantasy"),
         ("RainbowFantasy", "rainbow"), ("ClassicGray", "classic_gray"), ("ClassicTerminal", "terminal")]
for obj, k in order:
    p = L[k]
    def h(v): return "0x%08X.toInt()" % v
    args = [f"primary = {h(p['primary'])}", f"primaryDark = {h(p['deep'])}", f"primaryLight = {h(p['plight'])}",
            f"secondary = {h(p['secondary'])}", f"background = {h(p['bg'])}", f"surface = {h(p['surface'])}",
            f"card = {h(p['card'])}", f"cardWithAlpha = {h(p['cardA'])}", f"textPrimary = {h(p['t1'])}",
            f"textSecondary = {h(p['t2'])}", f"textTertiary = {h(p['t3'])}", f"border = {h(p['border'])}",
            f"divider = {h(p['divider'])}", f"ripple = {h(p['ripple'])}", f"link = {h(p['link'])}",
            f"info = {h(p['info'])}", f"share = {h(p['share'])}"]
    if "success" in p: args.append(f"success = {h(p['success'])}")
    if "gold" in p: args.append(f"gold = {h(p['gold'])}")
    kt.append(f"        {obj} to LightPalette(\n            " + ",\n            ".join(args) + "\n        )")
print("\n// ---- ColorScheme.LIGHT_PALETTES ----")
print(",\n".join(kt))
