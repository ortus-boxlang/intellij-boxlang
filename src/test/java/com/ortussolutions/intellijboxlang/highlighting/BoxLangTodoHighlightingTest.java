package com.ortussolutions.intellijboxlang.highlighting;

import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.search.PsiTodoSearchHelper;
import com.intellij.psi.search.TodoAttributes;
import com.intellij.psi.search.TodoPattern;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.awt.Color;
import java.awt.Font;

public class BoxLangTodoHighlightingTest extends BasePlatformTestCase {

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		// Exercise IntelliJ's native TODO pass without starting an external LSP.
		com.intellij.testFramework.ExtensionTestUtil.maskExtensions(
		    com.intellij.openapi.extensions.ExtensionPointName.create( "com.intellij.annotator" ),
		    java.util.List.of(), getTestRootDisposable() );
		com.intellij.lang.LanguageAnnotators.INSTANCE.clearCache( com.ortussolutions.intellijboxlang.BoxLangLanguage.INSTANCE );
	}

	@Override
	protected void tearDown() throws Exception {
		try {
			super.tearDown();
		} finally {
			com.intellij.lang.LanguageAnnotators.INSTANCE.clearCache( com.ortussolutions.intellijboxlang.BoxLangLanguage.INSTANCE );
		}
	}

	public void testCustomTodoColorInCfmlTemplateComments() {
		assertCustomTodoColor( "example.cfm", "<!--- HIGH: fix this --->\n<cfset message = 'HIGH: not a comment'>" );
	}

	public void testCustomTodoColorInCfmlLineComments() {
		assertCustomTodoColor( "example.cfc", "component {\n// HIGH: fix this\nvalue = 'HIGH: not a comment';\n}" );
	}

	public void testCustomTodoColorInCfmlBlockComments() {
		assertCustomTodoColor( "example.cfc", "component {\n/* HIGH: fix this */\nvalue = 'HIGH: not a comment';\n}" );
	}

	public void testCustomTodoColorInCfmlDocumentationComments() {
		assertCustomTodoColor( "example.cfc", "/**\n * HIGH: fix this\n */\ncomponent { value = 'HIGH: not a comment'; }" );
	}

	public void testCustomTodoColorInBoxLangComments() {
		assertCustomTodoColor( "example.bx", "// HIGH: fix this\nvalue = 'HIGH: not a comment';" );
	}

	private void assertCustomTodoColor( String filename, String source ) {
		TodoConfiguration	config		= TodoConfiguration.getInstance();
		TodoPattern[]		original	= config.getTodoPatterns();
		TextAttributes		colors		= new TextAttributes( Color.MAGENTA, Color.YELLOW, null, null, Font.BOLD );
		TodoAttributes		attributes	= new TodoAttributes( colors );
		attributes.setUseCustomTodoColor( true, colors );
		try {
			config.setTodoPatterns( new TodoPattern[] { new TodoPattern( "\\bHIGH:.*", attributes, true ) } );
			myFixture.configureByText( filename, source );
			var todos = PsiTodoSearchHelper.getInstance( getProject() ).findTodoItems( myFixture.getFile() );
			assertEquals( 1, todos.length );
			assertEquals( colors, todos[ 0 ].getPattern().getAttributes().getTextAttributes() );
			var highlights = myFixture.doHighlighting();
			assertTrue( "Custom TODO colors must reach the editor",
			    highlights.stream().anyMatch( info -> info.getStartOffset() == todos[ 0 ].getTextRange().getStartOffset()
			        && colors.equals( info.getTextAttributes( myFixture.getFile(), myFixture.getEditor().getColorsScheme() ) ) ) );
		} finally {
			config.setTodoPatterns( original );
		}
	}
}
