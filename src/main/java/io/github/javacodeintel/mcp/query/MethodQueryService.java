package io.github.javacodeintel.mcp.query;

import io.github.javacodeintel.mcp.domain.MethodInfo;
import io.github.javacodeintel.mcp.ports.IndexStore;
import java.io.IOException;
import java.util.*;

public final class MethodQueryService {
    private final IndexStore store;
    public MethodQueryService(IndexStore store){this.store=Objects.requireNonNull(store);}
    public PageResult<MethodInfo> find(String owner,String name,String descriptor,PageRequest request)throws IOException{List<MethodInfo> all=store.findMethods(owner,name,descriptor);return page(all,request);}
    private static <T> PageResult<T> page(List<T> all,PageRequest request){int start=Math.min(request.offset(),all.size());int end=Math.min(all.size(),start+request.effectiveLimit());return PageResult.of(all.subList(start,end),request,all.size());}
}
