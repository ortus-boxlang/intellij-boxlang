package com.ortussolutions.intellijboxlang.lsp;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.ortussolutions.intellijboxlang.highlighting.BoxLangTextAttributes;
import java.util.List;
import java.util.Set;
import junit.framework.TestCase;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolKind;

public class BoxLangLspAnnotatorTest extends TestCase {

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

	public void testMapSemanticToken_mapsVariableAsIdentifier() {
		assertSame(
		    BoxLangTextAttributes.IDENTIFIER,
		    BoxLangLspAnnotator.mapSemanticToken( "variable", Set.of() )
		);
	}

	public void testMapSemanticToken_keepsParameterSemanticColor() {
		assertSame(
		    DefaultLanguageHighlighterColors.PARAMETER,
		    BoxLangLspAnnotator.mapSemanticToken( "parameter", Set.of() )
		);
	}

	public void testMapSemanticToken_keepsPropertySemanticColor() {
		assertSame(
		    DefaultLanguageHighlighterColors.INSTANCE_FIELD,
		    BoxLangLspAnnotator.mapSemanticToken( "property", Set.of() )
		);
	}

	public void testCollectDeclaredProperties_readsNestedPropertyAndFieldSymbols() {
		DocumentSymbol root = new DocumentSymbol();
		root.setName( "QueryUtils" );
		root.setKind( SymbolKind.Class );

		DocumentSymbol property = new DocumentSymbol();
		property.setName( "convertEmptyStringsToNull" );
		property.setKind( SymbolKind.Property );

		DocumentSymbol field = new DocumentSymbol();
		field.setName( "log" );
		field.setKind( SymbolKind.Field );

		DocumentSymbol method = new DocumentSymbol();
		method.setName( "init" );
		method.setKind( SymbolKind.Method );

		root.setChildren( List.of( property, field, method ) );

		assertEquals(
		    Set.of( "convertemptystringstonull", "log" ),
		    BoxLangLspAnnotator.collectDeclaredProperties( List.of( root ) )
		);
	}

	public void testResolveSemanticToken_mapsDeclaredVariablesScopeMemberAsProperty() {
		String	source	= "variables.convertEmptyStringsToNull";
		int		start	= source.indexOf( "convertEmptyStringsToNull" );
		int		end		= start + "convertEmptyStringsToNull".length();

		assertSame(
		    DefaultLanguageHighlighterColors.INSTANCE_FIELD,
		    BoxLangLspAnnotator.resolveSemanticToken(
		        "variable",
		        Set.of(),
		        source,
		        start,
		        end,
		        Set.of( "convertemptystringstonull" )
		    )
		);
	}

	public void testResolveSemanticToken_doesNotMapUndeclaredVariablesScopeMemberAsProperty() {
		String	source	= "variables.someLocalValue";
		int		start	= source.indexOf( "someLocalValue" );
		int		end		= start + "someLocalValue".length();

		assertSame(
		    BoxLangTextAttributes.IDENTIFIER,
		    BoxLangLspAnnotator.resolveSemanticToken(
		        "variable",
		        Set.of(),
		        source,
		        start,
		        end,
		        Set.of( "convertemptystringstonull" )
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
