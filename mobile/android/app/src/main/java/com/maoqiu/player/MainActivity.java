package com.maoqiu.player;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
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
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.InputType;
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
import android.widget.Spinner;
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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends Activity {
    private static final int REQ_OPEN_MEDIA = 1001;
    private static final int REQ_IMPORT_MEDIA = 1002;
    private static final int REQ_SCAN_MEDIA = 1003;

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
        window.setStatusBarColor(0xff0f1114);
        window.setNavigationBarColor(0xff0f1114);
    }

    private void showHome() {
        currentScreen = "home";
        currentMediaPlayer = null;
        setFullscreen(false);

        LinearLayout page = page();
        page.addView(title("毛球播放器", "MaoqiuPlayer"));

        LinearLayout searchRow = row();
        EditText search = input("搜索本地媒体");
        Button searchButton = button("搜索");
        searchButton.setOnClickListener(v -> {
            currentQuery = search.getText().toString().trim();
            showLibrary("all");
        });
        searchRow.addView(search, new LinearLayout.LayoutParams(0, dp(46), 1));
        searchRow.addView(searchButton, wrapWithLeft(dp(10)));
        page.addView(searchRow);

        page.addView(section("主要入口"));
        page.addView(card("最近播放", recent.size() + " 个项目", v -> showRecent()));
        page.addView(card("本地视频", countKind(KIND_VIDEO) + " 个视频", v -> showLibrary(KIND_VIDEO)));
        page.addView(card("本地图片", countKind(KIND_IMAGE) + " 张图片", v -> showLibrary(KIND_IMAGE)));
        page.addView(card("播放列表", library.size() + " 个已导入项目", v -> showPlaylist()));
        page.addView(card("打开媒体", "选择视频、图片或 .mqp 媒体包", v -> openMediaPicker(REQ_OPEN_MEDIA, false)));
        page.addView(card("设置", "播放、媒体库、缓存和关于", v -> showSettings()));

        if (!recent.isEmpty()) {
            page.addView(section("继续播放"));
            for (int i = 0; i < Math.min(3, recent.size()); i++) {
                MediaItem item = recent.get(i);
                page.addView(mediaRow(item, recent, i));
            }
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

        LinearLayout tools = row();
        EditText query = input("搜索名称");
        query.setText(currentQuery);
        Button search = ghostButton("搜索");
        search.setOnClickListener(v -> {
            currentQuery = query.getText().toString().trim();
            showLibrary(currentFilter);
        });
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
        tools.addView(query, new LinearLayout.LayoutParams(0, dp(46), 1));
        tools.addView(search, wrapWithLeft(dp(10)));
        tools.addView(sort, wrapWithLeft(dp(10)));
        page.addView(tools);

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
        page.addView(section("常规"));
        page.addView(card("播放设置", "应用内播放、倍速和全屏控制", null));
        page.addView(card("媒体库", library.size() + " 个本地媒体项目", v -> showLibrary("all")));
        page.addView(card("缓存", "清理媒体包临时文件", v -> {
            clearDirectory(new File(getCacheDir(), "media-packages"));
            Toast.makeText(this, "缓存已清理", Toast.LENGTH_SHORT).show();
        }));
        page.addView(card("高级设置", "媒体包管理、文件校验和数据库维护", v -> showAdvancedTools()));
        page.addView(card("关于毛球播放器", "MaoqiuPlayer 0.1.5", null));

        Button clearRecent = ghostButton("清空最近播放");
        clearRecent.setOnClickListener(v -> {
            recent.clear();
            saveItems(KEY_RECENT, recent);
            Toast.makeText(this, "最近播放已清空", Toast.LENGTH_SHORT).show();
            showSettings();
        });
        page.addView(clearRecent, matchWithTop(dp(12)));

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
        page.addView(card("数据库维护", "重建本机媒体索引", v -> scanLocalMedia(true)));
        page.addView(card("文件校验", "打开媒体包时会自动校验文件完整性", null));
        setScrollableContent(page);
    }

    private void showVideoPlayer(MediaItem item) {
        currentScreen = "video";
        LinearLayout page = page();
        page.addView(header(item.name, v -> showLibrary(currentFilter)));

        VideoView video = new VideoView(this);
        video.setBackgroundColor(Color.BLACK);
        MediaController controller = new MediaController(this);
        controller.setAnchorView(video);
        video.setMediaController(controller);
        video.setVideoURI(Uri.parse(item.uri));
        video.setOnPreparedListener(mp -> {
            currentMediaPlayer = mp;
            applyPlaybackSpeed();
            video.start();
        });
        video.setOnErrorListener((mp, what, extra) -> {
            Toast.makeText(this, "该视频暂时无法在应用内播放", Toast.LENGTH_LONG).show();
            return false;
        });
        page.addView(video, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(320)));

        LinearLayout controls = row();
        Button previous = ghostButton("上一项");
        previous.setOnClickListener(v -> openNeighbor(-1));
        Button play = button("播放/暂停");
        play.setOnClickListener(v -> {
            if (video.isPlaying()) {
                video.pause();
            } else {
                video.start();
            }
        });
        Button next = ghostButton("下一项");
        next.setOnClickListener(v -> openNeighbor(1));
        Button speed = ghostButton(String.format(Locale.ROOT, "%.2fx", playbackSpeed));
        speed.setOnClickListener(v -> {
            playbackSpeed = nextSpeed(playbackSpeed);
            speed.setText(String.format(Locale.ROOT, "%.2fx", playbackSpeed));
            applyPlaybackSpeed();
        });
        Button full = ghostButton("全屏");
        full.setOnClickListener(v -> setFullscreen(!fullscreen));
        controls.addView(previous, new LinearLayout.LayoutParams(0, dp(46), 1));
        controls.addView(play, wrapWithLeft(dp(8)));
        controls.addView(next, wrapWithLeft(dp(8)));
        controls.addView(speed, wrapWithLeft(dp(8)));
        controls.addView(full, wrapWithLeft(dp(8)));
        page.addView(controls);

        Button external = ghostButton("系统播放器");
        external.setOnClickListener(v -> openExternal(item));
        page.addView(external, matchWithTop(dp(10)));

        setScrollableContent(page);
    }

    private void showImageViewer(MediaItem item) {
        currentScreen = "image";
        LinearLayout page = page();
        page.addView(header(item.name, v -> showLibrary(currentFilter)));

        ZoomImageView imageView = new ZoomImageView(this);
        try {
            imageView.setBitmap(loadBitmap(Uri.parse(item.uri)));
        } catch (IOException exc) {
            Toast.makeText(this, "无法显示该图片", Toast.LENGTH_LONG).show();
        }
        page.addView(imageView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(430)));

        LinearLayout controls = row();
        Button previous = ghostButton("上一张");
        previous.setOnClickListener(v -> openNeighbor(-1));
        Button next = ghostButton("下一张");
        next.setOnClickListener(v -> openNeighbor(1));
        Button fit = ghostButton("适应");
        fit.setOnClickListener(v -> imageView.fitCenter());
        Button actual = ghostButton("实际大小");
        actual.setOnClickListener(v -> imageView.actualSize());
        Button rotate = ghostButton("旋转");
        rotate.setOnClickListener(v -> imageView.rotateRight());
        controls.addView(previous, new LinearLayout.LayoutParams(0, dp(46), 1));
        controls.addView(next, wrapWithLeft(dp(8)));
        controls.addView(fit, wrapWithLeft(dp(8)));
        controls.addView(actual, wrapWithLeft(dp(8)));
        controls.addView(rotate, wrapWithLeft(dp(8)));
        page.addView(controls);

        setScrollableContent(page);
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
                    Toast.makeText(this, "无法打开该媒体包，请检查文件完整性", Toast.LENGTH_LONG).show();
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
        return new MediaItem(uri.toString(), name, mime == null ? "" : mime, classifyKind(name, mime), "已导入", System.currentTimeMillis());
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

    private ArrayList<MediaItem> extractPackage(Uri uri, String displayName) throws IOException, JSONException, GeneralSecurityException, NoSuchAlgorithmException {
        byte[] data = readAll(uri);
        byte[] magic = MQP_MAGIC.getBytes(StandardCharsets.UTF_8);
        if (data.length < magic.length + 4) {
            throw new IOException("Invalid package");
        }
        for (int i = 0; i < magic.length; i++) {
            if (data[i] != magic[i]) {
                throw new IOException("Invalid package magic");
            }
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
        scroll.setBackgroundColor(0xff111214);
        scroll.addView(page);
        setContentView(scroll);
    }

    private LinearLayout page() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(18), dp(18), dp(28));
        layout.setBackgroundColor(0xff111214);
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
        subtitle.setTextColor(0xffaeb4bd);
        box.addView(title);
        box.addView(subtitle);
        return box;
    }

    private View header(String label, View.OnClickListener backListener) {
        LinearLayout row = row();
        ImageButton back = new ImageButton(this);
        back.setImageResource(android.R.drawable.ic_media_previous);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setColorFilter(0xfff4f1ea);
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
        card.setBackground(rounded(0xff202226, dp(10), 0xff2d3036));
        card.setClickable(listener != null);
        if (listener != null) {
            card.setOnClickListener(listener);
        }
        TextView titleView = text(title, 17, true);
        TextView subtitleView = text(subtitle, 13, false);
        subtitleView.setTextColor(0xffaeb4bd);
        card.addView(titleView);
        card.addView(subtitleView);
        return withTop(card, dp(10));
    }

    private View mediaRow(MediaItem item, ArrayList<MediaItem> source, int index) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(13), dp(16), dp(13));
        card.setBackground(rounded(0xff1b1d21, dp(9), 0xff2a2d33));
        card.setOnClickListener(v -> openItemFromList(source, index));
        TextView name = text(item.name, 16, true);
        TextView meta = text(labelForKind(item.kind) + " · " + item.source, 12, false);
        meta.setTextColor(0xffaeb4bd);
        card.addView(name);
        card.addView(meta);
        return withTop(card, dp(8));
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(0xfff4f1ea);
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
        input.setTextColor(0xfff4f1ea);
        input.setHintTextColor(0xff767d88);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(rounded(0xff202226, dp(9), 0xff2d3036));
        return input;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(0xff06131c);
        button.setBackground(rounded(0xff82d8ff, dp(9), 0xff82d8ff));
        return button;
    }

    private Button ghostButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(0xfff4f1ea);
        button.setBackground(rounded(0xff25282d, dp(9), 0xff363a42));
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

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

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
            setBackgroundColor(Color.BLACK);
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
                paint.setColor(0xfff4f1ea);
                paint.setTextSize(dp(16));
                canvas.drawText("没有图片", dp(24), getHeight() / 2f, paint);
                return;
            }
            canvas.drawBitmap(bitmap, matrix, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
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
