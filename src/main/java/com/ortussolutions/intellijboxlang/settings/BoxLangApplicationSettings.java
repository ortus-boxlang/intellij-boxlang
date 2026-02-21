package com.ortussolutions.intellijboxlang.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service( Service.Level.APP )
@State( name = "BoxLangApplicationSettings", storages = @Storage( "boxlang.xml" ) )
public final class BoxLangApplicationSettings implements PersistentStateComponent<BoxLangSettingsState> {

	private final BoxLangSettingsState state = new BoxLangSettingsState();

	public BoxLangApplicationSettings() {
		state.boxLangVersion	= "1.9.0";
		state.lspVersion		= "bx-lsp@1.5.0+6";
	}

	public static BoxLangApplicationSettings getInstance() {
		return ApplicationManager.getApplication().getService( BoxLangApplicationSettings.class );
	}

	@Override
	public @Nullable BoxLangSettingsState getState() {
		return state;
	}

	@Override
	public void loadState( @NotNull BoxLangSettingsState state ) {
		XmlSerializerUtil.copyBean( state, this.state );
	}

	public BoxLangSettingsState getSettings() {
		return state;
	}
}
