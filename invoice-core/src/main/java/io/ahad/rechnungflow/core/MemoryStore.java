package io.ahad.rechnungflow.core;
import java.util.*;
import java.util.function.Function;
/** Deterministic transaction implementation for tests. Production uses FileStore. */
public final class MemoryStore implements Store {
    private Map<String,Object> state=Store.initial(); private final byte[] key=Crypto.random(32);
    public synchronized <T>T read(Function<Map<String,Object>,T> f){return f.apply(Json.object(Json.copy(state)));}
    public synchronized <T>T write(Function<Map<String,Object>,T> f){var next=Json.object(Json.copy(state));T result=f.apply(next);state=Json.object(Json.copy(next));return result;}
    public byte[] key(){return key.clone();}public void close(){}
}
