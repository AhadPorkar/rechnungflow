package io.ahad.rechnungflow.core;
/** Stable public error codes. Sensitive implementation details never form an API error. */
public final class Problem extends RuntimeException {
    private final int status; private final String code;
    public Problem(int status, String code) { super(code); this.status=status; this.code=code; }
    public int status() { return status; }
    public String code() { return code; }
    public static Problem invalid() { return new Problem(400,"INVALID_INPUT"); }
    public static Problem of(int status,String code) { return new Problem(status,code); }
    public static void require(boolean condition,int status,String code) { if(!condition) throw of(status,code); }
}
