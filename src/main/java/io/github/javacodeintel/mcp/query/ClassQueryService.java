package io.github.javacodeintel.mcp.query;

import io.github.javacodeintel.mcp.domain.ClassInfo;
import io.github.javacodeintel.mcp.ports.IndexStore;
import java.io.IOException;
import java.util.*;

public final class ClassQueryService {
    private final IndexStore store;
    public ClassQueryService(IndexStore store){this.store=Objects.requireNonNull(store);}
    public PageResult<ClassInfo> search(String query,PageRequest request)throws IOException{Objects.requireNonNull(query,"query");Objects.requireNonNull(request,"request");Optional<ClassInfo> exact=store.findClass(query);if(exact.isPresent()){List<ClassInfo> items=request.offset()==0&&request.effectiveLimit()>0?List.of(exact.get()):List.of();return PageResult.of(items,request,1);}int limit=request.effectiveLimit();List<ClassInfo> items=store.searchClasses(query,null,null,limit,request.offset());return PageResult.of(items,request,store.countClasses(query,null,null));}
    public Optional<ClassInfo> get(String fullyQualifiedName)throws IOException{return store.findClass(fullyQualifiedName);}
}
