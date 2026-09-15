package com.ortussolutions.intellijboxlang.lsp;

import com.ortussolutions.intellijboxlang.highlighting.BoxLangTextAttributes;
import java.util.List;
import java.util.Set;
import junit.framework.TestCase;

public class BoxLangLspAnnotatorTest extends TestCase {

	public void testZeroWidthEofDiagnosticRemainsVisible() {
		var	document	= new com.intellij.openapi.editor.impl.DocumentImpl( "<cfset value = >" );
		var	position	= new org.eclipse.lsp4j.Position( 0, document.getTextLength() );
		assertEquals( new com.intellij.openapi.util.TextRange( document.getTextLength() - 1, document.getTextLength() ),
		    BoxLangLspAnnotator.diagnosticRange( document, new org.eclipse.lsp4j.Range( position, position ) ) );
	}

	public void testZeroWidthInteriorDiagnosticHighlightsNextCharacter() {
		var	document	= new com.intellij.openapi.editor.impl.DocumentImpl( "abc" );
		var	position	= new org.eclipse.lsp4j.Position( 0, 1 );
		assertEquals( new com.intellij.openapi.util.TextRange( 1, 2 ),
		    BoxLangLspAnnotator.diagnosticRange( document, new org.eclipse.lsp4j.Range( position, position ) ) );
	}

	public void testDiagnosticColumnsDoNotSpillIntoNextLine() {
		var document = new com.intellij.openapi.editor.impl.DocumentImpl( "abc\ndef" );
		assertEquals( new com.intellij.openapi.util.TextRange( 1, 3 ), BoxLangLspAnnotator.diagnosticRange( document,
		    new org.eclipse.lsp4j.Range( new org.eclipse.lsp4j.Position( 0, 1 ), new org.eclipse.lsp4j.Position( 0, 100 ) ) ) );
	}

	public void testMapSemanticToken_mapsDefaultLibraryFunctionAsBuiltIn() {
		assertSame(
		    BoxLangTextAttributes.BUILTIN_FUNCTION,
		    BoxLangLspAnnotator.mapSemanticToken( "function", Set.of( "defaultLibrary" ) )
		);
	}

	public void testMapSemanticToken_mapsMethodInvocationAsFunctionCall() {
		assertSame(
		    BoxLangTextAttributes.METHOD_CALL,
		    BoxLangLspAnnotator.mapSemanticToken( "method", Set.of() )
		);
	}

	public void testMapSemanticToken_mapsDefaultLibraryMethodAsMemberFunction() {
		assertSame(
		    BoxLangTextAttributes.MEMBER_FUNCTION,
		    BoxLangLspAnnotator.mapSemanticToken( "method", Set.of( "defaultLibrary" ) )
		);
	}

	public void testMapSemanticToken_mapsMethodDeclarationAsFunctionName() {
		assertSame(
		    BoxLangTextAttributes.FUNCTION_NAME,
		    BoxLangLspAnnotator.mapSemanticToken( "method", Set.of( "declaration" ) )
		);
	}

	public void testResolveSemanticToken_mapsModifierFollowedByColonAsStructKey() {
		String	source	= "{ required: true }";
		int		start	= source.indexOf( "required" );
		int		end		= start + "required".length();

		assertSame(
		    BoxLangTextAttributes.STRUCT_KEY,
		    BoxLangLspAnnotator.resolveSemanticToken(
		        "modifier",
		        Set.of(),
		        source,
		        start,
		        end
		    )
		);
	}

	public void testResolveSemanticToken_keepsModifierWithoutColonAsStorageModifier() {
		String	source	= "public function test() {}";
		int		start	= source.indexOf( "public" );
		int		end		= start + "public".length();

		assertSame(
		    BoxLangTextAttributes.STORAGE_MODIFIER,
		    BoxLangLspAnnotator.resolveSemanticToken(
		        "modifier",
		        Set.of(),
		        source,
		        start,
		        end
		    )
		);
	}

	public void testDecodeTokenModifiers_decodesLegendBits() {
		Set<String> modifiers = BoxLangLspAnnotator.decodeTokenModifiers(
		    0b101,
		    List.of( "declaration", "defaultLibrary", "definition" )
		);
		assertEquals( Set.of( "declaration", "definition" ), modifiers );
	}
}
