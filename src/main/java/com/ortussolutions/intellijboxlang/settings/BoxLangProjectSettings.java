package com.ortussolutions.intellijboxlang.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.PROJECT)
@State(
    name = "BoxLangProjectSettings",
    storages = @Storage("boxlang.xml")
)
public final class BoxLangProjectSettings implements PersistentStateComponent<BoxLangProjectSettingsState> {
    private final BoxLangProjectSettingsState state = new BoxLangProjectSettingsState();

    public static BoxLangProjectSettings getInstance(Project project) {
        return project.getService(BoxLangProjectSettings.class);
    }

    @Override
    public @Nullable BoxLangProjectSettingsState getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull BoxLangProjectSettingsState state) {
        XmlSerializerUtil.copyBean(state, this.state);
    }

    public BoxLangProjectSettingsState getSettings() {
        return state;
    }
}
