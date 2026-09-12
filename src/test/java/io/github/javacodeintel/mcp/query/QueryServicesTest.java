package io.github.javacodeintel.mcp.query;

import io.github.javacodeintel.mcp.domain.*;
import io.github.javacodeintel.mcp.storage.SqliteIndexStore;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
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
}
