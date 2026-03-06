package com.ortussolutions.intellijboxlang.highlighting;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.ortussolutions.intellijboxlang.BoxLangIcons;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import javax.swing.Icon;

public final class BoxLangColorSettingsPage implements ColorSettingsPage {

	@Override
	@NotNull
	public String getDisplayName() {
		return "BoxLang";
	}

	@Override
	public Icon getIcon() {
		return BoxLangIcons.FILE;
	}

	@Override
	@NotNull
	public SyntaxHighlighter getHighlighter() {
		return new BoxLangSyntaxHighlighter();
	}

	@Override
	@NotNull
	public String getDemoText() {
		return """
		       import java.util.List;

		       /**
		        * A sample BoxLang class
		        * @author BoxLang
		        */
		       @output false
		       class extends BaseClass {

		           property string name;

		           public static function greet(required string name, numeric age = 0) {
		               // Say hello
		               var greeting = "Hello, #name#!";
		               var items = arrayNew(1);
		               var count = len(greeting);

		               if (name == "world" and age > 0) {
		                   return greeting;
		               }

		               for (var item in items) {
		                   arrayAppend(items, item);
		               }

		               var result = isNull(name) ? "nobody" : name;
		               var valid = true;
		               var empty = null;

		               variables.data = {
		                   key: "value",
		                   num: 42,
		                   hex: 0xFF,
		                   pi: 3.14
		               };

		               describe(
		                   title = "A spec",
		                   labels = "unit",
		                   body = function(){
		                       beforeEach( function(){
		                           local.setup = true;
		                       } );
		                   }
		               );

		               return "hi, #name# - #result#";
		           }

		           private function helper() {
		               var range = 1..10;
		               var cb = (x, y) => x + y;
		               request.total = cb(1, 2);
		           }
		       }

		       <!--- template comment --->
		       <bx:output>#variables.data.key#</bx:output>
		       """.stripIndent();
	}

	@Override
	public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
		return Map.of();
	}

	@Override
	@NotNull
	public AttributesDescriptor[] getAttributeDescriptors() {
		return DESCRIPTORS;
	}

	@Override
	@NotNull
	public ColorDescriptor[] getColorDescriptors() {
		return ColorDescriptor.EMPTY_ARRAY;
	}

	private static final AttributesDescriptor[] DESCRIPTORS = new AttributesDescriptor[] {
	    new AttributesDescriptor( "Keyword", BoxLangTextAttributes.KEYWORD ),
	    new AttributesDescriptor( "Storage type (class, function, var, ...)", BoxLangTextAttributes.STORAGE_TYPE ),
	    new AttributesDescriptor( "Storage modifier (public, static, ...)", BoxLangTextAttributes.STORAGE_MODIFIER ),
	    new AttributesDescriptor( "Constant (true, false, null)", BoxLangTextAttributes.CONSTANT ),
	    new AttributesDescriptor( "Scope variable (variables, request, this, ...)", BoxLangTextAttributes.SCOPE_VARIABLE ),
	    new AttributesDescriptor( "Built-in function", BoxLangTextAttributes.BUILTIN_FUNCTION ),
	    new AttributesDescriptor( "Function name (declaration)", BoxLangTextAttributes.FUNCTION_NAME ),
	    new AttributesDescriptor( "Struct/map key", BoxLangTextAttributes.STRUCT_KEY ),
	    new AttributesDescriptor( "Function call", BoxLangTextAttributes.FUNCTION_CALL ),
	    new AttributesDescriptor( "Named argument", BoxLangTextAttributes.NAMED_ARGUMENT ),
	    new AttributesDescriptor( "Annotation (@...)", BoxLangTextAttributes.ANNOTATION ),
	    new AttributesDescriptor( "Identifier", BoxLangTextAttributes.IDENTIFIER ),
	    new AttributesDescriptor( "Number", BoxLangTextAttributes.NUMBER ),
	    new AttributesDescriptor( "String", BoxLangTextAttributes.STRING ),
	    new AttributesDescriptor( "Interpolation delimiter (#)", BoxLangTextAttributes.HASH_SIGN ),
	    new AttributesDescriptor( "Line comment", BoxLangTextAttributes.LINE_COMMENT ),
	    new AttributesDescriptor( "Block comment", BoxLangTextAttributes.BLOCK_COMMENT ),
	    new AttributesDescriptor( "Documentation comment", BoxLangTextAttributes.DOC_COMMENT ),
	    new AttributesDescriptor( "Operator", BoxLangTextAttributes.OPERATOR ),
	    new AttributesDescriptor( "Tag (bx:...)", BoxLangTextAttributes.TAG ),
	    new AttributesDescriptor( "Brace", BoxLangTextAttributes.BRACE ),
	    new AttributesDescriptor( "Parenthesis", BoxLangTextAttributes.PAREN ),
	    new AttributesDescriptor( "Bracket", BoxLangTextAttributes.BRACKET ),
	    new AttributesDescriptor( "Comma", BoxLangTextAttributes.COMMA ),
	    new AttributesDescriptor( "Dot", BoxLangTextAttributes.DOT ),
	    new AttributesDescriptor( "Semicolon", BoxLangTextAttributes.SEMICOLON ),
	    new AttributesDescriptor( "Bad character", BoxLangTextAttributes.BAD_CHARACTER )
	};
}
