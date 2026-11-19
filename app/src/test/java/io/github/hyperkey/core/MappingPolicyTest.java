package io.github.hyperkey.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class MappingPolicyTest {
    @Test public void rawWinChordSurvivesFocusChangeAndWinReleasedBeforeD() {
        var route=new DirectWin();
        assertTrue(route.pass(1,117,10,true,false,true)); assertTrue(route.pass(1,32,11,true,true,true));
        assertTrue(route.pass(1,117,10,false,false,false)); assertTrue(route.pass(1,32,11,false,false,false));
        assertFalse(route.pass(1,32,25,true,true,false)); assertFalse(route.pass(2,32,11,false,false,true));
        assertFalse(route.pass(1,29,30,true,false,true));
    }
    @Test public void masterLeavesTypingVolumeAndBrightnessToAndroid() {
        Config all=Config.DEFAULT.withBlockAll(true);
        for(int key:new int[]{32,24,25,220,221}) assertFalse(ShortcutPolicy.bypass(all,key,0));
        assertFalse(ShortcutPolicy.bypass(all,32,0x1000)); assertTrue(ShortcutPolicy.bypass(all,32,0x10000));
        assertTrue(ShortcutPolicy.bypass(all,61,3)); assertTrue(ShortcutPolicy.bypass(all,111,0));
    }
    @Test public void orphanOrPausedAppRuleCannotShadowGlobal() {
        String pkg="com.example.editor";
        var global=new KeyMappings.Rule("*",32,0x10000,111,0,"");
        var app=new KeyMappings.Rule(pkg,32,0x10000,0,0,"HOME");
        var rules=KeyMappings.EMPTY.put(global).put(app);
        assertEquals(global,rules.forProfile(pkg,AppProfiles.EMPTY,32,0x10000));
        assertEquals(global,rules.forProfile(pkg,AppProfiles.EMPTY.with(pkg,AppProfiles.Profile.INHERIT.withEnabled(false)),32,0x10000));
        assertEquals(app,rules.forProfile(pkg,AppProfiles.EMPTY.with(pkg,AppProfiles.Profile.INHERIT),32,0x10000));
    }
    @Test public void secureFormatCarriesAnOpaqueActionAlongsideLegacyMappings() {
        var old=new KeyMappings.Rule("*",32,0,0,0,"HOME");
        var local=new KeyMappings.Rule("*",33,0,0,0,"local_0123456789abcdef0123456789abcdef");
        var decoded=KeyMappings.decode(KeyMappings.EMPTY.put(old).put(local).encode());
        assertEquals(old,decoded.find("*",32,0)); assertEquals(local,decoded.find("*",33,0));
        assertTrue(local.localAction()); assertFalse(local.systemAction());
        assertThrows(IllegalArgumentException.class,()->decoded.put(new KeyMappings.Rule("*",34,0,0,0,"local_echo hi")));
    }
    @Test public void remapOnlyStripsConsumedModifiersIncludingTheirSideBits() {
        int caps=0x100000, rightAlt=KeyMeta.withSide(58), leftCtrl=KeyMeta.withSide(113);
        assertEquals(0x22,rightAlt); assertEquals(0x3000,leftCtrl);
        assertEquals(caps|rightAlt,KeyMeta.remove(caps|leftCtrl|rightAlt,0x1000));
    }
}
