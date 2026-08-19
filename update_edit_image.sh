#!/system/bin/sh
# 修改 MainActivity.java，使修改材质时可以选择新图片

TARGET="app/src/main/java/com/k9t2/mcpackmaker/MainActivity.java"

if [ ! -f "$TARGET" ]; then
    echo "❌ 未找到 $TARGET"
    echo "请确认你在工程根目录执行本脚本。"
    exit 1
fi

if ! command -v perl >/dev/null 2>&1; then
    echo "❌ 需要 perl，请先安装： pkg install perl"
    exit 1
fi

BACKUP="$TARGET.bak_$(date +%Y%m%d_%H%M%S)"
cp "$TARGET" "$BACKUP"
echo "✅ 已备份原文件到：$BACKUP"

perl -0pi -e 's/(private static final int IMPORT_PACK = 2001;)/$1\n    private static final int EDIT_PICK_IMAGE = 3001;/' "$TARGET"

perl -0pi -e 's/(private Project currentProject;)/$1\n    private Dialog currentEditDialog;\n    private ImageView currentEditImage;\n    private Uri currentEditSelectedUri;/' "$TARGET"

cat > /tmp/old_method.txt <<'OLD'
    private void openEditEntryDialog(int index) {
        if (index < 0 || index >= entries.size()) return;
        Entry oldEntry = entries.get(index);

        Dialog d = new Dialog(this);
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

        ImageView currentImg = new ImageView(this);
        currentImg.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        currentImg.setBackground(round(card2(), 18, line(), 1));
        if (oldEntry.imagePath != null && !oldEntry.imagePath.isEmpty()) {
            File f = new File(oldEntry.imagePath);
            if (f.exists()) {
                Bitmap bmp = BitmapFactory.decodeFile(oldEntry.imagePath);
                if (bmp != null) currentImg.setImageBitmap(bmp);
            }
        }
        root.addView(currentImg, new LinearLayout.LayoutParams(-1, dp(150)));

        TextView save = primaryButton("保存修改");
        save.setOnClickListener(v -> {
            String newType = typeSpin.getSelectedItem().toString();
            String newId = idEdit.getText().toString().trim();
            if (!validId(newId)) {
                Toast.makeText(this, "ID格式错误", Toast.LENGTH_SHORT).show();
                return;
            }
            entries.set(index, new Entry(newId, newType, oldEntry.imagePath));
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
        d.show();
    }
OLD

cat > /tmp/new_method.txt <<'NEW'
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
NEW

perl -0pi -e 'BEGIN{open my $old, "<", "/tmp/old_method.txt"; local $/; $old_content = <$old>; close $old; open my $new, "<", "/tmp/new_method.txt"; $new_content = <$new>; close $new;} s/\Q$old_content\E/$new_content/s' "$TARGET"

perl -0pi -e 's/(if \(requestCode == IMPORT_PACK && resultCode == RESULT_OK && data != null\) \{\n\s*Uri uri = data\.getData\(\);\n\s*if \(uri != null\) \{\n\s*importMcpack\(uri\);\n\s*\}\n\s*\})/$1\n\n        if (requestCode == EDIT_PICK_IMAGE \&\& resultCode == RESULT_OK \&\& data != null) {\n            Uri uri = data.getData();\n            if (uri != null) {\n                try {\n                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);\n                } catch (Exception ignored) {}\n                try {\n                    Bitmap b = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));\n                    if (b != null) {\n                        currentEditSelectedUri = uri;\n                        if (currentEditImage != null) {\n                            currentEditImage.setImageBitmap(b);\n                        }\n                    } else {\n                        Toast.makeText(this, "无法读取图片", Toast.LENGTH_SHORT).show();\n                    }\n                } catch (Exception e) {\n                    Toast.makeText(this, "读取图片失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();\n                }\n            }\n        }/s' "$TARGET"

rm -f /tmp/old_method.txt /tmp/new_method.txt

echo ""
echo "========== 验证修改 =========="

if grep -q "EDIT_PICK_IMAGE" "$TARGET"; then
    echo "✅ 常量 EDIT_PICK_IMAGE 已添加"
else
    echo "❌ 常量 EDIT_PICK_IMAGE 未添加"
    exit 1
fi

if grep -q "currentEditDialog" "$TARGET"; then
    echo "✅ 成员变量 currentEditDialog 已添加"
else
    echo "❌ 成员变量 currentEditDialog 未添加"
    exit 1
fi

if grep -q "重新选择图片" "$TARGET"; then
    echo "✅ 按钮“重新选择图片”已添加"
else
    echo "❌ 按钮“重新选择图片”未添加"
    exit 1
fi

if grep -q "requestCode == EDIT_PICK_IMAGE" "$TARGET"; then
    echo "✅ onActivityResult 处理已添加"
else
    echo "❌ onActivityResult 处理未添加"
    exit 1
fi

echo ""
echo "🎉 所有修改完成！"
echo "原文件已备份为：$BACKUP"
echo "你可以继续编译或运行 App。"
