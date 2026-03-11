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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SymbolKind;
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
		Set<String>		declaredProperties	= collectDeclaredProperties(
		    lspService.requestDocumentSymbols( psiFile.getVirtualFile(), document )
		);

		List<Integer>	data				= tokens.getData();
		List<String>	tokenTypes			= legend.getTokenTypes();
		List<String>	tokenMods			= legend.getTokenModifiers();
		CharSequence	source				= document.getCharsSequence();
		int				line				= 0;
		int				column				= 0;
		for ( int i = 0; i + 4 < data.size(); i += 5 ) {
			int	deltaLine		= data.get( i );
			int	deltaStart		= data.get( i + 1 );
			int	length			= data.get( i + 2 );
			int	tokenTypeIndex	= data.get( i + 3 );
			int	tokenModifier	= data.get( i + 4 );

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
			Set<String>			modifiers	= decodeTokenModifiers( tokenModifier, tokenMods );
			TextAttributesKey	key			= resolveSemanticToken( tokenType, modifiers, source, startOffset, endOffset, declaredProperties );
			if ( key == null ) {
				continue;
			}

			holder.newSilentAnnotation( HighlightSeverity.INFORMATION )
			    .range( new TextRange( startOffset, endOffset ) )
			    .textAttributes( key )
			    .create();
		}
	}

	static Set<String> decodeTokenModifiers( int tokenModifierBits, List<String> legendModifiers ) {
		if ( tokenModifierBits == 0 || legendModifiers == null || legendModifiers.isEmpty() ) {
			return Set.of();
		}
		Set<String> modifiers = new HashSet<>();
		for ( int bit = 0; bit < legendModifiers.size(); bit++ ) {
			if ( ( tokenModifierBits & ( 1 << bit ) ) != 0 ) {
				modifiers.add( legendModifiers.get( bit ) );
			}
		}
		return modifiers.isEmpty() ? Set.of() : Set.copyOf( modifiers );
	}

	static TextAttributesKey resolveSemanticToken(
	    String tokenType,
	    Set<String> modifiers,
	    CharSequence source,
	    int startOffset,
	    int endOffset,
	    Set<String> declaredProperties ) {
		if ( "variable".equals( tokenType )
		    && isVariablesScopeMember( source, startOffset )
		    && isDeclaredPropertyReference( source, startOffset, endOffset, declaredProperties ) ) {
			// In BoxLang, component properties live in the variables scope.
			return DefaultLanguageHighlighterColors.INSTANCE_FIELD;
		}
		return mapSemanticToken( tokenType, modifiers );
	}

	static Set<String> collectDeclaredProperties( List<DocumentSymbol> symbols ) {
		if ( symbols == null || symbols.isEmpty() ) {
			return Set.of();
		}
		Set<String> properties = new HashSet<>();
		for ( DocumentSymbol symbol : symbols ) {
			collectDeclaredProperties( symbol, properties );
		}
		return properties.isEmpty() ? Set.of() : Set.copyOf( properties );
	}

	private static void collectDeclaredProperties( DocumentSymbol symbol, Set<String> properties ) {
		if ( symbol == null ) {
			return;
		}
		if ( symbol.getKind() == SymbolKind.Property || symbol.getKind() == SymbolKind.Field ) {
			String name = symbol.getName();
			if ( name != null && !name.isBlank() ) {
				properties.add( name.toLowerCase( Locale.ROOT ) );
			}
		}
		if ( symbol.getChildren() == null || symbol.getChildren().isEmpty() ) {
			return;
		}
		for ( DocumentSymbol child : symbol.getChildren() ) {
			collectDeclaredProperties( child, properties );
		}
	}

	private static boolean isDeclaredPropertyReference( CharSequence source, int startOffset, int endOffset, Set<String> declaredProperties ) {
		if ( declaredProperties == null || declaredProperties.isEmpty() || source == null ) {
			return false;
		}
		if ( startOffset < 0 || endOffset <= startOffset || endOffset > source.length() ) {
			return false;
		}
		String tokenText = source.subSequence( startOffset, endOffset ).toString().toLowerCase( Locale.ROOT );
		return declaredProperties.contains( tokenText );
	}

	static boolean isVariablesScopeMember( CharSequence source, int tokenStartOffset ) {
		if ( source == null || tokenStartOffset <= 0 || tokenStartOffset > source.length() ) {
			return false;
		}

		int index = tokenStartOffset - 1;
		while ( index >= 0 && Character.isWhitespace( source.charAt( index ) ) ) {
			index--;
		}
		if ( index < 0 || source.charAt( index ) != '.' ) {
			return false;
		}

		index--;
		while ( index >= 0 && Character.isWhitespace( source.charAt( index ) ) ) {
			index--;
		}
		if ( index < 0 || !isSemanticIdentifierPart( source.charAt( index ) ) ) {
			return false;
		}

		int end = index;
		while ( index >= 0 && isSemanticIdentifierPart( source.charAt( index ) ) ) {
			index--;
		}
		String scope = source.subSequence( index + 1, end + 1 ).toString();
		return "variables".equalsIgnoreCase( scope );
	}

	private static boolean isSemanticIdentifierPart( char ch ) {
		return ch == '_' || ch == '$' || Character.isLetterOrDigit( ch );
	}

	static TextAttributesKey mapSemanticToken( String tokenType, Set<String> modifiers ) {
		if ( tokenType == null ) {
			return null;
		}
		Set<String>	safeModifiers	= modifiers == null ? Set.of() : modifiers;
		boolean		isDeclaration	= safeModifiers.contains( "declaration" ) || safeModifiers.contains( "definition" );
		if ( isDeclaration && ( "function".equals( tokenType ) || "method".equals( tokenType ) ) ) {
			return BoxLangTextAttributes.FUNCTION_NAME;
		}
		if ( "function".equals( tokenType ) && safeModifiers.contains( "defaultLibrary" ) ) {
			return BoxLangTextAttributes.BUILTIN_FUNCTION;
		}
		if ( "method".equals( tokenType ) && safeModifiers.contains( "defaultLibrary" ) ) {
			return BoxLangTextAttributes.MEMBER_FUNCTION;
		}
		return switch ( tokenType ) {
			case "keyword" -> BoxLangTextAttributes.KEYWORD;
			case "string" -> BoxLangTextAttributes.STRING;
			case "comment" -> BoxLangTextAttributes.LINE_COMMENT;
			case "number" -> BoxLangTextAttributes.NUMBER;
			case "operator" -> BoxLangTextAttributes.OPERATOR;
			case "class", "type", "namespace" -> BoxLangTextAttributes.STORAGE_TYPE;
			case "function" -> BoxLangTextAttributes.FUNCTION_CALL;
			case "method" -> BoxLangTextAttributes.METHOD_CALL;
			case "parameter" -> DefaultLanguageHighlighterColors.PARAMETER;
			case "property" -> DefaultLanguageHighlighterColors.INSTANCE_FIELD;
			// Semantic "variable" covers local identifiers and should de-keyword lexed keyword-like names.
			case "variable" -> BoxLangTextAttributes.IDENTIFIER;
			case "tag" -> BoxLangTextAttributes.TAG;
			case "modifier" -> BoxLangTextAttributes.STORAGE_MODIFIER;
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
