package io.github.javacodeintel.mcp.query;

public record PageRequest(int limit,int offset) {
    public static final int DEFAULT_LIMIT=50, MAX_LIMIT=500;
    public PageRequest { if(limit<0||limit>MAX_LIMIT) throw new IllegalArgumentException("limit must be between 0 and 500"); if(offset<0) throw new IllegalArgumentException("offset must not be negative"); }
    public static PageRequest defaults(){return new PageRequest(DEFAULT_LIMIT,0);}
    public int effectiveLimit(){return limit==0?DEFAULT_LIMIT:limit;}
}
