package com.ortussolutions.intellijboxlang.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.ortussolutions.intellijboxlang.lexer.BoxLangLexer;
import com.ortussolutions.intellijboxlang.lexer.BoxLangTokenTypes;
import org.jetbrains.annotations.NotNull;

public final class BoxLangParserDefinition implements ParserDefinition {

	private static final TokenSet	COMMENTS	= TokenSet.create(
	    BoxLangTokenTypes.LINE_COMMENT,
	    BoxLangTokenTypes.BLOCK_COMMENT,
	    BoxLangTokenTypes.DOC_COMMENT
	);
	private static final TokenSet	STRINGS		= TokenSet.create( BoxLangTokenTypes.STRING );

	@Override
	public @NotNull Lexer createLexer( Project project ) {
		return new BoxLangLexer();
	}

	@Override
	public @NotNull PsiParser createParser( Project project ) {
		return ( root, builder ) -> {
			var rootNode = builder.mark();
			while ( !builder.eof() ) {
				builder.advanceLexer();
			}
			rootNode.done( root );
			return builder.getTreeBuilt();
		};
	}

	@Override
	public @NotNull IFileElementType getFileNodeType() {
		return BoxLangFileElementType.INSTANCE;
	}

	@Override
	public @NotNull TokenSet getCommentTokens() {
		return COMMENTS;
	}

	@Override
	public @NotNull TokenSet getStringLiteralElements() {
		return STRINGS;
	}

	@Override
	public @NotNull PsiElement createElement( ASTNode node ) {
		return new ASTWrapperPsiElement( node );
	}

	@Override
	public @NotNull PsiFile createFile( @NotNull FileViewProvider viewProvider ) {
		return new BoxLangFile( viewProvider );
	}

	@Override
	public @NotNull SpaceRequirements spaceExistenceTypeBetweenTokens( ASTNode left, ASTNode right ) {
		return SpaceRequirements.MAY;
	}
}
