package com.buschmais.jqassistant.core.store.api.model;

import com.buschmais.xo.neo4j.api.annotation.Label;

@Label("Directory")
public interface DirectoryDescriptor extends FileDescriptor, FileContainerDescriptor {
}
