package com.redpanel.tv;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
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
            drawBackdrop(canvas);
            drawHeader(canvas);
            drawHero(canvas);

            int count = tab == 0 ? 5 : 4;
            float width = tab == 0 ? 210 : 266;
            float gap = tab == 0 ? 15 : 16;
            for (int i = 0; i < count; i++) {
                drawCard(canvas, 85 + i * (width + gap), 302, width, 252, i);
            }
            drawFooter(canvas);
            if (settingsOpen) drawSettings(canvas);
            canvas.restore();
        }

        private void drawBackdrop(Canvas c) {
            c.drawColor(0xff09070c);
            radial(c, 350, 75, 650, 0x86631328, 0x00330012, 0, 0, 1280, 720);
            radial(c, 1050, 520, 520, 0x6655172e, 0x00290019, 0, 0, 1280, 720);
            radial(c, 650, 720, 410, 0x4a9a1c39, 0x00240013, 0, 0, 1280, 720);
            fillRound(c, 45, 34, 46, 674, 0, 0x36f75b77);
            fillRound(c, 1234, 34, 1235, 674, 0, 0x24f75b77);
        }

        private void drawHeader(Canvas c) {
            glass(c, 66, 42, 1214, 130, 28, false);
            radial(c, 103, 85, 31, 0xffff456b, 0x00ff456b, 72, 54, 134, 116);
            strokeCircle(c, 103, 85, 24, 0x95ffc4d0, 1.4f);
            fillCircle(c, 97, 77, 5, 0xdfffffff);
            text(c, "RED PANEL", 142, 87, 22, WHITE, true);
            text(c, "LIQUID EDITION", 143, 106, 10, 0xffd78396, false);

            drawTab(c, "Приложения", 453, 626, 0);
            drawTab(c, "Выходы", 640, 785, 1);
            drawGear(c);
        }

        private void drawTab(Canvas c, String label, float left, float right, int index) {
            boolean active = tab == index;
            boolean focused = focus == index && !settingsOpen;
            if (active || focused) {
                glass(c, left, 58, right, 114, 18, focused);
                if (active) fillRound(c, left + 19, 109, right - 19, 111, 1, 0xfffb5474);
            }
            text(c, label, left + 18, 94, 20,
                    active ? WHITE : 0xffbd9da9, active);
        }

        private void drawGear(Canvas c) {
            boolean focused = focus == 2 && !settingsOpen;
            glass(c, 1135, 59, 1193, 113, 18, focused || settingsOpen);
            strokeCircle(c, 1164, 86, 13, 0xfff8c4d0, 2f);
            strokeCircle(c, 1164, 86, 4, 0xfff8c4d0, 2f);
            p.setShader(null);
            p.setColor(0xfff8c4d0);
            p.setStrokeWidth(2f);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
                c.drawLine(1164 + 16 * dx, 86 + 16 * dy,
                        1164 + 20 * dx, 86 + 20 * dy, p);
            }
        }

        private void drawHero(Canvas c) {
            fillRound(c, 87, 181, 91, 242, 2, 0xffff5b78);
            text(c, tab == 0 ? "ТВОИ ПРИЛОЖЕНИЯ" : "ИСТОЧНИКИ СИГНАЛА",
                    111, 207, 34, WHITE, true);
            text(c, tab == 0 ? "Всё важное — в одном касании."
                    : "Выбери HDMI или открой эфирное ТВ.",
                    113, 238, 17, 0xffc3a5af, false);
            glass(c, 1001, 188, 1194, 232, 18, false);
            fillCircle(c, 1024, 210, 4, 0xffff5b78);
            text(c, tab == 0 ? "5 ПРИЛОЖЕНИЙ" : "4 ИСТОЧНИКА",
                    1040, 216, 13, 0xffedd0d7, true);
        }

        private void drawCard(Canvas c, float x, float y, float w, float h, int i) {
            boolean selected = focus == i + 3 && !settingsOpen;
            glass(c, x, y, x + w, y + h, 25, selected);
            text(c, String.format(java.util.Locale.ROOT, "%02d", i + 1),
                    x + 20, y + 34, 13, selected ? 0xffff9aae : 0xff8d7180, true);
            if (selected) {
                fillCircle(c, x + w - 27, y + 28, 5, 0xffff6482);
                strokeCircle(c, x + w - 27, y + 28, 9, 0x80ff6482, 1f);
            } else {
                fillCircle(c, x + w - 27, y + 28, 3, 0x70f5a7b8);
            }
            if (tab == 0) drawAppContent(c, x, y, w, i, selected);
            else drawSourceContent(c, x, y, w, i, selected);
            fillRound(c, x + 20, y + h - 39, x + w - 20, y + h - 38,
                    0, selected ? 0x78ff819b : 0x33d79eab);
        }

        private void drawAppContent(Canvas c, float x, float y, float w,
                                    int i, boolean selected) {
            float cx = x + w / 2;
            radial(c, cx, y + 111, 92, selected ? 0x9bc8224c : 0x76671832,
                    0x00341124, x + 13, y + 35, x + w - 13, y + 183);
            fillCircle(c, cx, y + 114, 52, selected ? 0x8a5d1d35 : 0x73402332);
            strokeCircle(c, cx, y + 114, 52,
                    selected ? 0x9bffb5c4 : 0x5beeb2c1, 1f);
            Drawable icon = appIcons[i];
            if (icon != null) {
                int l = Math.round(cx - 36), t = Math.round(y + 78);
                icon.setBounds(l, t, l + 72, t + 72);
                icon.draw(c);
            } else {
                text(c, APPS[i][2], cx, y + 129, 32, WHITE, true, true);
            }
            text(c, APPS[i][0], cx, y + 199,
                    APPS[i][0].length() > 11 ? 19 : 21, WHITE, true, true);
            text(c, "ОТКРЫТЬ ПРИЛОЖЕНИЕ", cx, y + 226, 10,
                    selected ? 0xffff9fb3 : 0xffac8997, false, true);
        }

        private void drawSourceContent(Canvas c, float x, float y, float w,
                                       int i, boolean selected) {
            float cx = x + w / 2;
            radial(c, cx, y + 113, 100, selected ? 0x9ec9254d : 0x64541933,
                    0x002c1020, x + 15, y + 36, x + w - 15, y + 187);
            strokeRound(c, cx - 38, y + 75, cx + 38, y + 129, 11,
                    selected ? 0xffffb4c5 : 0xffb9899a, 2f);
            fillRound(c, cx - 18, y + 134, cx + 18, y + 137, 1,
                    selected ? 0xffffb4c5 : 0xffb9899a);
            text(c, i < 3 ? "0" + (i + 1) : "ТВ", cx, y + 111, 27,
                    selected ? WHITE : 0xffd9bdc8, true, true);
            text(c, i < 3 ? "HDMI " + (i + 1) : "Эфирное ТВ", cx, y + 195,
                    22, WHITE, true, true);
            String detail = i == 3 ? "ТЕЛЕКАНАЛЫ" :
                    hdmiIds[i] == null ? "ВХОД НЕ НАЙДЕН" :
                    hdmiConnected[i] ? "ПОДКЛЮЧЕНО" : "НЕТ СИГНАЛА";
            text(c, detail, cx, y + 226, 11,
                    selected ? 0xffff9fb3 : 0xffac8997, false, true);
        }

        private void drawFooter(Canvas c) {
            glass(c, 85, 596, 1195, 651, 18, false);
            text(c, "←  →   ВЫБОР", 110, 631, 14, 0xffe6c7d0, false);
            fillRound(c, 280, 610, 281, 636, 0, 0x4bffc0cc);
            text(c, "OK   ОТКРЫТЬ", 301, 631, 14, 0xffe6c7d0, false);
            fillRound(c, 490, 610, 491, 636, 0, 0x4bffc0cc);
            text(c, "MENU   НАСТРОЙКИ", 511, 631, 14, 0xffe6c7d0, false);
            text(c, "RED LIQUID  /  02", 1170, 631, 12,
                    0xffaa8292, true, true);
        }

        private void drawSettings(Canvas c) {
            c.drawColor(0x6906030a);
            fillRound(c, 760, 140, 1195, 397, 28, 0xff150d19);
            glass(c, 760, 140, 1195, 397, 28, true);
            fillCircle(c, 797, 180, 6, 0xffff5a79);
            text(c, "Настройки", 820, 187, 25, WHITE, true);
            text(c, "Управление телевизором", 787, 219, 13, 0xffbc98a6, false);
            for (int i = 0; i < 2; i++) {
                float top = 239 + i * 65;
                boolean selected = settingsFocus == i;
                if (selected) glass(c, 777, top, 1178, top + 57, 16, true);
                text(c, i == 0 ? "Настройки телевизора" : "Заводская оболочка",
                        799, top + 36, 19, selected ? WHITE : 0xffd3b6c0, selected);
                text(c, "›", 1137, top + 37, 26,
                        selected ? 0xffff819c : 0xff906d7b, false);
            }
        }

        private void glass(Canvas c, float l, float t, float r, float b,
                           float radius, boolean selected) {
            fillRound(c, l, t + 7, r, b + 7, radius, 0x5c000000);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(l, t, r, b,
                    selected ? new int[]{0xe0552137, 0xee2b1725, 0xf017101b}
                            : new int[]{0xd02b1c2b, 0xe018121e, 0xea0c0b13},
                    new float[]{0f, .52f, 1f}, Shader.TileMode.CLAMP));
            box.set(l, t, r, b);
            c.drawRoundRect(box, radius, radius, p);
            p.setShader(null);
            strokeRound(c, l + .5f, t + .5f, r - .5f, b - .5f,
                    radius, selected ? 0xcfff829c : 0x75e7acbd, selected ? 2f : 1f);
            fillRound(c, l + 22, t + 2, r - 22, t + 3, 1,
                    selected ? 0xaaffc5d1 : 0x65f6c7d3);
            if (selected) {
                strokeRound(c, l - 3, t - 3, r + 3, b + 3,
                        radius + 3, 0x39ff436b, 4f);
            }
        }

        private void radial(Canvas c, float cx, float cy, float radius,
                            int center, int edge, float l, float t, float r, float b) {
            p.setStyle(Paint.Style.FILL);
            p.setShader(new RadialGradient(cx, cy, radius, center, edge,
                    Shader.TileMode.CLAMP));
            c.drawRect(l, t, r, b, p);
            p.setShader(null);
        }

        private void fillCircle(Canvas c, float x, float y, float radius, int color) {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            c.drawCircle(x, y, radius, p);
        }

        private void strokeCircle(Canvas c, float x, float y, float radius,
                                  int color, float width) {
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(width);
            p.setColor(color);
            c.drawCircle(x, y, radius, p);
            p.setStyle(Paint.Style.FILL);
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
            if (value.startsWith("RED LIQUID")) p.setTextAlign(Paint.Align.RIGHT);
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
                if (x >= 777 && x <= 1178 && y >= 239 && y <= 361) {
                    settingsFocus = y < 304 ? 0 : 1;
                    settingsOpen = false;
                    if (settingsFocus == 0) openSettings(); else openFactoryHome();
                } else settingsOpen = false;
                invalidate();
                return true;
            }
            if (y >= 42 && y <= 130) {
                if (x >= 1135 && x <= 1193) { focus = 2; settingsOpen = true; settingsFocus = 0; }
                else if (x >= 453 && x <= 785) { tab = x < 635 ? 0 : 1; focus = 3; }
            } else if (y >= 302 && y <= 554) {
                float width = tab == 0 ? 210 : 266;
                float gap = tab == 0 ? 15 : 16;
                float left = 85;
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
