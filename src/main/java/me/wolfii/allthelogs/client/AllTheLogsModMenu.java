package me.wolfii.allthelogs.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.wolfii.allthelogs.client.ui.screen.LogBrowserScreen;

/**
 * Opens the log browser from Mod Menu's configure button.
 */
public final class AllTheLogsModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return LogBrowserScreen::new;
    }
}
