package io.github.hyperkey.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class ConfigTest {
    @Test public void nativeImportPreservesMasterExtraControlsAndMissingTypes() {
        Config before = Config.DEFAULT.withEnabled(Shortcut.META_SINGLE, false)
                .withEnabled(Shortcut.SEARCH, false).withBlockAll(true);
        Config imported = before.withNativeStates(Shortcut.HOME.bit() | Shortcut.SCREENSHOT.bit(), Shortcut.SCREENSHOT.bit());
        assertFalse(imported.isIndividuallyEnabled(Shortcut.HOME));
        assertTrue(imported.isIndividuallyEnabled(Shortcut.SCREENSHOT));
        assertFalse(imported.isIndividuallyEnabled(Shortcut.META_SINGLE));
        assertFalse(imported.isIndividuallyEnabled(Shortcut.SEARCH));
        assertTrue(imported.blockAll);
        assertTrue(imported.withNativeStates(Shortcut.HOME.bit(), Shortcut.HOME.bit()).isIndividuallyEnabled(Shortcut.HOME));
    }
    @Test public void absentCorruptAndFutureConfigurationLeavesShortcutsEnabled() {
        for (String value : new String[]{null, "", "2:1:1", "1:-1:1", "1:0:true", "1:ffffffffffffffff:1", "1:0:1:extra"}) {
            Config parsed = Config.decode(value);
            assertFalse(parsed.blockAll);
            assertEquals(0, parsed.disabledMask);
        }
    }
    @Test public void everyShortcutCanRoundTripIndependently() {
        for (Shortcut shortcut : Shortcut.values()) {
            Config parsed = Config.decode(Config.DEFAULT.withEnabled(shortcut, false).encode());
            for (Shortcut candidate : Shortcut.values()) assertEquals(candidate == shortcut, parsed.isBlocked(candidate));
        }
    }
    @Test public void globalTogglePreservesIndividualChoices() {
        Config config = Config.DEFAULT.withEnabled(Shortcut.HOME, false).withBlockAll(true);
        for (Shortcut shortcut : Shortcut.values()) assertTrue(config.isBlocked(shortcut));
        config = Config.decode(config.encode()).withBlockAll(false);
        assertTrue(config.isBlocked(Shortcut.HOME));
        assertFalse(config.isBlocked(Shortcut.ALT_TAB));
    }
    @Test public void screenshotAndRegionAreIndependentAndCtrlCopyPasses() {
        Config config = Config.DEFAULT.withEnabled(Shortcut.SCREENSHOT, false);
        assertTrue(ShortcutPolicy.bypass(config, 29, 0x10000));
        assertFalse(ShortcutPolicy.bypass(config, 29, 0x10001));
        assertFalse(ShortcutPolicy.bypass(config, 31, 0x1000));
        assertFalse(ShortcutPolicy.bypass(config, 29, 0));
    }
    @Test public void altTabIncludesReverseButNotCtrlAltTab() {
        Config config = Config.DEFAULT.withEnabled(Shortcut.ALT_TAB, false);
        assertTrue(ShortcutPolicy.bypass(config, 61, 2));
        assertTrue(ShortcutPolicy.bypass(config, 61, 3));
        assertFalse(ShortcutPolicy.bypass(config, 61, 0x1002));
    }
    @Test public void nativeTypesCoverEveryConfiguredSystemRule() {
        for (int type = 0; type <= 14; type++) assertNotNull(Shortcut.forNativeType(type));
        assertNull(Shortcut.forNativeType(15));
        assertNull(Shortcut.forNativeType(-1));
    }
}
