#!/system/bin/sh
# 自动替换 openEditEntryDialog 方法

TARGET="app/src/main/java/com/k9t2/mcpackmaker/MainActivity.java"

if [ ! -f "$TARGET" ]; then
    echo "❌ 未找到 $TARGET"
    exit 1
fi

# 备份
BACKUP="$TARGET.bak_method_$(date +%Y%m%d_%H%M%S)"
cp "$TARGET" "$BACKUP"
echo "✅ 已备份到：$BACKUP"

# 定位方法起始行
START_LINE=$(grep -n "private void openEditEntryDialog" "$TARGET" | head -1 | cut -d: -f1)
if [ -z "$START_LINE" ]; then
    echo "❌ 未找到 openEditEntryDialog 方法"
    exit 1
fi
echo "方法起始行：$START_LINE"

# 定位方法结束行（下一个以 4 个空格开头的 private/public/@Override 或 // 的行）
END_LINE=$(awk -v start="$START_LINE" 'NR>start && /^    (private|public|@Override|\/\/)/ {print NR; exit}' "$TARGET")
if [ -z "$END_LINE" ]; then
    echo "❌ 未找到方法结束行"
    exit 1
fi
# 结束行需要减1，因为要保留下一个方法定义行
END_LINE=$((END_LINE - 1))
echo "方法结束行：$END_LINE"

# 生成新方法内容
cat > new_method_block.txt <<'NEWEOF'
    private void openEditEntryDialog(int index) {
        if (index < 0 || index >= entries.size()) return;
        Entry oldEntry = entries.get(index);

        Dialog d = new Dialog(this);
        currentEditDialog = d;
        currentEditSelectedUri = null;

        LinearLayout root = column();
        root.setPadding(dp(20), dp(18), dp(20), dp(18));
        root.setBackgroundColor(bg());

        root.addView(tv("修改材质", 20, text(), true), marginBottom(dp(12)));

        Spinner typeSpin = new Spinner(this);
        String[] types = {"物品", "方块", "生物", "UI", "天空"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, types);
        typeSpin.setAdapter(adapter);
        for (int i = 0; i < types.length; i++) {
            if (types[i].equals(oldEntry.type)) {
                typeSpin.setSelection(i);
                break;
            }
        }
        root.addView(typeSpin, marginBottom(dp(8)));

        EditText idEdit = new EditText(this);
        idEdit.setText(oldEntry.id);
        idEdit.setSingleLine(true);
        idEdit.setTextSize(14);
        idEdit.setTextColor(text());
        idEdit.setHintTextColor(sub());
        idEdit.setHint("Minecraft ID");
        idEdit.setPadding(dp(15), 0, dp(15), 0);
        idEdit.setBackground(round(card2(), 15, line(), 1));
        root.addView(idEdit, marginBottom(dp(10)));

        currentEditImage = new ImageView(this);
        currentEditImage.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        currentEditImage.setBackground(round(card2(), 18, line(), 1));
        if (oldEntry.imagePath != null && !oldEntry.imagePath.isEmpty()) {
            File f = new File(oldEntry.imagePath);
            if (f.exists()) {
                Bitmap bmp = BitmapFactory.decodeFile(oldEntry.imagePath);
                if (bmp != null) currentEditImage.setImageBitmap(bmp);
            }
        }
        root.addView(currentEditImage, new LinearLayout.LayoutParams(-1, dp(150)));

        TextView pickNew = secondaryButton("重新选择图片");
        pickNew.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("image/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(i, EDIT_PICK_IMAGE);
        });
        root.addView(pickNew, marginBottom(dp(8)));

        TextView save = primaryButton("保存修改");
        save.setOnClickListener(v -> {
            String newType = typeSpin.getSelectedItem().toString();
            String newId = idEdit.getText().toString().trim();
            if (!validId(newId)) {
                Toast.makeText(this, "ID格式错误", Toast.LENGTH_SHORT).show();
                return;
            }

            String newImagePath = oldEntry.imagePath;
            if (currentEditSelectedUri != null) {
                String copiedPath = copyImageToProject(currentEditSelectedUri);
                if (copiedPath != null) {
                    newImagePath = copiedPath;
                } else {
                    Toast.makeText(this, "图片保存失败，使用原图片", Toast.LENGTH_SHORT).show();
                }
            }

            entries.set(index, new Entry(newId, newType, newImagePath));
            refreshEditorList();
            d.dismiss();
            setStatus("已修改");
        });
        root.addView(save, marginTop(dp(10)));

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(round(bg(), 24, line(), 1));
            w.setLayout(-1, -1);
        }
        d.setOnDismissListener(dialog -> {
            currentEditDialog = null;
            currentEditImage = null;
            currentEditSelectedUri = null;
        });
        d.show();
    }
NEWEOF

# 使用 sed 替换：删除原方法行，插入新方法
# 先提取 START_LINE 之前的内容
sed -n "1,$((START_LINE - 1))p" "$TARGET" > tmp_before.txt
# 提取 END_LINE 之后的内容（从 END_LINE+1 到文件末尾）
sed -n "$((END_LINE + 1)),\$p" "$TARGET" > tmp_after.txt

# 组合新文件
cat tmp_before.txt new_method_block.txt tmp_after.txt > "$TARGET.new"

# 替换原文件
mv "$TARGET.new" "$TARGET"

# 清理临时文件
rm -f tmp_before.txt tmp_after.txt new_method_block.txt

# 验证
if grep -q "重新选择图片" "$TARGET"; then
    echo "✅ 方法替换成功，已包含“重新选择图片”按钮"
else
    echo "❌ 替换失败，请检查"
    exit 1
fi

echo "🎉 完成！"
