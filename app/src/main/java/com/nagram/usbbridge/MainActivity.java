package com.nagram.usbbridge;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int REQ_TREE = 2001;
    private static final int REQ_SHIZUKU = 2002;
    private static final int REQ_NOTIFICATIONS = 2003;

    private static final int BG = Color.rgb(0, 0, 0);
    private static final int CARD = Color.rgb(7, 11, 16);
    private static final int CARD_2 = Color.rgb(10, 15, 22);
    private static final int BORDER = Color.rgb(32, 42, 55);
    private static final int TEXT = Color.rgb(246, 248, 252);
    private static final int MUTED = Color.rgb(155, 166, 185);
    private static final int BLUE = Color.rgb(47, 140, 255);
    private static final int CYAN = Color.rgb(33, 187, 255);
    private static final int GREEN = Color.rgb(88, 235, 75);
    private static final int AMBER = Color.rgb(255, 177, 27);
    private static final int RED = Color.rgb(255, 78, 96);

    private enum Page { HOME, ACTIVITY, FILES, SETTINGS }

    private SharedPreferences prefs;
    private BridgeDatabase db;
    private FrameLayout pageHost;
    private Page currentPage = Page.HOME;
    private final LinearLayout[] navRoots = new LinearLayout[4];
    private final TextView[] navLabels = new TextView[4];
    private final TextView[] navIcons = new TextView[4];

    // Home live widgets.
    private TextView heroTitle, heroSub, todayPill;
    private PremiumViews.ShieldPulseView shield;
    private LinearLayout heroCard;
    private LinearLayout transferCardView;
    private PremiumViews.ArcProgressView arcProgress;
    private ProgressBar transferBar;
    private TextView transferName, transferSize, transferSpeed, transferEta;
    private Button automationButton;
    private TextView phoneStorageMain, phoneStorageSub, usbStorageMain, usbStorageSub;
    private ProgressBar phoneStorageBar, usbStorageBar;
    private TextView nagramState, shizukuState, usbState;
    private LinearLayout recentHomeList;
    private TextView saferBanner;

    // Other live widgets.
    private LinearLayout activityList;
    private TextView filesUsbTitle, filesUsbSub;
    private TextView settingsBridgeState;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private long lastActivityRender = 0L;
    private long lastHomeRecentRender = 0L;
    private boolean suppressMigrationToggle = false;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refresh();
            boolean active = prefs != null && prefs.getString("current_name", null) != null;
            long next = active ? 900L : (currentPage == Page.HOME ? 3200L : 4500L);
            refreshHandler.postDelayed(this, next);
        }
    };

    private final Shizuku.OnBinderReceivedListener binderReceived = this::refresh;
    private final Shizuku.OnBinderDeadListener binderDead = this::refresh;
    private final Shizuku.OnRequestPermissionResultListener permissionResult = (requestCode, grantResult) -> {
        if (requestCode == REQ_SHIZUKU) refresh();
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("bridge", MODE_PRIVATE);
        db = new BridgeDatabase(this);
        seedDefaults();
        configureSystemBars();
        setContentView(buildShell());

        Shizuku.addBinderReceivedListenerSticky(binderReceived);
        Shizuku.addBinderDeadListener(binderDead);
        Shizuku.addRequestPermissionResultListener(permissionResult);
        showPage(Page.HOME);
        refresh();
    }

    private void seedDefaults() {
        SharedPreferences.Editor e = prefs.edit();
        boolean changed = false;
        if (!prefs.contains("delete_after_verified")) { e.putBoolean("delete_after_verified", true); changed = true; }
        if (!prefs.getBoolean("duplicate_guard", true)) { e.putBoolean("duplicate_guard", true); changed = true; }
        else if (!prefs.contains("duplicate_guard")) { e.putBoolean("duplicate_guard", true); changed = true; }
        if (!prefs.contains("cleanup_delay_ms")) { e.putLong("cleanup_delay_ms", 5L * 60L * 1000L); changed = true; }
        if (!prefs.contains("reserve_bytes")) { e.putLong("reserve_bytes", 1024L * 1024L * 1024L); changed = true; }
        if (changed) e.apply();
    }

    private View buildShell() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(BG);

        pageHost = new FrameLayout(this);
        shell.addView(pageHost, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout.LayoutParams navLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(isCompactWidth() ? 76 : 82));
        navLp.setMargins(dp(10), 0, dp(10), dp(6));
        shell.addView(buildBottomNav(), navLp);
        applySystemBarInsets(shell);
        return shell;
    }

    private View buildBottomNav() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setPadding(dp(9), dp(8), dp(9), dp(8));
        wrap.setGravity(Gravity.CENTER);
        GradientDrawable bg = rounded(Color.rgb(7, 11, 16), 24, Color.rgb(38, 48, 63), 1);
        wrap.setBackground(bg);
        wrap.setElevation(dp(7));

        addNavItem(wrap, 0, "⌂", "Home", Page.HOME);
        addNavItem(wrap, 1, "≋", "Activity", Page.ACTIVITY);
        addNavItem(wrap, 2, "▣", "Files", Page.FILES);
        addNavItem(wrap, 3, "⚙", "Settings", Page.SETTINGS);
        return wrap;
    }

    private void addNavItem(LinearLayout parent, int index, String icon, String text, Page page) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(5), dp(5), dp(5), dp(4));
        TextView i = label(icon, isCompactWidth() ? 23 : 25, MUTED, false);
        TextView t = label(text, isCompactWidth() ? 11 : 12, MUTED, false);
        t.setGravity(Gravity.CENTER);
        item.addView(i);
        item.addView(t);
        item.setOnClickListener(v -> showPage(page));
        navRoots[index] = item;
        navIcons[index] = i;
        navLabels[index] = t;
        parent.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    }

    private void showPage(Page page) {
        currentPage = page;
        pageHost.removeAllViews();
        View v;
        switch (page) {
            case ACTIVITY: v = buildActivityPage(); break;
            case FILES: v = buildFilesPage(); break;
            case SETTINGS: v = buildSettingsPage(); break;
            default: v = buildHomePage(); break;
        }
        pageHost.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        animatePage(v);
        updateNav();
        refresh();
    }

    private void updateNav() {
        for (int i = 0; i < navRoots.length; i++) {
            boolean selected = i == currentPage.ordinal();
            navIcons[i].setTextColor(selected ? BLUE : MUTED);
            navLabels[i].setTextColor(selected ? BLUE : MUTED);
            navLabels[i].setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            navRoots[i].setBackground(selected ? rounded(Color.rgb(7, 24, 47), 18, Color.rgb(31, 111, 222), 1) : null);
        }
    }

    private View buildHomePage() {
        ScrollView scroll = premiumScroll();
        LinearLayout root = pageRoot();
        scroll.addView(root);

        root.addView(buildHeader());
        root.addView(space(14));
        root.addView(buildHeroCard());
        root.addView(space(14));
        root.addView(buildTransferCard());
        root.addView(space(14));
        root.addView(buildStorageCard());
        root.addView(space(12));
        root.addView(buildConnectionsCard());
        root.addView(space(12));
        saferBanner = label("", 12, AMBER, true);
        saferBanner.setVisibility(View.GONE);
        saferBanner.setPadding(dp(14), dp(11), dp(14), dp(11));
        saferBanner.setBackground(rounded(Color.rgb(35, 24, 6), 14, Color.rgb(101, 72, 17), 1));
        root.addView(saferBanner);
        root.addView(buildRecentCard());
        root.addView(space(20));
        return scroll;
    }

    private View buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(5), dp(4), dp(2), dp(3));

        ImageView avatar = new ImageView(this);
        avatar.setImageResource(R.drawable.profile_shahadat);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setClipToOutline(true);
        avatar.setBackground(roundedOval(Color.rgb(11, 20, 34), Color.rgb(45, 118, 255), 2));
        int avatarSize = dp(isCompactWidth() ? 58 : 68);
        row.addView(avatar, new LinearLayout.LayoutParams(avatarSize, avatarSize));
        avatar.setContentDescription("SHAHADAT profile");
        avatar.setAlpha(0f);
        avatar.setScaleX(.94f);
        avatar.setScaleY(.94f);
        avatar.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(420L).start();

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(dp(14), 0, 0, 0);
        names.addView(label("SHAHADAT", isCompactWidth() ? 21 : 23, TEXT, true));
        names.addView(label("USB Bridge", isCompactWidth() ? 13 : 14, MUTED, false));
        row.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView bell = label("♢", 27, Color.rgb(194, 205, 222), false);
        bell.setGravity(Gravity.CENTER);
        bell.setContentDescription("Open activity");
        bell.setOnClickListener(v -> showPage(Page.ACTIVITY));
        row.addView(bell, new LinearLayout.LayoutParams(dp(isCompactWidth() ? 42 : 48), dp(48)));
        TextView menu = label("⋮", 28, Color.rgb(194, 205, 222), false);
        menu.setGravity(Gravity.CENTER);
        menu.setContentDescription("Open settings");
        menu.setOnClickListener(v -> showPage(Page.SETTINGS));
        row.addView(menu, new LinearLayout.LayoutParams(dp(38), dp(48)));
        return row;
    }

    private View buildHeroCard() {
        heroCard = new LinearLayout(this);
        heroCard.setOrientation(LinearLayout.HORIZONTAL);
        heroCard.setGravity(Gravity.CENTER_VERTICAL);
        heroCard.setPadding(dp(10), dp(12), dp(15), dp(12));
        setHeroBackground(GREEN, false);

        shield = new PremiumViews.ShieldPulseView(this);
        int shieldSize = dp(isCompactWidth() ? 108 : 138);
        heroCard.addView(shield, new LinearLayout.LayoutParams(shieldSize, shieldSize));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(4), 0, 0, 0);
        heroTitle = label("ALL CAUGHT UP", isCompactWidth() ? 19 : 21, GREEN, true);
        heroTitle.setSingleLine(true);
        heroTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heroSub = label("No pending Nagram files.", isCompactWidth() ? 13 : 14, TEXT, false);
        heroSub.setPadding(0, dp(4), 0, dp(10));
        todayPill = label("Today: —", isCompactWidth() ? 11 : 13, Color.rgb(205, 214, 226), false);
        todayPill.setPadding(dp(13), dp(9), dp(13), dp(9));
        todayPill.setBackground(rounded(Color.argb(115, 24, 34, 29), 13, Color.rgb(42, 72, 51), 1));
        text.addView(heroTitle);
        text.addView(heroSub);
        text.addView(todayPill);
        heroCard.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return heroCard;
    }

    private View buildTransferCard() {
        transferCardView = new LinearLayout(this);
        transferCardView.setOrientation(LinearLayout.HORIZONTAL);
        transferCardView.setGravity(Gravity.CENTER_VERTICAL);
        transferCardView.setPadding(dp(10), dp(12), dp(14), dp(12));
        transferCardView.setBackground(roundedGradient(new int[]{Color.rgb(4, 14, 31), Color.rgb(7, 13, 24)}, 22, Color.rgb(22, 89, 183)));

        arcProgress = new PremiumViews.ArcProgressView(this);
        int arcSize = dp(isCompactWidth() ? 108 : 132);
        transferCardView.addView(arcProgress, new LinearLayout.LayoutParams(arcSize, arcSize));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(10), 0, 0, 0);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        transferName = label("Ready for next file", isCompactWidth() ? 15 : 17, TEXT, true);
        transferName.setSingleLine(true);
        transferName.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        transferSpeed = label("● Ready", isCompactWidth() ? 10 : 12, BLUE, true);
        transferSpeed.setGravity(Gravity.CENTER);
        transferSpeed.setPadding(dp(isCompactWidth() ? 7 : 10), dp(6), dp(isCompactWidth() ? 7 : 10), dp(6));
        transferSpeed.setBackground(rounded(Color.rgb(5, 27, 59), 14, Color.rgb(23, 92, 184), 1));
        titleRow.addView(transferName, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        titleRow.addView(transferSpeed);
        right.addView(titleRow);

        transferSize = label("No active transfer", 13, MUTED, false);
        transferSize.setPadding(0, dp(5), 0, dp(8));
        right.addView(transferSize);

        transferBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        transferBar.setMax(100);
        transferBar.setProgressTintList(ColorStateList.valueOf(BLUE));
        transferBar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(20, 31, 48)));
        right.addView(transferBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(7)));

        transferEta = label("Waiting safely", 12, MUTED, false);
        transferEta.setPadding(0, dp(8), 0, dp(7));
        right.addView(transferEta);

        automationButton = smallActionButton("Start Automation", BLUE);
        automationButton.setOnClickListener(v -> toggleBridge());
        right.addView(automationButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(45)));
        transferCardView.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return transferCardView;
    }

    private View buildStorageCard() {
        LinearLayout card = premiumCard();
        card.addView(sectionTitle("STORAGE"));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(9), 0, 0);

        View phone = buildStorageMini(true);
        View usb = buildStorageMini(false);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp1.setMargins(0, 0, dp(6), 0);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp2.setMargins(dp(6), 0, 0, 0);
        row.addView(phone, lp1);
        row.addView(usb, lp2);
        card.addView(row);
        return card;
    }

    private View buildStorageMini(boolean phone) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        box.setBackground(rounded(Color.rgb(10, 15, 21), 17, phone ? Color.rgb(39, 63, 48) : Color.rgb(78, 59, 27), 1));
        TextView title = label(phone ? "▯  Phone Storage" : "♙  Main USB", isCompactWidth() ? 11 : 12, TEXT, true);
        TextView main = label("Checking…", isCompactWidth() ? 15 : 17, phone ? GREEN : AMBER, true);
        main.setPadding(0, dp(8), 0, dp(7));
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(ColorStateList.valueOf(phone ? GREEN : AMBER));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(27, 31, 36)));
        TextView sub = label("—", isCompactWidth() ? 10 : 11, MUTED, false);
        sub.setPadding(0, dp(6), 0, 0);
        box.addView(title); box.addView(main); box.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6))); box.addView(sub);
        if (phone) { phoneStorageMain = main; phoneStorageBar = bar; phoneStorageSub = sub; }
        else { usbStorageMain = main; usbStorageBar = bar; usbStorageSub = sub; }
        return box;
    }

    private View buildConnectionsCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(8), dp(9), dp(8), dp(9));
        card.setBackground(rounded(Color.rgb(6, 10, 14), 18, Color.rgb(35, 46, 59), 1));
        nagramState = connectionItem(card, "N", "Nagram");
        divider(card);
        shizukuState = connectionItem(card, "S", "Shizuku");
        divider(card);
        usbState = connectionItem(card, "↯", "USB");
        return card;
    }

    private TextView connectionItem(LinearLayout root, String icon, String text) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER);
        TextView bubble = label(icon, isCompactWidth() ? 14 : 16, GREEN, true);
        bubble.setGravity(Gravity.CENTER);
        bubble.setBackground(roundedOval(Color.rgb(9, 42, 21), Color.rgb(27, 90, 42), 1));
        int bubbleSize = dp(isCompactWidth() ? 30 : 36);
        item.addView(bubble, new LinearLayout.LayoutParams(bubbleSize, bubbleSize));
        TextView state = label(text + "  •", isCompactWidth() ? 10 : 12, TEXT, false);
        state.setPadding(dp(isCompactWidth() ? 4 : 8), 0, 0, 0);
        item.addView(state);
        root.addView(item, new LinearLayout.LayoutParams(0, dp(45), 1f));
        return state;
    }

    private void divider(LinearLayout root) {
        View d = new View(this);
        d.setBackgroundColor(Color.rgb(32, 42, 54));
        root.addView(d, new LinearLayout.LayoutParams(dp(1), dp(30)));
    }

    private View buildRecentCard() {
        LinearLayout card = premiumCard();
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(sectionTitle("RECENT ACTIVITY"), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView all = label("View all  ›", 12, BLUE, false);
        all.setOnClickListener(v -> showPage(Page.ACTIVITY));
        head.addView(all);
        card.addView(head);
        recentHomeList = new LinearLayout(this);
        recentHomeList.setOrientation(LinearLayout.VERTICAL);
        recentHomeList.setPadding(0, dp(8), 0, 0);
        card.addView(recentHomeList);
        return card;
    }

    private View buildActivityPage() {
        ScrollView scroll = premiumScroll();
        LinearLayout root = pageRoot();
        scroll.addView(root);
        root.addView(pageHeader("Activity", "Transfer history & safety events"));
        root.addView(space(12));
        LinearLayout summary = premiumCard();
        summary.addView(sectionTitle("TODAY"));
        BridgeDatabase.Stats s = db.todayStats();
        summary.addView(label(s.files + " completed  •  " + BridgeService.human(s.bytes) + "  •  " + s.attention + " attention", 15, TEXT, true));
        root.addView(summary);
        root.addView(space(12));
        LinearLayout history = premiumCard();
        history.addView(sectionTitle("ALL RECENT EVENTS"));
        activityList = new LinearLayout(this);
        activityList.setOrientation(LinearLayout.VERTICAL);
        activityList.setPadding(0, dp(8), 0, 0);
        history.addView(activityList);
        root.addView(history);
        root.addView(space(20));
        renderActivityList(activityList, 100, true);
        return scroll;
    }

    private View buildFilesPage() {
        ScrollView scroll = premiumScroll();
        LinearLayout root = pageRoot();
        scroll.addView(root);
        root.addView(pageHeader("Files", "USB destination & migration controls"));
        root.addView(space(12));

        LinearLayout usbCard = premiumCard();
        usbCard.addView(sectionTitle("MAIN USB DESTINATION"));
        filesUsbTitle = label("No USB folder selected", 17, TEXT, true);
        filesUsbSub = label("Choose the Pendrive folder that Bridge is allowed to use.", 12, MUTED, false);
        filesUsbSub.setPadding(0, dp(5), 0, dp(10));
        usbCard.addView(filesUsbTitle); usbCard.addView(filesUsbSub);
        Button choose = neonButton("Choose USB / Pendrive Folder", BLUE);
        choose.setOnClickListener(v -> chooseUsbFolder());
        usbCard.addView(choose);
        Button test = ghostButton("Test destination safely");
        test.setOnClickListener(v -> testDestination());
        usbCard.addView(test);
        root.addView(usbCard);
        root.addView(space(12));

        LinearLayout migration = premiumCard();
        migration.addView(sectionTitle("EXISTING FILES / MIGRATION"));
        CheckBox moveExisting = premiumCheck("Process existing Nagram files", prefs.getBoolean("move_existing", false));
        moveExisting.setOnCheckedChangeListener((b, checked) -> {
            if (suppressMigrationToggle) return;
            if (checked && !prefs.getBoolean("move_existing", false)) {
                suppressMigrationToggle = true;
                b.setChecked(false);
                suppressMigrationToggle = false;
                new AlertDialog.Builder(this)
                        .setTitle("Include existing Nagram files?")
                        .setMessage("Older files can be numerous. They will still use Duplicate Guard, verification, safety delay and cleanup revalidation. Nothing is deleted just because this option is enabled.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Enable", (d, w) -> {
                            prefs.edit().putBoolean("move_existing", true).apply();
                            suppressMigrationToggle = true;
                            b.setChecked(true);
                            suppressMigrationToggle = false;
                        })
                        .show();
            } else {
                prefs.edit().putBoolean("move_existing", checked).apply();
            }
        });
        migration.addView(moveExisting);
        migration.addView(label("OFF = only new completed downloads. ON = older Nagram media is also eligible, still using the same verification and cleanup safety gates.", 12, MUTED, false));
        root.addView(migration);
        root.addView(space(12));

        LinearLayout duplicate = premiumCard();
        duplicate.addView(sectionTitle("SAME VIDEO DUPLICATE GUARD"));
        duplicate.addView(label("Filename changes do not matter. Exact byte size is only the candidate filter; content fingerprint and SHA-256 confirmation decide a verified duplicate.", 13, TEXT, false));
        duplicate.addView(label("Same displayed MB alone will never cause a skip or delete.", 12, GREEN, true));
        root.addView(duplicate);
        root.addView(space(20));
        return scroll;
    }

    private View buildSettingsPage() {
        ScrollView scroll = premiumScroll();
        LinearLayout root = pageRoot();
        scroll.addView(root);
        root.addView(pageHeader("Settings", "Winner safety engine • Premium V3"));
        root.addView(space(12));

        LinearLayout bridge = premiumCard();
        bridge.addView(sectionTitle("BRIDGE CONNECTION"));
        settingsBridgeState = label("Checking Shizuku…", 14, TEXT, true);
        bridge.addView(settingsBridgeState);
        Button grant = neonButton("Grant Shizuku Permission", BLUE);
        grant.setOnClickListener(v -> requestShizuku());
        bridge.addView(grant);
        root.addView(bridge);
        root.addView(space(12));

        LinearLayout automation = premiumCard();
        automation.addView(sectionTitle("AUTOMATION"));
        CheckBox boot = premiumCheck("Boot recovery / auto-start Bridge", prefs.getBoolean("auto_start", false));
        boot.setOnCheckedChangeListener((b, checked) -> prefs.edit().putBoolean("auto_start", checked).apply());
        automation.addView(boot);
        Button toggle = neonButton(prefs.getBoolean("running", false) ? "Pause Automation" : "Start Automation", BLUE);
        toggle.setOnClickListener(v -> { toggleBridge(); showPage(Page.SETTINGS); });
        automation.addView(toggle);
        root.addView(automation);
        root.addView(space(12));

        LinearLayout safety = premiumCard();
        safety.addView(sectionTitle("SAFE CLEANUP — RECOMMENDED"));
        safety.addView(label("Transfer → Verify → 5 min safety delay → Destination revalidation → Source cleanup", 12, GREEN, true));
        CheckBox delete = premiumCheck("Delete source only after verified safe cleanup", prefs.getBoolean("delete_after_verified", true));
        delete.setOnCheckedChangeListener((b, checked) -> prefs.edit().putBoolean("delete_after_verified", checked).apply());
        safety.addView(delete);
        safety.addView(infoRow("Same Video Duplicate Guard", "Always ON ✓"));
        safety.addView(infoRow("Cleanup delay", humanDuration(prefs.getLong("cleanup_delay_ms", 5L * 60L * 1000L))));
        safety.addView(infoRow("USB free-space reserve", BridgeService.human(prefs.getLong("reserve_bytes", 1024L * 1024L * 1024L))));
        root.addView(safety);
        root.addView(space(12));

        LinearLayout recovery = premiumCard();
        recovery.addView(sectionTitle("RECOVERY & DIAGNOSTICS"));
        recovery.addView(label("If the journal is uncertain, USB is removed, verification fails, or Nagram changes the source during transfer: the original is preserved.", 12, MUTED, false));
        Button test = ghostButton("Test USB destination");
        test.setOnClickListener(v -> testDestination());
        recovery.addView(test);
        Button reset = ghostButton("Reset Safer Mode after review");
        reset.setOnClickListener(v -> confirmResetSafety());
        recovery.addView(reset);
        root.addView(recovery);
        root.addView(space(12));

        LinearLayout profile = premiumCard();
        profile.addView(sectionTitle("PROFILE"));
        LinearLayout pr = new LinearLayout(this);
        pr.setOrientation(LinearLayout.HORIZONTAL); pr.setGravity(Gravity.CENTER_VERTICAL);
        ImageView img = new ImageView(this); img.setImageResource(R.drawable.profile_shahadat); img.setScaleType(ImageView.ScaleType.CENTER_CROP); img.setClipToOutline(true); img.setBackground(roundedOval(Color.rgb(12, 20, 34), BLUE, 2));
        pr.addView(img, new LinearLayout.LayoutParams(dp(60), dp(60)));
        LinearLayout tx = new LinearLayout(this); tx.setOrientation(LinearLayout.VERTICAL); tx.setPadding(dp(12),0,0,0);
        tx.addView(label("SHAHADAT", 18, TEXT, true)); tx.addView(label("Nagram USB Bridge V3", 12, MUTED, false));
        pr.addView(tx);
        profile.addView(pr);
        root.addView(profile);
        root.addView(space(20));
        return scroll;
    }

    private View pageHeader(String title, String sub) {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setPadding(dp(4), dp(8), dp(4), dp(3));
        h.addView(label(title, 27, TEXT, true));
        TextView s = label(sub, 13, MUTED, false); s.setPadding(0, dp(3), 0, 0); h.addView(s);
        return h;
    }

    private View infoRow(String left, String right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(0, dp(10), 0, dp(2));
        row.addView(label(left, 13, MUTED, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(label(right, 13, TEXT, true));
        return row;
    }

    private void refresh() {
        if (prefs == null) return;
        runOnUiThread(() -> {
            boolean binder = Shizuku.pingBinder();
            boolean granted = false;
            if (binder) {
                try { granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED; } catch (Throwable ignored) {}
            }
            boolean running = prefs.getBoolean("running", false);
            boolean cleanupSuspended = prefs.getBoolean("cleanup_suspended", false);
            String tree = prefs.getString("tree_uri", null);

            if (currentPage == Page.HOME && heroTitle != null) {
                refreshHome(binder, granted, running, cleanupSuspended, tree);
            }
            if (currentPage == Page.FILES && filesUsbTitle != null) refreshFiles(tree);
            if (currentPage == Page.SETTINGS && settingsBridgeState != null) {
                settingsBridgeState.setText(!binder ? "Shizuku not running • originals safe" : granted ? "Shizuku connected + allowed ✓" : "Shizuku permission required");
                settingsBridgeState.setTextColor(granted ? GREEN : AMBER);
            }
            if (currentPage == Page.ACTIVITY && activityList != null && System.currentTimeMillis() - lastActivityRender > 5000L) {
                renderActivityList(activityList, 100, true);
                lastActivityRender = System.currentTimeMillis();
            }
        });
    }

    private void refreshHome(boolean binder, boolean granted, boolean running, boolean cleanupSuspended, String tree) {
        String current = prefs.getString("current_name", null);
        String lastStatus = prefs.getString("last_status", "All caught up ✓");

        if (current != null) {
            setHeroState("TRANSFER ACTIVE", "Moving to USB with verification protection.", BLUE, PremiumViews.ShieldPulseView.BLUE, true);
            int pct = prefs.getInt("current_percent", 0);
            long done = prefs.getLong("current_done", 0L);
            long total = prefs.getLong("current_total", 0L);
            long speed = prefs.getLong("current_speed", 0L);
            long eta = prefs.getLong("current_eta", -1L);
            transferName.setText(current);
            transferSize.setText(BridgeService.human(done) + " of " + BridgeService.human(total));
            transferSpeed.setText("⚡ " + BridgeService.humanRate(speed));
            transferBar.setProgress(pct, true);
            arcProgress.setProgressAnimated(pct);
            transferEta.setText(eta >= 0 ? "◷  ~" + formatEta(eta) + " remaining" : "Verifying safe transfer…");
        } else {
            transferName.setText("Ready for next file");
            transferSize.setText(lastStatus == null ? "No active transfer" : lastStatus);
            transferSpeed.setText("● Ready");
            transferBar.setProgress(0, true);
            arcProgress.setProgressAnimated(0);
            transferEta.setText("Watching safely for completed downloads");

            if (!binder || !granted) setHeroState("SHIZUKU REQUIRED", "Your originals are safe. Start / allow Shizuku.", AMBER, PremiumViews.ShieldPulseView.AMBER, false);
            else if (tree == null) setHeroState("USB NEEDED", "Choose your approved Pendrive destination.", AMBER, PremiumViews.ShieldPulseView.AMBER, false);
            else if (cleanupSuspended) setHeroState("SAFER MODE", "Automatic source cleanup is suspended.", AMBER, PremiumViews.ShieldPulseView.AMBER, false);
            else if (running && lastStatus != null && lastStatus.toLowerCase(Locale.ROOT).contains("all caught up")) setHeroState("ALL CAUGHT UP", "No pending Nagram files.", GREEN, PremiumViews.ShieldPulseView.GREEN, false);
            else if (running) setHeroState("BRIDGE READY", "Watching Nagram for completed files.", GREEN, PremiumViews.ShieldPulseView.GREEN, false);
            else setHeroState("AUTOMATION PAUSED", "Start Automation when you are ready.", BLUE, PremiumViews.ShieldPulseView.BLUE, false);
        }

        automationButton.setText(running ? "Ⅱ  Pause Automation" : "▶  Start Automation");
        automationButton.setTextColor(TEXT);

        try {
            BridgeDatabase.Stats s = db.todayStats();
            todayPill.setText("▯  Today: " + s.files + " files  •  " + BridgeService.human(s.bytes));
        } catch (Throwable ignored) {}

        setConnectionState(nagramState, running && granted, running ? "Nagram  ✓" : "Nagram  •");
        setConnectionState(shizukuState, binder && granted, binder ? (granted ? "Shizuku  ✓" : "Shizuku  !") : "Shizuku  ×");
        setConnectionState(usbState, tree != null, tree != null ? "USB  ✓" : "USB  •");
        refreshStorage(tree);
        long now = System.currentTimeMillis();
        if (now - lastHomeRecentRender > 5000L) {
            renderActivityList(recentHomeList, 3, false);
            lastHomeRecentRender = now;
        }

        if (cleanupSuspended) {
            saferBanner.setText("⚠ Safer Mode: automatic source cleanup suspended after safety errors. Originals are being kept.");
            saferBanner.setVisibility(View.VISIBLE);
        } else saferBanner.setVisibility(View.GONE);
    }

    private void setHeroState(String title, String sub, int color, int shieldState, boolean active) {
        heroTitle.setText(title);
        heroTitle.setTextColor(color);
        heroSub.setText(sub);
        shield.setState(shieldState);
        setHeroBackground(color, active);
    }

    private void setHeroBackground(int accent, boolean active) {
        int r = Color.red(accent), g = Color.green(accent), b = Color.blue(accent);
        int left = Color.rgb(Math.max(0, r / 10), Math.max(0, g / 10), Math.max(0, b / 10));
        int right = Color.rgb(4, 10, 9);
        heroCard.setBackground(roundedGradient(new int[]{left, right}, 22, Color.rgb(Math.min(255, r / 2 + 15), Math.min(255, g / 2 + 20), Math.min(255, b / 2 + 20))));
        if (active) {
            heroCard.setAlpha(.98f);
        } else heroCard.setAlpha(1f);
    }

    private void setConnectionState(TextView tv, boolean ok, String text) {
        if (tv == null) return;
        tv.setText(text);
        tv.setTextColor(ok ? TEXT : AMBER);
    }

    private void refreshStorage(String tree) {
        try {
            StatFs s = new StatFs(Environment.getExternalStorageDirectory().getAbsolutePath());
            long total = s.getTotalBytes(), free = s.getAvailableBytes(), used = Math.max(0L, total - free);
            int pct = total > 0 ? (int)Math.min(100, (used * 100L) / total) : 0;
            phoneStorageMain.setText(BridgeService.human(used) + " / " + BridgeService.human(total));
            phoneStorageSub.setText(pct + "% used  •  " + BridgeService.human(free) + " free");
            phoneStorageBar.setProgress(pct, true);
        } catch (Throwable t) {
            phoneStorageMain.setText("Storage available"); phoneStorageSub.setText("Could not read totals");
        }

        if (tree == null) {
            usbStorageMain.setText("Not selected"); usbStorageSub.setText("Choose USB folder"); usbStorageBar.setProgress(0, true); return;
        }
        try {
            Uri u = Uri.parse(tree);
            File dir = StorageUtils.volumeDirectory(this, u);
            if (dir != null) {
                StatFs s = new StatFs(dir.getAbsolutePath());
                long total = s.getTotalBytes(), free = s.getAvailableBytes(), used = Math.max(0L, total - free);
                int pct = total > 0 ? (int)Math.min(100, (used * 100L) / total) : 0;
                usbStorageMain.setText(BridgeService.human(used) + " / " + BridgeService.human(total));
                usbStorageSub.setText(pct + "% used  •  " + BridgeService.human(free) + " free");
                usbStorageBar.setProgress(pct, true);
            } else {
                long free = StorageUtils.availableBytes(this, u);
                usbStorageMain.setText(free >= 0 ? BridgeService.human(free) + " free" : "USB selected");
                usbStorageSub.setText("SAF destination active"); usbStorageBar.setProgress(0, true);
            }
        } catch (Throwable t) {
            usbStorageMain.setText("USB selected"); usbStorageSub.setText("Waiting for volume details"); usbStorageBar.setProgress(0, true);
        }
    }

    private void refreshFiles(String tree) {
        if (tree == null) {
            filesUsbTitle.setText("No USB folder selected");
            filesUsbSub.setText("Choose the Pendrive folder that Bridge is allowed to use.");
        } else {
            String name = null;
            try { name = DocumentTreeUtils.getDisplayName(getContentResolver(), DocumentTreeUtils.rootDocumentUri(Uri.parse(tree))); } catch (Throwable ignored) {}
            filesUsbTitle.setText(name == null ? "USB destination selected ✓" : name + " ✓");
            filesUsbSub.setText("Persistent SAF permission saved • protected by transfer verification.");
        }
    }

    private void renderActivityList(LinearLayout target, int limit, boolean detailed) {
        if (target == null) return;
        target.removeAllViews();
        try {
            List<BridgeDatabase.Record> recent = db.recent(limit);
            if (recent.isEmpty()) {
                TextView empty = label("No transfer history yet.", 13, MUTED, false);
                empty.setPadding(0, dp(12), 0, dp(9)); target.addView(empty); return;
            }
            int shown = 0;
            for (BridgeDatabase.Record r : recent) {
                if (shown++ >= limit) break;
                target.addView(activityRow(r, detailed));
            }
        } catch (Throwable t) {
            target.addView(label("Activity journal temporarily unavailable.", 12, AMBER, false));
        }
    }

    private View activityRow(BridgeDatabase.Record r, boolean detailed) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(10), dp(4), dp(10));

        int c = statusColor(r.status);
        TextView icon = label(fileIcon(r.sourceName), 17, c, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(rounded(Color.argb(55, Color.red(c), Color.green(c), Color.blue(c)), 11, Color.argb(100, Color.red(c), Color.green(c), Color.blue(c)), 1));
        row.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL); center.setPadding(dp(10), 0, dp(6), 0);
        TextView name = label(r.sourceName == null ? "Unknown file" : r.sourceName, 13, TEXT, true);
        name.setSingleLine(true); name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        center.addView(name);
        String detail = friendlyStatus(r.status);
        boolean duplicateEvent = r.error != null && r.error.startsWith("Confirmed same content");
        if (duplicateEvent) {
            detail = r.status != null && r.status.equals("CLEANUP_PENDING")
                    ? "Same video already on USB • skipped • cleanup pending"
                    : "Same video already on USB • skipped";
        } else if (detailed && r.error != null && !r.error.isEmpty()) {
            detail += " • " + r.error;
        }
        TextView sub = label("• " + detail, 11, statusColor(r.status), false);
        center.addView(sub);
        row.addView(center, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout end = new LinearLayout(this); end.setOrientation(LinearLayout.VERTICAL); end.setGravity(Gravity.END);
        end.addView(label(formatTime(r.updatedAt), 10, MUTED, false));
        end.addView(label(BridgeService.human(r.sourceSize), 10, MUTED, false));
        row.addView(end);
        return row;
    }

    private static int statusColor(String s) {
        if (s == null) return BLUE;
        if (s.equals("COMPLETED") || s.equals("COMPLETED_SOURCE_KEPT") || s.equals("DUPLICATE_VERIFIED")) return GREEN;
        if (s.contains("FAILED") || s.equals("NEEDS_ATTENTION") || s.equals("DESTINATION_INCOMPATIBLE")) return RED;
        if (s.contains("WAITING") || s.equals("CLEANUP_PENDING") || s.equals("RETRY_PENDING")) return AMBER;
        return BLUE;
    }

    private static String fileIcon(String name) {
        if (name == null) return "▯";
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".mov") || n.endsWith(".webm")) return "▶";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp")) return "▧";
        if (n.endsWith(".zip") || n.endsWith(".rar") || n.endsWith(".7z")) return "▰";
        return "▯";
    }

    private static String friendlyStatus(String s) {
        if (s == null) return "Unknown";
        switch (s) {
            case "CLEANUP_PENDING": return "Verified • cleanup pending";
            case "COMPLETED": return "Transferred safely to USB";
            case "COMPLETED_SOURCE_KEPT": return "Verified • source kept";
            case "DUPLICATE_VERIFIED": return "Same video already on USB • skipped";
            case "WAITING_FOR_COMPLETION": return "Waiting for download to finish";
            case "WAITING_FOR_SPACE": return "Waiting for USB space";
            case "DESTINATION_INCOMPATIBLE": return "USB cannot accept this file safely";
            case "VERIFICATION_FAILED": return "Verification failed • original preserved";
            case "NEEDS_ATTENTION": return "Needs attention • original preserved";
            case "RETRY_PENDING": return "Recovered safely • retry pending";
            default: return s.replace('_', ' ').toLowerCase(Locale.ROOT);
        }
    }

    private void toggleBridge() {
        boolean running = prefs.getBoolean("running", false);
        if (running) {
            stopService(new Intent(this, BridgeService.class));
            prefs.edit().putBoolean("running", false).apply();
            Toast.makeText(this, "Automation paused • originals remain safe", Toast.LENGTH_SHORT).show();
            refresh();
        } else startBridge();
    }

    private void startBridge() {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, "আগে Shizuku চালু করুন", Toast.LENGTH_LONG).show(); return;
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            requestShizuku(); return;
        }
        if (prefs.getString("tree_uri", null) == null) {
            Toast.makeText(this, "আগে Pendrive/USB folder select করুন", Toast.LENGTH_LONG).show(); return;
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
        Intent i = new Intent(this, BridgeService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        prefs.edit().putBoolean("running", true).apply();
        Toast.makeText(this, "Auto Move started safely", Toast.LENGTH_SHORT).show();
        refresh();
    }

    private void requestShizuku() {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, "Shizuku চালু করুন", Toast.LENGTH_LONG).show(); return;
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Shizuku permission already granted", Toast.LENGTH_SHORT).show();
        } else Shizuku.requestPermission(REQ_SHIZUKU);
    }

    private void chooseUsbFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQ_TREE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_TREE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                getContentResolver().takePersistableUriPermission(uri, flags);
                prefs.edit().putString("tree_uri", uri.toString()).apply();
                Toast.makeText(this, "USB destination saved ✓", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Folder permission save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
            refresh();
        }
    }

    private void testDestination() {
        String tree = prefs.getString("tree_uri", null);
        if (tree == null) { Toast.makeText(this, "Select USB folder first", Toast.LENGTH_SHORT).show(); return; }
        Toast.makeText(this, "Testing USB safely…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            Uri test = null;
            try {
                Uri treeUri = Uri.parse(tree);
                Uri root = DocumentTreeUtils.rootDocumentUri(treeUri);
                ContentResolver r = getContentResolver();
                test = DocumentsContract.createDocument(r, root, "application/octet-stream", ".bridge_test_" + System.currentTimeMillis());
                if (test == null) throw new IllegalStateException("Could not create test file");
                byte[] payload = "NAGRAM_USB_BRIDGE_TEST".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                try (OutputStream out = r.openOutputStream(test, "w")) {
                    if (out == null) throw new IllegalStateException("USB is not writable"); out.write(payload); out.flush();
                }
                byte[] got = new byte[payload.length];
                try (InputStream in = r.openInputStream(test)) {
                    if (in == null || in.read(got) != payload.length) throw new IllegalStateException("Read-back failed");
                }
                final boolean ok = java.util.Arrays.equals(payload, got);
                runOnUiThread(() -> Toast.makeText(this, ok ? "✓ USB destination is working" : "USB verification failed", Toast.LENGTH_LONG).show());
            } catch (Throwable t) {
                final String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                runOnUiThread(() -> Toast.makeText(this, "USB test failed: " + msg, Toast.LENGTH_LONG).show());
            } finally {
                if (test != null) try { DocumentsContract.deleteDocument(getContentResolver(), test); } catch (Throwable ignored) {}
            }
        }).start();
    }

    private void confirmResetSafety() {
        new AlertDialog.Builder(this)
                .setTitle("Reset Safer Mode?")
                .setMessage("Only reset after checking the USB. Verification and cleanup revalidation will still remain mandatory.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset", (d, w) -> {
                    prefs.edit().putBoolean("cleanup_suspended", false).putInt("recent_safety_failures", 0).apply();
                    Toast.makeText(this, "Safer Mode reset", Toast.LENGTH_SHORT).show(); refresh();
                }).show();
    }

    private ScrollView premiumScroll() {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(true); s.setBackgroundColor(BG); s.setClipToPadding(false);
        return s;
    }

    private LinearLayout pageRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int side = isCompactWidth() ? 12 : 15;
        root.setPadding(dp(side), dp(14), dp(side), dp(24));
        return root;
    }

    private LinearLayout premiumCard() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(14), dp(14), dp(14), dp(14));
        l.setBackground(roundedGradient(new int[]{CARD_2, CARD}, 20, BORDER));
        l.setElevation(dp(3));
        return l;
    }

    private TextView sectionTitle(String text) {
        TextView t = label(text, 11, Color.rgb(171, 184, 203), true);
        t.setLetterSpacing(.05f); return t;
    }

    private Button smallActionButton(String text, int accent) {
        Button b = new Button(this);
        b.setText(text); b.setAllCaps(false); b.setTextSize(13); b.setTypeface(Typeface.DEFAULT_BOLD); b.setTextColor(TEXT);
        b.setBackground(rounded(Color.rgb(5, 19, 37), 16, accent, 1));
        return b;
    }

    private Button neonButton(String text, int accent) {
        Button b = smallActionButton(text, accent);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        lp.setMargins(0, dp(10), 0, 0); b.setLayoutParams(lp); return b;
    }

    private Button ghostButton(String text) {
        Button b = new Button(this);
        b.setText(text); b.setAllCaps(false); b.setTextSize(13); b.setTextColor(TEXT);
        b.setBackground(rounded(Color.rgb(12, 17, 24), 15, Color.rgb(42, 55, 72), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.setMargins(0, dp(9), 0, 0); b.setLayoutParams(lp); return b;
    }

    private CheckBox premiumCheck(String text, boolean checked) {
        CheckBox c = new CheckBox(this);
        c.setText(text); c.setTextColor(TEXT); c.setTextSize(13); c.setChecked(checked); c.setPadding(0, dp(8), 0, dp(7));
        if (Build.VERSION.SDK_INT >= 21) c.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{GREEN, MUTED}));
        return c;
    }

    private TextView label(String text, float sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text); tv.setTextSize(sp); tv.setTextColor(color); tv.setIncludeFontPadding(true);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(dp(strokeDp), strokeColor); return d;
    }

    private GradientDrawable roundedGradient(int[] colors, int radiusDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
        d.setCornerRadius(dp(radiusDp)); d.setStroke(dp(1), strokeColor); return d;
    }

    private GradientDrawable roundedOval(int color, int strokeColor, int strokeDp) {
        GradientDrawable d = new GradientDrawable(); d.setShape(GradientDrawable.OVAL); d.setColor(color); d.setStroke(dp(strokeDp), strokeColor); return d;
    }

    private View space(int value) {
        View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(value))); return v;
    }

    private void animatePage(View v) {
        v.setAlpha(0f); v.setTranslationY(dp(10));
        v.animate().alpha(1f).translationY(0f).setDuration(320L).start();
    }

    private String formatEta(long seconds) {
        if (seconds < 60) return seconds + " sec";
        long m = seconds / 60, s = seconds % 60;
        return s == 0 ? m + " min" : m + "m " + s + "s";
    }

    private static String formatTime(long ms) {
        try { return new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date(ms)); }
        catch (Throwable ignored) { return ""; }
    }

    private static String humanDuration(long ms) {
        if (ms <= 0) return "Immediate";
        long m = ms / 60000L;
        if (m < 60) return m + " min";
        long h = m / 60; return h + " hr";
    }

    @Override protected void onResume() {
        super.onResume(); refreshHandler.removeCallbacks(ticker); refreshHandler.post(ticker);
    }

    @Override protected void onPause() {
        refreshHandler.removeCallbacks(ticker); super.onPause();
    }

    @Override protected void onDestroy() {
        refreshHandler.removeCallbacks(ticker);
        try { Shizuku.removeBinderReceivedListener(binderReceived); } catch (Throwable ignored) {}
        try { Shizuku.removeBinderDeadListener(binderDead); } catch (Throwable ignored) {}
        try { Shizuku.removeRequestPermissionResultListener(permissionResult); } catch (Throwable ignored) {}
        if (db != null) db.close();
        super.onDestroy();
    }

    private void configureSystemBars() {
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(Color.rgb(5, 7, 10));
        if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
        if (Build.VERSION.SDK_INT >= 23) getWindow().getDecorView().setSystemUiVisibility(0);
    }

    private void applySystemBarInsets(View root) {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int left = insets.getSystemWindowInsetLeft();
            int top = insets.getSystemWindowInsetTop();
            int right = insets.getSystemWindowInsetRight();
            int bottom = insets.getSystemWindowInsetBottom();
            v.setPadding(left, top, right, bottom);
            return insets;
        });
        root.post(root::requestApplyInsets);
    }

    private boolean isCompactWidth() {
        return getResources().getConfiguration().screenWidthDp <= 380;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
