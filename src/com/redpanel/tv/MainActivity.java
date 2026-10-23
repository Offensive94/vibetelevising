package com.redpanel.tv;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;
import java.util.List;

/** A single-view TV launcher: no WebView, network requests, analytics or background services. */
public final class MainActivity extends Activity {
    private static final String[][] APPS = {
        {"Кинопоиск", "ru.kinopoisk.yandex.tv", "К"},
        {"VK Видео", "com.vk.tv", "VK"},
        {"Браузер", "com.yandex.browser.tv", "Я"},
        {"YouTube", "com.yandex.tv.ytplayer", "▶"},
        {"AmneziaVPN", "org.amnezia.vpn", "A"}
    };
    private PanelView panel;
    private final String[] hdmiIds = new String[3];
    private final boolean[] hdmiConnected = new boolean[3];

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        panel = new PanelView(this);
        setContentView(panel);
    }

    @Override protected void onResume() {
        super.onResume();
        refreshInputs();
        if (panel != null) panel.invalidate();
    }

    private void refreshInputs() {
        TvInputManager manager = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        if (manager == null) return;
        try {
            List<TvInputInfo> inputs = manager.getTvInputList();
            for (int i = 0; i < 3; i++) { hdmiIds[i] = null; hdmiConnected[i] = false; }
            for (TvInputInfo info : inputs) {
                if (info.getType() != TvInputInfo.TYPE_HDMI) continue;
                String id = info.getId();
                for (int port = 0; port < 3; port++) {
                    if (id.endsWith("/HW" + (port + 6))) {
                        hdmiIds[port] = id;
                        hdmiConnected[port] = manager.getInputState(id) == TvInputManager.INPUT_STATE_CONNECTED;
                    }
                }
            }
        } catch (Exception ignored) { /* The stock input picker is still available. */ }
    }

    private void openApp(int index) {
        String pkg = APPS[index][1];
        PackageManager pm = getPackageManager();
        Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
        if (launch == null) { toast("Приложение не найдено"); return; }
        try { startActivity(launch); }
        catch (Exception e) { toast("Не удалось открыть приложение"); }
    }

    private void openSource(int index) {
        if (index < 3) {
            if (hdmiIds[index] == null) { openInputPicker(); return; }
            try {
                Uri source = Uri.parse("live-tv://android.media.tv/passthrough/" + hdmiIds[index]);
                Intent intent = new Intent(Intent.ACTION_VIEW, source);
                intent.setPackage("com.yandex.tv.live");
                startActivity(intent);
            } catch (Exception e) { openInputPicker(); }
            return;
        }
        try {
            Intent tv = new Intent(Intent.ACTION_MAIN);
            tv.setComponent(new ComponentName("com.mediatek.wwtv.tvcenter",
                    "com.mediatek.wwtv.tvcenter.nav.TurnkeyUiMainActivity"));
            startActivity(tv);
        } catch (Exception e) { openInputPicker(); }
    }

    private void openInputPicker() {
        try { startActivity(new Intent("com.android.tv.action.VIEW_INPUTS")); }
        catch (Exception e) { toast("Меню источников недоступно"); }
    }

    private void openSettings() {
        try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        catch (Exception e) { toast("Настройки недоступны"); }
    }

    private void openFactoryHome() {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.setComponent(new ComponentName("com.yandex.tv.home", "com.yandex.tv.home.HomeActivity"));
            startActivity(home);
        } catch (Exception e) { toast("Заводская оболочка недоступна"); }
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (panel != null && panel.handleKey(keyCode)) return true;
        return super.onKeyDown(keyCode, event);
    }

    @Override public void onBackPressed() {
        if (panel != null && panel.closeOverlay()) return;
        if (panel != null && panel.isOutputs()) { panel.showApps(); return; }
        // A home screen has no previous page. Switching to the factory UI is in Settings.
    }

    private final class PanelView extends View {
        private static final int RED = 0xfff53649;
        private static final int WHITE = 0xfff9f3f4;
        private static final int MUTED = 0xffbda7ad;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private final Drawable[] appIcons = new Drawable[APPS.length];
        private final Path diamond = new Path();
        private int tab = 0;
        // 0/1: tabs, 2: settings icon, 3+: content cards.
        private int focus = 3;
        private boolean settingsOpen = false;
        private int settingsFocus = 0;

        PanelView(Context context) {
            super(context);
            setFocusable(true);
            setFocusableInTouchMode(true);
            for (int i = 0; i < APPS.length; i++) {
                try { appIcons[i] = getPackageManager().getApplicationIcon(APPS[i][1]); }
                catch (PackageManager.NameNotFoundException ignored) { }
            }
            diamond.moveTo(640, 136);
            diamond.lineTo(1205, 386);
            diamond.lineTo(640, 665);
            diamond.lineTo(75, 386);
            diamond.close();
            requestFocus();
        }

        boolean isOutputs() { return tab == 1; }
        void showApps() { tab = 0; focus = 3; invalidate(); }
        boolean closeOverlay() {
            if (!settingsOpen) return false;
            settingsOpen = false;
            focus = 2;
            invalidate();
            return true;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.save();
            canvas.scale(getWidth() / 1280f, getHeight() / 720f);
            canvas.drawColor(0xff202022);
            fillRound(canvas, 38, 30, 1242, 690, 14, 0xff080608);

            canvas.save();
            canvas.clipPath(diamond);
            p.setShader(new RadialGradient(640, 365, 565,
                    new int[]{0x8aaa061c, 0x806e0715, 0x0039000a},
                    new float[]{0f, .42f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(50, 120, 1230, 684, p);
            p.setShader(null);
            canvas.restore();
            fillRound(canvas, 75, 60, 1205, 137, 9, 0xc345151f);
            strokeRound(canvas, 75, 60, 1205, 137, 9, 0x55ff4d5e, 1f);
            drawTab(canvas, "Приложения", 105, 129, 0);
            drawTab(canvas, "Выходы", 329, 476, 1);
            drawGear(canvas);

            fillRound(canvas, 98, 194, 102, 246, 2, RED);
            text(canvas, tab == 0 ? "ТВОИ ПРИЛОЖЕНИЯ" : "ИСТОЧНИКИ СИГНАЛА",
                    122, 226, 30, WHITE, true);
            text(canvas, tab == 0 ? "Пять важных вещей. Ничего лишнего."
                    : "Выбери вход — и смотри.", 122, 258, 17, MUTED, false);

            int count = tab == 0 ? 5 : 4;
            float width = tab == 0 ? 207 : 246;
            float gap = tab == 0 ? 19 : 24;
            float left = tab == 0 ? 84 : 112;
            for (int i = 0; i < count; i++) {
                float x = left + i * (width + gap);
                drawCard(canvas, x, 314, width, 212, i);
            }
            fillRound(canvas, 80, 566, 1200, 567, 0, 0x44e04455);
            text(canvas, "◀  ▶   ВЫБОР       OK   ОТКРЫТЬ       ↑   МЕНЮ",
                    84, 607, 14, 0xffa98990, false);
            text(canvas, "RED PANEL  /  01", 1197, 607, 13, 0xff805b62, false, true);

            if (settingsOpen) drawSettings(canvas);
            canvas.restore();
        }

        private void drawTab(Canvas c, String label, float x, float right, int index) {
            int color = tab == index ? RED : 0xffb52b38;
            if (focus == index && !settingsOpen) fillRound(c, x - 11, 75, right + 11, 123, 7, 0x35f53649);
            text(c, label, x, 111, 29, color, false);
            if (tab == index) fillRound(c, x, 133, right, 136, 1, RED);
        }

        private void drawGear(Canvas c) {
            if (focus == 2 || settingsOpen) fillRound(c, 1127, 74, 1182, 124, 10, 0x40f53649);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3f);
            p.setColor(RED);
            c.drawCircle(1154, 99, 17, p);
            c.drawCircle(1154, 99, 6, p);
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
                c.drawLine(1154 + 17 * dx, 99 + 17 * dy, 1154 + 22 * dx, 99 + 22 * dy, p);
            }
            p.setStyle(Paint.Style.FILL);
        }

        private void drawCard(Canvas c, float x, float y, float w, float h, int i) {
            boolean selected = focus == i + 3 && !settingsOpen;
            fillRound(c, x, y, x + w, y + h, 12, selected ? 0xff321218 : 0xd7191115);
            strokeRound(c, x, y, x + w, y + h, 12,
                    selected ? RED : 0x894d2b33, selected ? 2.5f : 1f);
            fillRound(c, x + 16, y + 16, x + 20, y + 47, 2, selected ? RED : 0xff84303d);
            fillRound(c, x + 20, y + 31, x + w - 20, y + 32, 0, 0x33ff6070);
            if (tab == 0) {
                Drawable icon = appIcons[i];
                if (icon != null) {
                    int l = Math.round(x + w / 2 - 34), t = Math.round(y + 57);
                    icon.setBounds(l, t, l + 68, t + 68);
                    icon.draw(c);
                } else {
                    fillRound(c, x + w / 2 - 35, y + 56, x + w / 2 + 35, y + 126,
                            18, 0xff5d1825);
                    text(c, APPS[i][2], x + w / 2, y + 102, 28, WHITE, true, true);
                }
                text(c, APPS[i][0], x + w / 2, y + 166,
                        APPS[i][0].length() > 11 ? 19 : 21, WHITE, true, true);
                text(c, "ПРИЛОЖЕНИЕ", x + w / 2, y + 190, 11,
                        selected ? 0xffe68c98 : 0xff8a6770, false, true);
            } else {
                String number = i < 3 ? "0" + (i + 1) : "ТВ";
                text(c, number, x + w / 2, y + 112, 49, selected ? WHITE : 0xffd4b9bf,
                        true, true);
                text(c, i < 3 ? "HDMI " + (i + 1) : "Эфир / ТВ", x + w / 2,
                        y + 166, 21, WHITE, true, true);
                String detail = i == 3 ? "ТЕЛЕКАНАЛЫ" :
                        hdmiIds[i] == null ? "ВХОД НЕ НАЙДЕН" :
                        hdmiConnected[i] ? "ПОДКЛЮЧЕНО" : "НЕТ СИГНАЛА";
                text(c, detail, x + w / 2, y + 190, 11,
                        selected ? 0xffe68c98 : 0xff8a6770, false, true);
            }
        }

        private void drawSettings(Canvas c) {
            fillRound(c, 845, 151, 1196, 342, 12, 0xff1b1014);
            strokeRound(c, 845, 151, 1196, 342, 12, 0xff963342, 1f);
            text(c, "НАСТРОЙКИ", 869, 184, 16, WHITE, true);
            for (int i = 0; i < 2; i++) {
                float y = 199 + i * 61;
                if (settingsFocus == i) fillRound(c, 859, y, 1182, y + 52, 8, 0xff52212b);
                text(c, i == 0 ? "Настройки телевизора" : "Заводская оболочка",
                        876, y + 34, 20, settingsFocus == i ? WHITE : MUTED, false);
            }
        }

        private void text(Canvas c, String value, float x, float y, float size, int color,
                          boolean medium) { text(c, value, x, y, size, color, medium, false); }

        private void text(Canvas c, String value, float x, float y, float size, int color,
                          boolean medium, boolean centerOrRight) {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            p.setTextSize(size);
            p.setTypeface(Typeface.create("sans-serif", medium ? Typeface.BOLD : Typeface.NORMAL));
            p.setTextAlign(centerOrRight ? Paint.Align.CENTER : Paint.Align.LEFT);
            if (value.startsWith("RED PANEL")) p.setTextAlign(Paint.Align.RIGHT);
            c.drawText(value, x, y, p);
            p.setTextAlign(Paint.Align.LEFT);
        }

        private void fillRound(Canvas c, float l, float t, float r, float b, float radius, int color) {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            box.set(l, t, r, b);
            c.drawRoundRect(box, radius, radius, p);
        }

        private void strokeRound(Canvas c, float l, float t, float r, float b, float radius,
                                 int color, float width) {
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(width);
            p.setColor(color);
            box.set(l, t, r, b);
            c.drawRoundRect(box, radius, radius, p);
            p.setStyle(Paint.Style.FILL);
        }

        boolean handleKey(int key) {
            if (key == KeyEvent.KEYCODE_MENU) {
                settingsOpen = !settingsOpen;
                focus = 2;
                invalidate();
                return true;
            }
            if (key == KeyEvent.KEYCODE_BACK && settingsOpen) return closeOverlay();
            if (settingsOpen) {
                if (key == KeyEvent.KEYCODE_DPAD_UP) settingsFocus = 0;
                else if (key == KeyEvent.KEYCODE_DPAD_DOWN) settingsFocus = 1;
                else if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
                    settingsOpen = false;
                    if (settingsFocus == 0) openSettings(); else openFactoryHome();
                } else if (key != KeyEvent.KEYCODE_DPAD_LEFT && key != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
                invalidate();
                return true;
            }
            int count = tab == 0 ? 5 : 4;
            if (key == KeyEvent.KEYCODE_DPAD_UP) focus = focus >= 3 ? tab : focus;
            else if (key == KeyEvent.KEYCODE_DPAD_DOWN) focus = focus < 3 ? 3 : focus;
            else if (key == KeyEvent.KEYCODE_DPAD_LEFT) focus = focus >= 3 ? Math.max(3, focus - 1) : Math.max(0, focus - 1);
            else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) focus = focus >= 3 ? Math.min(2 + count, focus + 1) : Math.min(2, focus + 1);
            else if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
                if (focus == 0 || focus == 1) { tab = focus; focus = 3; }
                else if (focus == 2) { settingsOpen = true; settingsFocus = 0; }
                else if (tab == 0) openApp(focus - 3);
                else openSource(focus - 3);
            } else return false;
            invalidate();
            return true;
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() != MotionEvent.ACTION_UP) return true;
            float x = event.getX() * 1280f / getWidth();
            float y = event.getY() * 720f / getHeight();
            if (settingsOpen) {
                if (x >= 845 && x <= 1196 && y >= 199 && y <= 312) {
                    settingsFocus = y < 260 ? 0 : 1;
                    settingsOpen = false;
                    if (settingsFocus == 0) openSettings(); else openFactoryHome();
                } else settingsOpen = false;
                invalidate();
                return true;
            }
            if (y >= 60 && y <= 137) {
                if (x >= 1127) { focus = 2; settingsOpen = true; settingsFocus = 0; }
                else if (x >= 90 && x < 500) { tab = x < 315 ? 0 : 1; focus = 3; }
            } else if (y >= 314 && y <= 526) {
                float width = tab == 0 ? 207 : 246;
                float gap = tab == 0 ? 19 : 24;
                float left = tab == 0 ? 84 : 112;
                int index = (int) ((x - left) / (width + gap));
                if (index >= 0 && index < (tab == 0 ? 5 : 4) &&
                        x >= left + index * (width + gap) &&
                        x <= left + index * (width + gap) + width) {
                    focus = index + 3;
                    if (tab == 0) openApp(index); else openSource(index);
                }
            }
            invalidate();
            return true;
        }
    }
}
