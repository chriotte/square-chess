package com.dataespresso.squarechess.smoke;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Platform-only tests: no references to app classes and no test keep rules in the target APK. */
public final class R8SmokeRunner extends Instrumentation {
    private Activity activity;
    private final StringBuilder report = new StringBuilder();
    private int passed;

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if (!getTargetContext().getPackageName().equals("com.dataespresso.squarechess.dev"))
                throw new AssertionError("Tests require the isolated Dev app");
            if ((getTargetContext().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0)
                throw new AssertionError("R8 checks must target a non-debuggable release APK");
            seedLegacyDatabase();
            AccessibilityServiceInfo info = getUiAutomation().getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            getUiAutomation().setServiceInfo(info);
            launch();
            node("Play against computer");
            pass("Optimized app starts and opens its Room database");
            tap("Continue game"); node("e4, white pawn"); node("e5, black pawn");
            tap("Menu"); tap("Save & home");
            pass("Version 1 saved game survives Room migration in the optimized app");

            tap("Over the board"); tap("Start game");
            node("e2, white pawn"); node("e4, empty");
            tapSquare("e2"); tapSquare("e4"); node("e4, white pawn");
            tapSquare("e7"); tapSquare("e5"); node("e5, black pawn");
            tap("Menu"); tap("Save & home");
            closeActivity(); launch(); tap("Continue game");
            node("e4, white pawn"); node("e5, black pawn");
            pass("Legal moves and saved game survive Activity reopening");
            tap("Menu"); tap("Save & home");
            tap("Game history"); node("Your games"); tap("Export");
            node("Save file"); tap("Cancel"); tap("‹ Home");
            pass("Saved history and export dialog load");

            tap("Play against computer"); node("Play as White"); tap("Start game");
            node("e2, white pawn"); node("e4, empty");
            tapSquare("e2"); tapSquare("e4"); node("e4, white pawn");
            nodeMatching(s -> s.matches("[a-h][3-6], black (pawn|knight)"), "engine reply on board");
            pass("JNI engine replies to e2e4 in the optimized APK");
            tap("Menu"); tap("Save & home");

            tap("Puzzles"); tap("Continue training"); tap("Hint");
            nodeMatching(s -> s.startsWith("Hint:"), "puzzle hint");
            tap("Exit"); node("Continue training"); tap("‹ Home");
            pass("Bundled puzzles, chess notation and hints load");

            tap("Chess clock"); tap("Apply time control"); tap("Start clock");
            node("White clock, active"); tap("White clock, active");
            node("Black clock, active"); tap("Pause clock"); node("Resume clock");
            closeActivity(); launch();
            pass("Standalone clock starts, switches sides and pauses");

            tap("Settings"); tap("Language"); tap("Français");
            node("Langue"); tap("Langue"); tap("English"); node("Language");
            pass("Language resources survive shrinking and Activity recreation");
            result.putString("stream", report + "\nOK (" + passed + " checks)\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", report + "\nFAILED after " + passed + " checks: " + failure
                + "\nVisible UI: " + visible() + "\n" + android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void seedLegacyDatabase() {
        if (getTargetContext().getDatabasePath("square-chess.db").exists())
            throw new AssertionError("Clear the Dev app data before running these checks");
        try (SQLiteDatabase db = getTargetContext().openOrCreateDatabase("square-chess.db", 0, null)) {
            db.execSQL("CREATE TABLE games (id TEXT NOT NULL PRIMARY KEY, mode TEXT NOT NULL, "
                + "initialFen TEXT NOT NULL, moves TEXT NOT NULL, white TEXT NOT NULL, black TEXT NOT NULL, "
                + "humanWhite INTEGER NOT NULL, level INTEGER NOT NULL, result TEXT NOT NULL, updated INTEGER NOT NULL)");
            db.execSQL("INSERT INTO games VALUES (?,?,?,?,?,?,?,?,?,?)", new Object[] {
                "r8-legacy", "LOCAL_TWO_PLAYER", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                "e2e4 e7e5", "White", "Black", 1, 3, "*", 1L });
            db.setVersion(1);
        }
    }

    private void launch() {
        activity = startActivitySync(new Intent(Intent.ACTION_MAIN)
            .setClassName(getTargetContext().getPackageName(), "com.dataespresso.squarechess.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        waitForIdleSync();
    }
    private void closeActivity() { runOnMainSync(() -> activity.finish()); waitForIdleSync(); }
    private void pass(String name) {
        passed++; report.append("PASS: ").append(name).append('\n');
        Bundle progress = new Bundle(); progress.putString("stream", "PASS: " + name + "\n");
        sendStatus(0, progress);
    }
    private List<AccessibilityNodeInfo> roots() {
        List<AccessibilityNodeInfo> roots = new ArrayList<>();
        List<AccessibilityWindowInfo> windows = getUiAutomation().getWindows();
        windows.sort((a, b) -> Integer.compare(b.getLayer(), a.getLayer()));
        for (AccessibilityWindowInfo w : windows)
            if (w.getType() == AccessibilityWindowInfo.TYPE_APPLICATION && w.getRoot() != null)
                roots.add(w.getRoot());
        return roots;
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo n, Predicate<String> match) {
        if (n == null || !n.refresh()) return null;
        if ((n.getText() != null && match.test(n.getText().toString())) ||
            (n.getContentDescription() != null && match.test(n.getContentDescription().toString()))) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo found = find(n.getChild(i), match);
            if (found != null) return found;
        }
        return null;
    }
    private AccessibilityNodeInfo node(String text) { return nodeMatching(text::equals, text); }
    private AccessibilityNodeInfo nodeMatching(Predicate<String> match, String description) {
        long end = SystemClock.uptimeMillis() + 15000;
        int attempts = 0;
        while (SystemClock.uptimeMillis() < end) {
            for (AccessibilityNodeInfo root : roots()) {
                AccessibilityNodeInfo prompt = find(root, "Got it"::equals);
                if (prompt != null) click(prompt);
                AccessibilityNodeInfo found = find(root, match);
                if (found != null) return found;
                if (++attempts % 20 == 0) scroll(root);
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Missing UI node: " + description);
    }
    private void scroll(AccessibilityNodeInfo n) {
        if (n == null) return;
        if (n.isScrollable()) n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
        for (int i = 0; i < n.getChildCount(); i++) scroll(n.getChild(i));
    }
    private void click(AccessibilityNodeInfo n) {
        while (!n.isClickable() && n.getParent() != null) n = n.getParent();
        if (!n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) throw new AssertionError("Click failed");
        waitForIdleSync();
    }
    private void tap(String text) { click(node(text)); }
    private void tapSquare(String square) { click(nodeMatching(s -> s.startsWith(square + ", "), square)); }
    private void collect(AccessibilityNodeInfo n, StringBuilder text) {
        if (n == null) return;
        text.append('[').append(n.getText()).append('/').append(n.getContentDescription()).append(']');
        for (int i = 0; i < n.getChildCount(); i++) collect(n.getChild(i), text);
    }
    private String visible() {
        StringBuilder text = new StringBuilder();
        try { for (AccessibilityNodeInfo root : roots()) collect(root, text); }
        catch (Exception ignored) { text.append("unavailable"); }
        return text.toString();
    }
}
