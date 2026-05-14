package com.maoqiu.player;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.ContentUris;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.util.Base64;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.Window;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ScrollView;
import android.widget.HorizontalScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends Activity {
    private static final int REQ_OPEN_MEDIA = 1001;
    private static final int REQ_IMPORT_MEDIA = 1002;
    private static final int REQ_SCAN_MEDIA = 1003;
    private static final int REQ_PACKAGE_MEDIA = 1004;
    private static final int REQ_PICK_FOLDER = 1005;

    private static final String PREFS_NAME = "maoqiu_player_android";
    private static final String KEY_LIBRARY = "library";
    private static final String KEY_RECENT = "recent";

    private static final String KIND_VIDEO = "video";
    private static final String KIND_IMAGE = "image";
    private static final String KIND_PACKAGE = "package";
    private static final String KIND_UNKNOWN = "unknown";

    private static final String MQP_MAGIC = "MAOQIU_PLAYER_ENC_V1";
    private static final String MQP_FORMAT = "MaoqiuPlayerMediaPackage";
    private static final String MQP_DEFAULT_PHRASE = "MaoqiuPlayer local media package v1";
    private static final byte[] ENCRYPTOR_LITE_MAGIC = new byte[]{'M', 'A', 'O', 'L', 'I', 'T', 'E', '1', 0};
    private static final byte[] ENCRYPTOR_SECURE_MAGIC = new byte[]{'M', 'A', 'O', 'Q', 'I', 'U', '1', 0};
    private static final String ENCRYPTOR_LITE_FORMAT = "MAOQIU_LITE";
    private static final String ENCRYPTOR_LITE_PAYLOAD_FILE = "file";
    private static final String ENCRYPTOR_LITE_PAYLOAD_BUNDLE = "tar_bundle";
    private static final String ENCRYPTOR_FILE_CIPHER_AES_GCM = "AES-256-GCM";
    private static final String ENCRYPTOR_LITE_KEY_PHRASE = "Maoqiu Secure Lite v1 built-in application key";
    private static final String APP_VERSION = "0.1.17";
    private static final String KEY_THEME = "theme";
    private static final String THEME_DARK = "dark";
    private static final String THEME_LIGHT = "light";

    private final ArrayList<MediaItem> library = new ArrayList<>();
    private final ArrayList<MediaItem> recent = new ArrayList<>();
    private ArrayList<MediaItem> activePlaylist = new ArrayList<>();
    private int activeIndex = -1;
    private String currentScreen = "home";
    private String currentFilter = "all";
    private String currentQuery = "";
    private String currentSort = "time";
    private MediaPlayer currentMediaPlayer;
    private float playbackSpeed = 1.0f;
    private ArrayList<MediaItem> pendingPackageItems;
    private AlertDialog activePackageDialog;
    private EditText activePathInput;
    private boolean fullscreen = false;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        configureWindow();
        library.addAll(loadItems(KEY_LIBRARY));
        recent.addAll(loadItems(KEY_RECENT));
        if (!handleIncomingIntent(getIntent())) {
            showHome();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveItems(KEY_LIBRARY, library);
        saveItems(KEY_RECENT, recent);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!handleIncomingIntent(intent)) {
            showHome();
        }
    }

    @Override
    public void onBackPressed() {
        if (fullscreen) {
            setFullscreen(false);
            return;
        }
        if (!"home".equals(currentScreen)) {
            showHome();
            return;
        }
        super.onBackPressed();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(bgColor());
        window.setNavigationBarColor(bgColor());
        if (isLightTheme() && Build.VERSION.SDK_INT >= 23) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        } else {
            window.getDecorView().setSystemUiVisibility(0);
        }
    }

    private void showHome() {
        currentScreen = "home";
        currentMediaPlayer = null;
        setFullscreen(false);

        LinearLayout page = page();

        // B站风格大标题 + 副标题
        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(0, dp(8), 0, dp(16));
        TextView mainTitle = text("毛球播放器", 28, true);
        TextView subTitle = text("MaoqiuPlayer · 你的本地媒体中心", 14, false);
        subTitle.setTextColor(subtextColor());
        titleBox.addView(mainTitle);
        titleBox.addView(subTitle);
        page.addView(titleBox);

        // 分割线
        View divider = new View(this);
        divider.setBackgroundColor(dividerColor());
        divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        page.addView(divider);

        // 搜索栏 - 圆角搜索框
        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setPadding(dp(14), 0, dp(14), 0);
        searchBar.setBackground(rounded(inputBgColor(), dp(22), inputStroke()));
        searchBar.setLayoutParams(matchWithTop(dp(18)));
        TextView searchIcon = text("🔍", 16, false);
        EditText search = new EditText(this);
        search.setHint("搜索本地媒体");
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setTextColor(textColor());
        search.setHintTextColor(hintColor());
        search.setTextSize(15);
        search.setBackground(null);
        search.setPadding(dp(8), dp(12), dp(8), dp(12));
        search.setOnEditorActionListener((v, actionId, event) -> {
            currentQuery = search.getText().toString().trim();
            showLibrary("all");
            return true;
        });
        searchBar.addView(searchIcon);
        searchBar.addView(search, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        page.addView(searchBar);

        // 功能入口 - 2列网格
        page.addView(section("主要功能"));
        String[][] gridItems = {
            {"🕐", "最近播放", recent.size() + " 个项目"},
            {"🎬", "本地视频", countKind(KIND_VIDEO) + " 个视频"},
            {"🖼️", "本地图片", countKind(KIND_IMAGE) + " 张图片"},
            {"📋", "播放列表", library.size() + " 个已导入"},
            {"📂", "打开媒体", "视频、图片或 .mqp"},
            {"⚙️", "设置", "播放、缓存和关于"}
        };
        View.OnClickListener[] gridListeners = {
            v -> showRecent(),
            v -> showLibrary(KIND_VIDEO),
            v -> showLibrary(KIND_IMAGE),
            v -> showPlaylist(),
            v -> openMediaPicker(REQ_OPEN_MEDIA, false),
            v -> showSettings()
        };
        for (int i = 0; i < gridItems.length; i += 2) {
            LinearLayout gridRow = new LinearLayout(this);
            gridRow.setOrientation(LinearLayout.HORIZONTAL);
            gridRow.setLayoutParams(matchWithTop(dp(10)));
            gridRow.addView(gridCard(gridItems[i][0], gridItems[i][1], gridItems[i][2], gridListeners[i]),
                    new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            if (i + 1 < gridItems.length) {
                gridRow.addView(gridCard(gridItems[i + 1][0], gridItems[i + 1][1], gridItems[i + 1][2], gridListeners[i + 1]),
                        gridCardRightParams());
            } else {
                // Placeholder for odd count
                View placeholder = new View(this);
                gridRow.addView(placeholder, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            }
            page.addView(gridRow);
        }

        // 最近播放 - 横滑卡片
        if (!recent.isEmpty()) {
            page.addView(section("继续播放"));
            HorizontalScrollView hScroll = new HorizontalScrollView(this);
            hScroll.setHorizontalScrollBarEnabled(false);
            hScroll.setLayoutParams(matchWithTop(dp(4)));
            LinearLayout hList = new LinearLayout(this);
            hList.setOrientation(LinearLayout.HORIZONTAL);
            hList.setPadding(0, 0, dp(18), 0);
            for (int i = 0; i < Math.min(8, recent.size()); i++) {
                MediaItem item = recent.get(i);
                hList.addView(recentCard(item, recent, i));
            }
            hScroll.addView(hList);
            page.addView(hScroll);
        }

        setScrollableContent(page);
    }

    private void showRecent() {
        currentScreen = "recent";
        LinearLayout page = page();
        page.addView(header("最近播放", v -> showHome()));
        if (recent.isEmpty()) {
            page.addView(empty("还没有播放记录"));
        } else {
            for (int i = 0; i < recent.size(); i++) {
                page.addView(mediaRow(recent.get(i), recent, i));
            }
        }
        setScrollableContent(page);
    }

    private void showPlaylist() {
        currentScreen = "playlist";
        LinearLayout page = page();
        page.addView(header("播放列表", v -> showHome()));
        page.addView(section("默认播放列表"));
        if (library.isEmpty()) {
            page.addView(empty("还没有导入媒体"));
        } else {
            for (int i = 0; i < library.size(); i++) {
                page.addView(mediaRow(library.get(i), library, i));
            }
        }
        setScrollableContent(page);
    }

    private void showLibrary(String filter) {
        currentScreen = "library";
        currentFilter = filter;
        LinearLayout page = page();
        page.addView(header(libraryTitle(filter), v -> showHome()));

        // 圆角搜索栏
        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setPadding(dp(14), 0, dp(14), 0);
        searchBar.setBackground(rounded(inputBgColor(), dp(22), inputStroke()));
        searchBar.setLayoutParams(matchWithTop(dp(8)));
        TextView searchIcon = text("🔍", 16, false);
        EditText query = new EditText(this);
        query.setHint("搜索名称");
        query.setSingleLine(true);
        query.setInputType(InputType.TYPE_CLASS_TEXT);
        query.setTextColor(textColor());
        query.setHintTextColor(hintColor());
        query.setTextSize(15);
        query.setBackground(null);
        query.setPadding(dp(8), dp(12), dp(8), dp(12));
        query.setText(currentQuery);
        query.setOnEditorActionListener((v, actionId, event) -> {
            currentQuery = query.getText().toString().trim();
            showLibrary(currentFilter);
            return true;
        });
        searchBar.addView(searchIcon);
        searchBar.addView(query, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        page.addView(searchBar);

        // 胶囊标签 - 筛选按钮
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setLayoutParams(matchWithTop(dp(12)));
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, 0, dp(18), 0);
        String[][] chipData = {{"all", "全部"}, {KIND_VIDEO, "视频"}, {KIND_IMAGE, "图片"}, {KIND_PACKAGE, "媒体包"}};
        for (String[] cd : chipData) {
            chips.addView(chipButton(cd[1], cd[0].equals(filter), v -> showLibrary(cd[0])));
        }
        chipScroll.addView(chips);
        page.addView(chipScroll);

        // 操作按钮行
        LinearLayout actions = row();
        Button open = button("打开媒体");
        open.setOnClickListener(v -> openMediaPicker(REQ_OPEN_MEDIA, false));
        Button importButton = ghostButton("导入");
        importButton.setOnClickListener(v -> openMediaPicker(REQ_IMPORT_MEDIA, true));
        Button scan = ghostButton("扫描本机");
        scan.setOnClickListener(v -> scanLocalMedia(true));
        actions.addView(open, new LinearLayout.LayoutParams(0, dp(46), 1));
        actions.addView(importButton, wrapWithLeft(dp(10)));
        actions.addView(scan, wrapWithLeft(dp(10)));
        page.addView(actions);

        // 排序选择
        LinearLayout sortRow = new LinearLayout(this);
        sortRow.setOrientation(LinearLayout.HORIZONTAL);
        sortRow.setGravity(Gravity.CENTER_VERTICAL);
        sortRow.setPadding(0, dp(8), 0, dp(4));
        TextView sortLabel = text("排序：", 13, false);
        sortLabel.setTextColor(subtextColor());
        Spinner sort = new Spinner(this);
        sort.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"最近", "名称", "格式"}));
        sort.setSelection(sortIndex(currentSort));
        sort.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            String nextSort = position == 1 ? "name" : position == 2 ? "type" : "time";
            if (!nextSort.equals(currentSort)) {
                currentSort = nextSort;
                showLibrary(currentFilter);
            }
        }));
        sortRow.addView(sortLabel);
        sortRow.addView(sort);
        page.addView(sortRow);

        // 媒体列表
        ArrayList<MediaItem> items = filteredItems(filter);
        if (items.isEmpty()) {
            page.addView(empty("没有找到媒体"));
        } else {
            for (int i = 0; i < items.size(); i++) {
                page.addView(mediaRow(items.get(i), items, i));
            }
        }
        setScrollableContent(page);
    }

    private void showSettings() {
        currentScreen = "settings";
        LinearLayout page = page();
        page.addView(header("设置", v -> showHome()));

        // 常规设置组
        page.addView(section("常规"));
        LinearLayout themeCard = new LinearLayout(this);
        themeCard.setOrientation(LinearLayout.HORIZONTAL);
        themeCard.setGravity(Gravity.CENTER_VERTICAL);
        themeCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        themeCard.setBackground(rounded(surfaceColor(), dp(10), surfaceStroke()));
        themeCard.setLayoutParams(matchWithTop(dp(10)));
        LinearLayout themeText = new LinearLayout(this);
        themeText.setOrientation(LinearLayout.VERTICAL);
        themeText.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        themeText.addView(text("外观主题", 17, true));
        TextView themeSub = text(isLightTheme() ? "浅色主题" : "深色主题", 13, false);
        themeSub.setTextColor(subtextColor());
        themeText.addView(themeSub);
        themeCard.addView(themeText);
        Switch themeSwitch = new Switch(this);
        themeSwitch.setChecked(isLightTheme());
        themeSwitch.setOnCheckedChangeListener((btn, checked) -> {
            prefs.edit().putString(KEY_THEME, checked ? THEME_LIGHT : THEME_DARK).apply();
            configureWindow();
            showSettings();
        });
        themeCard.addView(themeSwitch);
        page.addView(themeCard);

        page.addView(card("播放设置", "应用内播放、倍速和全屏控制", null));
        page.addView(card("媒体库", library.size() + " 个本地媒体项目", v -> showLibrary("all")));

        // 存储设置组
        page.addView(section("存储"));
        page.addView(card("缓存", "清理媒体包临时文件", v -> {
            clearDirectory(new File(getCacheDir(), "media-packages"));
            Toast.makeText(this, "缓存已清理", Toast.LENGTH_SHORT).show();
        }));

        // 高级设置组
        page.addView(section("高级"));
        page.addView(card("高级设置", "媒体包管理、文件校验和数据库维护", v -> showAdvancedTools()));

        // 关于
        page.addView(section("关于"));
        page.addView(card("关于毛球播放器", "MaoqiuPlayer " + APP_VERSION, null));

        // 清空最近播放
        LinearLayout clearCard = new LinearLayout(this);
        clearCard.setOrientation(LinearLayout.HORIZONTAL);
        clearCard.setGravity(Gravity.CENTER_VERTICAL);
        clearCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        clearCard.setBackground(rounded(surfaceColor(), dp(10), surfaceStroke()));
        clearCard.setLayoutParams(matchWithTop(dp(10)));
        LinearLayout clearText = new LinearLayout(this);
        clearText.setOrientation(LinearLayout.VERTICAL);
        clearText.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        clearText.addView(text("清空最近播放", 17, true));
        TextView clearSub = text("删除所有播放记录", 13, false);
        clearSub.setTextColor(subtextColor());
        clearText.addView(clearSub);
        clearCard.addView(clearText);
        Switch clearSwitch = new Switch(this);
        clearSwitch.setChecked(false);
        clearSwitch.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) {
                recent.clear();
                saveItems(KEY_RECENT, recent);
                Toast.makeText(this, "最近播放已清空", Toast.LENGTH_SHORT).show();
                btn.setChecked(false);
            }
        });
        clearCard.addView(clearSwitch);
        page.addView(clearCard);

        setScrollableContent(page);
    }

    private void showAdvancedTools() {
        currentScreen = "advanced";
        LinearLayout page = page();
        page.addView(header("高级工具", v -> showSettings()));
        page.addView(card("媒体包管理", "导入、查看和打开 .mqp 媒体包", v -> showLibrary(KIND_PACKAGE)));
        page.addView(card("清理缓存", "删除媒体包临时播放文件", v -> {
            clearDirectory(new File(getCacheDir(), "media-packages"));
            Toast.makeText(this, "缓存已清理", Toast.LENGTH_SHORT).show();
        }));
        page.addView(card("打包媒体", "将视频和图片打包为 .mqp 媒体包", v -> showPackageMediaDialog()));
        page.addView(card("数据库维护", "重建本机媒体索引", v -> scanLocalMedia(true)));
        page.addView(card("文件校验", "打开媒体包时会自动校验文件完整性", null));
        setScrollableContent(page);
    }

    private Uri resolvePlayableUri(Uri uri) {
        if (!"content".equals(uri.getScheme())) {
            return uri;
        }
        // Try file path first — VideoView works best with file:// URIs
        String path = getPathFromUri(uri);
        if (path != null && new File(path).exists()) {
            return Uri.fromFile(new File(path));
        }
        // Fallback: verify content URI is readable
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is != null) {
                return uri;
            }
        } catch (Exception ignored) {
        }
        return uri;
    }

    private String getPathFromUri(Uri uri) {
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, new String[]{"_data"}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex("_data");
                    if (index >= 0) {
                        return cursor.getString(index);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if ("file".equals(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }

    private void showVideoPlayer(MediaItem item) {
        currentScreen = "video";
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xff000000);

        VideoView video = new VideoView(this);
        Uri videoUri = resolvePlayableUri(Uri.parse(item.uri));
        video.setVideoURI(videoUri);
        root.addView(video, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // --- Top overlay: back + title ---
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(12), dp(10), dp(12), dp(10));
        topBar.setBackgroundColor(0x99000000);
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        topLp.gravity = Gravity.TOP;

        ImageButton backBtn = new ImageButton(this);
        backBtn.setImageResource(android.R.drawable.ic_media_previous);
        backBtn.setBackgroundColor(Color.TRANSPARENT);
        backBtn.setColorFilter(0xffffffff);
        backBtn.setOnClickListener(v -> {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            showLibrary(currentFilter);
        });
        topBar.addView(backBtn, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView titleText = new TextView(this);
        titleText.setText(item.name);
        titleText.setTextColor(0xffffffff);
        titleText.setTextSize(16);
        titleText.setSingleLine(true);
        titleText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        topBar.addView(titleText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        root.addView(topBar, topLp);

        // --- Bottom overlay: SeekBar + time ---
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setPadding(dp(12), dp(8), dp(12), dp(10));
        bottomBar.setBackgroundColor(0x99000000);
        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        bottomLp.gravity = Gravity.BOTTOM;

        TextView timeCurrent = new TextView(this);
        timeCurrent.setText("00:00");
        timeCurrent.setTextColor(0xffffffff);
        timeCurrent.setTextSize(12);
        timeCurrent.setMinWidth(dp(48));

        SeekBar seekBar = new SeekBar(this);
        LinearLayout.LayoutParams seekLp = new LinearLayout.LayoutParams(0, dp(28), 1);
        seekLp.leftMargin = dp(8);
        seekLp.rightMargin = dp(8);

        TextView timeTotal = new TextView(this);
        timeTotal.setText("00:00");
        timeTotal.setTextColor(0xffffffff);
        timeTotal.setTextSize(12);
        timeTotal.setMinWidth(dp(48));
        timeTotal.setGravity(Gravity.END);

        bottomBar.addView(timeCurrent);
        bottomBar.addView(seekBar, seekLp);
        bottomBar.addView(timeTotal);
        root.addView(bottomBar, bottomLp);

        // --- Control layer visibility ---
        android.os.Handler hideHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        Runnable hideRunnable = () -> {
            topBar.setVisibility(View.GONE);
            bottomBar.setVisibility(View.GONE);
        };
        topBar.setVisibility(View.GONE);
        bottomBar.setVisibility(View.GONE);

        Runnable showControls = () -> {
            topBar.setVisibility(View.VISIBLE);
            bottomBar.setVisibility(View.VISIBLE);
            hideHandler.removeCallbacks(hideRunnable);
            hideHandler.postDelayed(hideRunnable, 3000);
        };

        // SeekBar drag handler
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            boolean wasPlaying = false;
            @Override public void onStartTrackingTouch(SeekBar sb) { wasPlaying = video.isPlaying(); video.pause(); hideHandler.removeCallbacks(hideRunnable); }
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) { video.seekTo(progress); timeCurrent.setText(formatDuration(progress)); }
            }
            @Override public void onStopTrackingTouch(SeekBar sb) { if (wasPlaying) video.start(); hideHandler.postDelayed(hideRunnable, 3000); }
        });

        // Progress updater
        android.os.Handler progressHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        Runnable progressUpdater = new Runnable() {
            @Override public void run() {
                if (video.isPlaying()) { int pos = video.getCurrentPosition(); seekBar.setProgress(pos); timeCurrent.setText(formatDuration(pos)); }
                progressHandler.postDelayed(this, 500);
            }
        };

        video.setOnPreparedListener(mp -> {
            currentMediaPlayer = mp;
            applyPlaybackSpeed();
            int dur = mp.getDuration();
            seekBar.setMax(dur);
            timeTotal.setText(formatDuration(dur));
            video.start();
            progressHandler.post(progressUpdater);
            showControls.run();
        });
        video.setOnErrorListener((mp, what, extra) -> {
            progressHandler.removeCallbacks(progressUpdater);
            if ("file".equals(videoUri.getScheme())) {
                try { Uri contentUri = Uri.parse(item.uri); if ("content".equals(contentUri.getScheme())) { video.setVideoURI(contentUri); return true; } } catch (Exception ignored) {}
            }
            if ("content".equals(videoUri.getScheme())) {
                try { String path = getPathFromUri(videoUri); if (path != null && new java.io.File(path).exists()) { video.setVideoPath(path); return true; } } catch (Exception ignored) {}
            }
            Toast.makeText(this, "该视频暂时无法播放 (" + what + ", " + extra + ")", Toast.LENGTH_LONG).show();
            return true;
        });
        video.setOnCompletionListener(mp -> {
            progressHandler.removeCallbacks(progressUpdater);
            seekBar.setProgress(seekBar.getMax());
            timeCurrent.setText(formatDuration(seekBar.getMax()));
            showControls.run();
        });

        // Tap screen: toggle play/pause + show/hide controls
        video.setOnClickListener(v -> {
            if (topBar.getVisibility() == View.VISIBLE) {
                hideHandler.removeCallbacks(hideRunnable);
                topBar.setVisibility(View.GONE);
                bottomBar.setVisibility(View.GONE);
            } else {
                showControls.run();
            }
            if (video.isPlaying()) {
                video.pause();
            } else {
                video.start();
                progressHandler.post(progressUpdater);
            }
        });

        setContentView(root);
    }

    private String formatDuration(int ms) {
        int totalSec = ms / 1000;
        int min = totalSec / 60;
        int sec = totalSec % 60;
        return String.format(Locale.ROOT, "%02d:%02d", min, sec);
    }

    private void showImageViewer(MediaItem item) {
        currentScreen = "image";

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(bgColor());

        ZoomImageView imageView = new ZoomImageView(this);
        try {
            imageView.setBitmap(loadBitmap(Uri.parse(item.uri)));
        } catch (IOException exc) {
            Toast.makeText(this, "无法显示该图片", Toast.LENGTH_LONG).show();
        }
        root.addView(imageView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // --- Top overlay: back + title ---
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(12), dp(10), dp(12), dp(10));
        topBar.setBackgroundColor(0x99000000);
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        topLp.gravity = Gravity.TOP;

        ImageButton backBtn = new ImageButton(this);
        backBtn.setImageResource(android.R.drawable.ic_media_previous);
        backBtn.setBackgroundColor(Color.TRANSPARENT);
        backBtn.setColorFilter(0xffffffff);
        backBtn.setOnClickListener(v -> showLibrary(currentFilter));
        topBar.addView(backBtn, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView titleText = new TextView(this);
        titleText.setText(item.name);
        titleText.setTextColor(0xffffffff);
        titleText.setTextSize(16);
        titleText.setSingleLine(true);
        titleText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        topBar.addView(titleText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        root.addView(topBar, topLp);

        // --- Control layer visibility ---
        android.os.Handler hideHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        Runnable hideRunnable = () -> topBar.setVisibility(View.GONE);
        Runnable showBar = () -> {
            topBar.setVisibility(View.VISIBLE);
            hideHandler.removeCallbacks(hideRunnable);
            hideHandler.postDelayed(hideRunnable, 3000);
        };
        showBar.run();

        // --- GestureDetector for swipe + tap ---
        GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (topBar.getVisibility() == View.VISIBLE) {
                    hideHandler.removeCallbacks(hideRunnable);
                    topBar.setVisibility(View.GONE);
                } else {
                    showBar.run();
                }
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX < 0) {
                        openNeighbor(1);  // left swipe -> next
                    } else {
                        openNeighbor(-1); // right swipe -> previous
                    }
                    return true;
                }
                return false;
            }
        });

        imageView.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return imageView.onTouchEvent(event);
        });

        setContentView(root);
    }

    private void showPackageDetails(MediaItem item) {
        currentScreen = "package";
        LinearLayout page = page();
        page.addView(header(item.name, v -> showLibrary(KIND_PACKAGE)));
        page.addView(section("媒体包"));
        page.addView(card("私有媒体包", "可在应用内打开并临时播放包内媒体", null));
        Button open = button("打开媒体包");
        open.setOnClickListener(v -> extractMediaPackageAndPlay(item));
        page.addView(open, matchWithTop(dp(12)));
        setScrollableContent(page);
    }

    private void showPackageLoading(String name) {
        currentScreen = "package-loading";
        LinearLayout page = page();
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.addView(header(name, v -> showLibrary(KIND_PACKAGE)));
        TextView status = text("正在打开媒体包...", 18, true);
        status.setGravity(Gravity.CENTER);
        page.addView(status, matchWithTop(dp(80)));
        setScrollableContent(page);
    }

    private void extractMediaPackageAndPlay(MediaItem item) {
        showPackageLoading(item.name);
        new Thread(() -> {
            try {
                ArrayList<MediaItem> extracted = extractPackage(Uri.parse(item.uri), item.name);
                runOnUiThread(() -> {
                    if (extracted.isEmpty()) {
                        Toast.makeText(this, "媒体包中没有可播放项目", Toast.LENGTH_LONG).show();
                        showPackageDetails(item);
                        return;
                    }
                    activePlaylist = extracted;
                    activeIndex = 0;
                    openItem(extracted.get(0), false);
                });
            } catch (Exception exc) {
                runOnUiThread(() -> {
                    String message = exc.getMessage();
                    if (message == null || message.trim().isEmpty()) {
                        message = "无法打开该媒体包，请检查文件完整性";
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    showPackageDetails(item);
                });
            }
        }).start();
    }

    private void openItem(MediaItem item, boolean updatePlaylist) {
        if (updatePlaylist) {
            activePlaylist = new ArrayList<>();
            activePlaylist.add(item);
            activeIndex = 0;
        }
        addRecent(item);
        if (KIND_VIDEO.equals(item.kind)) {
            showVideoPlayer(item);
        } else if (KIND_IMAGE.equals(item.kind)) {
            showImageViewer(item);
        } else if (KIND_PACKAGE.equals(item.kind)) {
            showPackageDetails(item);
        } else {
            Toast.makeText(this, "暂不支持该文件", Toast.LENGTH_SHORT).show();
        }
    }

    private void openItemFromList(ArrayList<MediaItem> list, int index) {
        activePlaylist = new ArrayList<>(list);
        activeIndex = index;
        openItem(list.get(index), false);
    }

    private void openNeighbor(int offset) {
        if (activePlaylist.isEmpty()) {
            return;
        }
        activeIndex = (activeIndex + offset + activePlaylist.size()) % activePlaylist.size();
        openItem(activePlaylist.get(activeIndex), false);
    }

    private void openMediaPicker(int requestCode, boolean multiple) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple);
        startActivityForResult(intent, requestCode);
    }

    private boolean handleIncomingIntent(Intent intent) {
        if (intent == null || intent.getData() == null || !Intent.ACTION_VIEW.equals(intent.getAction())) {
            return false;
        }
        Uri uri = intent.getData();
        MediaItem item = mediaItemFromUri(uri);
        upsert(library, item);
        saveItems(KEY_LIBRARY, library);
        openItem(item, true);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        ArrayList<MediaItem> picked = new ArrayList<>();
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                Uri uri = clipData.getItemAt(i).getUri();
                persistReadPermission(data, uri);
                picked.add(mediaItemFromUri(uri));
            }
        } else if (data.getData() != null) {
            Uri uri = data.getData();
            persistReadPermission(data, uri);
            picked.add(mediaItemFromUri(uri));
        }
        for (MediaItem item : picked) {
            upsert(library, item);
        }
        saveItems(KEY_LIBRARY, library);
        if (requestCode == REQ_OPEN_MEDIA && !picked.isEmpty()) {
            openItemFromList(picked, 0);
        } else if (requestCode == REQ_IMPORT_MEDIA) {
            Toast.makeText(this, "已导入 " + picked.size() + " 个媒体", Toast.LENGTH_SHORT).show();
            showLibrary(currentFilter);
        } else if (requestCode == REQ_PACKAGE_MEDIA) {
            if (!picked.isEmpty()) {
                showPackageOptionsDialog(picked);
            } else {
                Toast.makeText(this, "未选择任何文件", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQ_PICK_FOLDER) {
            Uri treeUri = data.getData();
            if (treeUri != null && activePathInput != null) {
                String folderPath = extractRelativePathFromTreeUri(treeUri);
                activePathInput.setText(folderPath);
            }
        }
    }

    private void persistReadPermission(Intent data, Uri uri) {
        int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if (flags == 0) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Some providers grant transient read access only.
        }
    }

    private void scanLocalMedia(boolean requestIfNeeded) {
        if (!hasMediaPermission()) {
            if (requestIfNeeded) {
                requestMediaPermission();
            }
            return;
        }
        int before = library.size();
        queryMediaStore(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, KIND_VIDEO);
        queryMediaStore(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, KIND_IMAGE);
        saveItems(KEY_LIBRARY, library);
        Toast.makeText(this, "已更新 " + (library.size() - before) + " 个本机媒体", Toast.LENGTH_SHORT).show();
        showLibrary(currentFilter);
    }

    private boolean hasMediaPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                    || (Build.VERSION.SDK_INT >= 34
                    && checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED);
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestMediaPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_IMAGES}, REQ_SCAN_MEDIA);
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_SCAN_MEDIA);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_SCAN_MEDIA) {
            return;
        }
        for (int result : grantResults) {
            if (result == PackageManager.PERMISSION_GRANTED) {
                scanLocalMedia(false);
                return;
            }
        }
        Toast.makeText(this, "没有媒体读取权限", Toast.LENGTH_SHORT).show();
    }

    private void queryMediaStore(Uri collection, String kind) {
        String[] projection = {
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATE_ADDED
        };
        try (Cursor cursor = getContentResolver().query(collection, projection, null, null, MediaStore.MediaColumns.DATE_ADDED + " DESC")) {
            if (cursor == null) {
                return;
            }
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
            int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
            int mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE);
            int dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idColumn);
                String name = cursor.getString(nameColumn);
                String mime = cursor.getString(mimeColumn);
                long date = cursor.getLong(dateColumn) * 1000L;
                Uri uri = ContentUris.withAppendedId(collection, id);
                upsert(library, new MediaItem(uri.toString(), name, mime, kind, "本机媒体", date));
            }
        } catch (SecurityException ignored) {
            // Users may grant images or videos separately on recent Android versions.
        }
    }

    private MediaItem mediaItemFromUri(Uri uri) {
        String name = queryDisplayName(uri);
        String mime = getContentResolver().getType(uri);
        if (mime == null) {
            mime = guessMimeType(name);
        }
        String kind = classifyKind(name, mime);
        if (!KIND_PACKAGE.equals(kind) && isPackageUri(uri)) {
            kind = KIND_PACKAGE;
        }
        return new MediaItem(uri.toString(), name, mime == null ? "" : mime, kind, "已导入", System.currentTimeMillis());
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String value = cursor.getString(index);
                    if (value != null && !value.isEmpty()) {
                        return value;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        String fallback = uri.getLastPathSegment();
        return fallback == null ? "未命名媒体" : fallback;
    }

    private String guessMimeType(String name) {
        String extension = extension(name);
        if (extension.isEmpty()) {
            return "";
        }
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.substring(1));
    }

    private String classifyKind(String name, String mime) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mqp")) {
            return KIND_PACKAGE;
        }
        if (mime != null && mime.startsWith("video/")) {
            return KIND_VIDEO;
        }
        if (mime != null && mime.startsWith("image/")) {
            return KIND_IMAGE;
        }
        if (lower.matches(".*\\.(mp4|m4v|mov|mkv|webm|avi|3gp)$")) {
            return KIND_VIDEO;
        }
        if (lower.matches(".*\\.(jpg|jpeg|png|webp|gif|bmp|heic|heif)$")) {
            return KIND_IMAGE;
        }
        return KIND_UNKNOWN;
    }

    private Bitmap loadBitmap(Uri uri) throws IOException {
        if ("file".equals(uri.getScheme()) && uri.getPath() != null) {
            Bitmap bitmap = BitmapFactory.decodeFile(uri.getPath());
            if (bitmap == null) {
                throw new IOException("Unable to decode image");
            }
            return bitmap;
        }
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
            return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE));
        }
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap == null) {
                throw new IOException("Unable to decode image");
            }
            return bitmap;
        }
    }

    private boolean isPackageUri(Uri uri) {
        int maxMagic = Math.max(MQP_MAGIC.getBytes(StandardCharsets.UTF_8).length, Math.max(ENCRYPTOR_LITE_MAGIC.length, ENCRYPTOR_SECURE_MAGIC.length));
        byte[] prefix = new byte[maxMagic];
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) {
                return false;
            }
            int total = 0;
            while (total < prefix.length) {
                int read = input.read(prefix, total, prefix.length - total);
                if (read == -1) {
                    break;
                }
                total += read;
            }
            if (total <= 0) {
                return false;
            }
            byte[] actual = new byte[total];
            System.arraycopy(prefix, 0, actual, 0, total);
            return startsWith(actual, MQP_MAGIC.getBytes(StandardCharsets.UTF_8))
                    || startsWith(actual, ENCRYPTOR_LITE_MAGIC)
                    || startsWith(actual, ENCRYPTOR_SECURE_MAGIC);
        } catch (IOException ignored) {
            return false;
        }
    }

    private ArrayList<MediaItem> extractPackage(Uri uri, String displayName) throws IOException, JSONException, GeneralSecurityException, NoSuchAlgorithmException {
        byte[] data = readAll(uri);
        byte[] magic = MQP_MAGIC.getBytes(StandardCharsets.UTF_8);
        if (startsWith(data, magic)) {
            return extractPlayerPackage(data, displayName, magic);
        }
        if (startsWith(data, ENCRYPTOR_LITE_MAGIC)) {
            return extractLitePackage(data, displayName);
        }
        if (startsWith(data, ENCRYPTOR_SECURE_MAGIC)) {
            throw new IOException("该加密文件需要用户名和密码，请使用桌面版打开。");
        }
        throw new IOException("Invalid package magic");
    }

    private ArrayList<MediaItem> extractPlayerPackage(byte[] data, String displayName, byte[] magic) throws IOException, JSONException, GeneralSecurityException, NoSuchAlgorithmException {
        if (data.length < magic.length + 4) {
            throw new IOException("Invalid package");
        }
        int headerLength = ByteBuffer.wrap(data, magic.length, 4).getInt();
        int payloadOffset = magic.length + 4 + headerLength;
        if (payloadOffset > data.length) {
            throw new IOException("Invalid package header");
        }
        String headerJson = new String(data, magic.length + 4, headerLength, StandardCharsets.UTF_8);
        JSONObject header = new JSONObject(headerJson);
        if (!MQP_FORMAT.equals(header.optString("format"))) {
            throw new IOException("Unsupported package");
        }
        byte[] encryptedPayload = new byte[data.length - payloadOffset];
        System.arraycopy(data, payloadOffset, encryptedPayload, 0, encryptedPayload.length);
        String expectedSha = header.optString("payload_sha256", "");
        if (!expectedSha.isEmpty() && !expectedSha.equals(sha256(encryptedPayload))) {
            throw new IOException("Package checksum failed");
        }

        byte[] salt = hexToBytes(header.getString("salt"));
        byte[] nonce = hexToBytes(header.getString("nonce"));
        int iterations = header.optInt("iterations", 390000);
        byte[] zipBytes = decryptPayload(encryptedPayload, salt, nonce, iterations);
        File target = new File(getCacheDir(), "media-packages/" + sanitize(displayName));
        clearDirectory(target);
        target.mkdirs();
        return unzipPackage(zipBytes, target);
    }

    private ArrayList<MediaItem> extractLitePackage(byte[] data, String displayName) throws IOException, JSONException, GeneralSecurityException, NoSuchAlgorithmException {
        if (data.length < ENCRYPTOR_LITE_MAGIC.length + 4) {
            throw new IOException("Invalid lite package");
        }
        int headerLength = ByteBuffer.wrap(data, ENCRYPTOR_LITE_MAGIC.length, 4).getInt();
        int payloadOffset = ENCRYPTOR_LITE_MAGIC.length + 4 + headerLength;
        if (payloadOffset > data.length) {
            throw new IOException("Invalid lite package header");
        }
        String headerJson = new String(data, ENCRYPTOR_LITE_MAGIC.length + 4, headerLength, StandardCharsets.UTF_8);
        JSONObject header = new JSONObject(headerJson);
        if (!ENCRYPTOR_LITE_FORMAT.equals(header.optString("format")) || header.optInt("version") != 1) {
            throw new IOException("Unsupported lite package");
        }
        byte[] encryptedPayload = new byte[data.length - payloadOffset];
        System.arraycopy(data, payloadOffset, encryptedPayload, 0, encryptedPayload.length);

        String cipherName = header.optString("cipher", ENCRYPTOR_FILE_CIPHER_AES_GCM);
        if (!ENCRYPTOR_FILE_CIPHER_AES_GCM.equals(cipherName)) {
            throw new IOException("Unsupported lite package cipher");
        }
        byte[] nonce = Base64.decode(header.getString("payload_nonce"), Base64.DEFAULT);
        byte[] plain = decryptAesGcmNoAad(encryptedPayload, liteAppKey(), nonce);
        String expectedSha = header.optString("original_sha256", "");
        if (!expectedSha.isEmpty() && !expectedSha.equals(sha256(plain))) {
            throw new IOException("Lite package checksum failed");
        }

        File target = new File(getCacheDir(), "media-packages/" + sanitize(displayName));
        clearDirectory(target);
        target.mkdirs();
        String payloadType = header.optString("payload_type", ENCRYPTOR_LITE_PAYLOAD_FILE);
        if (ENCRYPTOR_LITE_PAYLOAD_BUNDLE.equals(payloadType)) {
            return untarPackage(plain, target);
        }

        ArrayList<MediaItem> items = new ArrayList<>();
        String fileName = sanitize(header.optString("original_filename", displayName));
        File out = new File(target, fileName);
        try (FileOutputStream file = new FileOutputStream(out)) {
            file.write(plain);
        }
        String kind = classifyKind(out.getName(), guessMimeType(out.getName()));
        if (KIND_VIDEO.equals(kind) || KIND_IMAGE.equals(kind)) {
            items.add(new MediaItem(Uri.fromFile(out).toString(), out.getName(), guessMimeType(out.getName()), kind, "媒体包", System.currentTimeMillis()));
        }
        return items;
    }

    private byte[] readAll(Uri uri) throws IOException {
        try (InputStream input = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) {
                throw new IOException("Unable to open input");
            }
            byte[] buffer = new byte[1024 * 64];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private byte[] decryptPayload(byte[] payload, byte[] salt, byte[] nonce, int iterations) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(MQP_DEFAULT_PHRASE.toCharArray(), salt, iterations, 256);
        byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(MQP_MAGIC.getBytes(StandardCharsets.UTF_8));
        return cipher.doFinal(payload);
    }

    private byte[] decryptAesGcmNoAad(byte[] payload, byte[] key, byte[] nonce) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        return cipher.doFinal(payload);
    }

    private byte[] liteAppKey() throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256").digest(ENCRYPTOR_LITE_KEY_PHRASE.getBytes(StandardCharsets.UTF_8));
    }

    private ArrayList<MediaItem> unzipPackage(byte[] zipBytes, File targetDir) throws IOException {
        ArrayList<MediaItem> items = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                File out = new File(targetDir, sanitize(entry.getName()));
                try (FileOutputStream file = new FileOutputStream(out)) {
                    byte[] buffer = new byte[1024 * 64];
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        file.write(buffer, 0, read);
                    }
                }
                String kind = classifyKind(out.getName(), guessMimeType(out.getName()));
                if (KIND_VIDEO.equals(kind) || KIND_IMAGE.equals(kind)) {
                    items.add(new MediaItem(Uri.fromFile(out).toString(), out.getName(), guessMimeType(out.getName()), kind, "媒体包", System.currentTimeMillis()));
                }
            }
        }
        return items;
    }

    private ArrayList<MediaItem> untarPackage(byte[] tarBytes, File targetDir) throws IOException {
        ArrayList<MediaItem> items = new ArrayList<>();
        int offset = 0;
        while (offset + 512 <= tarBytes.length) {
            if (isZeroTarBlock(tarBytes, offset)) {
                break;
            }
            String entryName = readTarString(tarBytes, offset, 100);
            long size = parseTarSize(tarBytes, offset + 124);
            byte type = tarBytes[offset + 156];
            offset += 512;
            if (size < 0 || offset + size > tarBytes.length) {
                throw new IOException("Invalid tar entry");
            }
            if ((type == 0 || type == '0') && size > 0) {
                String safeName = safeArchiveName(entryName);
                if (!safeName.isEmpty()) {
                    File out = new File(targetDir, safeName);
                    try (FileOutputStream file = new FileOutputStream(out)) {
                        file.write(tarBytes, offset, (int) size);
                    }
                    String kind = classifyKind(out.getName(), guessMimeType(out.getName()));
                    if (KIND_VIDEO.equals(kind) || KIND_IMAGE.equals(kind)) {
                        items.add(new MediaItem(Uri.fromFile(out).toString(), out.getName(), guessMimeType(out.getName()), kind, "媒体包", System.currentTimeMillis()));
                    }
                }
            }
            offset += (int) (((size + 511) / 512) * 512);
        }
        return items;
    }

    private boolean isZeroTarBlock(byte[] data, int offset) {
        for (int i = offset; i < offset + 512 && i < data.length; i++) {
            if (data[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private String readTarString(byte[] data, int offset, int length) {
        int end = offset;
        int limit = Math.min(offset + length, data.length);
        while (end < limit && data[end] != 0) {
            end++;
        }
        return new String(data, offset, end - offset, StandardCharsets.UTF_8);
    }

    private long parseTarSize(byte[] data, int offset) {
        long size = 0;
        int limit = Math.min(offset + 12, data.length);
        for (int i = offset; i < limit; i++) {
            byte value = data[i];
            if (value == 0 || value == ' ') {
                continue;
            }
            if (value < '0' || value > '7') {
                break;
            }
            size = (size * 8) + (value - '0');
        }
        return size;
    }

    private String safeArchiveName(String value) {
        String normalized = value.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        if (name.equals(".") || name.equals("..")) {
            return "";
        }
        return sanitize(name);
    }

    private void showPackageMediaDialog() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, REQ_PACKAGE_MEDIA);
    }

    private void showPackageOptionsDialog(ArrayList<MediaItem> items) {
        pendingPackageItems = items;

        LinearLayout optionsLayout = new LinearLayout(this);
        optionsLayout.setOrientation(LinearLayout.VERTICAL);
        optionsLayout.setPadding(dp(24), dp(12), dp(24), 0);

        TextView infoLabel = new TextView(this);
        infoLabel.setText("已选择 " + items.size() + " 个文件");
        infoLabel.setTextSize(14);
        infoLabel.setTextColor(subtextColor());
        optionsLayout.addView(infoLabel);

        TextView nameLabel = new TextView(this);
        nameLabel.setText("文件名称（留空自动生成）");
        nameLabel.setTextSize(14);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(12);
        optionsLayout.addView(nameLabel, labelParams);
        EditText nameInput = new EditText(this);
        nameInput.setHint("例如: 我的旅行合集");
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        nameInput.setTextSize(14);
        optionsLayout.addView(nameInput);

        TextView pathLabel = new TextView(this);
        pathLabel.setText("保存路径（留空默认 Downloads）");
        pathLabel.setTextSize(14);
        optionsLayout.addView(pathLabel, labelParams);
        LinearLayout pathRow = new LinearLayout(this);
        pathRow.setOrientation(LinearLayout.HORIZONTAL);
        pathRow.setGravity(Gravity.CENTER_VERTICAL);
        activePathInput = new EditText(this);
        activePathInput.setHint("例如: Movies/MyPackages");
        activePathInput.setInputType(InputType.TYPE_CLASS_TEXT);
        activePathInput.setTextSize(14);
        activePathInput.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button pickFolderBtn = new Button(this);
        pickFolderBtn.setText("选择");
        pickFolderBtn.setAllCaps(false);
        pickFolderBtn.setTextSize(13);
        pickFolderBtn.setPadding(dp(12), 0, dp(12), 0);
        pickFolderBtn.setOnClickListener(v -> {
            Intent treeIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(treeIntent, REQ_PICK_FOLDER);
        });
        pathRow.addView(activePathInput);
        pathRow.addView(pickFolderBtn, wrapWithLeft(dp(8)));
        optionsLayout.addView(pathRow);

        TextView suffixLabel = new TextView(this);
        suffixLabel.setText("文件后缀（留空默认 .mqp）");
        suffixLabel.setTextSize(14);
        optionsLayout.addView(suffixLabel, labelParams);
        EditText suffixInput = new EditText(this);
        suffixInput.setHint(".mqp");
        suffixInput.setInputType(InputType.TYPE_CLASS_TEXT);
        suffixInput.setTextSize(14);
        optionsLayout.addView(suffixInput);

        activePackageDialog = new AlertDialog.Builder(this)
                .setTitle("打包媒体")
                .setView(optionsLayout)
                .setPositiveButton("打包", (dialog, which) -> {
                    if (pendingPackageItems == null || pendingPackageItems.isEmpty()) {
                        Toast.makeText(this, "没有可打包的文件", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String customName = nameInput.getText().toString().trim();
                    String customPath = activePathInput.getText().toString().trim();
                    String customSuffix = suffixInput.getText().toString().trim();
                    if (customSuffix.isEmpty()) customSuffix = ".mqp";
                    if (!customSuffix.startsWith(".")) customSuffix = "." + customSuffix;
                    startPackaging(pendingPackageItems, customName, customPath, customSuffix);
                    pendingPackageItems = null;
                })
                .setNegativeButton("取消", (dialog, which) -> {
                    pendingPackageItems = null;
                })
                .create();
        activePackageDialog.show();
    }

    private String extractRelativePathFromTreeUri(Uri treeUri) {
        String docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri);
        if (docId != null && docId.startsWith("primary:")) {
            return docId.substring("primary:".length());
        }
        return docId != null ? docId : "";
    }

    private void startPackaging(ArrayList<MediaItem> items, String customName, String customPath, String customSuffix) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在打包 " + items.size() + " 个媒体...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        new Thread(() -> {
            try {
                String fileName = buildMediaPackage(items, customName, customPath, customSuffix);
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "打包完成: " + fileName, Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "打包失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private String buildMediaPackage(ArrayList<MediaItem> items, String customName, String customPath, String customSuffix) throws Exception {
        ByteArrayOutputStream zipBuffer = new ByteArrayOutputStream();
        JSONArray itemsJson = new JSONArray();
        Set<String> usedNames = new HashSet<>();
        try (ZipOutputStream zos = new ZipOutputStream(zipBuffer)) {
            int index = 0;
            for (MediaItem item : items) {
                index++;
                String arcName = uniqueArchiveName(item.name, usedNames, index);
                usedNames.add(arcName);
                zos.putNextEntry(new ZipEntry(arcName));
                try (InputStream is = getContentResolver().openInputStream(Uri.parse(item.uri))) {
                    if (is == null) continue;
                    byte[] buf = new byte[64 * 1024];
                    int read;
                    while ((read = is.read(buf)) != -1) {
                        zos.write(buf, 0, read);
                    }
                }
                zos.closeEntry();
                JSONObject itemJson = new JSONObject();
                itemJson.put("name", item.name);
                itemJson.put("media_type", KIND_VIDEO.equals(item.kind) ? "video" : "image");
                itemJson.put("path", arcName);
                itemsJson.put(itemJson);
            }
        }
        byte[] zipBytes = zipBuffer.toByteArray();
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        byte[] nonce = new byte[12];
        random.nextBytes(salt);
        random.nextBytes(nonce);
        PBEKeySpec keySpec = new PBEKeySpec(MQP_DEFAULT_PHRASE.toCharArray(), salt, 390000, 256);
        byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).getEncoded();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(MQP_MAGIC.getBytes(StandardCharsets.UTF_8));
        byte[] encryptedPayload = cipher.doFinal(zipBytes);
        JSONObject header = new JSONObject();
        header.put("format", MQP_FORMAT);
        header.put("version", "1");
        header.put("app", "MaoqiuPlayer");
        String baseName = customName.isEmpty() ? ("media-pack-" + System.currentTimeMillis()) : customName;
        header.put("package_name", baseName);
        header.put("created_at", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).format(new java.util.Date()));
        header.put("item_count", items.size());
        header.put("items", itemsJson);
        header.put("suffix", customSuffix);
        header.put("cipher", "AES-256-GCM");
        header.put("kdf", "PBKDF2-HMAC-SHA256");
        header.put("iterations", 390000);
        header.put("salt", bytesToHex(salt));
        header.put("nonce", bytesToHex(nonce));
        header.put("payload_sha256", sha256(encryptedPayload));
        byte[] headerBytes = header.toString().getBytes(StandardCharsets.UTF_8);
        byte[] magicBytes = MQP_MAGIC.getBytes(StandardCharsets.UTF_8);
        String fileName = baseName + customSuffix;
        String relativePath = customPath.isEmpty() ? Environment.DIRECTORY_DOWNLOADS : Environment.DIRECTORY_DOWNLOADS + "/" + customPath;
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream");
            values.put(MediaStore.Downloads.RELATIVE_PATH, relativePath);
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("无法创建文件");
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os == null) throw new IOException("无法写入文件");
                os.write(magicBytes);
                os.write(ByteBuffer.allocate(4).putInt(headerBytes.length).array());
                os.write(headerBytes);
                os.write(encryptedPayload);
            }
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), customPath);
            if (!customPath.isEmpty() && !dir.exists()) dir.mkdirs();
            File outputFile = new File(dir, fileName);
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(magicBytes);
                fos.write(ByteBuffer.allocate(4).putInt(headerBytes.length).array());
                fos.write(headerBytes);
                fos.write(encryptedPayload);
            }
        }
        return fileName;
    }

    private String uniqueArchiveName(String name, Set<String> usedNames, int index) {
        if (!usedNames.contains(name)) return name;
        int dot = name.lastIndexOf('.');
        if (dot < 0) return name + "-" + index;
        return name.substring(0, dot) + "-" + index + name.substring(dot);
    }

    private String bytesToHex(byte[] data) {
        StringBuilder builder = new StringBuilder();
        for (byte b : data) {
            builder.append(String.format(Locale.ROOT, "%02x", b));
        }
        return builder.toString();
    }

    private ArrayList<MediaItem> filteredItems(String filter) {
        ArrayList<MediaItem> items = new ArrayList<>();
        String query = currentQuery.toLowerCase(Locale.ROOT);
        for (MediaItem item : library) {
            boolean matchesFilter = "all".equals(filter) || filter.equals(item.kind);
            boolean matchesQuery = query.isEmpty() || item.name.toLowerCase(Locale.ROOT).contains(query);
            if (matchesFilter && matchesQuery) {
                items.add(item);
            }
        }
        Comparator<MediaItem> comparator;
        if ("name".equals(currentSort)) {
            comparator = Comparator.comparing(a -> a.name.toLowerCase(Locale.ROOT));
        } else if ("type".equals(currentSort)) {
            comparator = Comparator.comparing(a -> a.mimeType);
        } else {
            comparator = (a, b) -> Long.compare(b.timestamp, a.timestamp);
        }
        Collections.sort(items, comparator);
        return items;
    }

    private void addRecent(MediaItem item) {
        removeByUri(recent, item.uri);
        recent.add(0, item.withTimestamp(System.currentTimeMillis()));
        while (recent.size() > 50) {
            recent.remove(recent.size() - 1);
        }
        saveItems(KEY_RECENT, recent);
    }

    private void upsert(ArrayList<MediaItem> items, MediaItem item) {
        removeByUri(items, item.uri);
        items.add(0, item);
    }

    private void removeByUri(ArrayList<MediaItem> items, String uri) {
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).uri.equals(uri)) {
                items.remove(i);
            }
        }
    }

    private ArrayList<MediaItem> loadItems(String key) {
        ArrayList<MediaItem> items = new ArrayList<>();
        String raw = prefs.getString(key, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                items.add(MediaItem.fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException ignored) {
        }
        return items;
    }

    private void saveItems(String key, ArrayList<MediaItem> items) {
        JSONArray array = new JSONArray();
        for (MediaItem item : items) {
            array.put(item.toJson());
        }
        prefs.edit().putString(key, array.toString()).apply();
    }

    private int countKind(String kind) {
        int count = 0;
        for (MediaItem item : library) {
            if (kind.equals(item.kind)) {
                count++;
            }
        }
        return count;
    }

    private void applyPlaybackSpeed() {
        if (currentMediaPlayer == null || Build.VERSION.SDK_INT < 23) {
            return;
        }
        try {
            currentMediaPlayer.setPlaybackParams(currentMediaPlayer.getPlaybackParams().setSpeed(playbackSpeed));
        } catch (Exception ignored) {
        }
    }

    private float nextSpeed(float current) {
        if (current < 1.01f) return 1.25f;
        if (current < 1.26f) return 1.5f;
        if (current < 1.51f) return 2.0f;
        return 1.0f;
    }

    private void setFullscreen(boolean enabled) {
        fullscreen = enabled;
        getWindow().getDecorView().setSystemUiVisibility(enabled
                ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                : 0);
    }

    private void openExternal(MediaItem item) {
        Intent viewIntent = new Intent(Intent.ACTION_VIEW);
        viewIntent.setDataAndType(Uri.parse(item.uri), item.mimeType);
        viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(viewIntent);
        } catch (Exception exc) {
            Toast.makeText(this, "没有找到可打开该媒体的应用", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearDirectory(File dir) {
        if (!dir.exists()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            dir.delete();
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                clearDirectory(file);
            } else {
                file.delete();
            }
        }
        dir.delete();
    }

    private void setScrollableContent(LinearLayout page) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bgColor());
        scroll.addView(page);
        setContentView(scroll);
    }

    private LinearLayout page() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(18), dp(18), dp(28));
        layout.setBackgroundColor(bgColor());
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setPadding(0, dp(6), 0, dp(6));
        return layout;
    }

    private View title(String primary, String secondary) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(8), 0, dp(18));
        TextView title = text(primary, 30, true);
        TextView subtitle = text(secondary, 16, false);
        subtitle.setTextColor(subtextColor());
        box.addView(title);
        box.addView(subtitle);
        return box;
    }

    private View header(String label, View.OnClickListener backListener) {
        LinearLayout row = row();
        ImageButton back = new ImageButton(this);
        back.setImageResource(android.R.drawable.ic_media_previous);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setColorFilter(headerIconColor());
        back.setOnClickListener(backListener);
        TextView title = text(label, 22, true);
        row.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        row.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private TextView section(String label) {
        TextView view = text(label, 15, true);
        view.setTextColor(0xff9fa7b2);
        view.setPadding(0, dp(18), 0, dp(8));
        return view;
    }

    private View empty(String label) {
        TextView view = text(label, 16, false);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(20), dp(46), dp(20), dp(46));
        return view;
    }

    private View card(String title, String subtitle, View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(rounded(surfaceColor(), dp(10), surfaceStroke()));
        card.setClickable(listener != null);
        if (listener != null) {
            card.setOnClickListener(listener);
        }
        TextView titleView = text(title, 17, true);
        TextView subtitleView = text(subtitle, 13, false);
        subtitleView.setTextColor(subtextColor());
        card.addView(titleView);
        card.addView(subtitleView);
        return withTop(card, dp(10));
    }

    private View mediaRow(MediaItem item, ArrayList<MediaItem> source, int index) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(10), dp(16), dp(10));
        card.setBackground(rounded(cardColor(), dp(10), cardStroke()));
        card.setOnClickListener(v -> openItemFromList(source, index));
        card.setLayoutParams(matchWithTop(dp(8)));

        // 左侧缩略图占位
        TextView icon = new TextView(this);
        icon.setText(KIND_VIDEO.equals(item.kind) ? "🎬" : KIND_IMAGE.equals(item.kind) ? "🖼️" : "📦");
        icon.setTextSize(22);
        icon.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        iconLp.rightMargin = dp(12);
        icon.setBackground(rounded(thumbnailBgColor(), dp(8), 0));
        card.addView(icon, iconLp);

        // 右侧文字信息
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView name = text(item.name, 15, true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView meta = text(labelForKind(item.kind) + " · " + item.source, 12, false);
        meta.setTextColor(subtextColor());
        meta.setSingleLine(true);
        info.addView(name);
        info.addView(meta);
        card.addView(info);

        return card;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(textColor());
        view.setLineSpacing(0, 1.12f);
        if (bold) {
            view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }
        return view;
    }

    private EditText input(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setTextColor(textColor());
        input.setHintTextColor(hintColor());
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(rounded(inputBgColor(), dp(9), inputStroke()));
        return input;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(accentTextColor());
        button.setBackground(rounded(accentColor(), dp(9), accentColor()));
        return button;
    }

    private Button ghostButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(textColor());
        button.setBackground(rounded(ghostBgColor(), dp(9), ghostStroke()));
        return button;
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeColor != 0) {
            drawable.setStroke(1, strokeColor);
        }
        return drawable;
    }

    private View withTop(View view, int topMargin) {
        view.setLayoutParams(matchWithTop(topMargin));
        return view;
    }

    private LinearLayout.LayoutParams matchWithTop(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = topMargin;
        return params;
    }

    private LinearLayout.LayoutParams wrapWithLeft(int leftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = leftMargin;
        return params;
    }

    private String libraryTitle(String filter) {
        if (KIND_VIDEO.equals(filter)) return "本地视频";
        if (KIND_IMAGE.equals(filter)) return "本地图片";
        if (KIND_PACKAGE.equals(filter)) return "媒体包管理";
        return "媒体库";
    }

    private String labelForKind(String kind) {
        if (KIND_VIDEO.equals(kind)) return "视频";
        if (KIND_IMAGE.equals(kind)) return "图片";
        if (KIND_PACKAGE.equals(kind)) return "媒体包";
        return "文件";
    }

    private int sortIndex(String sort) {
        if ("name".equals(sort)) return 1;
        if ("type".equals(sort)) return 2;
        return 0;
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        return name.substring(dot).toLowerCase(Locale.ROOT);
    }

    private String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private String sha256(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        StringBuilder builder = new StringBuilder();
        for (byte value : hash) {
            builder.append(String.format(Locale.ROOT, "%02x", value));
        }
        return builder.toString();
    }

    private byte[] hexToBytes(String value) {
        byte[] out = new byte[value.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private View gridCard(String emoji, String title, String subtitle, View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(16), dp(14), dp(14));
        card.setBackground(rounded(surfaceColor(), dp(12), surfaceStroke()));
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setClickable(listener != null);
        if (listener != null) {
            card.setOnClickListener(listener);
        }
        TextView icon = new TextView(this);
        icon.setText(emoji);
        icon.setTextSize(24);
        icon.setGravity(Gravity.CENTER);
        card.addView(icon);
        TextView titleView = text(title, 14, true);
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(0, dp(6), 0, dp(2));
        card.addView(titleView);
        TextView subView = text(subtitle, 11, false);
        subView.setTextColor(subtextColor());
        subView.setGravity(Gravity.CENTER);
        card.addView(subView);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp.rightMargin = dp(8);
        card.setLayoutParams(lp);
        return card;
    }

    private LinearLayout.LayoutParams gridCardRightParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp.leftMargin = dp(8);
        return lp;
    }

    private View chipButton(String label, boolean active, View.OnClickListener listener) {
        Button chip = new Button(this);
        chip.setText(label);
        chip.setAllCaps(false);
        chip.setTextSize(13);
        chip.setPadding(dp(18), dp(6), dp(18), dp(6));
        if (active) {
            chip.setTextColor(chipActiveTextColor());
            chip.setBackground(rounded(chipActiveBgColor(), dp(18), chipActiveBgColor()));
        } else {
            chip.setTextColor(subtextColor());
            chip.setBackground(rounded(chipBgColor(), dp(18), ghostStroke()));
        }
        chip.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        chip.setLayoutParams(lp);
        return chip;
    }

    private View recentCard(MediaItem item, ArrayList<MediaItem> source, int index) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(rounded(surfaceColor(), dp(10), surfaceStroke()));
        card.setOnClickListener(v -> openItemFromList(source, index));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(dp(140), LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.rightMargin = dp(10);
        card.setLayoutParams(cardLp);

        // 缩略图占位
        TextView icon = new TextView(this);
        icon.setText(KIND_VIDEO.equals(item.kind) ? "🎬" : "🖼️");
        icon.setTextSize(28);
        icon.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(80));
        iconLp.bottomMargin = dp(8);
        icon.setBackground(rounded(thumbnailBgColor(), dp(8), 0));
        card.addView(icon, iconLp);

        TextView name = text(item.name, 13, true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(name);
        return card;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private boolean isLightTheme() {
        return THEME_LIGHT.equals(prefs.getString(KEY_THEME, THEME_DARK));
    }

    private int color(int darkColor, int lightColor) {
        return isLightTheme() ? lightColor : darkColor;
    }

    private int bgColor() { return color(0xff17181a, 0xfffafafa); }
    private int surfaceColor() { return color(0xff212224, 0xffffffff); }
    private int surfaceStroke() { return color(0xff2d3036, 0xffe0e0e0); }
    private int cardColor() { return color(0xff212224, 0xfff5f5f5); }
    private int cardStroke() { return color(0xff2a2d33, 0xffe0e0e0); }
    private int textColor() { return color(0xffffffff, 0xff1a1a1a); }
    private int subtextColor() { return color(0xffaeb4bd, 0xff666666); }
    private int hintColor() { return color(0xff767d88, 0xff999999); }
    private int accentColor() { return 0xfffb7299; }
    private int accentTextColor() { return color(0xffffffff, 0xffffffff); }
    private int ghostBgColor() { return color(0xff2c2d30, 0xffeeeeee); }
    private int ghostStroke() { return color(0xff3a3b3e, 0xffcccccc); }
    private int inputBgColor() { return color(0xff2c2d30, 0xffffffff); }
    private int inputStroke() { return color(0xff3a3b3e, 0xffe0e0e0); }
    private int headerIconColor() { return color(0xffffffff, 0xff1a1a1a); }
    private int progressBgColor() { return color(0xff3a3b3e, 0xffcccccc); }
    private int progressFillColor() { return 0xfffb7299; }
    private int dividerColor() { return color(0xff2c2d30, 0xffe0e0e0); }
    private int chipBgColor() { return color(0xff2c2d30, 0xfff0f0f0); }
    private int chipActiveBgColor() { return 0xfffb7299; }
    private int chipActiveTextColor() { return 0xffffffff; }
    private int thumbnailBgColor() { return color(0xff2c2d30, 0xffe8e8e8); }

    private static class MediaItem {
        final String uri;
        final String name;
        final String mimeType;
        final String kind;
        final String source;
        final long timestamp;

        MediaItem(String uri, String name, String mimeType, String kind, String source, long timestamp) {
            this.uri = uri;
            this.name = name == null ? "未命名媒体" : name;
            this.mimeType = mimeType == null ? "" : mimeType;
            this.kind = kind == null ? KIND_UNKNOWN : kind;
            this.source = source == null ? "媒体库" : source;
            this.timestamp = timestamp;
        }

        MediaItem withTimestamp(long value) {
            return new MediaItem(uri, name, mimeType, kind, source, value);
        }

        JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("uri", uri);
                object.put("name", name);
                object.put("mimeType", mimeType);
                object.put("kind", kind);
                object.put("source", source);
                object.put("timestamp", timestamp);
            } catch (JSONException ignored) {
            }
            return object;
        }

        static MediaItem fromJson(JSONObject object) {
            return new MediaItem(
                    object.optString("uri"),
                    object.optString("name"),
                    object.optString("mimeType"),
                    object.optString("kind", KIND_UNKNOWN),
                    object.optString("source", "媒体库"),
                    object.optLong("timestamp", System.currentTimeMillis())
            );
        }
    }

    private interface PositionCallback {
        void onSelected(int position);
    }

    private static class SimpleItemSelectedListener implements android.widget.AdapterView.OnItemSelectedListener {
        private final PositionCallback callback;
        private boolean first = true;

        SimpleItemSelectedListener(PositionCallback callback) {
            this.callback = callback;
        }

        @Override
        public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
            if (first) {
                first = false;
                return;
            }
            callback.onSelected(position);
        }

        @Override
        public void onNothingSelected(android.widget.AdapterView<?> parent) {
        }
    }

    private class ZoomImageView extends View {
        private final Matrix matrix = new Matrix();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        private final ScaleGestureDetector scaleDetector;
        private final PointF last = new PointF();
        private Bitmap bitmap;
        private boolean dragging = false;

        ZoomImageView(Activity activity) {
            super(activity);
            setBackgroundColor(bgColor());
            scaleDetector = new ScaleGestureDetector(activity, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    float factor = detector.getScaleFactor();
                    matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                    invalidate();
                    return true;
                }
            });
        }

        void setBitmap(Bitmap value) {
            bitmap = value;
            fitCenter();
        }

        void fitCenter() {
            if (bitmap == null || getWidth() == 0 || getHeight() == 0) {
                invalidate();
                return;
            }
            matrix.reset();
            float scale = Math.min(1.0f, Math.min((float) getWidth() / bitmap.getWidth(), (float) getHeight() / bitmap.getHeight()));
            float dx = (getWidth() - bitmap.getWidth() * scale) / 2f;
            float dy = (getHeight() - bitmap.getHeight() * scale) / 2f;
            matrix.postScale(scale, scale);
            matrix.postTranslate(dx, dy);
            invalidate();
        }

        void actualSize() {
            if (bitmap == null) return;
            matrix.reset();
            matrix.postTranslate((getWidth() - bitmap.getWidth()) / 2f, (getHeight() - bitmap.getHeight()) / 2f);
            invalidate();
        }

        void rotateRight() {
            matrix.postRotate(90, getWidth() / 2f, getHeight() / 2f);
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            fitCenter();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (bitmap == null) {
                paint.setColor(textColor());
                paint.setTextSize(dp(16));
                canvas.drawText("没有图片", dp(24), getHeight() / 2f, paint);
                return;
            }
            canvas.drawBitmap(bitmap, matrix, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            // Prevent parent from stealing touch events
            getParent().requestDisallowInterceptTouchEvent(true);
            scaleDetector.onTouchEvent(event);
            if (!scaleDetector.isInProgress()) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        last.set(event.getX(), event.getY());
                        dragging = true;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (dragging) {
                            float dx = event.getX() - last.x;
                            float dy = event.getY() - last.y;
                            matrix.postTranslate(dx, dy);
                            last.set(event.getX(), event.getY());
                            invalidate();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        dragging = false;
                        return true;
                    default:
                        break;
                }
            }
            return true;
        }
    }
}
