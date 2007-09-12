/*******************************************************************************
 * Copyright (c) 2000, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.internal.corext.fix;

import java.util.List;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;

import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility2;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFix.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;
import org.eclipse.jdt.internal.ui.text.correction.CorrectionMessages;
import org.eclipse.jdt.internal.ui.viewsupport.BindingLabelProvider;

public class AddUnimplementedMethodsOperation extends CompilationUnitRewriteOperation {

	private ASTNode fTypeNode;
	private final int fProblemId;

	public AddUnimplementedMethodsOperation(ASTNode typeNode, int problemId) {
		fProblemId= problemId;
		fTypeNode= typeNode;
	}

	/**
	 * {@inheritDoc}
	 */
	public void rewriteAST(CompilationUnitRewrite cuRewrite, LinkedProposalModel model) throws CoreException {
		ImportRewriteContext context= new ContextSensitiveImportRewriteContext(((CompilationUnit) fTypeNode.getRoot()), fTypeNode.getStartPosition(), cuRewrite.getImportRewrite());

		if (fTypeNode instanceof EnumDeclaration && fProblemId == IProblem.EnumAbstractMethodMustBeImplemented) {
			EnumDeclaration typeNode= (EnumDeclaration) fTypeNode;
			List enumConstants= typeNode.enumConstants();
			for (int i= 0; i < enumConstants.size(); i++) {
				EnumConstantDeclaration enumConstant= (EnumConstantDeclaration) enumConstants.get(i);
				AnonymousClassDeclaration anonymousClassDeclaration= enumConstant.getAnonymousClassDeclaration();
				if (anonymousClassDeclaration == null) {
					addEnumConstantDeclarationBody(enumConstant, cuRewrite, context);
				} else {
					addUnimplementedMethods(anonymousClassDeclaration, cuRewrite, context);
				}
			}
		} else {
			addUnimplementedMethods(fTypeNode, cuRewrite, context);
		}
	}

	private void addUnimplementedMethods(ASTNode typeNode, CompilationUnitRewrite cuRewrite, ImportRewriteContext context) throws CoreException {
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		ListRewrite listRewrite;
		ITypeBinding binding;
		if (typeNode instanceof AnonymousClassDeclaration) {
			AnonymousClassDeclaration decl= (AnonymousClassDeclaration) typeNode;
			binding= decl.resolveBinding();
			listRewrite= rewrite.getListRewrite(decl, AnonymousClassDeclaration.BODY_DECLARATIONS_PROPERTY);
		} else {
			AbstractTypeDeclaration decl= (AbstractTypeDeclaration) typeNode;
			binding= decl.resolveBinding();
			listRewrite= rewrite.getListRewrite(decl, decl.getBodyDeclarationsProperty());
		}
		if (binding != null) {
			ICompilationUnit unit= cuRewrite.getCu();
			ImportRewrite imports= cuRewrite.getImportRewrite();

			IMethodBinding[] methods= StubUtility2.getUnimplementedMethods(binding);
			CodeGenerationSettings settings= JavaPreferencesSettings.getCodeGenerationSettings(unit.getJavaProject());
			if (binding.isAnonymous()) {
				settings.createComments= false;
			}

			for (int i= 0; i < methods.length; i++) {
				MethodDeclaration newMethodDecl= StubUtility2.createImplementationStub(unit, rewrite, imports, context, methods[i], binding.getName(), settings, binding.isInterface());
				listRewrite.insertLast(newMethodDecl, createTextEditGroup(CorrectionMessages.AddUnimplementedMethodsOperation_AddMissingMethod_group, cuRewrite));
			}
		}
	}

	private void addEnumConstantDeclarationBody(EnumConstantDeclaration constDecl, CompilationUnitRewrite cuRewrite, ImportRewriteContext context) throws CoreException {
		ASTRewrite rewrite= cuRewrite.getASTRewrite();

		AnonymousClassDeclaration anonymDecl= constDecl.getAST().newAnonymousClassDeclaration();
		rewrite.set(constDecl, EnumConstantDeclaration.ANONYMOUS_CLASS_DECLARATION_PROPERTY, anonymDecl, createTextEditGroup(CorrectionMessages.AddUnimplementedMethodsOperation_AddMissingMethod_group, cuRewrite));
		IVariableBinding varBinding= constDecl.resolveVariable();
		if (varBinding != null) {
			ICompilationUnit unit= cuRewrite.getCu();
			ImportRewrite imports= cuRewrite.getImportRewrite();

			CodeGenerationSettings settings= JavaPreferencesSettings.getCodeGenerationSettings(unit.getJavaProject());
			settings.createComments= false;

			IMethodBinding[] declaredMethods= varBinding.getDeclaringClass().getDeclaredMethods();
			for (int k= 0; k < declaredMethods.length; k++) {
				IMethodBinding curr= declaredMethods[k];
				if (Modifier.isAbstract(curr.getModifiers())) {
					MethodDeclaration newMethodDecl= StubUtility2.createImplementationStub(unit, rewrite, imports, context, curr, curr.getDeclaringClass().getName(), settings, false);
					anonymDecl.bodyDeclarations().add(newMethodDecl);
				}
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public String getAdditionalInfo() {
		if (fTypeNode instanceof EnumDeclaration)
			return CorrectionMessages.UnimplementedMethodsCorrectionProposal_enum_info;

		IMethodBinding[] methodsToOverride= getMethodsToOverride();
		StringBuffer buf= new StringBuffer();
		buf.append("<b>"); //$NON-NLS-1$
		buf.append(Messages.format(CorrectionMessages.UnimplementedMethodsCorrectionProposal_info, String.valueOf(methodsToOverride.length)));
		buf.append("</b><ul>"); //$NON-NLS-1$
		for (int i= 0; i < methodsToOverride.length; i++) {
			buf.append("<li>"); //$NON-NLS-1$
			buf.append(BindingLabelProvider.getBindingLabel(methodsToOverride[i], JavaElementLabels.ALL_FULLY_QUALIFIED));
			buf.append("</li>"); //$NON-NLS-1$
		}
		buf.append("</ul>"); //$NON-NLS-1$
		return buf.toString();
	}

	private IMethodBinding[] getMethodsToOverride() {
		if (fTypeNode instanceof EnumDeclaration && fProblemId == IProblem.EnumAbstractMethodMustBeImplemented) {
			EnumDeclaration typeNode= (EnumDeclaration) fTypeNode;
			List enumConstants= typeNode.enumConstants();
			for (int i= 0; i < enumConstants.size(); i++) {
				EnumConstantDeclaration enumConstant= (EnumConstantDeclaration) enumConstants.get(i);
				AnonymousClassDeclaration anonymousClassDeclaration= enumConstant.getAnonymousClassDeclaration();
				if (anonymousClassDeclaration != null)
					return getUnimplementedMethods(anonymousClassDeclaration);
			}

			return new IMethodBinding[0];
		} else {
			return getUnimplementedMethods(fTypeNode);
		}
	}

	private IMethodBinding[] getUnimplementedMethods(ASTNode typeNode) {
		ITypeBinding binding;
		if (typeNode instanceof AnonymousClassDeclaration) {
			AnonymousClassDeclaration decl= (AnonymousClassDeclaration) typeNode;
			binding= decl.resolveBinding();
		} else {
			AbstractTypeDeclaration decl= (AbstractTypeDeclaration) typeNode;
			binding= decl.resolveBinding();
		}
		if (binding == null)
			return new IMethodBinding[0];


		return StubUtility2.getUnimplementedMethods(binding);
	}
}