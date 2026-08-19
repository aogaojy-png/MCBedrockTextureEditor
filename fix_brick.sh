#!/system/bin/sh
# 替换所有 🧱 为 title_icon.png

TARGET="app/src/main/java/com/k9t2/mcpackmaker/MainActivity.java"

if [ ! -f "$TARGET" ]; then
    echo "❌ 未找到 MainActivity.java"
    exit 1
fi

# 备份
BACKUP="$TARGET.bak_brick_$(date +%Y%m%d_%H%M%S)"
cp "$TARGET" "$BACKUP"
echo "✅ 已备份到：$BACKUP"

# 第 1 处：首页标题
perl -0pi -e 's{\QLinearLayout titles = column();\nTextView title = tv("🧱  MC 基岩版材质编辑器", 26, text(), true);\nTextView subTitle = tv("轻松创建你的专属材质包", 14, sub(), false);\ntitles.addView(title); titles.addView(subTitle, marginTop(dp(4)));\E}{LinearLayout titles = column();\n\nLinearLayout titleRow = new LinearLayout(this);\ntitleRow.setOrientation(LinearLayout.HORIZONTAL);\ntitleRow.setGravity(Gravity.CENTER_VERTICAL);\n\nImageView titleIcon = new ImageView(this);\ntitleIcon.setImageResource(R.drawable.title_icon);\nLinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(30), dp(30));\niconParams.rightMargin = dp(8);\ntitleRow.addView(titleIcon, iconParams);\n\nTextView title = tv("MC 基岩版材质编辑器", 26, text(), true);\ntitleRow.addView(title);\n\nTextView subTitle = tv("轻松创建你的专属材质包", 14, sub(), false);\ntitles.addView(titleRow);\ntitles.addView(subTitle, marginTop(dp(4)));}s' "$TARGET"

# 第 2 处：“我的”页面头像
perl -0pi -e 's{\Qprofile.addView(tv("🧱", 40, GREEN, true), marginBottom(dp(4)));\E}{ImageView profileIcon = new ImageView(this);\nprofileIcon.setImageResource(R.drawable.title_icon);\nprofileIcon.setColorFilter(GREEN);\nLinearLayout.LayoutParams profileIconParams = new LinearLayout.LayoutParams(dp(40), dp(40));\nprofileIconParams.gravity = Gravity.CENTER_HORIZONTAL;\nprofileIconParams.bottomMargin = dp(4);\nprofile.addView(profileIcon, profileIconParams);}s' "$TARGET"

# 第 3 处：历史项目行图标
perl -0pi -e 's{\QTextView icon = tv("🧱", 27, text(), true);\nrow.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(46)));\E}{ImageView icon = new ImageView(this);\nicon.setImageResource(R.drawable.title_icon);\nicon.setColorFilter(GREEN);\nLinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(28), dp(28));\niconParams.gravity = Gravity.CENTER_VERTICAL;\nrow.addView(icon, iconParams);}s' "$TARGET"

# 验证
if grep -q "title_icon" "$TARGET"; then
    echo "✅ 替换完成，已使用 title_icon.png"
else
    echo "❌ 替换可能失败，请检查"
fi

echo "🎉 完成！"
