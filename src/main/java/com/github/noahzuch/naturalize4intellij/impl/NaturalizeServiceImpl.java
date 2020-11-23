package com.github.noahzuch.naturalize4intellij.impl;

import codemining.java.tokenizers.JavaTokenizer;
import com.github.noahzuch.naturalize4intellij.NaturalizeService;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
import renaming.renamers.AbstractIdentifierRenamings;
import renaming.renamers.BaseIdentifierRenamings;

public class NaturalizeServiceImpl implements NaturalizeService {

  private final Project project;

  private static final Logger LOG = Logger.getInstance(NaturalizeServiceImpl.class);

  private AbstractIdentifierRenamings lastIdentifierRenamings;
  private String lastFileName=null;

  public NaturalizeServiceImpl(Project project) {
    this.project = project;
  }

  @Override
  public synchronized AbstractIdentifierRenamings getRenamer(VirtualFile current) {
    if (lastFileName == null || !current.getName().equals(lastFileName)) {
      lastFileName = current.getName();
      lastIdentifierRenamings = new BaseIdentifierRenamings(new JavaTokenizer());
      List<File> files = FileTypeIndex.getFiles(
              JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project)).stream()
              .filter(VirtualFile::isInLocalFileSystem)
              .filter(virtualFile -> !virtualFile.equals(current))
              .map(virtualFile -> new File(virtualFile.getPath())).collect(Collectors.toList());

      LOG.warn("FileCount: " + files.size());
      lastIdentifierRenamings.buildRenamingModel(files);
    }

    System.out.println("renamer for file: "+ lastFileName);
    return lastIdentifierRenamings;
  }

}
