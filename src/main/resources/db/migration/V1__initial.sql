CREATE TABLE IF NOT EXISTS schema_version (
    version INTEGER PRIMARY KEY,
    applied_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS projects (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    path TEXT NOT NULL UNIQUE,
    target_java_release INTEGER NOT NULL DEFAULT 8,
    change_detection TEXT NOT NULL DEFAULT 'NONE',
    status TEXT NOT NULL DEFAULT 'new',
    last_indexed_at TEXT
);

CREATE TABLE IF NOT EXISTS jars (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id INTEGER,
    group_id TEXT,
    artifact_id TEXT,
    version TEXT,
    classifier TEXT,
    path TEXT NOT NULL UNIQUE,
    file_name TEXT NOT NULL,
    file_size INTEGER,
    last_modified INTEGER,
    sha256 TEXT,
    change_detection_mode TEXT NOT NULL DEFAULT 'NONE',
    index_status TEXT NOT NULL DEFAULT 'new',
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS classes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    jar_id INTEGER NOT NULL,
    internal_name TEXT NOT NULL,
    fully_qualified_name TEXT NOT NULL,
    package_name TEXT,
    kind TEXT NOT NULL,
    access_flags INTEGER NOT NULL,
    super_name TEXT,
    signature TEXT,
    source_file TEXT,
    class_file_major_version INTEGER NOT NULL,
    UNIQUE (jar_id, internal_name),
    FOREIGN KEY (jar_id) REFERENCES jars(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS class_interfaces (
    class_id INTEGER NOT NULL,
    interface_name TEXT NOT NULL,
    position INTEGER NOT NULL,
    PRIMARY KEY (class_id, interface_name),
    FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS annotations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    target_kind TEXT NOT NULL,
    target_id INTEGER NOT NULL,
    type_name TEXT NOT NULL,
    values_json TEXT NOT NULL DEFAULT '{}'
);

CREATE TABLE IF NOT EXISTS fields (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    class_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    type TEXT NOT NULL,
    generic_type TEXT,
    access_flags INTEGER NOT NULL,
    is_static INTEGER NOT NULL,
    is_final INTEGER NOT NULL,
    UNIQUE (class_id, name),
    FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS methods (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    class_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    descriptor TEXT NOT NULL,
    return_type TEXT NOT NULL,
    generic_signature TEXT,
    access_flags INTEGER NOT NULL,
    UNIQUE (class_id, name, descriptor),
    FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS method_parameters (
    method_id INTEGER NOT NULL,
    position INTEGER NOT NULL,
    type TEXT NOT NULL,
    name TEXT,
    PRIMARY KEY (method_id, position),
    FOREIGN KEY (method_id) REFERENCES methods(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS method_exceptions (
    method_id INTEGER NOT NULL,
    exception_name TEXT NOT NULL,
    PRIMARY KEY (method_id, exception_name),
    FOREIGN KEY (method_id) REFERENCES methods(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS calls (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    caller_method_id INTEGER NOT NULL,
    target_owner TEXT NOT NULL,
    target_name TEXT NOT NULL,
    target_descriptor TEXT NOT NULL,
    opcode TEXT NOT NULL,
    resolution_kind TEXT NOT NULL,
    source_jar_id INTEGER,
    FOREIGN KEY (caller_method_id) REFERENCES methods(id) ON DELETE CASCADE,
    FOREIGN KEY (source_jar_id) REFERENCES jars(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS decompile_cache (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    jar_identity TEXT NOT NULL,
    class_entry TEXT NOT NULL,
    target_java_release INTEGER NOT NULL,
    decompiler_version TEXT NOT NULL,
    class_name TEXT NOT NULL,
    source TEXT NOT NULL,
    source_path TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (jar_identity, class_entry, target_java_release, decompiler_version)
);

CREATE TABLE IF NOT EXISTS index_errors (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    jar_id INTEGER,
    code TEXT NOT NULL,
    message TEXT NOT NULL,
    severity TEXT NOT NULL,
    jar_entry TEXT,
    retryable INTEGER NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (jar_id) REFERENCES jars(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_classes_name ON classes(fully_qualified_name);
CREATE INDEX IF NOT EXISTS idx_classes_package ON classes(package_name);
CREATE INDEX IF NOT EXISTS idx_classes_super ON classes(super_name);
CREATE INDEX IF NOT EXISTS idx_interfaces_name ON class_interfaces(interface_name);
CREATE INDEX IF NOT EXISTS idx_methods_lookup ON methods(name, descriptor);
CREATE INDEX IF NOT EXISTS idx_calls_target ON calls(target_owner, target_name, target_descriptor);
CREATE INDEX IF NOT EXISTS idx_calls_caller ON calls(caller_method_id);

INSERT OR IGNORE INTO schema_version(version) VALUES (1);
