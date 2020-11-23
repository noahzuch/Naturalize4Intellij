package com.github.noahzuch.naturalize4intellij;

import static com.google.common.base.Preconditions.checkArgument;

import codemining.cpp.codeutils.AbstractCdtASTAnnotatedTokenizer;
import codemining.java.codeutils.scopes.AllScopeExtractor;
import codemining.java.codeutils.scopes.AllScopeExtractor.AllScopeSnippetExtractor;
import codemining.java.codeutils.scopes.MethodScopeExtractor;
import codemining.java.codeutils.scopes.TypenameScopeExtractor;
import codemining.java.tokenizers.JavaTokenizer;
import codemining.languagetools.IScopeExtractor;
import codemining.languagetools.Scope;
import codemining.languagetools.Scope.ScopeType;
import codemining.languagetools.bindings.TokenNameBinding;
import com.intellij.codeInsight.daemon.impl.quickfix.RenameElementFix;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.history.core.StreamUtil;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;

import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.Optional;
import java.util.SortedSet;
import java.util.Spliterator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import jdk.internal.org.jline.utils.Log;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nls.Capitalization;
import org.jetbrains.annotations.NotNull;
import org.jf.dexlib2.iface.MethodParameter;
import renaming.renamers.AbstractIdentifierRenamings;
import renaming.renamers.BaseIdentifierRenamings;
import renaming.renamers.INGramIdentifierRenamer.Renaming;
import com.intellij.openapi.diagnostic.Logger;
import renaming.segmentranking.SegmentRenamingSuggestion.Suggestion;
import renaming.segmentranking.SnippetScorer;

public class NaturalizeInspection implements Annotator {

    private static final Logger LOG = Logger
            .getInstance("#com.intellij.codeInspection.ComparingReferencesInspection");
    private final DecimalFormat df = new DecimalFormat("#.00");

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element instanceof PsiClass || element instanceof PsiField
                || element instanceof PsiParameter
                || element instanceof PsiLocalVariable
                || (element instanceof PsiMethod)) {
            final String elementName = ((PsiNameIdentifierOwner) element).getName();
            if (elementName == null) {
                return;
            }

            final PsiFile containingFile = element.getContainingFile();

            final NaturalizeService naturalizeService = NaturalizeService
                    .getInstance(element.getProject());

            final AbstractIdentifierRenamings renamer = naturalizeService.getRenamer(containingFile.getVirtualFile());
            SortedSet<Renaming> renamings = renamer.getRenamings(
                    new Scope(getCodeSnippetForPsiElement(element), ScopeType.SCOPE_LOCAL,
                            null, -1, -1),
                    elementName);


            LOG.warn("Renamings for " + elementName + ": " + renamings);

            removeWorseCandidates(renamings,elementName);

            final double scoreOfCurrent = renamings.stream().filter(
                    renaming -> renaming.name.equals(elementName) || renaming.name.equals("UNK_SYMBOL"))
                    .mapToDouble(r -> r.score).min().orElseThrow(IllegalStateException::new);
            double confidence = scoreOfCurrent - renamings.first().score;

            double calcTotal = 0;
            double scoreOfCurrentConverted = Double.POSITIVE_INFINITY;
            for (final Renaming alternative : renamings) {
                calcTotal += Math.pow(2, -alternative.score);
                if ((alternative.name.equals(elementName) || alternative.name
                        .equals("UNK_SYMBOL"))
                        && Double.isInfinite(scoreOfCurrentConverted)) {
                    scoreOfCurrentConverted = alternative.score;
                }
            }
            final double total = calcTotal;
            LOG.warn("Confidence for " + elementName + " : " + confidence);
            renamings.removeIf(renaming -> scoreOfCurrent - renaming.score < 1.8 || renaming.name.equals("UNK_SYMBOL"));
            if (confidence > 1.8 && renamings.size() > 0) {
                Annotation annotation = holder
                        .createWarningAnnotation(getElementToAnnotate(element), "Naturalize-Fix");
                renamings.stream().limit(5)
                        .map(alternative -> new RenameElementFix((PsiNamedElement) element, alternative.name,
                                "Naturalize Renaming: " + alternative.name + " (" + df
                                        .format(Math.pow(2, -alternative.score) * 100
                                                / total) + "%), ")).forEach(annotation::registerFix);
            }
        }
    }

    private void removeWorseCandidates(SortedSet<Renaming> renamings, String identifierName) {
        boolean foundOriginal = false;
        Iterator<Renaming> iterator = renamings.iterator();
        while (!foundOriginal && iterator.hasNext()) {
            Renaming renaming = iterator.next();
            if (renaming.name.equals(identifierName) || renaming.name.equals("UNK_SYMBOL")) {
                foundOriginal = true;
            }
        }
        while (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    @NotNull
    private PsiElement getElementToAnnotate(@NotNull PsiElement element) {
        return ((PsiNameIdentifierOwner) element).getNameIdentifier();
    }

    public String getIdentifierTypeForPsiElement(PsiElement element) {
        if (element instanceof PsiField
                || element instanceof PsiParameter
                || element instanceof PsiLocalVariable) {
            return "variable";
        } else if (element instanceof PsiMethod) {
            return MethodScopeExtractor.METHOD_CALL;
        } else if (element instanceof PsiClass) {
            return TypenameScopeExtractor.TYPENAME;
        } else {
            throw new IllegalArgumentException();
        }
    }

    public String getCodeSnippetForPsiElement(PsiElement element) {
        if (element instanceof PsiField || (element instanceof PsiMethod) || (element instanceof PsiClass)) {
            String ownText;
            if(element instanceof PsiField){
                ownText=element.getText();
            }else if (element instanceof PsiMethod){
                ownText = element.getText();
            }else {
                ownText = "";
            }
            Spliterator<PsiReference> split = ReferencesSearch.search(element).spliterator();
            //String string = StreamSupport.stream(split, false).map(ref -> ref.getElement().getContext().getText()).collect(Collectors.joining("\n"));
            //System.out.printf("Code snippet for %s:\n%s%n",element,string);
            //return string;
            String string = ownText + "\n" + StreamSupport.stream(split, false).map(ref -> Optional.ofNullable(PsiUtil.getEnclosingStatement(ref.getElement())).map(PsiElement::getText).orElse("")).collect(Collectors.joining("\n"));
            System.out.printf("Code snippet for %s:\n%s%n",element,string);
            return string;
        } else if (element instanceof PsiParameter || element instanceof PsiLocalVariable) {
            Spliterator<PsiReference> split = ReferencesSearch.search(element).spliterator();
            String string = element.getText() + "\n" + StreamSupport.stream(split, false).map(ref -> Optional.ofNullable(PsiUtil.getEnclosingStatement(ref.getElement())).map(PsiElement::getText).orElse("")).collect(Collectors.joining("\n"));
            System.out.printf("Code snippet for %s:\n%s%n",element,string);
            return string;
        } else {
            throw new IllegalArgumentException();
        }

    }


}
