package io.github.javacodeintel.mcp.storage;

import io.github.javacodeintel.mcp.domain.*;
import io.github.javacodeintel.mcp.ports.IndexStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/** SQLite-backed implementation of the bytecode index. */
public final class SqliteIndexStore implements IndexStore {
    private final Connection connection;

    private SqliteIndexStore(Connection connection) {
        this.connection = connection;
    }

    public static SqliteIndexStore open(Path database) throws IOException {
        Objects.requireNonNull(database, "database");
        try {
            Path absolute = database.toAbsolutePath();
            if (absolute.getParent() != null) Files.createDirectories(absolute.getParent());
            Connection connection = DriverManager.getConnection("jdbc:sqlite:" + absolute);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("PRAGMA busy_timeout = 5000");
            }
            return new SqliteIndexStore(connection);
        } catch (SQLException e) {
            throw new IOException("Unable to open SQLite database " + database, e);
        }
    }

    @Override
    public synchronized void initialize() throws IOException {
        SchemaMigrator.apply(connection);
    }

    @Override
    public synchronized boolean containsJar(JarArtifact jar) throws IOException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM jars WHERE path=?")) {
            ps.setString(1, jar.path().toAbsolutePath().toString());
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { throw sql("Failed to check jar", e); }
    }

    @Override public synchronized Optional<JarIndexState> findJarState(JarArtifact jar) throws IOException { try(PreparedStatement ps=connection.prepareStatement("SELECT path,file_size,last_modified,sha256,change_detection_mode FROM jars WHERE path=?")){ps.setString(1,jar.path().toAbsolutePath().toString());try(ResultSet rs=ps.executeQuery()){if(!rs.next())return Optional.empty();String mode=rs.getString("change_detection_mode");return Optional.of(new JarIndexState(Path.of(rs.getString("path")),rs.getLong("file_size"),rs.getLong("last_modified"),rs.getString("sha256"),ChangeDetectionMode.valueOf(mode)));}}catch(SQLException e){throw sql("Failed to query jar state",e);}}
    @Override public synchronized void saveJarState(JarArtifact jar,JarIndexState state) throws IOException {try(PreparedStatement ps=connection.prepareStatement("UPDATE jars SET file_size=?,last_modified=?,sha256=?,change_detection_mode=? WHERE path=?")){ps.setLong(1,state.size());ps.setLong(2,state.lastModified());setNullable(ps,3,state.sha256(),Types.VARCHAR);ps.setString(4,state.mode().name());ps.setString(5,jar.path().toAbsolutePath().toString());ps.executeUpdate();}catch(SQLException e){throw sql("Failed to save jar state",e);}}

    @Override
    public synchronized void replaceJarIndex(JarArtifact jar, List<ParsedClass> parsed) throws IOException {
        Objects.requireNonNull(jar, "jar");
        List<ParsedClass> classes = parsed == null ? List.of() : parsed;
        boolean previousAutoCommit = true;
        try {
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            long jarId;
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO jars(group_id,artifact_id,version,classifier,path,file_name,file_size,last_modified,change_detection_mode,index_status) " +
                            "VALUES(?,?,?,?,?,?,?,?,?,?) ON CONFLICT(path) DO UPDATE SET group_id=excluded.group_id,artifact_id=excluded.artifact_id,version=excluded.version,classifier=excluded.classifier,file_name=excluded.file_name,file_size=excluded.file_size,last_modified=excluded.last_modified,change_detection_mode=excluded.change_detection_mode,index_status=excluded.index_status",
                    Statement.RETURN_GENERATED_KEYS)) {
                MavenCoordinate c = jar.coordinate();
                ps.setString(1, c.groupId()); ps.setString(2, c.artifactId()); ps.setString(3, c.version()); ps.setString(4, c.classifier());
                Path path = jar.path().toAbsolutePath(); ps.setString(5, path.toString()); ps.setString(6, jar.fileName());
                if (Files.exists(path)) { ps.setLong(7, Files.size(path)); ps.setLong(8, Files.getLastModifiedTime(path).toMillis()); } else { ps.setNull(7, Types.INTEGER); ps.setNull(8, Types.INTEGER); }
                ps.setString(9, ChangeDetectionMode.NONE.name()); ps.setString(10, "ready"); ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("SELECT id FROM jars WHERE path=?")) {
                ps.setString(1, jar.path().toAbsolutePath().toString());
                try (ResultSet rs = ps.executeQuery()) { if (!rs.next()) throw new SQLException("jar row was not created"); jarId = rs.getLong(1); }
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM annotations WHERE (target_kind='class' AND target_id IN (SELECT id FROM classes WHERE jar_id=?)) OR (target_kind='field' AND target_id IN (SELECT f.id FROM fields f JOIN classes c ON c.id=f.class_id WHERE c.jar_id=?)) OR (target_kind='method' AND target_id IN (SELECT m.id FROM methods m JOIN classes c ON c.id=m.class_id WHERE c.jar_id=?))")) { ps.setLong(1, jarId); ps.setLong(2, jarId); ps.setLong(3, jarId); ps.executeUpdate(); }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM classes WHERE jar_id=?")) { ps.setLong(1, jarId); ps.executeUpdate(); }
            Map<String, Long> methodIds = new HashMap<>();
            Map<String, Long> classIds = new HashMap<>();
            for (ParsedClass pc : classes) {
                ClassInfo ci = pc.classInfo();
                long classId;
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO classes(jar_id,internal_name,fully_qualified_name,package_name,kind,access_flags,super_name,signature,source_file,class_file_major_version) VALUES(?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, jarId); ps.setString(2, ci.internalName()); ps.setString(3, ci.name()); ps.setString(4, ci.packageName()); ps.setString(5, ci.kind()); ps.setInt(6, ci.accessFlags()); setNullable(ps, 7, ci.superName(), Types.VARCHAR); setNullable(ps, 8, ci.signature(), Types.VARCHAR); setNullable(ps, 9, ci.sourceFile(), Types.VARCHAR); ps.setInt(10, ci.classFileMajorVersion()); ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) { rs.next(); classId = rs.getLong(1); }
                }
                classIds.put(ci.name(), classId);
                insertInterfaces(classId, ci.interfaces());
                insertAnnotations("class", classId, ci.annotations());
                for (FieldInfo fi : pc.fields()) {
                    long fieldId;
                    try (PreparedStatement ps = connection.prepareStatement("INSERT INTO fields(class_id,name,type,generic_type,access_flags,is_static,is_final) VALUES(?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                        ps.setLong(1, classId); ps.setString(2, fi.name()); ps.setString(3, fi.type()); setNullable(ps, 4, fi.genericType(), Types.VARCHAR); ps.setInt(5, fi.accessFlags()); ps.setInt(6, fi.isStatic() ? 1 : 0); ps.setInt(7, fi.isFinal() ? 1 : 0); ps.executeUpdate();
                        try (ResultSet rs = ps.getGeneratedKeys()) { rs.next(); fieldId = rs.getLong(1); }
                    }
                    insertAnnotations("field", fieldId, fi.annotations());
                }
                for (MethodInfo mi : pc.methods()) {
                    long methodId;
                    try (PreparedStatement ps = connection.prepareStatement("INSERT INTO methods(class_id,name,descriptor,return_type,generic_signature,access_flags) VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                        ps.setLong(1, classId); ps.setString(2, mi.name()); ps.setString(3, mi.descriptor()); ps.setString(4, mi.returnType()); setNullable(ps, 5, mi.genericSignature(), Types.VARCHAR); ps.setInt(6, mi.accessFlags()); ps.executeUpdate();
                        try (ResultSet rs = ps.getGeneratedKeys()) { rs.next(); methodId = rs.getLong(1); }
                    }
                    methodIds.put(methodKey(mi.owner(), mi.name(), mi.descriptor()), methodId);
                    insertParameters(methodId, mi.parameters()); insertExceptions(methodId, mi.exceptions()); insertAnnotations("method", methodId, mi.annotations());
                }
            }
            for (ParsedClass pc : classes) {
                for (CallEdge edge : pc.callEdges()) {
                    Long callerId = methodIds.get(methodKey(edge.callerOwner(), edge.callerName(), edge.callerDescriptor()));
                    if (callerId == null) continue;
                    try (PreparedStatement ps = connection.prepareStatement("INSERT INTO calls(caller_method_id,target_owner,target_name,target_descriptor,opcode,resolution_kind,source_jar_id) VALUES(?,?,?,?,?,?,?)")) {
                        ps.setLong(1, callerId); ps.setString(2, edge.targetOwner()); ps.setString(3, edge.targetName()); ps.setString(4, edge.targetDescriptor()); ps.setString(5, edge.opcode()); ps.setString(6, edge.resolutionKind().name()); ps.setLong(7, jarId); ps.executeUpdate();
                    }
                }
            }
            connection.commit();
        } catch (SQLException | IOException e) {
            try { connection.rollback(); } catch (SQLException ignored) { }
            if (e instanceof IOException io) throw io;
            throw sql("Failed to replace jar index", (SQLException) e);
        } finally {
            try { connection.setAutoCommit(previousAutoCommit); } catch (SQLException ignored) { }
        }
    }

    private void insertInterfaces(long classId, List<String> interfaces) throws SQLException {
        if (interfaces == null) return;
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO class_interfaces(class_id,interface_name,position) VALUES(?,?,?)")) {
            for (int i = 0; i < interfaces.size(); i++) { ps.setLong(1, classId); ps.setString(2, interfaces.get(i)); ps.setInt(3, i); ps.addBatch(); }
            ps.executeBatch();
        }
    }

    private void insertAnnotations(String targetKind, long targetId, List<AnnotationInfo> annotations) throws SQLException {
        if (annotations == null) return;
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO annotations(target_kind,target_id,type_name,values_json) VALUES(?,?,?,?)")) {
            for (AnnotationInfo annotation : annotations) { ps.setString(1, targetKind); ps.setLong(2, targetId); ps.setString(3, annotation.type()); ps.setString(4, SqlValueCodec.encodeMap(annotation.values())); ps.addBatch(); }
            ps.executeBatch();
        }
    }

    private void insertParameters(long methodId, List<String> parameters) throws SQLException {
        if (parameters == null) return;
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO method_parameters(method_id,position,type) VALUES(?,?,?)")) {
            for (int i = 0; i < parameters.size(); i++) { ps.setLong(1, methodId); ps.setInt(2, i); ps.setString(3, parameters.get(i)); ps.addBatch(); }
            ps.executeBatch();
        }
    }

    private void insertExceptions(long methodId, List<String> exceptions) throws SQLException {
        if (exceptions == null) return;
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO method_exceptions(method_id,exception_name) VALUES(?,?)")) {
            for (String exception : exceptions) { ps.setLong(1, methodId); ps.setString(2, exception); ps.addBatch(); }
            ps.executeBatch();
        }
    }

    @Override public synchronized void removeJar(JarArtifact jar) throws IOException { try (PreparedStatement ps = connection.prepareStatement("DELETE FROM jars WHERE path=?")) { ps.setString(1, jar.path().toAbsolutePath().toString()); ps.executeUpdate(); } catch (SQLException e) { throw sql("Failed to remove jar", e); } }

    @Override public synchronized List<ClassInfo> searchClasses(String query, String packagePrefix, String kind, int limit, int offset) throws IOException { StringBuilder sql = new StringBuilder("SELECT c.*,j.path jar_path,j.group_id jar_group,j.artifact_id jar_artifact,j.version jar_version,j.classifier jar_classifier FROM classes c JOIN jars j ON j.id=c.jar_id WHERE 1=1"); List<Object> args = new ArrayList<>(); addClassFilters(sql,args,query,packagePrefix,kind); sql.append(" ORDER BY c.fully_qualified_name LIMIT ? OFFSET ?"); args.add(Math.max(0,limit)); args.add(Math.max(0,offset)); return queryClasses(sql.toString(),args); }

    @Override public synchronized long countClasses(String query, String packagePrefix, String kind) throws IOException { StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM classes c WHERE 1=1"); List<Object> args = new ArrayList<>(); addClassFilters(sql,args,query,packagePrefix,kind); return scalar(sql.toString(),args); }

    private void addClassFilters(StringBuilder sql,List<Object> args,String query,String packagePrefix,String kind) { if (query != null && !query.isBlank()) { sql.append(" AND (c.fully_qualified_name LIKE ? OR c.internal_name LIKE ?)"); String q="%"+query+"%"; args.add(q); args.add(q); } if (packagePrefix != null && !packagePrefix.isBlank()) { sql.append(" AND c.package_name LIKE ?"); args.add(packagePrefix+"%"); } if (kind != null && !kind.isBlank()) { sql.append(" AND c.kind=?"); args.add(kind); } }

    @Override public synchronized Optional<ClassInfo> findClass(String name) throws IOException { List<ClassInfo> result=queryClasses("SELECT c.*,j.path jar_path,j.group_id jar_group,j.artifact_id jar_artifact,j.version jar_version,j.classifier jar_classifier FROM classes c JOIN jars j ON j.id=c.jar_id WHERE c.fully_qualified_name=? LIMIT 1",List.of(name)); return result.stream().findFirst(); }

    private List<ClassInfo> queryClasses(String sql,List<?> args) throws IOException { try (PreparedStatement ps=connection.prepareStatement(sql)) { bind(ps,args); try(ResultSet rs=ps.executeQuery()) { List<ClassInfo> result=new ArrayList<>(); while(rs.next()) result.add(readClass(rs)); return result; } } catch(SQLException e){throw sql("Failed to query classes",e);} }

    private ClassInfo readClass(ResultSet rs) throws SQLException {
        long id=rs.getLong("id"); List<String> interfaces=new ArrayList<>(); try(PreparedStatement ps=connection.prepareStatement("SELECT interface_name FROM class_interfaces WHERE class_id=? ORDER BY position")){ps.setLong(1,id);try(ResultSet r=ps.executeQuery()){while(r.next())interfaces.add(r.getString(1));}}
        List<AnnotationInfo> annotations=readAnnotations("class",id);
        return new ClassInfo(rs.getString("fully_qualified_name"),rs.getString("internal_name"),rs.getString("package_name"),rs.getString("kind"),rs.getInt("access_flags"),rs.getString("super_name"),interfaces,rs.getString("signature"),rs.getString("source_file"),rs.getInt("class_file_major_version"),readJar(rs),annotations);
    }

    private List<AnnotationInfo> readAnnotations(String targetKind,long targetId) throws SQLException { List<AnnotationInfo> result=new ArrayList<>(); try(PreparedStatement ps=connection.prepareStatement("SELECT type_name,values_json FROM annotations WHERE target_kind=? AND target_id=? ORDER BY id")){ps.setString(1,targetKind);ps.setLong(2,targetId);try(ResultSet rs=ps.executeQuery()){while(rs.next())result.add(new AnnotationInfo(rs.getString(1),SqlValueCodec.decodeMap(rs.getString(2))));}} return result; }

    private JarArtifact readJar(ResultSet rs) throws SQLException { MavenCoordinate coordinate=new MavenCoordinate(rs.getString("jar_group"),rs.getString("jar_artifact"),rs.getString("jar_version"),rs.getString("jar_classifier")); return new JarArtifact(Path.of(rs.getString("jar_path")),coordinate); }

    @Override public synchronized List<MethodInfo> findMethods(String owner,String name,String descriptor) throws IOException { StringBuilder sql=new StringBuilder("SELECT m.*,c.fully_qualified_name owner_name FROM methods m JOIN classes c ON c.id=m.class_id WHERE c.fully_qualified_name=?"); List<Object> args=new ArrayList<>(List.of(owner)); if(name!=null&&!name.isBlank()){sql.append(" AND m.name=?");args.add(name);} if(descriptor!=null&&!descriptor.isBlank()){sql.append(" AND m.descriptor=?");args.add(descriptor);} sql.append(" ORDER BY m.name,m.descriptor"); try(PreparedStatement ps=connection.prepareStatement(sql.toString())){bind(ps,args);try(ResultSet rs=ps.executeQuery()){List<MethodInfo> result=new ArrayList<>();while(rs.next()){long id=rs.getLong("id");result.add(new MethodInfo(rs.getString("owner_name"),rs.getString("name"),rs.getString("descriptor"),rs.getString("return_type"),readParameterTypes(id),readExceptions(id),rs.getInt("access_flags"),rs.getString("generic_signature"),readAnnotations("method",id)));}return result;}}catch(SQLException e){throw sql("Failed to query methods",e);} }

    private List<String> readParameterTypes(long id)throws SQLException{List<String> result=new ArrayList<>();try(PreparedStatement ps=connection.prepareStatement("SELECT type FROM method_parameters WHERE method_id=? ORDER BY position")){ps.setLong(1,id);try(ResultSet rs=ps.executeQuery()){while(rs.next())result.add(rs.getString(1));}}return result;}
    private List<String> readExceptions(long id)throws SQLException{List<String> result=new ArrayList<>();try(PreparedStatement ps=connection.prepareStatement("SELECT exception_name FROM method_exceptions WHERE method_id=? ORDER BY exception_name")){ps.setLong(1,id);try(ResultSet rs=ps.executeQuery()){while(rs.next())result.add(rs.getString(1));}}return result;}

    @Override public synchronized List<FieldInfo> findFields(String owner,String fieldName)throws IOException{StringBuilder sql=new StringBuilder("SELECT f.*,c.fully_qualified_name owner_name FROM fields f JOIN classes c ON c.id=f.class_id WHERE c.fully_qualified_name=?");List<Object> args=new ArrayList<>(List.of(owner));if(fieldName!=null&&!fieldName.isBlank()){sql.append(" AND f.name=?");args.add(fieldName);}sql.append(" ORDER BY f.name");try(PreparedStatement ps=connection.prepareStatement(sql.toString())){bind(ps,args);try(ResultSet rs=ps.executeQuery()){List<FieldInfo> result=new ArrayList<>();while(rs.next())result.add(new FieldInfo(rs.getString("owner_name"),rs.getString("name"),rs.getString("type"),rs.getString("generic_type"),rs.getInt("access_flags"),rs.getInt("is_static")!=0,rs.getInt("is_final")!=0,readAnnotations("field",rs.getLong("id"))));return result;}}catch(SQLException e){throw sql("Failed to query fields",e);}}

    @Override public synchronized List<ClassInfo> findSubtypes(String typeName,boolean recursive,boolean includeInterfaces,int limit,int offset)throws IOException{LinkedHashSet<String> names=subtypeNames(typeName,recursive,includeInterfaces);if(offset>=names.size())return List.of();List<String> page=new ArrayList<>(names).subList(Math.max(0,offset),Math.min(names.size(),offset+Math.max(0,limit)));List<ClassInfo> result=new ArrayList<>();for(String name:page)result.addAll(queryClasses("SELECT c.*,j.path jar_path,j.group_id jar_group,j.artifact_id jar_artifact,j.version jar_version,j.classifier jar_classifier FROM classes c JOIN jars j ON j.id=c.jar_id WHERE c.fully_qualified_name=?",List.of(name)));return result;}
    @Override public synchronized long countSubtypes(String typeName,boolean recursive,boolean includeInterfaces)throws IOException{return subtypeNames(typeName,recursive,includeInterfaces).size();}
    private LinkedHashSet<String> subtypeNames(String typeName,boolean recursive,boolean includeInterfaces)throws IOException{LinkedHashSet<String> found=new LinkedHashSet<>();LinkedHashSet<String> frontier=new LinkedHashSet<>();frontier.add(typeName);while(!frontier.isEmpty()){LinkedHashSet<String> next=new LinkedHashSet<>();for(String parent:frontier){String sql="SELECT c.fully_qualified_name FROM classes c WHERE c.super_name=?"+(includeInterfaces?" OR c.id IN (SELECT class_id FROM class_interfaces WHERE interface_name=?)":"");try(PreparedStatement ps=connection.prepareStatement(sql)){ps.setString(1,parent);if(includeInterfaces)ps.setString(2,parent);try(ResultSet rs=ps.executeQuery()){while(rs.next()){String child=rs.getString(1);if(found.add(child)&&recursive)next.add(child);}}}catch(SQLException e){throw sql("Failed to query subtypes",e);}}if(!recursive)break;frontier=next;}return found;}

    @Override public synchronized List<CallEdge> findCallers(String owner,String name,String descriptor,boolean includePolymorphic,int limit,int offset)throws IOException{String sql="SELECT c.*,m.name caller_name,m.descriptor caller_descriptor,cl.fully_qualified_name caller_owner,j.path jar_path,j.group_id jar_group,j.artifact_id jar_artifact,j.version jar_version,j.classifier jar_classifier FROM calls c JOIN methods m ON m.id=c.caller_method_id JOIN classes cl ON cl.id=m.class_id LEFT JOIN jars j ON j.id=c.source_jar_id WHERE c.target_owner=? AND c.target_name=? AND c.target_descriptor=? ORDER BY cl.fully_qualified_name,m.name LIMIT ? OFFSET ?";try(PreparedStatement ps=connection.prepareStatement(sql)){ps.setString(1,owner);ps.setString(2,name);ps.setString(3,descriptor);ps.setInt(4,Math.max(0,limit));ps.setInt(5,Math.max(0,offset));try(ResultSet rs=ps.executeQuery()){List<CallEdge> result=new ArrayList<>();while(rs.next()){JarArtifact jar=null;if(rs.getString("jar_path")!=null)jar=new JarArtifact(Path.of(rs.getString("jar_path")),new MavenCoordinate(rs.getString("jar_group"),rs.getString("jar_artifact"),rs.getString("jar_version"),rs.getString("jar_classifier")));result.add(new CallEdge(rs.getString("caller_owner"),rs.getString("caller_name"),rs.getString("caller_descriptor"),rs.getString("target_owner"),rs.getString("target_name"),rs.getString("target_descriptor"),rs.getString("opcode"),ResolutionKind.valueOf(rs.getString("resolution_kind")),jar));}return result;}}catch(SQLException e){throw sql("Failed to query callers",e);}}
    @Override public synchronized long countCallers(String owner,String name,String descriptor,boolean includePolymorphic)throws IOException{return scalar("SELECT COUNT(*) FROM calls WHERE target_owner=? AND target_name=? AND target_descriptor=?",List.of(owner,name,descriptor));}
    @Override public synchronized List<CallEdge> findCallees(String owner,String name,String descriptor)throws IOException{String sql="SELECT c.*,cl.fully_qualified_name caller_owner,m.name caller_name,m.descriptor caller_descriptor,j.path jar_path,j.group_id jar_group,j.artifact_id jar_artifact,j.version jar_version,j.classifier jar_classifier FROM calls c JOIN methods m ON m.id=c.caller_method_id JOIN classes cl ON cl.id=m.class_id LEFT JOIN jars j ON j.id=c.source_jar_id WHERE cl.fully_qualified_name=? AND m.name=? AND m.descriptor=? ORDER BY c.target_owner,c.target_name";try(PreparedStatement ps=connection.prepareStatement(sql)){ps.setString(1,owner);ps.setString(2,name);ps.setString(3,descriptor);try(ResultSet rs=ps.executeQuery()){List<CallEdge> result=new ArrayList<>();while(rs.next()){JarArtifact jar=null;if(rs.getString("jar_path")!=null)jar=new JarArtifact(Path.of(rs.getString("jar_path")),new MavenCoordinate(rs.getString("jar_group"),rs.getString("jar_artifact"),rs.getString("jar_version"),rs.getString("jar_classifier")));result.add(new CallEdge(rs.getString("caller_owner"),rs.getString("caller_name"),rs.getString("caller_descriptor"),rs.getString("target_owner"),rs.getString("target_name"),rs.getString("target_descriptor"),rs.getString("opcode"),ResolutionKind.valueOf(rs.getString("resolution_kind")),jar));}return result;}}catch(SQLException e){throw sql("Failed to query callees",e);}}

    @Override public synchronized Optional<DecompiledSource> findDecompiled(DecompileCacheKey key)throws IOException{String sql="SELECT class_name,source,source_path,decompiler_version FROM decompile_cache WHERE jar_identity=? AND class_entry=? AND target_java_release=? AND decompiler_version=?";try(PreparedStatement ps=connection.prepareStatement(sql)){ps.setString(1,key.jarIdentity());ps.setString(2,key.classEntry());ps.setInt(3,key.targetJavaRelease());ps.setString(4,key.decompilerVersion());try(ResultSet rs=ps.executeQuery()){if(!rs.next())return Optional.empty();String p=rs.getString("source_path");return Optional.of(new DecompiledSource(rs.getString("class_name"),rs.getString("source"),true,p==null?null:Path.of(p),rs.getString("decompiler_version")));}}catch(SQLException e){throw sql("Failed to query decompiled cache",e);}}
    @Override public synchronized void saveDecompiled(DecompileCacheKey key,DecompiledSource source)throws IOException{String sql="INSERT INTO decompile_cache(jar_identity,class_entry,target_java_release,decompiler_version,class_name,source,source_path) VALUES(?,?,?,?,?,?,?) ON CONFLICT(jar_identity,class_entry,target_java_release,decompiler_version) DO UPDATE SET class_name=excluded.class_name,source=excluded.source,source_path=excluded.source_path";try(PreparedStatement ps=connection.prepareStatement(sql)){ps.setString(1,key.jarIdentity());ps.setString(2,key.classEntry());ps.setInt(3,key.targetJavaRelease());ps.setString(4,key.decompilerVersion());ps.setString(5,source.className());ps.setString(6,source.source());setNullable(ps,7,source.sourcePath()==null?null:source.sourcePath().toString(),Types.VARCHAR);ps.executeUpdate();}catch(SQLException e){throw sql("Failed to save decompiled cache",e);}}
    @Override public synchronized void invalidateDecompiledForJar(JarArtifact jar)throws IOException{try(PreparedStatement ps=connection.prepareStatement("DELETE FROM decompile_cache WHERE jar_identity IN (?,?)")){ps.setString(1,jar.path().toAbsolutePath().toString());ps.setString(2,jar.coordinate().toString());ps.executeUpdate();}catch(SQLException e){throw sql("Failed to invalidate decompiled cache",e);}}

    @Override public synchronized IndexSummary summary()throws IOException{return new IndexSummary(scalar("SELECT COUNT(*) FROM projects",List.of()),scalar("SELECT COUNT(*) FROM jars",List.of()),scalar("SELECT COUNT(*) FROM classes",List.of()),scalar("SELECT COUNT(*) FROM methods",List.of()),scalar("SELECT COUNT(*) FROM fields",List.of()),scalar("SELECT COUNT(*) FROM calls",List.of()),scalar("SELECT COUNT(*) FROM index_errors",List.of()));}
    @Override public synchronized void close() throws IOException { try { connection.close(); } catch(SQLException e){throw sql("Failed to close SQLite database",e);} }

    private long scalar(String sql,List<?> args)throws IOException{try(PreparedStatement ps=connection.prepareStatement(sql)){bind(ps,args);try(ResultSet rs=ps.executeQuery()){return rs.next()?rs.getLong(1):0;}}catch(SQLException e){throw sql("Failed to query SQLite",e);}}
    private static void bind(PreparedStatement ps,List<?> args)throws SQLException{for(int i=0;i<args.size();i++){Object value=args.get(i);if(value==null)ps.setNull(i+1,Types.VARCHAR);else if(value instanceof Integer v)ps.setInt(i+1,v);else if(value instanceof Long v)ps.setLong(i+1,v);else ps.setString(i+1,value.toString());}}
    private static void setNullable(PreparedStatement ps,int index,Object value,int type)throws SQLException{if(value==null)ps.setNull(index,type);else ps.setObject(index,value);}
    private static String methodKey(String owner,String name,String descriptor){return owner+"\u0000"+name+"\u0000"+descriptor;}
    private static IOException sql(String message,SQLException e){return new IOException(message,e);}
}
