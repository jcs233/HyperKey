package io.github.hyperkey.core;

import java.util.*;

/** Order and pair generated events, including shared keys held by two concurrently active outputs. */
public final class TargetChord {
    public interface Sink { void emit(int key, long time, int meta, int repeat); }
    private static final int[] MOD_KEYS={113,57,59,117,119};
    private final List<Integer> keys = new ArrayList<>();
    private final int addedMods;
    public TargetChord(KeyMappings.Rule rule, int physicalMeta) {
        int added=0;
        for (int k : MOD_KEYS) {
            int group=KeyMeta.group(k);
            if ((rule.targetModifiers() & group)!=0 && (physicalMeta & group)==0 && group!=KeyMeta.group(rule.targetKey())) {
                keys.add(k); added |= group;
            }
        }
        addedMods=added; keys.addAll(rule.targetHeld()); keys.add(rule.targetKey());
    }
    public static final class Ledger {
        private static final class Hold { final long time; int count=1; Hold(long t) {time=t;} }
        private final Map<Long,Hold> active=new HashMap<>();
        private static long id(int device,int key) { return ((long)device<<32) | key; }
    }
    public void emit(Ledger ledger,int device,boolean down,int repeat,long time,int meta,Sink sink) {
        if (repeat>0 && down) {
            int last=keys.get(keys.size()-1); Ledger.Hold h=ledger.active.get(Ledger.id(device,last));
            if(h!=null) sink.emit(last,h.time,meta,repeat); return;
        }
        if(down) meta=KeyMeta.remove(meta,addedMods);
        for(int i=0;i<keys.size();i++) {
            int k=keys.get(down ? i : keys.size()-1-i);
            long id=Ledger.id(device,k); Ledger.Hold hold=ledger.active.get(id);
            if(down) {
                meta |= KeyMeta.withSide(k);
                if(hold!=null) { hold.count++; continue; }
                ledger.active.put(id,new Ledger.Hold(time)); sink.emit(k,time,meta,0);
            } else if(hold!=null && --hold.count==0) {
                ledger.active.remove(id); meta=KeyMeta.remove(meta,KeyMeta.group(k)); sink.emit(k,hold.time,meta,0);
            }
        }
    }
}
