package com.github.noahzuch.naturalize4intellij;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import renaming.renamers.AbstractIdentifierRenamings;

public interface NaturalizeService {

  static NaturalizeService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, NaturalizeService.class);
  }

  AbstractIdentifierRenamings getRenamer(VirtualFile current);
}
