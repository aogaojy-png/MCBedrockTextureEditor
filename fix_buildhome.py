import shutil, time

path = "app/src/main/java/com/k9t2/mcpackmaker/MainActivity.java"

# 备份
shutil.copy(path, path + ".bak_fix_home_" + time.strftime("%Y%m%d_%H%M%S"))

with open(path, "r", encoding="utf-8") as f:
    lines = f.readlines()

start = None
end = None

# 找到 "LinearLayout top = row();" 和 "TextView theme = tv(darkMode" 的行号
for i, line in enumerate(lines):
    if "LinearLayout top = row();" in line and start is None:
        start = i
    if "TextView theme = tv(darkMode" in line and end is None:
        end = i
        break

if start is None or end is None:
    print("❌ 未找到目标行")
    exit(1)

# 正确的新代码块
new_block = [
    "    LinearLayout top = row();\n",
    "    LinearLayout titles = column();\n",
    "\n",
    "    LinearLayout titleRow = new LinearLayout(this);\n",
    "    titleRow.setOrientation(LinearLayout.HORIZONTAL);\n",
    "    titleRow.setGravity(Gravity.CENTER_VERTICAL);\n",
    "\n",
    "    ImageView titleIcon = new ImageView(this);\n",
    "    titleIcon.setImageResource(R.drawable.title_icon);\n",
    "    LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(30), dp(30));\n",
    "    iconParams.rightMargin = dp(8);\n",
    "    titleRow.addView(titleIcon, iconParams);\n",
    "\n",
    "    TextView title = tv(\"MC 基岩版材质编辑器\", 26, text(), true);\n",
    "    titleRow.addView(title);\n",
    "\n",
    "    TextView subTitle = tv(\"轻松创建你的专属材质包\", 14, sub(), false);\n",
    "    titles.addView(titleRow);\n",
    "    titles.addView(subTitle, marginTop(dp(4)));\n",
    "    top.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));\n",
    "\n",
]

lines[start:end] = new_block

with open(path, "w", encoding="utf-8") as f:
    f.writelines(lines)

print("✅ buildHome 标题区域已修复")
