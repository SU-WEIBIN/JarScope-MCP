package io.github.javacodeintel.mcp.query;

import java.util.*;

public record PageResult<T>(List<T> items,int limit,int offset,long total,boolean truncated,Integer nextOffset) {
    public PageResult { items=items==null?List.of():List.copyOf(items); }
    public static <T> PageResult<T> of(List<T> items,PageRequest request,long total){int limit=request.effectiveLimit();int offset=request.offset();boolean truncated=offset+items.size()<total;return new PageResult<>(items,limit,offset,total,truncated,truncated?offset+items.size():null);}
}
