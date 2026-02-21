package com.ortussolutions.intellijboxlang.lsp;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.ortussolutions.intellijboxlang.file.BoxLangFileType;
import com.ortussolutions.intellijboxlang.highlighting.BoxLangTextAttributes;
import java.util.List;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.jetbrains.annotations.NotNull;

public final class BoxLangLspAnnotator implements Annotator {

	private static final Logger	LOG			= Logger.getInstance( BoxLangLspAnnotator.class );
	private static boolean		loggedOnce	= false;

	@Override
	public void annotate( @NotNull PsiElement element, @NotNull AnnotationHolder holder ) {
		PsiFile psiFile = element.getContainingFile();
		if ( psiFile == null || psiFile.getFileType() != BoxLangFileType.INSTANCE ) {
			return;
		}
		if ( element != psiFile ) {
			return;
		}
		if ( !loggedOnce ) {
			loggedOnce = true;
			LOG.info( "BoxLang LSP annotator invoked for " + psiFile.getVirtualFile() );
		}
		Project		project		= psiFile.getProject();
		Document	document	= PsiDocumentManager.getInstance( project ).getDocument( psiFile );
		if ( document == null ) {
			return;
		}

		BoxLangLspClientService lspService = BoxLangLspClientService.getInstance( project );
		if ( psiFile.getVirtualFile() != null ) {
			annotateDiagnostics( lspService, psiFile, document, holder );
		}
		SemanticTokens			tokens	= lspService.requestSemanticTokens( psiFile.getVirtualFile(), document );
		SemanticTokensLegend	legend	= lspService.getLegend();
		if ( tokens == null || legend == null ) {
			return;
		}

		List<Integer>	data		= tokens.getData();
		List<String>	tokenTypes	= legend.getTokenTypes();
		int				line		= 0;
		int				column		= 0;
		for ( int i = 0; i + 4 < data.size(); i += 5 ) {
			int	deltaLine		= data.get( i );
			int	deltaStart		= data.get( i + 1 );
			int	length			= data.get( i + 2 );
			int	tokenTypeIndex	= data.get( i + 3 );

			line	+= deltaLine;
			column	= deltaLine == 0 ? column + deltaStart : deltaStart;

			if ( line >= document.getLineCount() ) {
				continue;
			}
			int	startOffset	= document.getLineStartOffset( line ) + column;
			int	endOffset	= Math.min( startOffset + length, document.getTextLength() );
			if ( startOffset >= endOffset ) {
				continue;
			}

			String				tokenType	= tokenTypeIndex < tokenTypes.size() ? tokenTypes.get( tokenTypeIndex ) : null;
			TextAttributesKey	key			= mapTokenType( tokenType );
			if ( key == null ) {
				continue;
			}

			holder.newSilentAnnotation( HighlightSeverity.INFORMATION )
			    .range( new TextRange( startOffset, endOffset ) )
			    .textAttributes( key )
			    .create();
		}
	}

	private TextAttributesKey mapTokenType( String tokenType ) {
		if ( tokenType == null ) {
			return null;
		}
		return switch ( tokenType ) {
			case "keyword" -> BoxLangTextAttributes.KEYWORD;
			case "string" -> BoxLangTextAttributes.STRING;
			case "comment" -> BoxLangTextAttributes.LINE_COMMENT;
			case "number" -> BoxLangTextAttributes.NUMBER;
			case "operator" -> BoxLangTextAttributes.OPERATOR;
			case "class", "type" -> DefaultLanguageHighlighterColors.CLASS_NAME;
			case "function", "method" -> DefaultLanguageHighlighterColors.FUNCTION_DECLARATION;
			case "parameter" -> DefaultLanguageHighlighterColors.PARAMETER;
			case "property" -> DefaultLanguageHighlighterColors.INSTANCE_FIELD;
			case "variable" -> DefaultLanguageHighlighterColors.LOCAL_VARIABLE;
			case "namespace" -> DefaultLanguageHighlighterColors.CLASS_NAME;
			case "tag" -> BoxLangTextAttributes.TAG;
			default -> BoxLangTextAttributes.IDENTIFIER;
		};
	}

	private void annotateDiagnostics( BoxLangLspClientService lspService, PsiFile psiFile, Document document, AnnotationHolder holder ) {
		List<org.eclipse.lsp4j.Diagnostic> diagnostics = lspService.requestDiagnostics( psiFile.getVirtualFile(), document );
		if ( diagnostics.isEmpty() ) {
			return;
		}
		for ( org.eclipse.lsp4j.Diagnostic diagnostic : diagnostics ) {
			if ( diagnostic.getRange() == null ) {
				continue;
			}
			int	startOffset	= offsetFor( document, diagnostic.getRange().getStart().getLine(), diagnostic.getRange().getStart().getCharacter() );
			int	endOffset	= offsetFor( document, diagnostic.getRange().getEnd().getLine(), diagnostic.getRange().getEnd().getCharacter() );
			if ( startOffset >= endOffset ) {
				continue;
			}
			holder.newAnnotation( mapSeverity( diagnostic.getSeverity() ), diagnostic.getMessage() )
			    .range( new TextRange( startOffset, endOffset ) )
			    .create();
		}
	}

	private int offsetFor( Document document, int line, int column ) {
		if ( line < 0 || line >= document.getLineCount() ) {
			return document.getTextLength();
		}
		int start = document.getLineStartOffset( line );
		return Math.min( start + Math.max( column, 0 ), document.getTextLength() );
	}

	private HighlightSeverity mapSeverity( org.eclipse.lsp4j.DiagnosticSeverity severity ) {
		if ( severity == null ) {
			return HighlightSeverity.WARNING;
		}
		return switch ( severity ) {
			case Error -> HighlightSeverity.ERROR;
			case Warning -> HighlightSeverity.WARNING;
			case Information -> HighlightSeverity.INFORMATION;
			case Hint -> HighlightSeverity.WEAK_WARNING;
		};
	}
}
