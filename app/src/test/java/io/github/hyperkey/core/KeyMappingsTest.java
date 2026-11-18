package io.github.hyperkey.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class KeyMappingsTest {
    @Test public void appRuleWinsAndOtherAppsUseGlobalRule() {
        KeyMappings.Rule global = new KeyMappings.Rule("*", 115, 0, 111, 0, "");
        KeyMappings.Rule app = new KeyMappings.Rule("com.example.editor", 115, 0, 61, 0, "");
        KeyMappings value = KeyMappings.EMPTY.put(global).put(app);
        assertEquals(app, value.find("com.example.editor", 115, 0));
        assertEquals(global, value.find("com.example.other", 115, 0));
        assertNull(value.find("com.example.editor", 115, 0x1000));
        assertEquals(value.encode(), KeyMappings.decode(value.encode()).encode());
    }
    @Test public void saveReplacesSameSourceAndDeleteDoesNotRemoveGlobal() {
        KeyMappings.Rule a = new KeyMappings.Rule("com.example.app", 32, 0x1002, 0, 0, "HOME");
        KeyMappings value = KeyMappings.EMPTY.put(a).put(new KeyMappings.Rule("com.example.app", 32, 0x1002, 111, 0, ""));
        assertEquals(1, value.rules.size());
        assertTrue(value.remove(a.id()).rules.isEmpty());
    }
    @Test public void rejectsMalformedDangerousAndUnknownTargets() {
        for (String s : new String[]{null, "", "2", "1;*:26:0:111:0:", "1;*:115:0:26:0:",
                "1;bad:115:0:111:0:", "1;*:115:0:115:0:", "1;*:115:0:0:0:UNKNOWN", "1;*:115:0:0:0:HOME;*:115:0:111:0:"})
            assertTrue(KeyMappings.decode(s).rules.isEmpty());
    }
}
