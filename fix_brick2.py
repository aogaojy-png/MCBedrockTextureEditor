import shutil, time

path = "app/src/main/java/com/k9t2/mcpackmaker/MainActivity.java"

# 读取原文件
with open(path, "r", encoding="utf-8") as f:
    lines = f.readlines()

# 备份
shutil.copy(path, path + ".bak_brick2_" + time.strftime("%Y%m%d_%H%M%S"))

# 第 170 行（索引169）替换为多行
new_title_block = '''LinearLayout titles = column();

LinearLayout titleRow = new LinearLayout(this);
titleRow.setOrientation(LinearLayout.HORIZONTAL);
titleRow.setGravity(Gravity.CENTER_VERTICAL);

ImageView titleIcon = new ImageView(this);
titleIcon.setImageResource(R.drawable.title_icon);
LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(30), dp(30));
iconParams.rightMargin = dp(8);
titleRow.addView(titleIcon, iconParams);

TextView title = tv("MC 基岩版材质编辑器", 26, text(), true);
titleRow.addView(title);

TextView subTitle = tv("轻松创建你的专属材质包", 14, sub(), false);
titles.addView(titleRow);
titles.addView(subTitle, marginTop(dp(4)));
'''

# 第 596 行（索引595）替换为多行
new_icon_block = '''ImageView icon = new ImageView(this);
icon.setImageResource(R.drawable.title_icon);
icon.setColorFilter(GREEN);
LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(28), dp(28));
iconParams.gravity = Gravity.CENTER_VERTICAL;
row.addView(icon, iconParams);
'''

lines[169] = new_title_block
lines[595] = new_icon_block

with open(path, "w", encoding="utf-8") as f:
    f.writelines(lines)

print("✅ 替换完成")
