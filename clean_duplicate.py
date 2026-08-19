import shutil, time

path = "app/src/main/java/com/k9t2/mcpackmaker/MainActivity.java"
with open(path, "r", encoding="utf-8") as f:
    lines = f.readlines()

# 备份
shutil.copy(path, path + ".bak_clean_" + time.strftime("%Y%m%d_%H%M%S"))

# 找到 buildHome 方法内第一个 "LinearLayout titles = column();"
first_titles = None
for i, line in enumerate(lines):
    if "LinearLayout titles = column();" in line:
        first_titles = i
        break

if first_titles is None:
    print("❌ 未找到 titles 定义")
    exit(1)

# 从 first_titles 往后找第二个 "LinearLayout titles = column();"
second_titles = None
for i in range(first_titles + 1, len(lines)):
    if "LinearLayout titles = column();" in line:
        second_titles = i
        break

if second_titles is None:
    print("❌ 未找到重复的 titles 定义")
    exit(1)

# 从 second_titles 往后找第一个 "top.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));"
end_keep = None
for i in range(second_titles + 1, len(lines)):
    if "top.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));" in line:
        end_keep = i
        break

if end_keep is None:
    print("❌ 未找到 top.addView 行")
    exit(1)

# 删除 second_titles 到 end_keep-1 的所有行（保留 end_keep 行）
del lines[second_titles:end_keep]

with open(path, "w", encoding="utf-8") as f:
    f.writelines(lines)

print("✅ 重复定义已清理")
