package me.wolfii.allthelogs.client.ui.screen;

import io.wispforest.owo.ui.component.BoxComponent;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.util.NinePatchTexture;
import me.wolfii.allthelogs.client.export.MessageExport;
import me.wolfii.allthelogs.client.ui.theme.PanelSurfaces;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * Menu on the messages toolbar. Hovering or clicking the hamburger opens Export, Import, and Scripts
 * above it, top to bottom, with the database summary under those actions. A click keeps the menu open
 * so Import and Scripts can be chosen. Export opens to the side, then the chosen scope opens the file
 * format beside that. The scope row stays highlighted while its format menu is open.
 */
final class BrowserToolsMenu {
    private static final int CLOSE_DELAY_MS = 160;
    private static final int GAP = 2;

    enum Scope {
        SELECTION, VISIBLE, QUERY
    }

    private final StackLayout overlays;
    private final IntSupplier screenWidth;
    private final IntSupplier screenHeight;
    private final BooleanSupplier hasSelection;
    private final BooleanSupplier suppressed;
    private final Runnable onOpened;
    private final Runnable openScripts;
    private final Runnable openImport;
    private final BiConsumer<Scope, MessageExport.Format> export;

    private ButtonComponent anchor;
    private FlowLayout actions;
    private FlowLayout databaseInfo;
    private List<Component> databaseLines = List.of(Component.translatable("allthelogs.meta.loading"));
    private int actionWidth;
    private ButtonComponent exportButton;
    private FlowLayout scopeMenu;
    private ButtonComponent selectionButton;
    private ButtonComponent onScreenButton;
    private ButtonComponent queryButton;
    private boolean scopeIncludesSelection;
    private FlowLayout formatMenu;
    private Scope formatScope;
    private boolean pinned;
    private long closeAt = Long.MAX_VALUE;
    private int placedActionsX = Integer.MIN_VALUE;
    private int placedActionsY = Integer.MIN_VALUE;
    private int placedScopeX = Integer.MIN_VALUE;
    private int placedScopeY = Integer.MIN_VALUE;
    private int placedFormatX = Integer.MIN_VALUE;
    private int placedFormatY = Integer.MIN_VALUE;

    BrowserToolsMenu(StackLayout overlays, IntSupplier screenWidth, IntSupplier screenHeight,
                     BooleanSupplier hasSelection, BooleanSupplier suppressed, Runnable onOpened,
                     Runnable openScripts, Runnable openImport,
                     BiConsumer<Scope, MessageExport.Format> export) {
        this.overlays = overlays;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.hasSelection = hasSelection;
        this.suppressed = suppressed;
        this.onOpened = onOpened;
        this.openScripts = openScripts;
        this.openImport = openImport;
        this.export = export;
    }

    /**
     * Square button at the right end of the messages toolbar. Menus grow up and to the left,
     * into the screen, instead of off the right edge.
     */
    ButtonComponent button() {
        if (anchor != null) return anchor;
        anchor = UIComponents.button(Component.empty(), pressed -> toggle());
        anchor.horizontalSizing(Sizing.fixed(20));
        anchor.renderer((graphics, widget, delta) -> {
            ButtonComponent.Renderer.VANILLA.draw(graphics, widget, delta);
            int bar = 10;
            int left = widget.x() + (widget.width() - bar) / 2;
            int right = left + bar;
            int mid = widget.y() + widget.height() / 2;
            int color = 0xFFE8E8E8;
            graphics.fill(left, mid - 4, right, mid - 2, color);
            graphics.fill(left, mid - 1, right, mid + 1, color);
            graphics.fill(left, mid + 2, right, mid + 4, color);
        });
        return anchor;
    }

    boolean isOpen() {
        return actions != null;
    }

    /**
     * Opens the menu and keeps it open after the pointer leaves, until it is clicked again or closed.
     */
    void toggle() {
        if (pinned) {
            close();
            return;
        }
        pinned = true;
        closeAt = Long.MAX_VALUE;
        ensureActions();
    }

    void close() {
        pinned = false;
        closeAt = Long.MAX_VALUE;
        closeFormat();
        closeScope();
        remove(actions);
        actions = null;
        databaseInfo = null;
        exportButton = null;
        placedActionsX = Integer.MIN_VALUE;
        placedActionsY = Integer.MIN_VALUE;
    }

    void sync(int mouseX, int mouseY) {
        if (suppressed.getAsBoolean()) {
            close();
            return;
        }
        if (!inside(mouseX, mouseY) && !pinned) {
            if (!isOpen()) return;
            if (closeAt == Long.MAX_VALUE) closeAt = System.currentTimeMillis() + CLOSE_DELAY_MS;
            if (System.currentTimeMillis() >= closeAt) close();
            return;
        }
        closeAt = Long.MAX_VALUE;
        boolean wasOpen = isOpen();
        ensureActions();
        if (exportHover(mouseX, mouseY)) ensureScope();
        else closeScope();
        Scope hovered = scopeAt(mouseX, mouseY);
        if (hovered != null) {
            ensureFormat(hovered);
        } else if (!hit(formatMenu, mouseX, mouseY) && !horizontalGap(formatMenu, scopeButton(formatScope), mouseX, mouseY)) {
            closeFormat();
        }
        if (!wasOpen && isOpen()) onOpened.run();
    }

    private boolean inside(int mouseX, int mouseY) {
        return hit(anchor, mouseX, mouseY)
            || hit(actions, mouseX, mouseY)
            || verticalGap(actions, anchor, mouseX, mouseY)
            || hit(scopeMenu, mouseX, mouseY)
            || horizontalGap(scopeMenu, exportButton, mouseX, mouseY)
            || hit(formatMenu, mouseX, mouseY)
            || horizontalGap(formatMenu, scopeButton(formatScope), mouseX, mouseY);
    }

    private boolean exportHover(int mouseX, int mouseY) {
        return hit(exportButton, mouseX, mouseY)
            || hit(scopeMenu, mouseX, mouseY)
            || horizontalGap(scopeMenu, exportButton, mouseX, mouseY)
            || hit(formatMenu, mouseX, mouseY)
            || horizontalGap(formatMenu, scopeButton(formatScope), mouseX, mouseY);
    }

    private void ensureActions() {
        if (actions != null) {
            placeActions();
            return;
        }
        actionWidth = labelWidth("allthelogs.menu.scripts", "allthelogs.menu.import", "allthelogs.menu.export");
        FlowLayout menu = panel(panelWidth());
        exportButton = action(Component.translatable("allthelogs.menu.export"), this::ensureScope);
        menu.child(exportButton);
        menu.child(action(Component.translatable("allthelogs.menu.import"), openImport));
        menu.child(action(Component.translatable("allthelogs.menu.scripts"), openScripts));
        databaseInfo = databaseBlock();
        menu.child(databaseInfo);
        actions = menu;
        overlays.child(menu);
        placeActions();
    }

    private void ensureScope() {
        boolean includeSelection = hasSelection.getAsBoolean();
        if (scopeMenu != null && scopeIncludesSelection == includeSelection) {
            placeScope();
            return;
        }
        closeScope();
        scopeIncludesSelection = includeSelection;
        int width = labelWidth("allthelogs.export.selection", "allthelogs.export.visible", "allthelogs.export.query");
        FlowLayout menu = panel(width);
        if (includeSelection) {
            selectionButton = action(Component.translatable("allthelogs.export.selection"),
                () -> ensureFormat(Scope.SELECTION));
            menu.child(selectionButton);
        }
        onScreenButton = action(Component.translatable("allthelogs.export.visible"),
            () -> ensureFormat(Scope.VISIBLE));
        queryButton = action(Component.translatable("allthelogs.export.query"),
            () -> ensureFormat(Scope.QUERY));
        menu.child(onScreenButton);
        menu.child(queryButton);
        scopeMenu = menu;
        overlays.child(menu);
        placeScope();
    }

    private void ensureFormat(Scope scope) {
        if (formatMenu != null && formatScope == scope) {
            placeFormat();
            markSelectedScope();
            return;
        }
        closeFormat();
        formatScope = scope;
        int width = labelWidth("allthelogs.export.text", "allthelogs.export.json", "allthelogs.export.csv");
        FlowLayout menu = panel(width);
        menu.child(action(Component.translatable("allthelogs.export.text"), () -> export.accept(scope, MessageExport.Format.TEXT)));
        menu.child(action(Component.translatable("allthelogs.export.json"), () -> export.accept(scope, MessageExport.Format.JSON)));
        menu.child(action(Component.translatable("allthelogs.export.csv"), () -> export.accept(scope, MessageExport.Format.CSV)));
        formatMenu = menu;
        overlays.child(menu);
        placeFormat();
        markSelectedScope();
    }

    private void closeScope() {
        closeFormat();
        remove(scopeMenu);
        scopeMenu = null;
        selectionButton = null;
        onScreenButton = null;
        queryButton = null;
        placedScopeX = Integer.MIN_VALUE;
        placedScopeY = Integer.MIN_VALUE;
    }

    private void closeFormat() {
        remove(formatMenu);
        formatMenu = null;
        formatScope = null;
        placedFormatX = Integer.MIN_VALUE;
        placedFormatY = Integer.MIN_VALUE;
        markSelectedScope();
    }

    /**
     * Keeps the scope whose format menu is open drawn in the hovered style, so moving onto Text, JSON,
     * or CSV does not make that choice look unselected.
     */
    private void markSelectedScope() {
        markScope(selectionButton, formatScope == Scope.SELECTION);
        markScope(onScreenButton, formatScope == Scope.VISIBLE);
        markScope(queryButton, formatScope == Scope.QUERY);
    }

    private static void markScope(ButtonComponent button, boolean selected) {
        if (button == null) return;
        button.renderer((graphics, widget, delta) -> {
            Identifier texture = !widget.active()
                ? ButtonComponent.DISABLED_TEXTURE
                : selected || widget.isHovered()
                    ? ButtonComponent.HOVERED_TEXTURE
                    : ButtonComponent.ACTIVE_TEXTURE;
            NinePatchTexture.draw(texture, graphics, widget.getX(), widget.getY(), widget.width(), widget.height());
        });
    }

    private void placeActions() {
        if (actions == null || anchor == null || actions.width() <= 0 || actions.height() <= 0) return;
        int x = Math.max(4, anchor.x() + anchor.width() - actions.width());
        int y = Math.max(4, anchor.y() - actions.height() - GAP);
        if (placedActionsX == x && placedActionsY == y) return;
        actions.positioning(Positioning.absolute(x, y));
        placedActionsX = x;
        placedActionsY = y;
    }

    private void placeScope() {
        if (scopeMenu == null || exportButton == null) return;
        int[] at = beside(scopeMenu, exportButton);
        if (at == null || (placedScopeX == at[0] && placedScopeY == at[1])) return;
        scopeMenu.positioning(Positioning.absolute(at[0], at[1]));
        placedScopeX = at[0];
        placedScopeY = at[1];
    }

    private void placeFormat() {
        ButtonComponent row = scopeButton(formatScope);
        if (formatMenu == null || row == null) return;
        int[] at = beside(formatMenu, row);
        if (at == null || (placedFormatX == at[0] && placedFormatY == at[1])) return;
        formatMenu.positioning(Positioning.absolute(at[0], at[1]));
        placedFormatX = at[0];
        placedFormatY = at[1];
    }

    /**
     * Replaces the database summary under the actions. Safe to call before the menu is open.
     */
    void setDatabaseInfo(List<Component> lines) {
        List<Component> next = lines == null || lines.isEmpty()
            ? List.of(Component.translatable("allthelogs.meta.unavailable"))
            : List.copyOf(lines);
        if (sameLines(databaseLines, next)) return;
        databaseLines = next;
        if (actions == null) return;
        fillDatabaseInfo();
        actions.horizontalSizing(Sizing.fixed(panelWidth()));
        placedActionsX = Integer.MIN_VALUE;
        placedActionsY = Integer.MIN_VALUE;
        placeActions();
        placedScopeX = Integer.MIN_VALUE;
        placedScopeY = Integer.MIN_VALUE;
        placeScope();
        placedFormatX = Integer.MIN_VALUE;
        placedFormatY = Integer.MIN_VALUE;
        placeFormat();
    }

    private FlowLayout databaseBlock() {
        FlowLayout block = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        block.gap(1).padding(Insets.top(4));
        block.mouseDown().subscribe((mouse, doubled) -> true);
        fillDatabaseInfo(block);
        return block;
    }

    private void fillDatabaseInfo() {
        fillDatabaseInfo(databaseInfo);
    }

    private void fillDatabaseInfo(FlowLayout block) {
        if (block == null) return;
        block.clearChildren();
        BoxComponent rule = UIComponents.box(Sizing.fill(), Sizing.fixed(1));
        rule.fill(true).color(Color.ofRgb(0x3C3C3C));
        block.child(rule);
        for (Component line : databaseLines) {
            LabelComponent label = UIComponents.label(line);
            label.shadow(true);
            block.child(label);
        }
    }

    private int panelWidth() {
        Font font = Minecraft.getInstance().font;
        int text = 0;
        for (Component line : databaseLines) {
            text = Math.max(text, font.width(line));
        }
        return Math.max(actionWidth, text + 8);
    }

    private static boolean sameLines(List<Component> left, List<Component> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            if (!left.get(i).getString().equals(right.get(i).getString())) return false;
        }
        return true;
    }

    /**
     * Prefers the left side of {@code row} so a menu on the right edge stays on screen.
     * The menu's top padding is removed from {@code y} so its first row lines up with {@code row}.
     */
    private int[] beside(FlowLayout menu, UIComponent row) {
        if (menu.width() <= 0 || menu.height() <= 0) return null;
        int left = row.x() - menu.width() - GAP;
        int right = row.x() + row.width() + GAP;
        int x = left >= 4 ? left : right;
        if (x + menu.width() > screenWidth.getAsInt() - 4) x = Math.max(4, left);
        int y = submenuTop(row.y(), menu.padding().get().top(), menu.height(), screenHeight.getAsInt());
        return new int[]{x, y};
    }

    /**
     * Top of a side menu whose first row should meet {@code rowY}. {@code paddingTop} is that
     * menu's own top padding; leaving it in place drops the row by that many pixels.
     */
    static int submenuTop(int rowY, int paddingTop, int menuHeight, int screenHeight) {
        int y = rowY - paddingTop;
        int limit = screenHeight - 4;
        if (y + menuHeight > limit) y = Math.max(4, limit - menuHeight);
        return y;
    }

    private ButtonComponent scopeButton(Scope scope) {
        if (scope == null) return null;
        return switch (scope) {
            case SELECTION -> selectionButton;
            case VISIBLE -> onScreenButton;
            case QUERY -> queryButton;
        };
    }

    private Scope scopeAt(int mouseX, int mouseY) {
        if (hit(selectionButton, mouseX, mouseY)) return Scope.SELECTION;
        if (hit(onScreenButton, mouseX, mouseY)) return Scope.VISIBLE;
        if (hit(queryButton, mouseX, mouseY)) return Scope.QUERY;
        return null;
    }

    private ButtonComponent action(Component label, Runnable onPress) {
        ButtonComponent button = UIComponents.button(label, pressed -> onPress.run());
        button.horizontalSizing(Sizing.fill());
        return button;
    }

    private FlowLayout panel(int width) {
        FlowLayout panel = UIContainers.verticalFlow(Sizing.fixed(width), Sizing.content());
        panel.gap(GAP).padding(Insets.of(3)).surface(PanelSurfaces.menu());
        return panel;
    }

    private int labelWidth(String... keys) {
        Font font = Minecraft.getInstance().font;
        int width = 0;
        for (String key : keys) {
            width = Math.max(width, font.width(Component.translatable(key)));
        }
        return width + 28;
    }

    private void remove(UIComponent component) {
        if (component != null && overlays.children().contains(component)) {
            overlays.removeChild(component);
        }
    }

    private static boolean hit(UIComponent component, int mouseX, int mouseY) {
        if (component == null || component.width() <= 0 || component.height() <= 0) return false;
        return mouseX >= component.x() && mouseX < component.x() + component.width()
            && mouseY >= component.y() && mouseY < component.y() + component.height();
    }

    private static boolean verticalGap(UIComponent upper, UIComponent lower, int mouseX, int mouseY) {
        if (upper == null || lower == null || upper.height() <= 0 || lower.height() <= 0) return false;
        int top = upper.y() + upper.height();
        int bottom = lower.y();
        if (bottom <= top) return false;
        int left = Math.min(upper.x(), lower.x());
        int right = Math.max(upper.x() + upper.width(), lower.x() + lower.width());
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    private static boolean horizontalGap(UIComponent side, UIComponent row, int mouseX, int mouseY) {
        if (side == null || row == null || side.width() <= 0 || row.height() <= 0) return false;
        int left;
        int right;
        if (side.x() + side.width() <= row.x()) {
            left = side.x() + side.width();
            right = row.x();
        } else if (row.x() + row.width() <= side.x()) {
            left = row.x() + row.width();
            right = side.x();
        } else {
            return false;
        }
        if (right <= left) return false;
        return mouseX >= left && mouseX < right && mouseY >= row.y() && mouseY < row.y() + row.height();
    }
}
