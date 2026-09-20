package me.wolfii.allthelogs.client.config;

import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.AbstractWidget;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.controllers.string.IStringController;
import dev.isxander.yacl3.gui.controllers.string.StringControllerElement;
import me.wolfii.allthelogs.client.AllTheLogsPaths;
import me.wolfii.allthelogs.client.files.NativeFilePicker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

/**
 * String path field with a folder-picker button, used for extra auto-import logs directories.
 */
public final class FolderPathController implements IStringController<String> {
    private static final int BUTTON = 20;

    private final Option<String> option;

    public FolderPathController(Option<String> option) {
        this.option = option;
    }

    @Override
    public Option<String> option() {
        return option;
    }

    @Override
    public String getString() {
        return option.pendingValue();
    }

    @Override
    public void setFromString(String value) {
        option.requestSet(value == null ? "" : value);
    }

    @Override
    public AbstractWidget provideWidget(YACLScreen screen, Dimension<Integer> widgetDimension) {
        return new FolderPathWidget(this, screen, widgetDimension);
    }

    private static final class FolderPathWidget extends AbstractWidget {
        private final FolderPathController controller;
        private final StringControllerElement field;

        private FolderPathWidget(FolderPathController controller, YACLScreen screen, Dimension<Integer> dim) {
            super(dim);
            this.controller = controller;
            this.field = new StringControllerElement(controller, screen, fieldDim(dim), true);
        }

        @Override
        public void setDimension(Dimension<Integer> dim) {
            super.setDimension(dim);
            field.setDimension(fieldDim(dim));
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            field.extractRenderState(graphics, mouseX, mouseY, delta);
            Dimension<Integer> button = buttonDim();
            boolean hovered = button.isPointInside(mouseX, mouseY);
            drawButtonRect(graphics, button.x(), button.y(), button.xLimit(), button.yLimit(), hovered, true);
            Component label = Component.translatable("allthelogs.settings.browse");
            int textX = button.x() + (button.width() - textRenderer.width(label)) / 2;
            int textY = button.y() + (button.height() - textRenderer.lineHeight) / 2;
            graphics.text(textRenderer, label, textX, textY, 0xFFE0E0E0, true);
            if (hovered) {
                graphics.setTooltipForNextFrame(Component.translatable("allthelogs.settings.browse.logs"),
                    mouseX, mouseY);
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() == 0 && buttonDim().isPointInside((int) event.x(), (int) event.y())) {
                playDownSound();
                Path current = ExtraImportDirectories.normalize(Path.of(controller.getString().isBlank()
                    ? AllTheLogsPaths.gameDirectory().resolve("logs").toString()
                    : controller.getString()));
                NativeFilePicker.pickFolder(current, picked ->
                    controller.setFromString(ExtraImportDirectories.toLogsFolder(picked).toString()));
                return true;
            }
            return field.mouseClicked(event, doubleClick);
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            return field.keyPressed(event);
        }

        @Override
        public boolean charTyped(CharacterEvent event) {
            return field.charTyped(event);
        }

        @Override
        public void setFocused(boolean focused) {
            field.setFocused(focused);
        }

        @Override
        public boolean isFocused() {
            return field.isFocused();
        }

        @Override
        public void unfocus() {
            field.unfocus();
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return getDimension().isPointInside((int) mouseX, (int) mouseY);
        }

        private Dimension<Integer> buttonDim() {
            Dimension<Integer> dim = getDimension();
            return Dimension.ofInt(dim.xLimit() - BUTTON, dim.y(), BUTTON, dim.height());
        }

        private static Dimension<Integer> fieldDim(Dimension<Integer> dim) {
            return dim.withWidth(Math.max(8, dim.width() - BUTTON - 2));
        }
    }
}
