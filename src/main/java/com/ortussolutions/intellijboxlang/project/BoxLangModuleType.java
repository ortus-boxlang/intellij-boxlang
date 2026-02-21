package com.ortussolutions.intellijboxlang.project;

import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.ortussolutions.intellijboxlang.BoxLangIcons;
import javax.swing.Icon;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Module type for BoxLang projects in IntelliJ IDEA.
 */
public final class BoxLangModuleType extends ModuleType<BoxLangModuleBuilder> {

	public static final String ID = "BOXLANG_MODULE";

	public BoxLangModuleType() {
		super( ID );
	}

	public static BoxLangModuleType getInstance() {
		return ( BoxLangModuleType ) ModuleTypeManager.getInstance().findByID( ID );
	}

	@Override
	public @NotNull BoxLangModuleBuilder createModuleBuilder() {
		return new BoxLangModuleBuilder();
	}

	@Override
	public @NotNull @Nls( capitalization = Nls.Capitalization.Title ) String getName() {
		return "BoxLang";
	}

	@Override
	public @NotNull @Nls( capitalization = Nls.Capitalization.Sentence ) String getDescription() {
		return "Create a new BoxLang project";
	}

	@Override
	public @NotNull Icon getNodeIcon( boolean isOpened ) {
		return BoxLangIcons.FILE;
	}
}
