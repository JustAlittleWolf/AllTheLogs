package me.wolfii.allthelogs.client.ui.screen;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.CheckboxComponent;
import io.wispforest.owo.ui.component.DiscreteSliderComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import me.wolfii.allthelogs.client.AllTheLogsPaths;
import me.wolfii.allthelogs.client.config.AllTheLogsConfig;
import me.wolfii.allthelogs.client.config.ExtraImportDirectories;
import me.wolfii.allthelogs.client.config.FilterPersistence;
import me.wolfii.allthelogs.client.files.NativeFilePicker;
import me.wolfii.allthelogs.client.ui.theme.OverflowScrollbar;
import me.wolfii.allthelogs.client.ui.text.WrappedTooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Mod settings: extra boot-import directories, filter persistence, message font size, and browser chrome.
 */
public final class SettingsScreen extends BaseOwoScreen<StackLayout> {
    private final Screen parent;
    private final AllTheLogsConfig config;
    private final Path instanceDir;
    private final List<String> extraDirectories = new ArrayList<>();
    private FlowLayout directoryList;
    private ButtonComponent persistenceButton;

    public SettingsScreen() {
        this(null);
    }

    public SettingsScreen(@Nullable Screen parent) {
        super(Component.translatable("allthelogs.screen.settings"));
        this.parent = parent;
        this.config = AllTheLogsConfig.get();
        this.instanceDir = AllTheLogsPaths.gameDirectory();
        this.extraDirectories.addAll(config.extraImportDirectories());
    }

    @Override
    protected @NotNull OwoUIAdapter<StackLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::stack);
    }

    @Override
    protected void build(StackLayout root) {
        FlowLayout content = UIContainers.verticalFlow(Sizing.fill(), Sizing.fill());
        content.gap(8);
        content.allowOverflow(true);
        content.surface(Surface.VANILLA_TRANSLUCENT)
            .padding(Insets.of(16))
            .horizontalAlignment(HorizontalAlignment.LEFT)
            .verticalAlignment(VerticalAlignment.TOP);

        FlowLayout form = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        form.gap(10);
        form.padding(Insets.right(12));

        form.child(UIComponents.label(Component.translatable("allthelogs.screen.settings")));
        LabelComponent description = UIComponents.label(Component.translatable("allthelogs.settings.description"));
        description.color(Color.ofRgb(0xA0A0A0));
        description.maxWidth(Math.max(160, this.width - 64));
        form.child(description);

        form.child(sectionHeader("allthelogs.settings.import_directories"));
        LabelComponent directoryHint = UIComponents.label(Component.translatable("allthelogs.settings.import_directories.hint"));
        directoryHint.color(Color.ofRgb(0xA0A0A0));
        directoryHint.maxWidth(Math.max(160, this.width - 64));
        form.child(directoryHint);

        directoryList = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        directoryList.gap(4);
        form.child(directoryList);
        refreshDirectories();

        form.child(paddedButton(Component.translatable("allthelogs.settings.add_directory"),
            button -> NativeFilePicker.pickFolder(instanceDir, this::addDirectory)));

        form.child(sectionHeader("allthelogs.settings.browser"));

        FlowLayout persistenceRow = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        persistenceRow.gap(8).verticalAlignment(VerticalAlignment.CENTER);
        persistenceRow.child(UIComponents.label(Component.translatable("allthelogs.settings.filter_persistence"))
            .horizontalSizing(Sizing.expand()));
        persistenceButton = paddedButton(persistenceLabel(), button -> cyclePersistence());
        persistenceButton.tooltip(WrappedTooltip.of(
            Component.translatable("allthelogs.settings.filter_persistence.hint")));
        persistenceRow.child(persistenceButton);
        form.child(persistenceRow);

        CheckboxComponent hideImport = UIComponents.checkbox(
            Component.translatable("allthelogs.settings.hide_import_button"));
        hideImport.checked(config.hideImportButton());
        hideImport.onChanged(value -> {
            config.setHideImportButton(value);
            config.save();
        });
        hideImport.tooltip(WrappedTooltip.of(Component.translatable("allthelogs.settings.hide_import_button.hint")));
        form.child(hideImport);

        form.child(fontSlider());

        ScrollContainer<FlowLayout> scroll = UIContainers.verticalScroll(
            Sizing.fill(), Sizing.expand(), form);
        scroll.scrollbar(OverflowScrollbar.vanillaFlat());
        scroll.padding(Insets.right(6));
        content.child(scroll);

        FlowLayout actions = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        actions.horizontalAlignment(HorizontalAlignment.RIGHT);
        actions.child(paddedButton(Component.translatable("allthelogs.done"), button -> onClose()));
        content.child(actions);
        root.child(content);
    }

    @Override
    public void onClose() {
        saveDirectories();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    private LabelComponent sectionHeader(String key) {
        LabelComponent label = UIComponents.label(Component.translatable(key));
        return label;
    }

    private FlowLayout fontSlider() {
        FlowLayout row = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        row.gap(2);
        LabelComponent label = UIComponents.label(Component.translatable("allthelogs.settings.message_font_size"));
        label.tooltip(WrappedTooltip.of(Component.translatable("allthelogs.settings.message_font_size.hint")));
        row.child(label);
        DiscreteSliderComponent slider = UIComponents.discreteSlider(Sizing.fill(),
            AllTheLogsConfig.MIN_MESSAGE_FONT_SIZE, AllTheLogsConfig.MAX_MESSAGE_FONT_SIZE);
        slider.decimalPlaces(0);
        slider.snap(true);
        slider.setFromDiscreteValue(config.messageFontSize());
        slider.message(value -> Component.translatable("allthelogs.settings.message_font_size.value", value));
        slider.onChanged().subscribe(value -> {
            config.setMessageFontSize((int) Math.round(value));
            config.save();
        });
        row.child(slider);
        return row;
    }

    private void cyclePersistence() {
        config.setFilterPersistence(config.filterPersistence().next());
        config.save();
        if (persistenceButton != null) persistenceButton.setMessage(persistenceLabel());
    }

    private Component persistenceLabel() {
        FilterPersistence mode = config.filterPersistence();
        return Component.translatable("allthelogs.settings.filter_persistence."
            + mode.name().toLowerCase(Locale.ROOT));
    }

    private void addDirectory(Path chosen) {
        if (ExtraImportDirectories.isInstanceDirectory(chosen, instanceDir)) return;
        List<String> next = new ArrayList<>(extraDirectories);
        next.add(chosen.toString());
        extraDirectories.clear();
        extraDirectories.addAll(ExtraImportDirectories.persisted(next, instanceDir));
        saveDirectories();
        refreshDirectories();
    }

    private void removeDirectory(String directory) {
        extraDirectories.remove(directory);
        saveDirectories();
        refreshDirectories();
    }

    private void saveDirectories() {
        config.setExtraImportDirectories(extraDirectories, instanceDir);
        config.save();
        extraDirectories.clear();
        extraDirectories.addAll(config.extraImportDirectories());
    }

    private void refreshDirectories() {
        if (directoryList == null) return;
        for (var child : List.copyOf(directoryList.children())) {
            directoryList.removeChild(child);
        }
        directoryList.child(directoryRow(instanceDir.toString(), true));
        for (String directory : extraDirectories) {
            directoryList.child(directoryRow(directory, false));
        }
    }

    private FlowLayout directoryRow(String path, boolean locked) {
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        row.gap(8).verticalAlignment(VerticalAlignment.CENTER);
        LabelComponent label = UIComponents.label(Component.literal(path));
        label.horizontalSizing(Sizing.expand());
        if (locked) {
            label.tooltip(WrappedTooltip.of(Component.translatable("allthelogs.settings.instance_directory")));
        }
        row.child(label);
        if (locked) {
            LabelComponent badge = UIComponents.label(Component.translatable("allthelogs.settings.instance_directory"));
            badge.color(Color.ofRgb(0xA0A0A0));
            row.child(badge);
        } else {
            row.child(paddedButton(Component.translatable("allthelogs.settings.remove_directory"),
                button -> removeDirectory(path)));
        }
        return row;
    }

    private ButtonComponent paddedButton(Component label, Consumer<ButtonComponent> onPress) {
        ButtonComponent button = UIComponents.button(label, onPress);
        button.horizontalSizing(Sizing.fixed(Math.max(40, this.font.width(label) + 32)));
        return button;
    }
}
