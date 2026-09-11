package io.github.javacodeintel.mcp.query;

import io.github.javacodeintel.mcp.domain.*;
import io.github.javacodeintel.mcp.ports.IndexStore;
import java.io.IOException;
import java.util.*;

public final class RelationQueryService {
    private final IndexStore store;
    public RelationQueryService(IndexStore store){this.store=Objects.requireNonNull(store);}
    public PageResult<ClassInfo> subtypes(String typeName,boolean recursive,boolean includeInterfaces,PageRequest request)throws IOException{List<ClassInfo> items=store.findSubtypes(typeName,recursive,includeInterfaces,request.effectiveLimit(),request.offset());return PageResult.of(items,request,store.countSubtypes(typeName,recursive,includeInterfaces));}
    public PageResult<CallEdge> callers(String owner,String name,String descriptor,boolean polymorphic,PageRequest request)throws IOException{List<CallEdge> items=store.findCallers(owner,name,descriptor,polymorphic,request.effectiveLimit(),request.offset());return PageResult.of(items,request,store.countCallers(owner,name,descriptor,polymorphic));}
}
