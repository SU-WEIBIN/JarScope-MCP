package io.github.javacodeintel.mcp.query;

import io.github.javacodeintel.mcp.domain.*;
import io.github.javacodeintel.mcp.ports.IndexStore;
import java.io.IOException;
import java.util.*;

public final class RelationQueryService {
    private final IndexStore store;
    private final CallEdgeClassifier classifier = new CallEdgeClassifier();
    public RelationQueryService(IndexStore store){this.store=Objects.requireNonNull(store);}
    public PageResult<ClassInfo> subtypes(String typeName,boolean recursive,boolean includeInterfaces,PageRequest request)throws IOException{List<ClassInfo> items=store.findSubtypes(typeName,recursive,includeInterfaces,request.effectiveLimit(),request.offset());return PageResult.of(items,request,store.countSubtypes(typeName,recursive,includeInterfaces));}
    public PageResult<CallEdge> callers(String owner,String name,String descriptor,boolean polymorphic,PageRequest request)throws IOException{List<CallEdge> items=store.findCallers(owner,name,descriptor,polymorphic,request.effectiveLimit(),request.offset());return PageResult.of(items,request,store.countCallers(owner,name,descriptor,polymorphic));}

    public PageResult<CallEdgeView> callees(String owner,String name,String descriptor,CalleeQuery query)throws IOException {
        if(!query.groupByTarget()&&query.category()==CallCategory.ALL&&query.packagePrefix()==null&&query.nameQuery()==null){
            List<CallEdgeView> items=new ArrayList<>();
            for(CallEdge edge:store.findCallees(owner,name,descriptor,query.page().effectiveLimit(),query.page().offset())) items.add(classifier.classify(edge));
            return PageResult.of(items,query.page(),store.countCallees(owner,name,descriptor));
        }
        List<CallEdgeView> all=allCallees(owner,name,descriptor,query);
        int start=Math.min(query.page().offset(),all.size());
        int end=Math.min(all.size(),start+query.page().effectiveLimit());
        return PageResult.of(all.subList(start,end),query.page(),all.size());
    }

    List<CallEdgeView> allCallees(String owner,String name,String descriptor,CalleeQuery query)throws IOException {
        List<CallEdge> raw=store.findCallees(owner,name,descriptor);
        LinkedHashMap<String,CallEdgeView> grouped=new LinkedHashMap<>();
        List<CallEdgeView> result=new ArrayList<>();
        for(CallEdge edge:raw){
            if(!matches(edge,query)) continue;
            CallEdgeView view=classifier.classify(edge);
            if(query.category()!=CallCategory.ALL&&query.category()!=view.category()) continue;
            if(!query.groupByTarget()){result.add(view);continue;}
            String key=edge.targetOwner()+"\u0000"+edge.targetName()+"\u0000"+edge.targetDescriptor();
            CallEdgeView previous=grouped.get(key);
            if(previous==null) grouped.put(key,view);
            else grouped.put(key,new CallEdgeView(previous.edge(),previous.category(),previous.callSiteCount()+1,previous.reason()));
        }
        if(query.groupByTarget()) result.addAll(grouped.values());
        return List.copyOf(result);
    }

    private static boolean matches(CallEdge edge,CalleeQuery query){
        if(query.packagePrefix()!=null&&(edge.targetOwner()==null||!edge.targetOwner().startsWith(query.packagePrefix()))) return false;
        if(query.nameQuery()!=null){String owner=edge.targetOwner()==null?"":edge.targetOwner();String name=edge.targetName()==null?"":edge.targetName();String q=query.nameQuery().toLowerCase(Locale.ROOT);if(!owner.toLowerCase(Locale.ROOT).contains(q)&&!name.toLowerCase(Locale.ROOT).contains(q)) return false;}
        return true;
    }
}
