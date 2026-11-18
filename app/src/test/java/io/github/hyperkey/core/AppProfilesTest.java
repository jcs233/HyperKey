package io.github.hyperkey.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class AppProfilesTest {
    @Test public void profilesOverrideOnlyExplicitBitsAndMasterAlwaysWins() {
        Config global = Config.DEFAULT.withEnabled(Shortcut.HOME, false);
        AppProfiles p = AppProfiles.EMPTY.with("com.example.editor", AppProfiles.Profile.INHERIT.withShortcut(Shortcut.HOME, true));
        assertFalse(p.resolve("com.example.editor", global).isBlocked(Shortcut.HOME));
        assertTrue(p.resolve("com.example.other", global).isBlocked(Shortcut.HOME));
        assertTrue(p.resolve("com.example.editor", global.withBlockAll(true)).isBlocked(Shortcut.HOME));
        assertTrue(p.resolve("com.example.editor", global.withEnabled(Shortcut.SEARCH, false)).isBlocked(Shortcut.SEARCH));
        assertTrue(p.with("com.example.editor", p.entries.get("com.example.editor").withEnabled(false))
                .resolve("com.example.editor", global).isBlocked(Shortcut.HOME));
    }
    @Test public void resetAndDeleteResumeCurrentGlobalState() {
        AppProfiles.Profile p = AppProfiles.Profile.INHERIT.preset(false).withShortcut(Shortcut.HOME, null);
        assertFalse(p.resolve(Config.DEFAULT).isBlocked(Shortcut.HOME));
        assertTrue(p.resolve(Config.DEFAULT).isBlocked(Shortcut.SEARCH));
        AppProfiles list = AppProfiles.EMPTY.with("com.example.app", p);
        assertEquals(list.encode(), AppProfiles.decode(list.encode()).encode());
        assertTrue(list.with("com.example.app", null).entries.isEmpty());
        assertEquals(0, p.preset(null).overrideMask);
    }
    @Test public void corruptUnsupportedDuplicateOrOversizedInputFailsOpen() {
        for (String value : new String[]{null, "", "2", "1;com.foo:1:0:1", "1;com.foo:1:80000:0", "1;bad:1:0:0",
                "1;com.foo:1:0:0;com.foo:1:0:0", "1;com.foo:2:0:0", "1;com.foo:1:-1:0", "1".repeat(32769)})
            assertTrue(AppProfiles.decode(value).entries.isEmpty());
    }
    @Test public void listIsImmutableAndAllPresetsIncludeEveryShortcut() {
        AppProfiles list = AppProfiles.EMPTY.with("com.example.app", AppProfiles.Profile.INHERIT.preset(false));
        for (Shortcut s : Shortcut.values()) {
            assertTrue(list.resolve("com.example.app", Config.DEFAULT).isBlocked(s));
            assertFalse(AppProfiles.Profile.INHERIT.preset(true).resolve(new Config(AppProfiles.ALL, false)).isBlocked(s));
        }
        try { list.entries.clear(); fail(); } catch (UnsupportedOperationException expected) { }
    }
    @Test public void winPassthroughSurvivesPresetsAndOldFormatRemainsReadable() {
        AppProfiles.Profile p = AppProfiles.Profile.INHERIT.withRawWin(true).preset(false).withShortcut(Shortcut.HOME, true);
        AppProfiles value = AppProfiles.EMPTY.with("com.example.remote", p);
        assertTrue(AppProfiles.decode(value.encode()).entries.get("com.example.remote").rawWin);
        assertFalse(AppProfiles.decode("1;com.example.remote:1:0:0").entries.get("com.example.remote").rawWin);
    }
}
