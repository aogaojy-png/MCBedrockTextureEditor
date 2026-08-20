package com.k9t2.mcpackmaker;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;
import android.widget.AdapterView;

import org.json.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;

public class MainActivity extends android.app.Activity {
    private static final int PICK_IMAGE = 1001;
    private static final int SAVE_PACK = 1002;
    private static final int IMPORT_PACK = 2001;
    private static final int EDIT_PICK_IMAGE = 3001;

    private LinearLayout pageHost;
    private LinearLayout bottomNav;
    private int currentPage = 0;
    private boolean darkMode;

    private Spinner typeSpinner;
    private EditText idInput;
    private ImageView preview;
    private TextView selectedText, status;
    private LinearLayout editorListContainer;
    private Uri selectedUri;
    private File pendingPack;
    private String pendingHistoryName = "";
    private EditText packNameInput, authorInput, descriptionInput;
    private ArrayList<Entry> entries = new ArrayList<>();
    private ArrayList<String> subpackList = new ArrayList<>();

    private Project currentProject;

    private Dialog currentEditDialog;
    private ImageView currentEditImage;
    private Uri currentEditSelectedUri;

    private Spinner subpackSpinner;
    private ArrayAdapter<String> subpackAdapter;

    private CheckBox independentCheckBox;
    private EditText switchNameInput;

    // 筛选器
    private Spinner filterSubpackSpinner;
    private Spinner filterSwitchSpinner;
    private String currentSubpackFilter = "";
    private String currentSwitchFilter = "";

    // 粘性筛选器
    private Spinner stickySubpackSpinner;
    private Spinner stickySwitchSpinner;

    private static final int GREEN = Color.rgb(52, 190, 73);
    private static final int BLUE = Color.rgb(66, 139, 224);
    private static final int PURPLE = Color.rgb(157, 104, 226);

    // ---------- 内部类 ----------
    private static class Entry {
        String id, type, subpack;
        String imagePath;
        boolean independentEnabled;
        String switchName;
        Entry(String id, String type, String subpack, String imagePath,
              boolean independentEnabled, String switchName) {
            this.id = id;
            this.type = type;
            this.subpack = subpack == null ? "" : subpack;
            this.imagePath = imagePath;
            this.independentEnabled = independentEnabled;
            this.switchName = switchName == null ? "" : switchName;
        }
    }

    private static class Project {
        String id;
        String name;
        String author;
        String description;
        String date;
        ArrayList<Entry> entries = new ArrayList<>();
    }

    // ---------- 异步导入任务 ----------
    private class ImportTask extends AsyncTask<Void, Integer, ArrayList<Entry>> {
        private Uri uri;
        private ProgressDialog progressDialog;
        private Exception error;

        ImportTask(Uri uri) { this.uri = uri; }

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(MainActivity.this);
            progressDialog.setTitle("正在导入材质包");
            progressDialog.setMessage("正在解析文件结构...");
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setMax(100);
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        protected ArrayList<Entry> doInBackground(Void... voids) {
            try {
                File tempZip = new File(getCacheDir(), "import_temp.zip");
                try (InputStream in = getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(tempZip)) {
                    byte[] buf = new byte[8192];
                    int n;
                    long total = 0;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                        total += n;
                        publishProgress((int) (total / 1024 / 1024 % 30));
                    }
                }

                File extractDir = new File(getCacheDir(), "import_extract");
                delete(extractDir);
                extractDir.mkdirs();
                unzip(tempZip, extractDir);
                publishProgress(40);

                ArrayList<Entry> imported = new ArrayList<>();

                File texturesDir = new File(extractDir, "textures");
                if (texturesDir.exists()) {
                    scanTextures(texturesDir, "", imported);
                }
                publishProgress(60);

                File subpacksDir = new File(extractDir, "subpacks");
                if (subpacksDir.exists()) {
                    File[] subFolders = subpacksDir.listFiles(File::isDirectory);
                    if (subFolders != null) {
                        int totalSub = subFolders.length;
                        int processed = 0;
                        for (File subFolder : subFolders) {
                            String subName = subFolder.getName();
                            if (!subpackList.contains(subName)) {
                                subpackList.add(subName);
                            }
                            File subTextures = new File(subFolder, "textures");
                            if (subTextures.exists()) {
                                scanTextures(subTextures, subName, imported);
                            }
                            processed++;
                            publishProgress(60 + (int) ((float) processed / totalSub * 20));
                        }
                        saveSubpackList();
                    }
                }
                publishProgress(85);

                File modelsDir = new File(extractDir, "models");
                if (modelsDir.exists()) {
                    scanModels(modelsDir, imported);
                }
                publishProgress(95);

                return imported;
            } catch (Exception e) {
                error = e;
                return null;
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            progressDialog.setProgress(values[0]);
            if (values[0] < 30) progressDialog.setMessage("正在解压材质包...");
            else if (values[0] < 60) progressDialog.setMessage("正在扫描纹理文件...");
            else if (values[0] < 85) progressDialog.setMessage("正在扫描子包...");
            else if (values[0] < 95) progressDialog.setMessage("正在解析模型...");
            else progressDialog.setMessage("正在整理...");
        }

        @Override
        protected void onPostExecute(ArrayList<Entry> result) {
            progressDialog.dismiss();
            if (error != null) {
                Toast.makeText(MainActivity.this, "导入失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }
            if (result == null || result.isEmpty()) {
                Toast.makeText(MainActivity.this, "未在材质包中找到可导入的纹理", Toast.LENGTH_LONG).show();
                return;
            }
            entries.clear();
            entries.addAll(result);
            currentProject = null;
            showPage(4);
            Toast.makeText(MainActivity.this, "导入成功，共 " + result.size() + " 个材质", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        darkMode = getPreferences(MODE_PRIVATE).getBoolean("dark_mode", true);
        loadSubpackList();
        if (!getPreferences(MODE_PRIVATE).getBoolean("agreed", false)) {
            showAgreementDialog();
        } else {
            buildShell();
            showPage(0);
        }
    }private void buildShell() {
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(bg());
    root.setClipToPadding(false);

    FrameLayout contentFrame = new FrameLayout(this);
    pageHost = new LinearLayout(this);
    pageHost.setOrientation(LinearLayout.VERTICAL);
    pageHost.setBackgroundColor(bg());
    contentFrame.addView(pageHost, new FrameLayout.LayoutParams(-1, -1));
    root.addView(contentFrame, new LinearLayout.LayoutParams(-1, 0, 1));

    bottomNav = new LinearLayout(this);
    bottomNav.setOrientation(LinearLayout.HORIZONTAL);
    bottomNav.setGravity(Gravity.CENTER);
    bottomNav.setPadding(dp(10), dp(8), dp(10), dp(8));
    bottomNav.setBackground(round(card(), 22, line(), 1));

    LinearLayout.LayoutParams navLp = new LinearLayout.LayoutParams(-1, dp(78));
    navLp.leftMargin = dp(10);
    navLp.rightMargin = dp(10);
    navLp.bottomMargin = dp(8);
    root.addView(bottomNav, navLp);

    setContentView(root);
    root.setOnApplyWindowInsetsListener((v, insets) -> {
        int top, bottom;
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars());
            top = i.top; bottom = i.bottom;
        } else {
            top = insets.getSystemWindowInsetTop(); bottom = insets.getSystemWindowInsetBottom();
        }
        v.setPadding(0, top, 0, bottom);
        return insets;
    });
    refreshBottomNav();
    styleWindow();
}

private void styleWindow() {
    getWindow().setStatusBarColor(bg());
    getWindow().setNavigationBarColor(bg());
    int flags = 0;
    if (!darkMode) flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
    getWindow().getDecorView().setSystemUiVisibility(flags);
}

private int bg() { return darkMode ? Color.rgb(17, 21, 27) : Color.rgb(247, 249, 252); }
private int card() { return darkMode ? Color.rgb(29, 34, 42) : Color.WHITE; }
private int card2() { return darkMode ? Color.rgb(35, 40, 49) : Color.rgb(246, 248, 251); }
private int text() { return darkMode ? Color.WHITE : Color.rgb(25, 29, 35); }
private int sub() { return darkMode ? Color.rgb(200, 200, 200) : Color.rgb(100, 108, 120); }
private int line() { return darkMode ? Color.rgb(53, 60, 70) : Color.rgb(228, 232, 239); }

private void loadSubpackList() {
    String raw = getPreferences(MODE_PRIVATE).getString("subpack_list", "");
    subpackList.clear();
    if (!raw.isEmpty()) {
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                subpackList.add(arr.getString(i));
            }
        } catch (Exception ignored) {}
    }
}

private void saveSubpackList() {
    JSONArray arr = new JSONArray();
    for (String s : subpackList) arr.put(s);
    getPreferences(MODE_PRIVATE).edit().putString("subpack_list", arr.toString()).apply();
    refreshSubpackSpinner();
    updateFilterOptions();
}

private void refreshSubpackSpinner() {
    if (subpackSpinner != null && subpackAdapter != null) {
        subpackAdapter.notifyDataSetChanged();
        String selected = (String) subpackSpinner.getSelectedItem();
        if (selected != null && !subpackList.contains(selected) && !selected.equals("默认/根包")) {
            subpackSpinner.setSelection(0);
        }
    }
}

private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
private float dpF(float n) { return n * getResources().getDisplayMetrics().density; }
private int blend(int a, int b, float amount) { return Color.rgb((int)(Color.red(a)*(1-amount)+Color.red(b)*amount),(int)(Color.green(a)*(1-amount)+Color.green(b)*amount),(int)(Color.blue(a)*(1-amount)+Color.blue(b)*amount)); }
private GradientDrawable round(int color, float radius, int strokeColor, int strokeWidth) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dpF(radius)); if (strokeWidth>0) g.setStroke(dp(strokeWidth), strokeColor); return g; }
private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
private LinearLayout cardColumn() { LinearLayout l = column(); l.setPadding(dp(14), dp(14), dp(14), dp(14)); l.setBackground(round(card(), 19, line(), 1)); return l; }
private TextView tv(String s, float size, int color, boolean bold) { TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); t.setTypeface(android.graphics.Typeface.DEFAULT, bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL); return t; }
private LinearLayout.LayoutParams marginTop(int v) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.topMargin=v; return p; }
private LinearLayout.LayoutParams marginBottom(int v) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.bottomMargin=v; return p; }
private LinearLayout.LayoutParams marginLeft(int v) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2,-2); p.leftMargin=v; return p; }
private LinearLayout.LayoutParams centerParams() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.gravity=Gravity.CENTER_HORIZONTAL; return p; }
private LinearLayout.LayoutParams centerMarginParams(int top) { LinearLayout.LayoutParams p=centerParams(); p.topMargin=top; return p; }
private View space(int h) { Space s=new Space(this); return sized(s,-1,h); }
private <T extends View> T sized(T v,int w,int h){v.setLayoutParams(new LinearLayout.LayoutParams(w,h)); return v;}
private ScrollView scroll() { ScrollView s = new ScrollView(this); s.setFillViewport(true); s.setBackgroundColor(bg()); return s; }
private TextView primaryButton(String s) { TextView t = tv(s, 15, Color.WHITE, true); t.setGravity(Gravity.CENTER); t.setBackground(round(GREEN, 17, Color.TRANSPARENT, 0)); return t; }
private TextView secondaryButton(String s) { TextView t = tv(s, 14, text(), true); t.setGravity(Gravity.CENTER); t.setBackground(round(card2(), 15, line(), 1)); return t; }
private EditText input(String hint, String subHint) {
    EditText e = new EditText(this);
    e.setSingleLine(true);
    e.setTextSize(14);
    e.setTextColor(text());
    e.setHintTextColor(sub());
    e.setHint(hint + "  ·  " + subHint);
    e.setPadding(dp(15), 0, dp(15), 0);
    e.setBackground(round(card2(), 15, line(), 1));
    return e;
}

private ArrayAdapter<String> createSpinnerAdapter(String[] items) {
    return new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, items) {
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            if (view instanceof TextView) ((TextView) view).setTextColor(text());
            return view;
        }
        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            View view = super.getDropDownView(position, convertView, parent);
            if (view instanceof TextView) {
                ((TextView) view).setTextColor(text());
                view.setBackgroundColor(Color.TRANSPARENT);
            }
            return view;
        }
    };
}

private ArrayAdapter<String> createSpinnerAdapter(List<String> items) {
    return new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, items) {
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            if (view instanceof TextView) ((TextView) view).setTextColor(text());
            return view;
        }
        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            View view = super.getDropDownView(position, convertView, parent);
            if (view instanceof TextView) {
                ((TextView) view).setTextColor(text());
                view.setBackgroundColor(Color.TRANSPARENT);
            }
            return view;
        }
    };
}

private void setupSpinnerPopup(Spinner spinner) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
        spinner.setPopupBackgroundDrawable(round(bg(), 0, 0, 0));
    }
}

private void showPage(int page) {
    currentPage = page;
    pageHost.removeAllViews();
    pageHost.setBackgroundColor(bg());
    if (page == 0) buildHome();
    else if (page == 1) { buildEditorList(); updateFilterOptions(); }
    else if (page == 2) buildGenerator();
    else if (page == 3) buildMine();
    else if (page == 4) buildEditorPage();
    refreshBottomNav();
    styleWindow();
}

private void refreshBottomNav() {
    if (bottomNav == null) return;
    bottomNav.removeAllViews();
    bottomNav.setBackground(round(card(), 22, line(), 1));

    String[] icons = {"⌂", "☷", "▣", "♙"};
    String[] labels = {"首页", "编辑", "生成", "我的"};
    for (int i = 0; i < 4; i++) {
        final int page = i;
        boolean selected = i == currentPage;

        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(4), dp(5), dp(4), dp(5));
        item.setBackground(round(selected ? blend(card(), GREEN, 0.12f) : Color.TRANSPARENT, 16, Color.TRANSPARENT, 0));
        item.setOnClickListener(v -> showPage(page));

        TextView icon = tv(icons[i], 23, selected ? GREEN : sub(), true);
        icon.setGravity(Gravity.CENTER);
        TextView label = tv(labels[i], 12, selected ? GREEN : text(), selected);
        label.setGravity(Gravity.CENTER);
        item.addView(icon, new LinearLayout.LayoutParams(-1, dp(32)));
        item.addView(label, new LinearLayout.LayoutParams(-1, dp(20)));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        lp.leftMargin = dp(3);
        lp.rightMargin = dp(3);
        bottomNav.addView(item, lp);
    }
}private void buildHome() {
    ScrollView scroll = scroll();
    LinearLayout box = column();
    box.setPadding(dp(20), dp(18), dp(20), dp(28));

    LinearLayout header = row();
    header.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout titles = column();
    TextView eyebrow = tv("MINECRAFT BEDROCK", 11, GREEN, true);
    TextView title = tv("材质编辑器", 29, text(), true);
    TextView subtitle = tv("在手机上创建、编辑和生成你的材质包", 13, sub(), false);
    titles.addView(eyebrow);
    titles.addView(title, marginTop(dp(2)));
    titles.addView(subtitle, marginTop(dp(3)));
    header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));

    TextView theme = tv(darkMode ? "🌙" : "☀", 20, text(), true);
    theme.setGravity(Gravity.CENTER);
    theme.setBackground(round(card2(), 50, line(), 1));
    theme.setOnClickListener(v -> toggleTheme());
    header.addView(theme, new LinearLayout.LayoutParams(dp(50), dp(50)));
    box.addView(header, marginBottom(dp(18)));

    LinearLayout hero = cardColumn();
    hero.setPadding(dp(18), dp(18), dp(18), dp(18));
    hero.setBackground(round(blend(card(), GREEN, darkMode ? 0.10f : 0.06f), 22, blend(GREEN, bg(), 0.65f), 1));
    hero.addView(tv("开始制作", 13, GREEN, true));
    hero.addView(tv("从一个空白项目开始", 22, text(), true), marginTop(dp(4)));
    hero.addView(tv("输入 Minecraft ID，选择纹理图片，然后生成 .mcpack。", 13, sub(), false), marginTop(dp(5)));
    TextView create = primaryButton("＋  创建新材质");
    create.setTextSize(15);
    create.setOnClickListener(v -> {
        currentProject = null;
        entries.clear();
        subpackList.clear();
        saveSubpackList();
        showPage(4);
    });
    hero.addView(create, marginTop(dp(14)));
    box.addView(hero, marginBottom(dp(14)));

    box.addView(sectionTitle("快速入口", "常用操作都在这里"), marginBottom(dp(9)));
    LinearLayout actions = row();
    actions.setWeightSum(2f);
    actions.addView(actionCard("▣", "继续编辑", "打开已有项目", BLUE, v -> showPage(1)), equalActionParams(0));
    actions.addView(actionCard("↗", "导入材质包", "从 .mcpack 开始", PURPLE, v -> importPack()), equalActionParams(1));
    box.addView(actions, marginBottom(dp(16)));

    LinearLayout recentCard = cardColumn();
    LinearLayout recentHead = row();
    recentHead.addView(tv("最近项目", 18, text(), true), new LinearLayout.LayoutParams(0, -2, 1));
    TextView all = tv("全部  ›", 13, GREEN, true);
    all.setOnClickListener(v -> showPage(1));
    recentHead.addView(all);
    recentCard.addView(recentHead, marginBottom(dp(10)));
    addRecentRows(recentCard, 3);
    box.addView(recentCard, marginBottom(dp(14)));

    LinearLayout infoCard = cardColumn();
    infoCard.addView(sectionTitle("项目状态", "当前应用信息"), marginBottom(dp(8)));
    LinearLayout stats = row();
    stats.addView(statItem("材质", String.valueOf(entries.size()), GREEN), new LinearLayout.LayoutParams(0, dp(70), 1));
    stats.addView(statItem("子包", String.valueOf(subpackList.size()), BLUE), new LinearLayout.LayoutParams(0, dp(70), 1));
    stats.addView(statItem("版本", "1.2.0", PURPLE), new LinearLayout.LayoutParams(0, dp(70), 1));
    infoCard.addView(stats);
    box.addView(infoCard);

    scroll.addView(box);
    pageHost.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
}

private void buildEditorList() {
    ScrollView scroll = scroll();
    LinearLayout box = column();
    box.setPadding(dp(20), dp(18), dp(20), dp(26));
    box.addView(topTitle("编辑项目", "管理你保存过的材质包项目"), marginBottom(dp(14)));

    LinearLayout toolbar = cardColumn();
    toolbar.setPadding(dp(12), dp(10), dp(12), dp(10));
    LinearLayout subpackRow = row();
    subpackRow.setGravity(Gravity.CENTER_VERTICAL);
    subpackRow.addView(tv("子包", 13, text(), true), new LinearLayout.LayoutParams(dp(52), -2));
    filterSubpackSpinner = new Spinner(this);
    setupFilterSpinner(filterSubpackSpinner, () -> {
        String selected = (String) filterSubpackSpinner.getSelectedItem();
        currentSubpackFilter = (selected == null || selected.equals("全部")) ? "" : selected;
        refreshEditorList();
    });
    subpackRow.addView(filterSubpackSpinner, new LinearLayout.LayoutParams(0, -2, 1));
    toolbar.addView(subpackRow);

    LinearLayout switchRow = row();
    switchRow.setGravity(Gravity.CENTER_VERTICAL);
    switchRow.addView(tv("开关", 13, text(), true), new LinearLayout.LayoutParams(dp(52), -2));
    filterSwitchSpinner = new Spinner(this);
    setupFilterSpinner(filterSwitchSpinner, () -> {
        String selected = (String) filterSwitchSpinner.getSelectedItem();
        currentSwitchFilter = (selected == null || selected.equals("全部")) ? "" : selected;
        refreshEditorList();
    });
    switchRow.addView(filterSwitchSpinner, new LinearLayout.LayoutParams(0, -2, 1));
    toolbar.addView(switchRow, marginTop(dp(4)));
    box.addView(toolbar, marginBottom(dp(12)));

    LinearLayout list = column();
    ArrayList<Project> all = loadAllProjects();
    if (all.isEmpty()) {
        LinearLayout empty = cardColumn();
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(20), dp(46), dp(20), dp(46));
        empty.addView(tv("＋", 34, GREEN, true), centerParams());
        empty.addView(tv("还没有项目", 18, text(), true), marginTop(dp(8)));
        empty.addView(tv("创建并生成一个材质包后，它会自动出现在这里。", 13, sub(), false), marginTop(dp(5)));
        TextView create = primaryButton("创建第一个项目");
        create.setOnClickListener(v -> {
            currentProject = null;
            entries.clear();
            showPage(4);
        });
        empty.addView(create, new LinearLayout.LayoutParams(-1, dp(44)));
        list.addView(empty);
    } else {
        for (Project p : all) addProjectRow(list, p);
    }
    box.addView(list);
    scroll.addView(box);
    pageHost.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
}

private void buildGenerator() {
    RelativeLayout rootLayout = new RelativeLayout(this);
    rootLayout.setBackgroundColor(bg());

    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setBackgroundColor(bg());
    LinearLayout box = column();
    box.setPadding(dp(20), dp(18), dp(20), dp(170));
    box.addView(topTitle("生成材质包", "检查项目后生成可导入 Minecraft 的 .mcpack"), marginBottom(dp(14)));

    LinearLayout summary = cardColumn();
    summary.addView(tv("当前项目", 13, sub(), false));
    String projectName = currentProject != null && currentProject.name != null && !currentProject.name.isEmpty()
            ? currentProject.name : "未命名材质包";
    summary.addView(tv(projectName, 20, text(), true), marginTop(dp(3)));
    LinearLayout stats = row();
    stats.addView(statItem("材质", String.valueOf(entries.size()), GREEN), new LinearLayout.LayoutParams(0, dp(68), 1));
    stats.addView(statItem("子包", String.valueOf(subpackList.size()), BLUE), new LinearLayout.LayoutParams(0, dp(68), 1));
    stats.addView(statItem("作者", "本地", PURPLE), new LinearLayout.LayoutParams(0, dp(68), 1));
    summary.addView(stats, marginTop(dp(10)));
    box.addView(summary, marginBottom(dp(12)));

    LinearLayout previewCard = cardColumn();
    previewCard.addView(tv("材质预览", 17, text(), true), marginBottom(dp(8)));
    if (entries.isEmpty()) {
        previewCard.addView(tv("暂无材质项目，请先添加纹理。", 13, sub(), false));
    } else {
        int count = Math.min(entries.size(), 6);
        for (int i = 0; i < count; i++) {
            previewCard.addView(buildEntryRow(entries.get(i), i), marginBottom(dp(6)));
        }
        if (entries.size() > 6) {
            previewCard.addView(tv("还有 " + (entries.size() - 6) + " 个材质……", 12, sub(), false), marginTop(dp(4)));
        }
    }
    box.addView(previewCard, marginBottom(dp(12)));

    TextView addBtn = secondaryButton("＋  返回编辑器添加材质");
    addBtn.setOnClickListener(v -> showPage(4));
    box.addView(addBtn, marginBottom(dp(12)));

    LinearLayout form = cardColumn();
    form.addView(tv("材质包信息", 17, text(), true), marginBottom(dp(8)));
    packNameInput = input("材质包名称", "例如：我的 PVP 材质包");
    if (currentProject != null && currentProject.name != null && !currentProject.name.isEmpty()) packNameInput.setText(currentProject.name);
    form.addView(packNameInput, marginBottom(dp(7)));
    authorInput = input("作者", "熬糕aogao");
    authorInput.setText(currentProject != null && currentProject.author != null && !currentProject.author.isEmpty() ? currentProject.author : "熬糕aogao");
    form.addView(authorInput, marginBottom(dp(7)));
    descriptionInput = input("描述", "个人制作的 Minecraft 基岩版材质包");
    if (currentProject != null && currentProject.description != null) descriptionInput.setText(currentProject.description);
    form.addView(descriptionInput, marginBottom(dp(7)));
    form.addView(tv("许可证：V3C（以项目仓库中的 LICENSE 为准）", 11, sub(), false));
    box.addView(form);

    scroll.addView(box);
    rootLayout.addView(scroll, new RelativeLayout.LayoutParams(-1, -1));

    LinearLayout bottom = column();
    bottom.setPadding(dp(12), dp(10), dp(12), dp(10));
    bottom.setBackground(round(card(), 20, line(), 1));
    TextView generateBtn = primaryButton("生成 .mcpack");
    generateBtn.setTextSize(16);
    generateBtn.setOnClickListener(v -> generate());
    bottom.addView(generateBtn, new LinearLayout.LayoutParams(-1, dp(48)));
    TextView hint = tv(entries.isEmpty() ? "请先添加至少一个材质" : "准备完成后即可生成", 11, sub(), false);
    hint.setGravity(Gravity.CENTER);
    bottom.addView(hint, marginTop(dp(4)));
    RelativeLayout.LayoutParams bp = new RelativeLayout.LayoutParams(-1, -2);
    bp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
    bp.leftMargin = dp(10); bp.rightMargin = dp(10); bp.bottomMargin = dp(8);
    rootLayout.addView(bottom, bp);
    pageHost.addView(rootLayout, new LinearLayout.LayoutParams(-1, -1));
}

private void buildMine() {
    ScrollView scroll = scroll();
    LinearLayout box = column();
    box.setPadding(dp(20), dp(18), dp(20), dp(26));
    box.addView(topTitle("我的", "软件设置与开源项目说明"), marginBottom(dp(14)));

    LinearLayout profile = cardColumn();
    profile.setGravity(Gravity.CENTER_HORIZONTAL);
    ImageView profileIcon = new ImageView(this);
    profileIcon.setImageResource(R.drawable.title_icon);
    profile.addView(profileIcon, new LinearLayout.LayoutParams(dp(54), dp(54)));
    profile.addView(tv("熬糕aogao", 20, text(), true), marginTop(dp(6)));
    profile.addView(tv("MC 基岩版材质编辑器开发者", 12, sub(), false), marginTop(dp(2)));
    box.addView(profile, marginBottom(dp(12)));

    LinearLayout settings = cardColumn();
    settings.addView(tv("设置", 18, text(), true), marginBottom(dp(7)));
    addSetting(settings, "主题模式", darkMode ? "深色模式" : "浅色模式", "◐", v -> toggleTheme());
    addSetting(settings, "默认材质类型", getDefaultType(), "▣", v -> showDefaultTypeDialog());
    addSetting(settings, "更新日志", "查看版本变化", "⌁", v -> showChangelog());
    addSetting(settings, "用户协议", "查看完整协议及免责声明", "📄", v -> showFullDisclaimerDialog());
    box.addView(settings, marginBottom(dp(12)));

    LinearLayout open = cardColumn();
    open.addView(tv("开源项目", 18, text(), true), marginBottom(dp(7)));
    open.addView(tv("MC 基岩版材质编辑器", 15, text(), true));
    open.addView(tv("移动端本地材质包制作工具", 12, sub(), false), marginTop(dp(2)));
    open.addView(tv("版本 1.3.0-bedrock", 12, sub(), false), marginTop(dp(2)));
    open.addView(tv("许可证：V3C（以仓库 LICENSE 为准）", 12, GREEN, true), marginTop(dp(7)));
    TextView about = secondaryButton("关于软件 / 制作名单");
    about.setOnClickListener(v -> showAbout());
    open.addView(about, marginTop(dp(10)));
    box.addView(open, marginBottom(dp(12)));

    LinearLayout guide = cardColumn();
    guide.addView(tv("帮助", 18, text(), true), marginBottom(dp(7)));
    addGuide(guide, "?", "常见问题", "手机制作、ID、材质包等", BLUE, v -> showFAQ());
    addGuide(guide, "▣", "材质包结构", "了解 textures / subpacks", PURPLE, v -> showPackStructure());
    box.addView(guide);

    scroll.addView(box);
    pageHost.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
}private void buildEditorPage() {
    FrameLayout rootFrame = new FrameLayout(this);
    rootFrame.setBackgroundColor(bg());
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setBackgroundColor(bg());
    LinearLayout box = column();
    box.setPadding(dp(18), dp(16), dp(18), dp(24));

    LinearLayout head = row();
    TextView back = tv("‹", 26, GREEN, true);
    back.setGravity(Gravity.CENTER);
    back.setOnClickListener(v -> showPage(0));
    head.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
    LinearLayout headText = column();
    headText.addView(tv("编辑材质", 23, text(), true));
    headText.addView(tv("添加纹理并管理当前项目", 12, sub(), false), marginTop(dp(2)));
    head.addView(headText, new LinearLayout.LayoutParams(0, -2, 1));
    box.addView(head, marginBottom(dp(14)));

    LinearLayout addCard = cardColumn();
    addCard.addView(tv("添加新材质", 18, text(), true), marginBottom(dp(8)));
    typeSpinner = new Spinner(this);
    String[] types = {"物品", "方块", "生物", "UI", "天空"};
    typeSpinner.setAdapter(createSpinnerAdapter(types));
    setupSpinnerPopup(typeSpinner);
    String defType = getDefaultType();
    for (int i = 0; i < types.length; i++) if (types[i].equals(defType)) { typeSpinner.setSelection(i); break; }
    addCard.addView(typeSpinner, marginBottom(dp(7)));

    idInput = input("Minecraft ID", "例如：minecraft:diamond");
    addCard.addView(idInput, marginBottom(dp(7)));

    TextView quick = tv("常用 ID：minecraft:stone   minecraft:diamond   minecraft:oak_log   minecraft:diamond_sword", 11, sub(), false);
    quick.setPadding(dp(4), dp(4), dp(4), dp(4));
    addCard.addView(quick, marginBottom(dp(6)));

    LinearLayout subpackRow = row();
    subpackRow.setGravity(Gravity.CENTER_VERTICAL);
    subpackRow.addView(tv("子包", 13, text(), true), new LinearLayout.LayoutParams(dp(48), -2));
    subpackSpinner = new Spinner(this);
    List<String> spinnerItems = new ArrayList<>(); spinnerItems.add("默认/根包"); spinnerItems.addAll(subpackList);
    subpackAdapter = createSpinnerAdapter(spinnerItems);
    subpackSpinner.setAdapter(subpackAdapter); setupSpinnerPopup(subpackSpinner);
    subpackRow.addView(subpackSpinner, new LinearLayout.LayoutParams(0, -2, 1));
    addCard.addView(subpackRow, marginBottom(dp(7)));

    LinearLayout switchRow = row(); switchRow.setGravity(Gravity.CENTER_VERTICAL);
    independentCheckBox = new CheckBox(this); independentCheckBox.setText("独立启用"); independentCheckBox.setTextColor(text());
    switchRow.addView(independentCheckBox);
    switchNameInput = input("开关名称", "例如：显示钻石剑"); switchNameInput.setVisibility(View.GONE);
    switchRow.addView(switchNameInput, new LinearLayout.LayoutParams(0, -2, 1));
    independentCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> switchNameInput.setVisibility(isChecked ? View.VISIBLE : View.GONE));
    addCard.addView(switchRow, marginBottom(dp(7)));

    TextView choose = secondaryButton("▣  选择图片"); choose.setOnClickListener(v -> pickImage());
    addCard.addView(choose, marginBottom(dp(7)));
    preview = new ImageView(this); preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE); preview.setBackground(round(card2(), 18, line(), 1));
    addCard.addView(preview, new LinearLayout.LayoutParams(-1, dp(180)));
    selectedText = tv("尚未选择图片", 12, sub(), false); addCard.addView(selectedText, marginTop(dp(5)));
    TextView add = primaryButton("＋  加入编辑列表"); add.setOnClickListener(v -> addEntry());
    addCard.addView(add, marginTop(dp(10)));
    status = tv("准备就绪", 12, sub(), false); addCard.addView(status, marginTop(dp(6)));
    box.addView(addCard, marginBottom(dp(14)));

    LinearLayout listCard = cardColumn();
    listCard.addView(tv("当前材质", 18, text(), true), marginBottom(dp(7)));
    LinearLayout filterContainer = createFilterView(false); listCard.addView(filterContainer, marginBottom(dp(7)));
    editorListContainer = new LinearLayout(this); editorListContainer.setOrientation(LinearLayout.VERTICAL);
    listCard.addView(editorListContainer);
    refreshEditorList();
    TextView clearAll = secondaryButton("清空当前编辑列表");
    clearAll.setOnClickListener(v -> {
        entries.clear(); refreshEditorList(); setStatus("已清空"); updateEditorListTitle();
    });
    listCard.addView(clearAll, marginTop(dp(8)));
    box.addView(listCard);

    scroll.addView(box); rootFrame.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
    pageHost.addView(rootFrame, new LinearLayout.LayoutParams(-1, -1));
    updateFilterOptions();
}

private LinearLayout createFilterView(boolean isSticky) {
    LinearLayout container = new LinearLayout(this);
    container.setOrientation(LinearLayout.VERTICAL);
    container.setPadding(0, dp(8), 0, dp(8));

    LinearLayout subpackRow = row();
    subpackRow.setGravity(Gravity.CENTER_VERTICAL);
    TextView subpackLabel = tv("子包筛选:", 14, text(), true);
    subpackRow.addView(subpackLabel, new LinearLayout.LayoutParams(-2, -2));

    Spinner subpackSpinner;
    if (isSticky) {
        stickySubpackSpinner = new Spinner(this);
        subpackSpinner = stickySubpackSpinner;
    } else {
        filterSubpackSpinner = new Spinner(this);
        subpackSpinner = filterSubpackSpinner;
    }
    setupFilterSpinner(subpackSpinner, () -> {
        String selected = (String) subpackSpinner.getSelectedItem();
        currentSubpackFilter = (selected == null || selected.equals("全部")) ? "" : selected;
        refreshEditorList();
    });
    subpackRow.addView(subpackSpinner, new LinearLayout.LayoutParams(0, -2, 1));
    container.addView(subpackRow, marginBottom(dp(4)));

    LinearLayout switchRow = row();
    switchRow.setGravity(Gravity.CENTER_VERTICAL);
    TextView switchLabel = tv("开关筛选:", 14, text(), true);
    switchRow.addView(switchLabel, new LinearLayout.LayoutParams(-2, -2));

    Spinner switchSpinner;
    if (isSticky) {
        stickySwitchSpinner = new Spinner(this);
        switchSpinner = stickySwitchSpinner;
    } else {
        filterSwitchSpinner = new Spinner(this);
        switchSpinner = filterSwitchSpinner;
    }
    setupFilterSpinner(switchSpinner, () -> {
        String selected = (String) switchSpinner.getSelectedItem();
        currentSwitchFilter = (selected == null || selected.equals("全部")) ? "" : selected;
        refreshEditorList();
    });
    switchRow.addView(switchSpinner, new LinearLayout.LayoutParams(0, -2, 1));
    container.addView(switchRow);

    return container;
}

private void updateEditorListTitle() {
    if (pageHost == null) return;
    ViewGroup parent = (ViewGroup) pageHost.getChildAt(0);
    if (parent == null) return;
    ViewGroup content = (ViewGroup) parent.getChildAt(0);
    if (content == null) return;
    for (int i = 0; i < content.getChildCount(); i++) {
        View v = content.getChildAt(i);
        if (v instanceof TextView) {
            String text = ((TextView) v).getText().toString();
            if (text.startsWith("当前项目列表")) {
                ((TextView) v).setText("当前项目列表 (" + entries.size() + ")");
                break;
            }
        }
    }
}

private void showAddSubpackDialog() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("添加子包");
    final EditText input = new EditText(this);
    input.setHint("输入子包名称");
    input.setTextColor(text());
    input.setHintTextColor(sub());
    input.setSingleLine(true);
    builder.setView(input);
    builder.setPositiveButton("添加", (dialog, which) -> {
        String name = input.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (subpackList.contains(name)) {
            Toast.makeText(this, "子包已存在", Toast.LENGTH_SHORT).show();
            return;
        }
        subpackList.add(name);
        saveSubpackList();
        refreshSubpackSpinner();
        updateFilterOptions();
        Toast.makeText(this, "子包已添加", Toast.LENGTH_SHORT).show();
    });
    builder.setNegativeButton("取消", null);
    builder.show();
}

private void deleteSelectedSubpack() {
    if (subpackSpinner == null) return;
    String selected = (String) subpackSpinner.getSelectedItem();
    if (selected == null || selected.equals("默认/根包")) {
        Toast.makeText(this, "不能删除默认根包", Toast.LENGTH_SHORT).show();
        return;
    }
    new AlertDialog.Builder(this)
            .setTitle("删除子包")
            .setMessage("确定要删除子包 \"" + selected + "\" 吗？\n所有属于该子包的材质将自动移至默认根包。")
            .setPositiveButton("删除", (dialog, which) -> {
                subpackList.remove(selected);
                saveSubpackList();
                for (Entry e : entries) {
                    if (e.subpack.equals(selected)) e.subpack = "";
                }
                refreshEditorList();
                refreshSubpackSpinner();
                updateFilterOptions();
                Toast.makeText(this, "已删除子包", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消", null)
            .show();
}

private void refreshEditorList() {
    if (editorListContainer == null) return;
    editorListContainer.removeAllViews();

    List<Entry> filteredEntries = new ArrayList<>();
    for (Entry e : entries) {
        boolean subpackMatch = currentSubpackFilter.isEmpty() || e.subpack.equals(currentSubpackFilter);
        boolean switchMatch = currentSwitchFilter.isEmpty() || e.switchName.equals(currentSwitchFilter);
        if (subpackMatch && switchMatch) filteredEntries.add(e);
    }

    if (filteredEntries.isEmpty()) {
        String msg = (currentSubpackFilter.isEmpty() && currentSwitchFilter.isEmpty()) ?
                "暂无项目" : "没有符合条件的材质";
        editorListContainer.addView(tv(msg, 13, sub(), false));
        updateEditorListTitle();
        return;
    }

    LinearLayout loadingLayout = new LinearLayout(this);
    loadingLayout.setOrientation(LinearLayout.VERTICAL);
    loadingLayout.setGravity(Gravity.CENTER);
    loadingLayout.setPadding(dp(20), dp(40), dp(20), dp(40));

    ProgressBar progressBar = new ProgressBar(this);
    loadingLayout.addView(progressBar);

    TextView loadingText = tv("正在加载材质列表...", 14, sub(), false);
    loadingText.setGravity(Gravity.CENTER);
    loadingText.setPadding(0, dp(12), 0, 0);
    loadingLayout.addView(loadingText);

    editorListContainer.addView(loadingLayout);

    new Thread(() -> {
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        runOnUiThread(() -> {
            editorListContainer.removeAllViews();
            for (Entry e : filteredEntries) {
                int index = entries.indexOf(e);
                LinearLayout row = buildEntryRow(e, index);
                editorListContainer.addView(row, marginBottom(dp(8)));
            }
            updateEditorListTitle();
        });
    }).start();
}

private LinearLayout buildEntryRow(Entry e, int index) {
    LinearLayout card = new LinearLayout(this);
    card.setOrientation(LinearLayout.HORIZONTAL);
    card.setGravity(Gravity.CENTER_VERTICAL);
    card.setPadding(dp(10), dp(8), dp(8), dp(8));
    card.setBackground(round(card2(), 15, line(), 1));

    ImageView img = new ImageView(this);
    img.setScaleType(ImageView.ScaleType.CENTER_CROP);
    img.setBackground(round(card(), 12, line(), 1));
    if (e.imagePath != null && !e.imagePath.isEmpty()) {
        File f = new File(e.imagePath);
        if (f.exists()) {
            Bitmap bmp = BitmapFactory.decodeFile(e.imagePath);
            if (bmp != null) img.setImageBitmap(bmp);
        }
    }
    LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(dp(56), dp(56));
    imgParams.rightMargin = dp(10);
    card.addView(img, imgParams);

    LinearLayout info = column();
    String subInfo = e.subpack.isEmpty() ? "" : " [子包:" + e.subpack + "]";
    String switchInfo = e.independentEnabled && !e.switchName.isEmpty() ? " [开关:" + e.switchName + "]" : "";
    info.addView(tv(e.type + "  " + e.id + subInfo + switchInfo, 14, text(), true));

    LinearLayout buttons = row();
    buttons.setGravity(Gravity.CENTER_VERTICAL);
    TextView edit = tv("✏ 修改", 12, GREEN, true);
    edit.setPadding(dp(8), dp(4), dp(8), dp(4));
    edit.setBackground(round(blend(card(), GREEN, 0.15f), 10, Color.TRANSPARENT, 0));
    edit.setOnClickListener(v -> openEditEntryDialog(index));
    buttons.addView(edit);

    TextView del = tv("🗑 删除", 12, Color.rgb(255, 80, 80), true);
    del.setPadding(dp(8), dp(4), dp(8), dp(4));
    del.setBackground(round(blend(card(), Color.rgb(255, 80, 80), 0.15f), 10, Color.TRANSPARENT, 0));
    del.setOnClickListener(v -> {
        entries.remove(index);
        refreshEditorList();
        setStatus("已删除");
    });
    buttons.addView(del, marginLeft(dp(6)));
    info.addView(buttons, marginTop(dp(4)));

    card.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
    return card;
}

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
    typeSpin.setAdapter(createSpinnerAdapter(types));
    setupSpinnerPopup(typeSpin);
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

    Spinner subEditSpinner = new Spinner(this);
    List<String> items = new ArrayList<>();
    items.add("默认/根包");
    items.addAll(subpackList);
    subEditSpinner.setAdapter(createSpinnerAdapter(items));
    setupSpinnerPopup(subEditSpinner);
    int pos = 0;
    if (!oldEntry.subpack.isEmpty()) {
        int idx = subpackList.indexOf(oldEntry.subpack);
        if (idx >= 0) pos = idx + 1;
    }
    subEditSpinner.setSelection(pos);
    root.addView(subEditSpinner, marginBottom(dp(10)));

    LinearLayout switchRow = row();
    switchRow.setGravity(Gravity.CENTER_VERTICAL);
    CheckBox editCheckBox = new CheckBox(this);
    editCheckBox.setText("独立启用");
    editCheckBox.setTextColor(text());
    editCheckBox.setChecked(oldEntry.independentEnabled);
    switchRow.addView(editCheckBox);

    EditText editSwitchName = input("开关名称", "例如：显示钻石剑");
    editSwitchName.setText(oldEntry.switchName);
    editSwitchName.setVisibility(oldEntry.independentEnabled ? View.VISIBLE : View.GONE);
    switchRow.addView(editSwitchName, new LinearLayout.LayoutParams(0, -2, 1));

    editCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
        editSwitchName.setVisibility(isChecked ? View.VISIBLE : View.GONE);
    });
    root.addView(switchRow, marginBottom(dp(10)));

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
        String selectedSub = (String) subEditSpinner.getSelectedItem();
        String newSubpack = selectedSub.equals("默认/根包") ? "" : selectedSub;
        boolean newIndependent = editCheckBox.isChecked();
        String newSwitchName = newIndependent ? editSwitchName.getText().toString().trim() : "";
        if (newIndependent && newSwitchName.isEmpty()) {
            Toast.makeText(this, "请输入开关名称", Toast.LENGTH_SHORT).show();
            return;
        }
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

        entries.set(index, new Entry(newId, newType, newSubpack, newImagePath, newIndependent, newSwitchName));
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
}private void addRecentRows(LinearLayout parent, int max) {
    ArrayList<Project> projects = loadAllProjects();
    if (projects.isEmpty()) {
        parent.addView(tv("还没有项目。点击“创建材质”开始你的第一个作品。", 13, sub(), false), marginTop(dp(6)));
        return;
    }
    for (int i = 0; i < Math.min(max, projects.size()); i++) {
        addProjectRow(parent, projects.get(i));
    }
}

private void addProjectRow(LinearLayout parent, Project p) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(dp(12), dp(10), dp(10), dp(10));
    row.setBackground(round(card2(), 15, Color.TRANSPARENT, 0));

    ImageView icon = new ImageView(this);
    icon.setImageResource(R.drawable.title_icon);
    LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(28), dp(28));
    iconParams.gravity = Gravity.CENTER_VERTICAL;
    row.addView(icon, iconParams);

    LinearLayout info = column();
    info.addView(tv(p.name, 14, text(), true));
    info.addView(tv(p.date + "   " + p.entries.size() + " 个材质", 12, sub(), false), marginTop(dp(3)));
    row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

    TextView state = tv("打开", 11, GREEN, true);
    state.setGravity(Gravity.CENTER);
    state.setPadding(dp(10), 0, dp(10), 0);
    state.setBackground(round(darkMode ? Color.rgb(28, 72, 38) : Color.rgb(231, 248, 235), 12, Color.TRANSPARENT, 0));
    row.addView(state, new LinearLayout.LayoutParams(dp(64), dp(30)));

    row.setOnClickListener(v -> {
        currentProject = p;
        entries.clear();
        entries.addAll(p.entries);
        showPage(4);
    });
    parent.addView(row, marginBottom(dp(7)));
}

private void addGuide(LinearLayout parent, String iconText, String title,
                      String desc, int accent, View.OnClickListener click) {
    LinearLayout r = new LinearLayout(this);
    r.setOrientation(LinearLayout.HORIZONTAL);
    r.setGravity(Gravity.CENTER_VERTICAL);
    r.setPadding(dp(10), dp(8), dp(8), dp(8));
    r.setBackground(round(card2(), 13, Color.TRANSPARENT, 0));

    TextView icon = tv(iconText, 20, accent, true);
    icon.setGravity(Gravity.CENTER);
    r.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));

    LinearLayout info = column();
    info.addView(tv(title, 14, text(), true));
    info.addView(tv(desc, 11, sub(), false), marginTop(dp(2)));
    r.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

    TextView arrow = tv("›", 22, sub(), false);
    arrow.setGravity(Gravity.CENTER);
    r.addView(arrow, new LinearLayout.LayoutParams(dp(25), dp(42)));
    r.setOnClickListener(click);
    parent.addView(r, marginBottom(dp(6)));
}

private void addPerson(LinearLayout parent, String name, String role, int accent) {
    LinearLayout r = row();
    TextView avatar = tv("●", 19, accent, true); avatar.setGravity(Gravity.CENTER);
    r.addView(avatar, new LinearLayout.LayoutParams(dp(40), dp(42)));
    LinearLayout info = column();
    info.addView(tv(name, 14, text(), true));
    info.addView(tv(role, 11, sub(), false), marginTop(dp(2)));
    r.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
    parent.addView(r, marginBottom(dp(5)));
}

private void addSetting(LinearLayout parent, String title, String value, String icon, View.OnClickListener click) {
    LinearLayout r = row(); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(dp(5), dp(9), dp(5), dp(9));
    TextView ic = tv(icon, 20, GREEN, true); ic.setGravity(Gravity.CENTER);
    r.addView(ic, new LinearLayout.LayoutParams(dp(42), dp(44)));
    LinearLayout info = column(); info.addView(tv(title, 14, text(), true)); info.addView(tv(value, 11, sub(), false), marginTop(dp(2)));
    r.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
    r.addView(tv("›", 21, sub(), false));
    r.setOnClickListener(click); parent.addView(r, marginBottom(dp(2)));
}

private LinearLayout actionCard(String icon, String title, String desc, int accent, View.OnClickListener click) {
    LinearLayout c = cardColumn(); c.setGravity(Gravity.CENTER); c.setPadding(dp(8), dp(14), dp(8), dp(12)); c.setOnClickListener(click);
    TextView ic = tv(icon, 32, accent, true); ic.setGravity(Gravity.CENTER); c.addView(ic, new LinearLayout.LayoutParams(-1, dp(42)));
    c.addView(tv(title, 14, text(), true), centerParams());
    c.addView(tv(desc, 10, sub(), false), centerMarginParams(dp(4)));
    c.setBackground(round(darkMode ? blend(card(), accent, 0.12f) : blend(card(), accent, 0.10f), 18, blend(accent, bg(), 0.55f), 1));
    return c;
}

private LinearLayout statItem(String label, String value, int accent) {
    LinearLayout box = column();
    box.setGravity(Gravity.CENTER);
    box.setPadding(dp(6), dp(8), dp(6), dp(6));
    box.setBackground(round(card2(), 14, Color.TRANSPARENT, 0));
    box.addView(tv(value, 20, accent, true), centerParams());
    box.addView(tv(label, 11, sub(), false), centerMarginParams(dp(2)));
    return box;
}

private LinearLayout.LayoutParams equalActionParams(int position) {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(138), 1f);
    int gap = dp(6);
    if (position == 0) lp.setMargins(0, 0, gap, 0);
    else if (position == 1) lp.setMargins(gap, 0, gap, 0);
    else lp.setMargins(gap, 0, 0, 0);
    return lp;
}

private LinearLayout sectionTitle(String title, String desc) {
    LinearLayout l = column();
    l.addView(tv(title, 18, text(), true));
    l.addView(tv(desc, 12, sub(), false), marginTop(dp(3)));
    return l;
}

private LinearLayout topTitle(String title, String desc) {
    LinearLayout l = column();
    l.addView(tv(title, 26, text(), true));
    l.addView(tv(desc, 13, sub(), false), marginTop(dp(4)));
    return l;
}

private String getDefaultType() {
    return getPreferences(MODE_PRIVATE).getString("default_material_type", "物品");
}

private void showDefaultTypeDialog() {
    final String[] types = {"物品", "方块", "生物", "UI", "天空"};
    final String current = getDefaultType();
    int checked = 0;
    for (int i = 0; i < types.length; i++) {
        if (types[i].equals(current)) { checked = i; break; }
    }
    new AlertDialog.Builder(this)
            .setTitle("默认材质类型")
            .setSingleChoiceItems(types, checked, (dialog, which) -> {
                getPreferences(MODE_PRIVATE).edit().putString("default_material_type", types[which]).apply();
                dialog.dismiss();
                showPage(3);
            })
            .setNegativeButton("取消", null)
            .show();
}

private void toggleTheme() {
    darkMode = !darkMode;
    getPreferences(MODE_PRIVATE).edit().putBoolean("dark_mode", darkMode).apply();
    buildShell();
    showPage(currentPage);
}

private void showTextureGuide() {
    new AlertDialog.Builder(this)
            .setTitle("如何替换物品纹理")
            .setMessage("① 点击“创建材质”\n\n② 选择类型“物品”\n\n③ 输入 Minecraft ID\n例如：minecraft:diamond\n\n④ 点击“选择图片”\n选择你准备好的 PNG 图片。\n\n⑤ 点击“加入编辑列表”\n\n⑥ 前往“生成”，填写材质包名称和作者。\n\n⑦ 点击“生成基岩版材质包”。\n\n生成的 .mcpack 可以直接用于 Minecraft 基岩版。")
            .setPositiveButton("知道了", null)
            .show();
}

private void showPackStructure() {
    new AlertDialog.Builder(this)
            .setTitle("材质包结构说明")
            .setMessage("一个 Minecraft 基岩版材质包通常包含：\n\n📄 manifest.json\n材质包的基本信息和版本。\n\n📁 textures/\n存放各种纹理图片。\n\n📁 textures/items/\n物品纹理。\n\n📁 textures/blocks/\n方块纹理。\n\n📁 textures/entity/\n生物相关纹理。\n\n📁 textures/ui/\nUI 相关纹理。\n\n📁 textures/environment/\n天空等环境纹理。\n\n📁 subpacks/\n（可选）子包文件夹，每个子包可独立覆盖纹理。\n本编辑器现已支持子包导入与生成。")
            .setPositiveButton("知道了", null)
            .show();
}

private void showFAQ() {
    new AlertDialog.Builder(this)
            .setTitle("常见问题解答")
            .setMessage("Q：需要电脑才能制作吗？\nA：不需要，本软件就是面向手机用户设计的。\n\nQ：支持哪些 Minecraft？\nA：当前目标为 Minecraft 基岩版 1.26+。\n\nQ：图片必须是什么格式？\nA：建议使用 PNG 图片。\n\nQ：为什么必须输入 namespace:id？\nA：这是 Minecraft 识别物品和方块的重要方式。\n例如：minecraft:diamond。\n\nQ：生成后怎么安装？\nA：保存 .mcpack 后，可以使用 Minecraft 打开。\n\nQ：可以修改模组里的物品吗？\nA：可以尝试输入对应模组提供的完整 ID，例如：modid:item_name。\n\nQ：会不会影响原版材质？\nA：材质包主要覆盖你制作的对应纹理，具体效果取决于 Minecraft 和资源包结构。")
            .setPositiveButton("知道了", null)
            .show();
}

private void showCredits() {
    new AlertDialog.Builder(this).setTitle("制作名单")
            .setMessage("K9T2\n项目开发 / 基岩版材质工具\n\n测试与建议\n感谢所有参与测试、反馈问题和提出功能建议的玩家\n\n特别感谢\n每一位使用、分享和支持本项目的人。")
            .setPositiveButton("好的", null).show();
}

private void showChangelog() {
    new AlertDialog.Builder(this).setTitle("更新日志")
            .setMessage("1.2.0\n• 全新首页与双主题 UI\n• 增加材质包历史列表\n• 增加生成中心\n• 增加制作名单\n• 优化手机端操作体验\n\n材质生成核心功能保持不变。 ")
            .setPositiveButton("关闭", null).show();
}

private void showAbout() {
    new AlertDialog.Builder(this).setTitle("关于软件")
            .setMessage("MC 基岩版材质编辑器\n版本 1.3.0-bedrock\n\n面向普通玩家的手机本地材质包制作工具。\n支持 Minecraft Bedrock 1.26+ 材质包生成。 ")
            .setPositiveButton("关闭", null).show();
}

private void importPack() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("导入免责声明");
    builder.setMessage("导入第三方材质包可能涉及版权问题，请确保您有权使用该材质包。\n因导入造成的任何侵权损失，与软件作者无关。\n\n点击下方「阅读完整协议」以查看完整免责声明及用户协议。");
    builder.setPositiveButton("继续导入", (dialog, which) -> {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, IMPORT_PACK);
    });
    builder.setNegativeButton("取消", null);
    builder.setNeutralButton("阅读完整协议", (dialog, which) -> showFullDisclaimerDialog());
    builder.show();
}

private void pickImage() {
    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    i.setType("image/*");
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    startActivityForResult(i, PICK_IMAGE);
}

@Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null) {
        selectedUri = data.getData();
        if (selectedUri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(selectedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}
        try {
            Bitmap b = BitmapFactory.decodeStream(getContentResolver().openInputStream(selectedUri));
            if (b == null) throw new IOException("无法解析图片");
            preview.setImageBitmap(b);
            selectedText.setText("已选择：" + fileName(selectedUri));
        } catch (Exception e) {
            Toast.makeText(this, "读取失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    if (requestCode == SAVE_PACK && resultCode == RESULT_OK && data != null && pendingPack != null) {
        Uri target = data.getData();
        if (target == null) return;
        try (InputStream in = new FileInputStream(pendingPack); OutputStream out = getContentResolver().openOutputStream(target)) {
            if (out == null) throw new IOException("无法打开保存位置");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            out.flush();
            setStatus("材质包已保存");
            Toast.makeText(this, "生成完成！", Toast.LENGTH_LONG).show();
            showPage(1);
        } catch (Exception e) {
            Toast.makeText(this, "保存失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    if (requestCode == IMPORT_PACK && resultCode == RESULT_OK && data != null) {
        Uri uri = data.getData();
        if (uri != null) {
            importMcpack(uri);
        }
    }

    if (requestCode == EDIT_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
        Uri uri = data.getData();
        if (uri != null) {
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            try {
                Bitmap b = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
                if (b != null) {
                    currentEditSelectedUri = uri;
                    if (currentEditImage != null) {
                        currentEditImage.setImageBitmap(b);
                    }
                } else {
                    Toast.makeText(this, "无法读取图片", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "读取图片失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }
}

private void importMcpack(Uri uri) {
    new ImportTask(uri).execute();
}

private void scanTextures(File dir, String subpack, ArrayList<Entry> out) {
    File[] files = dir.listFiles();
    if (files == null) return;
    for (File f : files) {
        if (f.isDirectory()) {
            scanTextures(f, subpack, out);
        } else if (f.getName().toLowerCase().endsWith(".png")) {
            String parent = dir.getAbsolutePath();
            String base = new File(parent).getParent();
            if (base == null) continue;
            String relative = f.getAbsolutePath().substring(base.length() + 1);
            String[] parts = relative.split("/");
            String type = "物品";
            String id = "unknown:" + f.getName().replace(".png", "");
            if (parts.length >= 2) {
                String folder = parts[0];
                String filename = parts[parts.length - 1].replace(".png", "");
                if (folder.equals("items") || folder.equals("item")) {
                    type = "物品";
                    if (filename.contains("_")) {
                        int idx = filename.lastIndexOf('_');
                        id = filename.substring(0, idx) + ":" + filename.substring(idx + 1);
                    } else {
                        id = "minecraft:" + filename;
                    }
                } else if (folder.equals("blocks") || folder.equals("block")) {
                    type = "方块";
                    if (filename.contains("_")) {
                        int idx = filename.lastIndexOf('_');
                        id = filename.substring(0, idx) + ":" + filename.substring(idx + 1);
                    } else {
                        id = "minecraft:" + filename;
                    }
                } else if (folder.equals("entity") || folder.equals("mobs")) {
                    type = "生物";
                    id = "minecraft:" + filename;
                } else if (folder.equals("environment") || folder.equals("sky")) {
                    type = "天空";
                    id = "minecraft:" + filename;
                } else if (folder.equals("ui")) {
                    type = "UI";
                    id = "minecraft:" + filename;
                }
            }
            out.add(new Entry(id, type, subpack, f.getAbsolutePath(), false, ""));
        }
    }
}

private void scanModels(File dir, ArrayList<Entry> out) {
    File[] files = dir.listFiles();
    if (files == null) return;
    for (File f : files) {
        if (f.isDirectory()) {
            scanModels(f, out);
        } else if (f.getName().toLowerCase().endsWith(".json")) {
            try {
                String content = new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                JSONObject root = new JSONObject(content);
                JSONObject textures = root.optJSONObject("textures");
                if (textures == null) continue;
                String textureValue = null;
                Iterator<String> keys = textures.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    textureValue = textures.getString(key);
                    break;
                }
                if (textureValue == null || textureValue.isEmpty()) continue;
                if (textureValue.contains(":")) {
                    textureValue = textureValue.substring(textureValue.indexOf(":") + 1);
                }
                File extractRoot = dir.getParentFile().getParentFile();
                if (extractRoot == null) extractRoot = dir.getParentFile();
                File actualTexture = findTextureFile(extractRoot, textureValue);
                if (actualTexture == null) continue;
                String relativePath = f.getAbsolutePath().substring(extractRoot.getAbsolutePath().length() + 1);
                String type = "物品";
                if (relativePath.startsWith("models/block/")) {
                    type = "方块";
                } else if (relativePath.startsWith("models/item/")) {
                    type = "物品";
                }
                String baseName = f.getName().replace(".json", "");
                String id = "minecraft:" + baseName;
                out.add(new Entry(id, type, "", actualTexture.getAbsolutePath(), false, ""));
            } catch (Exception e) {}
        }
    }
}

private File findTextureFile(File root, String textureName) {
    File candidate = new File(root, "textures/" + textureName + ".png");
    if (candidate.exists()) return candidate;
    File texturesDir = new File(root, "textures");
    if (texturesDir.exists()) {
        return findFileRecursive(texturesDir, textureName + ".png");
    }
    return null;
}

private File findFileRecursive(File dir, String fileName) {
    File[] files = dir.listFiles();
    if (files == null) return null;
    for (File f : files) {
        if (f.isDirectory()) {
            File result = findFileRecursive(f, fileName);
            if (result != null) return result;
        } else if (f.getName().equals(fileName)) {
            return f;
        }
    }
    return null;
}

private void unzip(File zipFile, File destDir) throws IOException {
    try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            File outFile = new File(destDir, entry.getName());
            if (entry.isDirectory()) {
                outFile.mkdirs();
            } else {
                File parent = outFile.getParentFile();
                if (parent != null) parent.mkdirs();
                try (OutputStream os = new BufferedOutputStream(new FileOutputStream(outFile))) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = zis.read(buf)) != -1) os.write(buf, 0, n);
                }
            }
            zis.closeEntry();
        }
    }
}    private void addEntry() {
        String id = idInput.getText().toString().trim();
        String type = typeSpinner.getSelectedItem().toString();
        if (!validId(id)) {
            Toast.makeText(this, "ID 格式错误，需要 namespace:id", Toast.LENGTH_LONG).show();
            return;
        }
        if (selectedUri == null) {
            Toast.makeText(this, "请先选择图片", Toast.LENGTH_LONG).show();
            return;
        }
        String imagePath = copyImageToProject(selectedUri);
        if (imagePath == null) {
            Toast.makeText(this, "图片复制失败", Toast.LENGTH_LONG).show();
            return;
        }
        String selectedSub = (String) subpackSpinner.getSelectedItem();
        String subpack = selectedSub.equals("默认/根包") ? "" : selectedSub;

        boolean independent = independentCheckBox.isChecked();
        String switchName = independent ? switchNameInput.getText().toString().trim() : "";
        if (independent && switchName.isEmpty()) {
            Toast.makeText(this, "请输入开关名称", Toast.LENGTH_SHORT).show();
            return;
        }

        for (Entry e : entries) {
            if (e.id.equals(id) && e.type.equals(type) && e.subpack.equals(subpack)) {
                Toast.makeText(this, "该项目已存在（相同ID、类型和子包）", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        entries.add(new Entry(id, type, subpack, imagePath, independent, switchName));
        refreshEditorList();
        updateFilterOptions();
        setStatus("已加入：" + id + (subpack.isEmpty() ? "" : " (子包:" + subpack + ")") +
                (independent ? " [开关:" + switchName + "]" : ""));
        idInput.setText("");
        selectedUri = null;
        preview.setImageDrawable(null);
        selectedText.setText("尚未选择图片");
        independentCheckBox.setChecked(false);
        switchNameInput.setVisibility(View.GONE);
        switchNameInput.setText("");
    }

    private String copyImageToProject(Uri uri) {
        try {
            File imagesDir = new File(getFilesDir(), "temp_images");
            if (!imagesDir.exists()) imagesDir.mkdirs();
            String filename = System.currentTimeMillis() + "_" + fileName(uri);
            File dest = new File(imagesDir, filename);
            try (InputStream in = getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            }
            return dest.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    private void setStatus(String s) { if (status != null) status.setText(s); }

    private interface BuildProgressListener {
        void update(int progress, String message);
    }

    private void generate() {
        if (entries.isEmpty()) {
            Toast.makeText(this, "请先在编辑器加入至少一个项目", Toast.LENGTH_LONG).show();
            showPage(4);
            return;
        }

        final String name;
        final String author;
        final String description;
        try {
            String n = (packNameInput == null ? "" : packNameInput.getText().toString().trim());
            if (n.isEmpty()) n = "我的基岩材质";
            if (!n.toLowerCase(Locale.ROOT).endsWith(".mcpack")) n += ".mcpack";
            name = n;

            String a = authorInput == null ? "熬糕aogao" : authorInput.getText().toString().trim();
            author = a.isEmpty() ? "熬糕aogao" : a;

            String d = descriptionInput == null ? "个人制作的 Minecraft 基岩版材质包" : descriptionInput.getText().toString().trim();
            description = d.isEmpty() ? "个人制作的 Minecraft 基岩版材质包" : d;
        } catch (Exception e) {
            Toast.makeText(this, "读取生成设置失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("正在生成材质包");
        progressDialog.setMessage("准备生成...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(100);
        progressDialog.setProgress(0);
        progressDialog.setCancelable(false);
        progressDialog.setIndeterminate(false);
        progressDialog.show();

        new AsyncTask<Void, Integer, Exception>() {
            @Override
            protected Exception doInBackground(Void... ignored) {
                try {
                    File work = new File(getCacheDir(), "mcpack_build");
                    delete(work);
                    if (!work.mkdirs()) throw new IOException("无法创建工作目录");

                    publishProgress(2);
                    buildBedrock(work, author, description, (progress, msg) -> publishProgress(progress));

                    pendingPack = new File(getCacheDir(), name);
                    if (pendingPack.exists() && !pendingPack.delete()) {
                        throw new IOException("无法覆盖旧的材质包");
                    }

                    publishProgress(70);
                    zip(work, pendingPack, (progress, msg) -> publishProgress(70 + (int)(progress * 0.30f)));
                    publishProgress(100);

                    pendingHistoryName = name.substring(0, name.length() - ".mcpack".length());
                    return null;
                } catch (Exception e) {
                    return e;
                }
            }

            @Override
            protected void onProgressUpdate(Integer... values) {
                if (values == null || values.length == 0) return;
                int p = Math.max(0, Math.min(100, values[0]));
                progressDialog.setProgress(p);
                String message;
                if (p < 5) message = "准备工作目录";
                else if (p < 60) message = "正在处理纹理";
                else if (p < 70) message = "材质文件准备完成";
                else if (p < 100) message = "正在压缩材质包";
                else message = "生成完成";
                progressDialog.setMessage(message + "  " + p + "%");
            }

            @Override
            protected void onPostExecute(Exception error) {
                progressDialog.dismiss();
                if (error != null) {
                    Toast.makeText(MainActivity.this, "生成失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
                    setStatus("生成失败");
                    return;
                }

                saveProject();

                Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                save.setType("application/octet-stream");
                save.putExtra(Intent.EXTRA_TITLE, name);
                startActivityForResult(save, SAVE_PACK);
                setStatus("材质包生成完成");
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void buildBedrock(File root, String author, String description) throws Exception {
        buildBedrock(root, author, description, null);
    }

    private void buildBedrock(File root, String author, String description, BuildProgressListener progress) throws Exception {
        Set<String> subpackSet = new HashSet<>();
        for (Entry e : entries) {
            subpackSet.add(e.subpack);
        }
        File rootTextures = new File(root, "textures");
        rootTextures.mkdirs();
        Map<String, File> subpackTexturesMap = new HashMap<>();
        for (String sub : subpackSet) {
            if (sub.isEmpty()) continue;
            File subDir = new File(root, "subpacks/" + sub + "/textures");
            subDir.mkdirs();
            subpackTexturesMap.put(sub, subDir);
        }

        Map<String, Map<String, String>> itemMapBySubpack = new HashMap<>();
        Map<String, Map<String, String>> terrainMapBySubpack = new HashMap<>();

        int totalEntries = Math.max(1, entries.size());
        int processedEntries = 0;
        for (Entry e : entries) {
            String sub = e.subpack;
            File targetTexturesDir;
            if (sub.isEmpty()) {
                targetTexturesDir = rootTextures;
            } else {
                targetTexturesDir = subpackTexturesMap.get(sub);
                if (targetTexturesDir == null) continue;
            }

            String[] p = e.id.split(":", 2);
            if (p.length != 2) continue;
            String namespace = p[0];
            String id = p[1];
            String base = safe(id);
            File out = null;
            String relPath = null;

            if ("物品".equals(e.type)) {
                out = new File(targetTexturesDir, "items/" + namespace + "_" + base + ".png");
                relPath = "textures/items/" + namespace + "_" + base;
                copyAsPng(new File(e.imagePath), out);
                itemMapBySubpack.computeIfAbsent(sub, k -> new LinkedHashMap<>()).put(e.id, relPath);
            } else if ("方块".equals(e.type)) {
                out = new File(targetTexturesDir, "blocks/" + namespace + "_" + base + ".png");
                relPath = "textures/blocks/" + namespace + "_" + base;
                copyAsPng(new File(e.imagePath), out);
                terrainMapBySubpack.computeIfAbsent(sub, k -> new LinkedHashMap<>()).put(e.id, relPath);
            } else if ("生物".equals(e.type)) {
                out = new File(targetTexturesDir, "entity/" + namespace + "_" + base + ".png");
                copyAsPng(new File(e.imagePath), out);
            } else if ("天空".equals(e.type)) {
                out = new File(targetTexturesDir, "environment/sky.png");
                copyAsPng(new File(e.imagePath), out);
            } else if ("UI".equals(e.type)) {
                out = new File(targetTexturesDir, "ui/" + namespace + "_" + base + ".png");
                copyAsPng(new File(e.imagePath), out);
            }

            processedEntries++;
            if (progress != null) {
                int progressPercent = 5 + (int) ((processedEntries / (float) totalEntries) * 55f);
                progress.update(progressPercent, "正在处理纹理 " + processedEntries + "/" + totalEntries);
            }
        }

        if (progress != null) progress.update(62, "正在生成纹理映射...");
        for (Map.Entry<String, Map<String, String>> entry : itemMapBySubpack.entrySet()) {
            String sub = entry.getKey();
            File targetDir = sub.isEmpty() ? rootTextures : subpackTexturesMap.get(sub);
            if (targetDir != null) {
                writeItemTextureJson(new File(targetDir, "item_texture.json"), entry.getValue());
            }
        }
        for (Map.Entry<String, Map<String, String>> entry : terrainMapBySubpack.entrySet()) {
            String sub = entry.getKey();
            File targetDir = sub.isEmpty() ? rootTextures : subpackTexturesMap.get(sub);
            if (targetDir != null) {
                writeTerrainTextureJson(new File(targetDir, "terrain_texture.json"), entry.getValue());
            }
        }

        boolean usesSettings = false;
        for (Entry e : entries) {
            if (e.independentEnabled && !e.switchName.isEmpty()) {
                usesSettings = true;
                break;
            }
        }

        int[] minVersion;
        if (usesSettings) {
            minVersion = new int[]{1, 21, 110};
        } else {
            minVersion = new int[]{1, 16, 0};
        }

        String h = UUID.randomUUID().toString();
        String m = UUID.randomUUID().toString();
        String safeAuthor = author == null || author.trim().isEmpty() ? "熬糕aogao" : author.trim();
        String versionStr = "1.0.0";

        StringBuilder manifest = new StringBuilder();
        manifest.append("{\n");
        manifest.append("  \"format_version\": 3,\n");
        manifest.append("  \"header\": {\n");
        manifest.append("    \"name\": \"MC 基岩版材质编辑器\",\n");
        manifest.append("    \"description\": \"" + json(description) + " | 作者：" + json(safeAuthor) + " | 禁止商业使用\",\n");
        manifest.append("    \"uuid\": \"" + h + "\",\n");
        manifest.append("    \"version\": \"" + versionStr + "\",\n");
        manifest.append("    \"min_engine_version\": \"" + minVersion[0] + "." + minVersion[1] + "." + minVersion[2] + "\",\n");
        manifest.append("    \"pack_optimization_version\": \"0.1.0\"\n");
        manifest.append("  },\n");
        manifest.append("  \"modules\": [{\n");
        manifest.append("    \"type\": \"resources\",\n");
        manifest.append("    \"uuid\": \"" + m + "\",\n");
        manifest.append("    \"version\": \"" + versionStr + "\"\n");
        manifest.append("  }],\n");
        manifest.append("  \"metadata\": {\n");
        manifest.append("    \"authors\": [\"" + json(safeAuthor) + "\"]\n");
        manifest.append("  }\n");

        List<String> subpackNames = new ArrayList<>(subpackSet);
        subpackNames.remove("");
        if (!subpackNames.isEmpty()) {
            manifest.append("  ,\"subpacks\": [\n");
            int idx = 0;
            for (String sub : subpackNames) {
                manifest.append("    {\n");
                manifest.append("      \"folder_name\": \"" + json(sub) + "\",\n");
                manifest.append("      \"name\": \"" + json(sub) + "\",\n");
                manifest.append("      \"memory_performance_tier\": 0\n");
                manifest.append("    }" + (++idx < subpackNames.size() ? "," : ""));
                manifest.append("\n");
            }
            manifest.append("  ]\n");
        }

        Map<String, Boolean> switchMap = new LinkedHashMap<>();
        for (Entry e : entries) {
            if (e.independentEnabled && !e.switchName.isEmpty()) {
                switchMap.put(e.switchName, false);
            }
        }
        if (!switchMap.isEmpty()) {
            manifest.append("  ,\"settings\": [\n");
            int idx = 0;
            for (Map.Entry<String, Boolean> entry : switchMap.entrySet()) {
                String key = entry.getKey();
                String safeName = key.matches("\\d+") ? "switch_" + key : key.replace(" ", "_").toLowerCase();
                manifest.append("    {\n");
                manifest.append("      \"type\": \"toggle\",\n");
                manifest.append("      \"text\": \"" + json(key) + "\",\n");
                manifest.append("      \"name\": \"" + json(safeName) + "\",\n");
                manifest.append("      \"default\": " + entry.getValue() + "\n");
                manifest.append("    }" + (++idx < switchMap.size() ? "," : ""));
                manifest.append("\n");
            }
            manifest.append("  ]\n");
        }

        manifest.append("}\n");
        write(new File(root, "manifest.json"), manifest.toString());

        String info = "MC 基岩版材质编辑器 - 制作信息\n\n作者：" + safeAuthor + "\n开发成员：熬糕aogao\n\n使用许可：仅限个人与非商业用途。\n禁止将本材质包用于商业销售、付费服务、商业宣传或其他商业用途。\n如需商业使用，请先联系作者获得授权。\n\n本文件随材质包一起发布，请勿删除。\n";
        write(new File(root, "制作信息.txt"), info);
        if (progress != null) progress.update(70, "材质文件准备完成");
    }

    private void writeItemTextureJson(File f, Map<String, String> map) throws Exception {
        StringBuilder j = new StringBuilder();
        j.append("{\n  \"resource_pack_name\":\"MC 基岩版材质编辑器\",\n  \"texture_name\":\"atlas.items\",\n  \"texture_data\":{\n");
        int i = 0;
        for (Map.Entry<String, String> x : map.entrySet()) {
            if (i++ > 0) j.append(",\n");
            j.append("    \"").append(json(x.getKey())).append("\":{\"textures\":\"").append(x.getValue()).append("\"}");
        }
        j.append("\n  }\n}\n");
        write(f, j.toString());
    }

    private void writeTerrainTextureJson(File f, Map<String, String> map) throws Exception {
        StringBuilder j = new StringBuilder();
        j.append("{\n  \"resource_pack_name\":\"MC 基岩版材质编辑器\",\n  \"texture_name\":\"atlas.terrain\",\n  \"texture_data\":{\n");
        int i = 0;
        for (Map.Entry<String, String> x : map.entrySet()) {
            if (i++ > 0) j.append(",\n");
            j.append("    \"").append(json(x.getKey())).append("\":{\"textures\":[\"").append(x.getValue()).append("\"]}");
        }
        j.append("\n  }\n}\n");
        write(f, j.toString());
    }

    private void copyAsPng(File srcFile, File out) throws Exception {
        if (!srcFile.exists()) throw new IOException("图片文件不存在");
        Bitmap b = BitmapFactory.decodeFile(srcFile.getAbsolutePath());
        if (b == null) throw new IOException("图片无法解码");
        File p = out.getParentFile();
        if (p != null) p.mkdirs();
        try (FileOutputStream fos = new FileOutputStream(out)) {
            if (!b.compress(Bitmap.CompressFormat.PNG, 100, fos)) throw new IOException("PNG 编码失败");
        }
    }

    private boolean validId(String id) {
        if (id == null || id.isEmpty()) return false;
        String[] p = id.split(":", -1);
        return p.length == 2 && p[0].matches("[a-z0-9_.-]+") && p[1].matches("[a-z0-9_./-]+");
    }

    private String safe(String s) {
        return s.replace(":", "_").replaceAll("[^a-zA-Z0-9_.-]", "_");
    }

    private String json(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String fileName(Uri uri) {
        try (android.database.Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {}
        return uri.toString();
    }

    private void write(File f, String s) throws Exception {
        File p = f.getParentFile();
        if (p != null) p.mkdirs();
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(s.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void zip(File dir, File out) throws Exception {
        zip(dir, out, null);
    }

    private void zip(File dir, File out, BuildProgressListener progress) throws Exception {
        int totalFiles = countFiles(dir);
        int[] done = {0};
        try (ZipOutputStream z = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)))) {
            zipRec(dir, dir, z, totalFiles, done, progress);
        }
    }

    private int countFiles(File f) {
        if (f == null) return 0;
        if (f.isFile()) return 1;
        File[] list = f.listFiles();
        if (list == null) return 0;
        int count = 0;
        for (File x : list) count += countFiles(x);
        return count;
    }

    private void zipRec(File root, File f, ZipOutputStream z, int totalFiles,
                         int[] done, BuildProgressListener progress) throws Exception {
        File[] list = f.listFiles();
        if (list == null) return;
        for (File x : list) {
            String name = root.toURI().relativize(x.toURI()).getPath();
            if (x.isDirectory()) {
                zipRec(root, x, z, totalFiles, done, progress);
            } else {
                z.putNextEntry(new ZipEntry(name));
                try (InputStream in = new BufferedInputStream(new FileInputStream(x))) {
                    byte[] b = new byte[8192];
                    int n;
                    while ((n = in.read(b)) != -1) z.write(b, 0, n);
                }
                z.closeEntry();

                done[0]++;
                if (progress != null) {
                    int p = totalFiles <= 0 ? 100 : (int)((done[0] / (float) totalFiles) * 100f);
                    progress.update(p, "正在压缩文件 " + done[0] + "/" + totalFiles);
                }
            }
        }
    }

    private void delete(File f) {
        if (!f.exists()) return;
        File[] list = f.listFiles();
        if (list != null) for (File x : list) delete(x);
        f.delete();
    }

    private void setupFilterSpinner(Spinner spinner, Runnable onSelect) {
        spinner.setAdapter(createSpinnerAdapter(new ArrayList<>()));
        setupSpinnerPopup(spinner);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (onSelect != null) onSelect.run();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void updateFilterOptions() {
        if (filterSubpackSpinner != null) {
            List<String> subpackOptions = new ArrayList<>();
            subpackOptions.add("全部");
            subpackOptions.addAll(subpackList);
            filterSubpackSpinner.setAdapter(createSpinnerAdapter(subpackOptions));
            int pos = subpackOptions.indexOf(currentSubpackFilter.isEmpty() ? "全部" : currentSubpackFilter);
            if (pos < 0) pos = 0;
            filterSubpackSpinner.setSelection(pos);
        }

        if (filterSwitchSpinner != null) {
            Set<String> switchSet = new HashSet<>();
            for (Entry e : entries) {
                if (!e.switchName.isEmpty()) switchSet.add(e.switchName);
            }
            List<String> switchOptions = new ArrayList<>();
            switchOptions.add("全部");
            switchOptions.addAll(switchSet);
            filterSwitchSpinner.setAdapter(createSpinnerAdapter(switchOptions));
            int pos = switchOptions.indexOf(currentSwitchFilter.isEmpty() ? "全部" : currentSwitchFilter);
            if (pos < 0) pos = 0;
            filterSwitchSpinner.setSelection(pos);
        }

        if (stickySubpackSpinner != null) {
            List<String> subpackOptions = new ArrayList<>();
            subpackOptions.add("全部");
            subpackOptions.addAll(subpackList);
            stickySubpackSpinner.setAdapter(createSpinnerAdapter(subpackOptions));
            int pos = subpackOptions.indexOf(currentSubpackFilter.isEmpty() ? "全部" : currentSubpackFilter);
            if (pos < 0) pos = 0;
            stickySubpackSpinner.setSelection(pos);
        }

        if (stickySwitchSpinner != null) {
            Set<String> switchSet = new HashSet<>();
            for (Entry e : entries) {
                if (!e.switchName.isEmpty()) switchSet.add(e.switchName);
            }
            List<String> switchOptions = new ArrayList<>();
            switchOptions.add("全部");
            switchOptions.addAll(switchSet);
            stickySwitchSpinner.setAdapter(createSpinnerAdapter(switchOptions));
            int pos = switchOptions.indexOf(currentSwitchFilter.isEmpty() ? "全部" : currentSwitchFilter);
            if (pos < 0) pos = 0;
            stickySwitchSpinner.setSelection(pos);
        }
    }

    private ArrayList<Project> loadAllProjects() {
        ArrayList<Project> list = new ArrayList<>();
        String raw = getPreferences(MODE_PRIVATE).getString("projects", "");
        if (raw.isEmpty()) return list;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                Project p = new Project();
                p.id = obj.optString("id");
                p.name = obj.optString("name");
                p.author = obj.optString("author");
                p.description = obj.optString("description");
                p.date = obj.optString("date");
                JSONArray entriesArr = obj.optJSONArray("entries");
                if (entriesArr != null) {
                    for (int j = 0; j < entriesArr.length(); j++) {
                        JSONObject eObj = entriesArr.getJSONObject(j);
                        Entry e = new Entry(
                                eObj.optString("id"),
                                eObj.optString("type"),
                                eObj.optString("subpack"),
                                eObj.optString("imagePath"),
                                eObj.optBoolean("independentEnabled", false),
                                eObj.optString("switchName")
                        );
                        p.entries.add(e);
                    }
                }
                list.add(p);
            }
        } catch (Exception ignored) {}
        Collections.sort(list, (a, b) -> b.date.compareTo(a.date));
        return list;
    }

    private void saveProject() {
        if (entries.isEmpty() && currentProject == null) return;
        Project p = new Project();
        p.id = currentProject != null ? currentProject.id : UUID.randomUUID().toString();
        p.name = packNameInput != null ? packNameInput.getText().toString().trim() : (currentProject != null ? currentProject.name : "未命名");
        if (p.name.isEmpty()) p.name = "未命名";
        p.author = authorInput != null ? authorInput.getText().toString().trim() : (currentProject != null ? currentProject.author : "熬糕aogao");
        if (p.author.isEmpty()) p.author = "熬糕aogao";
        p.description = descriptionInput != null ? descriptionInput.getText().toString().trim() : (currentProject != null ? currentProject.description : "");
        p.date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());

        File projectDir = new File(getFilesDir(), "projects/" + p.id);
        File imagesDir = new File(projectDir, "images");
        if (!imagesDir.exists()) imagesDir.mkdirs();
        ArrayList<Entry> newEntries = new ArrayList<>();
        for (Entry e : entries) {
            String newPath = e.imagePath;
            if (e.imagePath != null && !e.imagePath.isEmpty() && !e.imagePath.startsWith(imagesDir.getAbsolutePath())) {
                try {
                    File src = new File(e.imagePath);
                    if (src.exists()) {
                        File dest = new File(imagesDir, System.currentTimeMillis() + "_" + new File(e.imagePath).getName());
                        copyFile(src, dest);
                        newPath = dest.getAbsolutePath();
                    }
                } catch (Exception ex) {}
            }
            newEntries.add(new Entry(e.id, e.type, e.subpack, newPath, e.independentEnabled, e.switchName));
        }
        p.entries = newEntries;

        ArrayList<Project> all = loadAllProjects();
        boolean found = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(p.id)) {
                all.set(i, p);
                found = true;
                break;
            }
        }
        if (!found) all.add(0, p);
        while (all.size() > 20) all.remove(all.size() - 1);

        JSONArray arr = new JSONArray();
        for (Project proj : all) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("id", proj.id);
                obj.put("name", proj.name);
                obj.put("author", proj.author);
                obj.put("description", proj.description);
                obj.put("date", proj.date);
                JSONArray entriesArr = new JSONArray();
                for (Entry e : proj.entries) {
                    JSONObject eObj = new JSONObject();
                    eObj.put("id", e.id);
                    eObj.put("type", e.type);
                    eObj.put("subpack", e.subpack);
                    eObj.put("imagePath", e.imagePath);
                    eObj.put("independentEnabled", e.independentEnabled);
                    eObj.put("switchName", e.switchName);
                    entriesArr.put(eObj);
                }
                obj.put("entries", entriesArr);
                arr.put(obj);
            } catch (Exception ignored) {}
        }
        getPreferences(MODE_PRIVATE).edit().putString("projects", arr.toString()).apply();
    }

    private void copyFile(File src, File dest) throws IOException {
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
    }

    private String getFullDisclaimerText() {
        return "━━━ 免责声明及用户协议 ━━━\n\n" +
               "1. 本工具的性质\n" +
               "MC基岩版材质编辑器是一款本地文件编辑工具，用于帮助玩家创建和修改Minecraft基岩版资源包。\n" +
               "本工具不包含任何Minecraft官方游戏资产（如纹理、模型、音效等），所有生成的内容均由用户自行提供。\n\n" +
               "2. 版权与使用许可\n" +
               "您使用本工具导入、编辑、生成的任何材质包，其版权归内容创作者所有。\n" +
               "您应确保您拥有所使用素材的合法使用权，并遵守《Minecraft最终用户许可协议》(EULA) 及所有适用法律法规。\n\n" +
               "3. 用户责任\n" +
               "您在使用本工具时，需自行承担以下责任：\n" +
               "• 确保您导入的图片、纹理、模型等素材不侵犯任何第三方的版权、商标权或其他合法权益。\n" +
               "• 不得将本工具用于任何商业盈利活动（如销售材质包、付费分发等），除非您已获得所有必要的授权。\n" +
               "• 使用本工具生成的材质包仅限个人非商业用途，如需分发，请遵守Mojang的EULA。\n\n" +
               "4. 免责声明\n" +
               "本工具按“现状”提供，开发者不对因使用本工具而产生的任何直接或间接损失承担责任。\n" +
               "因用户导入、编辑、分发第三方素材而引发的任何法律纠纷，均与开发者无关。\n\n" +
               "5. 协议变更\n" +
               "开发者保留随时更新本协议的权利，更新后的协议将在软件更新时公布。继续使用即视为接受。\n\n" +
               "如有疑问，请联系：熬糕aogao (邮箱/社交平台)";
    }

    private void showFullDisclaimerDialog() {
        new AlertDialog.Builder(this)
                .setTitle("免责声明及用户协议")
                .setMessage(getFullDisclaimerText())
                .setPositiveButton("关闭", null)
                .show();
    }

    private void showAgreementDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("用户协议及免责声明");
        builder.setCancelable(false);

        ScrollView scrollView = new ScrollView(this);
        TextView textView = new TextView(this);
        textView.setText(getFullDisclaimerText());
        textView.setTextColor(text());
        textView.setBackgroundColor(card());
        textView.setPadding(dp(16), dp(16), dp(16), dp(16));
        scrollView.addView(textView);

        CheckBox checkBox = new CheckBox(this);
        checkBox.setText("我已阅读并同意以上条款");
        checkBox.setTextColor(text());

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));
        layout.addView(checkBox, new LinearLayout.LayoutParams(-1, dp(50)));

        builder.setView(layout);
        builder.setPositiveButton("同意", null);
        builder.setNegativeButton("不同意", null);

        AlertDialog dialog = builder.create();
        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(round(card(), 24, line(), 1));
        }

        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);

        positive.setOnClickListener(v -> {
            if (checkBox.isChecked()) {
                getPreferences(MODE_PRIVATE).edit().putBoolean("agreed", true).apply();
                dialog.dismiss();
                buildShell();
                showPage(0);
            } else {
                Toast.makeText(this, "请先勾选“我已阅读并同意”", Toast.LENGTH_SHORT).show();
            }
        });

        negative.setOnClickListener(v -> {
            finish();
        });

        dialog.setOnCancelListener(d -> finish());
    }
}