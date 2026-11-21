package io.github.hyperkey.core;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ChordTest {
    static final List<Integer> NONE=Collections.emptyList();
    static KeyMappings.Rule rule(String owner,int last,int mods,Integer... held) {
        return new KeyMappings.Rule(owner,last,mods,0,0,"HOME",Arrays.asList(held),NONE);
    }
    @Test public void migrationPreservesExistingBindingsAndLocalActionReferences() {
        String old="1;*:32:4096:0:0:local_0123456789abcdef0123456789abcdef";
        var legacy=KeyMappings.decode(old); assertEquals(old,legacy.encode());
        var added=new KeyMappings.Rule("*",32,8,40,1,"",Arrays.asList(31,30),Arrays.asList(39,37));
        var next=KeyMappings.decode(legacy.put(added).encode());
        assertEquals("*:32:4096",next.find("*",32,4096).id());
        assertEquals(added,next.find("*",32,8,Set.of(30,31)));
        assertEquals(Arrays.asList(39,37),added.targetHeld()); assertEquals("*:30+31+32:8",added.id());
        assertThrows(IllegalArgumentException.class,()->legacy.put(rule("*",32,0,30,30)));
        assertThrows(IllegalArgumentException.class,()->legacy.put(rule("*",32,0,119)));
        assertThrows(IllegalArgumentException.class,()->legacy.put(rule("*",32,0,24)));
    }
    @Test public void prefixKeysMatchOnlyWhileHeldOnSameDeviceAndScope() {
        var h=new HeldChordKeys(); var r=rule("*",31,8,29,30); var rules=KeyMappings.EMPTY.put(r);
        h.scope(rules,7); h.update(2,29,true,rules.tracks(29)); h.update(3,30,true,rules.tracks(30));
        assertNull(rules.find("*",31,8,h.get(2))); h.update(2,30,true,true);
        assertNull(rules.find("*",31,0,h.get(2))); assertEquals(r,rules.find("*",31,8,h.get(2)));
        h.update(2,29,false,true); assertNull(rules.find("*",31,8,h.get(2)));
        h.scope(rules,8); assertTrue(h.get(2).isEmpty()); assertTrue(h.get(3).isEmpty());
        assertEquals(8,KeyMeta.group(119)); assertEquals(8,KeyMeta.withSide(119)); assertEquals(4096,KeyMeta.remove(4104,8));
    }
    @Test public void choosesSpecificRuleAndDeclinesAmbiguousSiblings() {
        var a=rule("*",31,0,29); var b=rule("*",31,0,30); var ab=rule("*",31,0,29,30); var app=rule("com.example.a",31,0,29);
        var rules=KeyMappings.EMPTY.put(a).put(b); assertNull(rules.find("*",31,0,Set.of(29,30)));
        rules=rules.put(ab).put(app); assertEquals(ab,rules.find("*",31,0,Set.of(29,30)));
        assertEquals(app,rules.find("com.example.a",31,0,Set.of(29,30)));
    }
    @Test public void targetSendsTrueDownsThenReversedUpsAndOnlyRepeatsTrigger() {
        var r=new KeyMappings.Rule("*",31,0,34,0x1008,"",NONE,List.of(32,33));
        var plan=new TargetChord(r,0); var ledger=new TargetChord.Ledger(); List<Integer> trace=new ArrayList<>(),meta=new ArrayList<>();
        TargetChord.Sink sink=(k,t,m,repeat)->{ trace.add(k); meta.add(m); assertEquals(17,t); };
        plan.emit(ledger,2,true,0,17,0x1008,sink); plan.emit(ledger,2,true,2,17,0x1008,sink); plan.emit(ledger,2,false,0,17,0x1008,sink);
        assertEquals(List.of(113,119,32,33,34,34,34,33,32,119,113),trace);
        assertEquals(0,(int)meta.get(0)&8); assertEquals(0x1008,(int)meta.get(4)&0x1008); assertEquals(0,(int)meta.get(10)&0x1008);
    }
    @Test public void overlappingTargetsReleaseSharedKeysOnlyAfterLastOwner() {
        var a=new TargetChord(new KeyMappings.Rule("*",29,0,32,0x1000,""),0);
        var b=new TargetChord(new KeyMappings.Rule("*",30,0,33,0x1000,""),0);
        var ledger=new TargetChord.Ledger(); List<String> trace=new ArrayList<>(); TargetChord.Sink sink=(k,t,m,r)->trace.add(k+":"+t);
        a.emit(ledger,4,true,0,10,4096,sink); b.emit(ledger,4,true,0,11,4096,sink);
        a.emit(ledger,4,false,0,10,4096,sink); b.emit(ledger,4,false,0,11,4096,sink);
        assertEquals(List.of("113:10","32:10","33:11","32:10","33:11","113:10"),trace);
    }
}
