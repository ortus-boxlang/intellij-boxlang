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
component {
    function greet(name) {
        // Say hello
        if (name == "world") {
            return "hello";
        }

        return "hi, #name#";
    }
}

<!--- template comment --->
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
        new AttributesDescriptor("Keyword", BoxLangTextAttributes.KEYWORD),
        new AttributesDescriptor("Identifier", BoxLangTextAttributes.IDENTIFIER),
        new AttributesDescriptor("Number", BoxLangTextAttributes.NUMBER),
        new AttributesDescriptor("String", BoxLangTextAttributes.STRING),
        new AttributesDescriptor("Line comment", BoxLangTextAttributes.LINE_COMMENT),
        new AttributesDescriptor("Block comment", BoxLangTextAttributes.BLOCK_COMMENT),
        new AttributesDescriptor("Operator", BoxLangTextAttributes.OPERATOR),
        new AttributesDescriptor("Brace", BoxLangTextAttributes.BRACE),
        new AttributesDescriptor("Parenthesis", BoxLangTextAttributes.PAREN),
        new AttributesDescriptor("Bracket", BoxLangTextAttributes.BRACKET),
        new AttributesDescriptor("Comma", BoxLangTextAttributes.COMMA),
        new AttributesDescriptor("Dot", BoxLangTextAttributes.DOT),
        new AttributesDescriptor("Semicolon", BoxLangTextAttributes.SEMICOLON),
        new AttributesDescriptor("Bad character", BoxLangTextAttributes.BAD_CHARACTER)
    };
}
