package me.wolfii.allthelogs.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.wolfii.allthelogs.client.config.AllTheLogsConfig;

/**
 * Opens the YACL settings screen from Mod Menu's configure button.
 */
public final class AllTheLogsModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return AllTheLogsConfig::createScreen;
    }
}
