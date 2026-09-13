package io.github.javacodeintel.mcp.query;

import io.github.javacodeintel.mcp.domain.*;
import io.github.javacodeintel.mcp.storage.SqliteIndexStore;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryServicesTest {
    @Test
    void appliesPagingAndDoesNotGuessAmbiguousSimpleNames() throws Exception {
        Path db=Files.createTempFile("jci-query-", ".db");
        JarArtifact jar=new JarArtifact(Path.of("/path/m2/demo.jar"),new MavenCoordinate("demo","demo","1"));
        ClassInfo one=new ClassInfo("a.OrderDTO","a/OrderDTO","a","class",1,"java.lang.Object",List.of(),null,null,52,jar);
        ClassInfo two=new ClassInfo("b.OrderDTO","b/OrderDTO","b","class",1,"java.lang.Object",List.of(),null,null,52,jar);
        try(SqliteIndexStore store=SqliteIndexStore.open(db)){
            store.initialize(); store.replaceJarIndex(jar,List.of(new ParsedClass(one,List.of(),List.of(),List.of()),new ParsedClass(two,List.of(),List.of(),List.of())));
            ClassQueryService classes=new ClassQueryService(store);
            assertThat(classes.search("OrderDTO",PageRequest.defaults()).items()).hasSize(2);
            assertThat(classes.search("a.OrderDTO",PageRequest.defaults()).items()).extracting(ClassInfo::name).containsExactly("a.OrderDTO");
            assertThatThrownBy(() -> classes.search("OrderDTO",new PageRequest(501,0))).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void groupsRepeatedCalleeCallSitesAndPagesStableTargets() throws Exception {
        Path db=Files.createTempFile("jci-callees-", ".db");
        JarArtifact jar=new JarArtifact(Path.of("/path/m2/bom.jar"),new MavenCoordinate("demo","bom","1"));
        String owner="demo.BomService";
        MethodInfo method=new MethodInfo(owner,"expand","()V","void",List.of(),List.of(),1,null,List.of());
        ClassInfo clazz=new ClassInfo(owner,"demo/BomService","demo","class",1,"java.lang.Object",List.of(),null,null,52,jar);
        List<CallEdge> edges=new ArrayList<>();
        edges.add(edge(owner,"expand","demo.PartMapper","queryChildPartByFatherOids"));
        edges.add(edge(owner,"expand","demo.PartMapper","queryChildPartByFatherOids"));
        edges.add(edge(owner,"expand","demo.BomExportService","writeExcel"));
        edges.add(edge(owner,"expand","java.util.ArrayList","add"));
        try(SqliteIndexStore store=SqliteIndexStore.open(db)){
            store.initialize();
            store.replaceJarIndex(jar,List.of(new ParsedClass(clazz,List.of(),List.of(method),edges)));
            RelationQueryService relations=new RelationQueryService(store);

            PageResult<CallEdgeView> page=relations.callees(owner,"expand","()V",
                    new CalleeQuery(new PageRequest(1,0),CallCategory.ALL,null,null,true));
            assertThat(page.total()).isEqualTo(3);
            assertThat(page.items()).singleElement().satisfies(view -> {
                assertThat(view.edge().targetOwner()).isEqualTo("demo.BomExportService");
                assertThat(view.callSiteCount()).isEqualTo(1);
            });
            assertThat(page.truncated()).isTrue();
            assertThat(page.nextOffset()).isEqualTo(1);

            PageResult<CallEdgeView> dataAccess=relations.callees(owner,"expand","()V",
                    new CalleeQuery(new PageRequest(10,0),CallCategory.DATA_ACCESS,null,null,true));
            assertThat(dataAccess.items()).singleElement()
                    .extracting(v -> v.edge().targetName()).isEqualTo("queryChildPartByFatherOids");
            assertThat(dataAccess.items().get(0).callSiteCount()).isEqualTo(2);

            PageResult<CallEdgeView> callSites=relations.callees(owner,"expand","()V",
                    new CalleeQuery(new PageRequest(1,1),CallCategory.ALL,null,null,false));
            assertThat(callSites.total()).isEqualTo(4);
            assertThat(callSites.items()).singleElement().extracting(v -> v.edge().targetOwner())
                    .isEqualTo("demo.PartMapper");
        }
    }

    @Test
    void methodOverviewStaysBoundedAndKeepsHighValueEvidence() throws Exception {
        Path db=Files.createTempFile("jci-overview-", ".db");
        JarArtifact jar=new JarArtifact(Path.of("/path/m2/bom.jar"),new MavenCoordinate("demo","bom","1"));
        String owner="demo.BomService";
        MethodInfo method=new MethodInfo(owner,"expand","()V","void",List.of(),List.of(),1,null,List.of());
        ClassInfo clazz=new ClassInfo(owner,"demo/BomService","demo","class",1,"java.lang.Object",List.of(),null,null,52,jar);
        List<CallEdge> edges=new ArrayList<>();
        for(int i=0;i<283;i++) edges.add(edge(owner,"expand","java.util.ArrayList","add"+i));
        edges.add(edge(owner,"expand","demo.PartMapper","queryChildPartByFatherOids"));
        edges.add(edge(owner,"expand","demo.BomExportService","writeExcel"));
        try(SqliteIndexStore store=SqliteIndexStore.open(db)){
            store.initialize();
            store.replaceJarIndex(jar,List.of(new ParsedClass(clazz,List.of(),List.of(method),edges)));
            MethodOverview overview=new MethodOverviewService(store).overview(owner,"expand","()V",2);
            assertThat(overview.calleeCallSiteCount()).isEqualTo(285);
            assertThat(overview.uniqueCalleeCount()).isEqualTo(285);
            assertThat(overview.categoryCounts()).anyMatch(c -> c.category()==CallCategory.DATA_ACCESS && c.count()==1);
            assertThat(overview.categoryCounts()).anyMatch(c -> c.category()==CallCategory.EXPORT_OR_BATCH && c.count()==1);
            assertThat(overview.representativeCallees()).hasSizeLessThanOrEqualTo(4);
            assertThat(overview.representativeCallees())
                    .extracting(v -> v.edge().targetOwner())
                    .contains("demo.PartMapper","demo.BomExportService");
            assertThat(overview.toString().getBytes(StandardCharsets.UTF_8)).hasSizeLessThan(32 * 1024);
        }
    }

    private static CallEdge edge(String owner,String method,String targetOwner,String targetName){
        return new CallEdge(owner,method,"()V",targetOwner,targetName,"()V","invokevirtual",ResolutionKind.DIRECT,null);
    }
}
