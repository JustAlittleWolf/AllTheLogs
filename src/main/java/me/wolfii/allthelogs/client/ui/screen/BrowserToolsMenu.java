package me.wolfii.allthelogs.client.ui.screen;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.container.UIContainers;
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
 * Menu on the messages toolbar. Hovering or clicking the hamburger opens Playtime, Import, and Advanced
 * above it, top to bottom, with a ? button under those actions. Hovering ? shows the database summary.
 * A click keeps the menu open. Advanced opens Export, Clean up, and Scripts to the side; Export opens the
 * export scope beside that, and the chosen scope opens the file format. A row whose side menu is open stays
 * highlighted.
 * <p>
 * Side menus grow upward from their row, so a menu at the bottom of the screen stays on it. Every menu stays
 * open for {@link #CLOSE_DELAY_MS} after the pointer leaves it, so it can be reached again, unless the
 * pointer moves onto another row of the menu it came from. Dragging the timeline scrollbar does not count
 * as a hover, including the moment that drag is released on the button.
 */
final class BrowserToolsMenu {
    static final long CLOSE_DELAY_MS = 1000;
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
    private final Runnable openPlaytime;
    private final Runnable openCleanup;
    private final BiConsumer<Scope, MessageExport.Format> export;

    private ButtonComponent anchor;
    private FlowLayout actions;
    private ButtonComponent databaseButton;
    private List<Component> databaseTooltip = List.of(Component.translatable("allthelogs.meta.loading"));
    private ButtonComponent advancedButton;
    private FlowLayout advancedMenu;
    private ButtonComponent exportButton;
    private FlowLayout scopeMenu;
    private ButtonComponent selectionButton;
    private ButtonComponent onScreenButton;
    private ButtonComponent queryButton;
    private boolean scopeIncludesSelection;
    private FlowLayout formatMenu;
    private Scope formatScope;
    private boolean pinned;
    private boolean blockHoverOpen;
    private long closeAt = Long.MAX_VALUE;
    private long advancedCloseAt = Long.MAX_VALUE;
    private long scopeCloseAt = Long.MAX_VALUE;
    private long formatCloseAt = Long.MAX_VALUE;
    private int placedActionsX = Integer.MIN_VALUE;
    private int placedActionsY = Integer.MIN_VALUE;

    BrowserToolsMenu(StackLayout overlays, IntSupplier screenWidth, IntSupplier screenHeight,
                     BooleanSupplier hasSelection, BooleanSupplier suppressed, Runnable onOpened,
                     Runnable openScripts, Runnable openImport, Runnable openPlaytime, Runnable openCleanup,
                     BiConsumer<Scope, MessageExport.Format> export) {
        this.overlays = overlays;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.hasSelection = hasSelection;
        this.suppressed = suppressed;
        this.onOpened = onOpened;
        this.openScripts = openScripts;
        this.openImport = openImport;
        this.openPlaytime = openPlaytime;
        this.openCleanup = openCleanup;
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
        closeAdvanced();
        remove(actions);
        actions = null;
        databaseButton = null;
        advancedButton = null;
        placedActionsX = Integer.MIN_VALUE;
        placedActionsY = Integer.MIN_VALUE;
    }

    void sync(int mouseX, int mouseY, boolean scrollbarDrag) {
        if (suppressed.getAsBoolean()) {
            blockHoverOpen = false;
            close();
            return;
        }
        blockHoverOpen = blockHoverOpen(blockHoverOpen, scrollbarDrag, hit(anchor, mouseX, mouseY));
        if (blockHoverOpen && !isOpen()) return;
        long now = System.currentTimeMillis();
        if (!inside(mouseX, mouseY) && !pinned) {
            if (!isOpen()) return;
            if (closeAt == Long.MAX_VALUE) closeAt = now + CLOSE_DELAY_MS;
            if (now >= closeAt) close();
            return;
        }
        closeAt = Long.MAX_VALUE;
        boolean wasOpen = isOpen();
        ensureActions();
        syncAdvanced(mouseX, mouseY, now);
        if (!wasOpen && isOpen()) onOpened.run();
    }

    /**
     * Opens or keeps each side menu while the pointer is on its row or inside it. Leaving starts the close
     * delay; moving onto another row of the parent menu closes it at once.
     */
    private void syncAdvanced(int mouseX, int mouseY, long now) {
        if (advancedHover(mouseX, mouseY)) {
            advancedCloseAt = Long.MAX_VALUE;
            ensureAdvanced();
        } else if (advancedMenu != null) {
            if (hit(actions, mouseX, mouseY)) {
                closeAdvanced();
                return;
            }
            advancedCloseAt = expire(advancedCloseAt, now);
            if (now >= advancedCloseAt) {
                closeAdvanced();
                return;
            }
        }
        if (advancedMenu == null) return;

        if (exportHover(mouseX, mouseY)) {
            scopeCloseAt = Long.MAX_VALUE;
            ensureScope();
        } else if (scopeMenu != null) {
            if (hit(advancedMenu, mouseX, mouseY)) {
                closeScope();
                return;
            }
            scopeCloseAt = expire(scopeCloseAt, now);
            if (now >= scopeCloseAt) {
                closeScope();
                return;
            }
        }
        if (scopeMenu == null) return;

        Scope hovered = scopeAt(mouseX, mouseY);
        if (hovered != null) {
            formatCloseAt = Long.MAX_VALUE;
            ensureFormat(hovered);
        } else if (hit(formatMenu, mouseX, mouseY) || horizontalGap(formatMenu, scopeButton(formatScope), mouseX, mouseY)) {
            formatCloseAt = Long.MAX_VALUE;
        } else if (formatMenu != null) {
            formatCloseAt = expire(formatCloseAt, now);
            if (now >= formatCloseAt) closeFormat();
        }
    }

    /**
     * Starts the close delay the first time the pointer is found outside, then keeps that deadline.
     */
    static long expire(long closeAt, long now) {
        return closeAt == Long.MAX_VALUE ? now + CLOSE_DELAY_MS : closeAt;
    }

    /**
     * Hover must not open the menu while the timeline scrollbar is held. Releasing that drag on the
     * button keeps the block until the pointer leaves, so the menu does not pop open on mouse-up.
     */
    static boolean blockHoverOpen(boolean blocking, boolean scrollbarDrag, boolean overAnchor) {
        if (scrollbarDrag) return true;
        return blocking && overAnchor;
    }

    private boolean inside(int mouseX, int mouseY) {
        return hit(anchor, mouseX, mouseY)
            || hit(actions, mouseX, mouseY)
            || verticalGap(actions, anchor, mouseX, mouseY)
            || advancedHover(mouseX, mouseY);
    }

    private boolean advancedHover(int mouseX, int mouseY) {
        return hit(advancedButton, mouseX, mouseY)
            || hit(advancedMenu, mouseX, mouseY)
            || horizontalGap(advancedMenu, advancedButton, mouseX, mouseY)
            || exportHover(mouseX, mouseY);
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
        int width = labelWidth("allthelogs.menu.playtime", "allthelogs.menu.import", "allthelogs.menu.advanced");
        FlowLayout menu = panel(width);
        menu.child(action(Component.translatable("allthelogs.menu.playtime"), openPlaytime));
        menu.child(action(Component.translatable("allthelogs.menu.import"), openImport));
        advancedButton = action(Component.translatable("allthelogs.menu.advanced"), this::ensureAdvanced);
        menu.child(advancedButton);
        databaseButton = action(Component.translatable("allthelogs.meta.marker"), () -> {});
        databaseButton.tooltip(databaseTooltip);
        menu.child(databaseButton);
        actions = menu;
        overlays.child(menu);
        placeActions();
    }

    private void ensureAdvanced() {
        if (advancedMenu != null) {
            place(advancedMenu, advancedButton);
            return;
        }
        int width = labelWidth("allthelogs.menu.export", "allthelogs.menu.cleanup", "allthelogs.menu.scripts");
        FlowLayout menu = panel(width);
        exportButton = action(Component.translatable("allthelogs.menu.export"), this::ensureScope);
        menu.child(exportButton);
        menu.child(action(Component.translatable("allthelogs.menu.cleanup"), openCleanup));
        menu.child(action(Component.translatable("allthelogs.menu.scripts"), openScripts));
        advancedMenu = menu;
        overlays.child(menu);
        place(advancedMenu, advancedButton);
        markOpen(advancedButton, true);
    }

    private void ensureScope() {
        boolean includeSelection = hasSelection.getAsBoolean();
        if (scopeMenu != null && scopeIncludesSelection == includeSelection) {
            place(scopeMenu, exportButton);
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
        place(scopeMenu, exportButton);
        markOpen(exportButton, true);
    }

    private void ensureFormat(Scope scope) {
        if (formatMenu != null && formatScope == scope) {
            place(formatMenu, scopeButton(formatScope));
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
        place(formatMenu, scopeButton(formatScope));
        markSelectedScope();
    }

    private void closeAdvanced() {
        closeScope();
        remove(advancedMenu);
        advancedMenu = null;
        exportButton = null;
        advancedCloseAt = Long.MAX_VALUE;
        markOpen(advancedButton, false);
    }

    private void closeScope() {
        closeFormat();
        remove(scopeMenu);
        scopeMenu = null;
        selectionButton = null;
        onScreenButton = null;
        queryButton = null;
        scopeCloseAt = Long.MAX_VALUE;
        markOpen(exportButton, false);
    }

    private void closeFormat() {
        remove(formatMenu);
        formatMenu = null;
        formatScope = null;
        formatCloseAt = Long.MAX_VALUE;
        markSelectedScope();
    }

    /**
     * Keeps the scope whose format menu is open drawn in the hovered style, so moving onto Text, JSON,
     * or CSV does not make that choice look unselected.
     */
    private void markSelectedScope() {
        markOpen(selectionButton, formatScope == Scope.SELECTION);
        markOpen(onScreenButton, formatScope == Scope.VISIBLE);
        markOpen(queryButton, formatScope == Scope.QUERY);
    }

    /**
     * Draws a row in the hovered style while its side menu is open.
     */
    private static void markOpen(ButtonComponent button, boolean open) {
        if (button == null) return;
        button.renderer((graphics, widget, delta) -> {
            Identifier texture = !widget.active()
                ? ButtonComponent.DISABLED_TEXTURE
                : open || widget.isHovered()
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

    private void place(FlowLayout menu, UIComponent row) {
        if (menu == null || row == null) return;
        int[] at = beside(menu, row);
        if (at == null) return;
        Positioning current = menu.positioning().get();
        if (current.type == Positioning.Type.ABSOLUTE && current.x == at[0] && current.y == at[1]) return;
        menu.positioning(Positioning.absolute(at[0], at[1]));
    }

    /**
     * Tooltip for the ? row at the bottom of the menu. Safe to call before the menu is open.
     */
    void setDatabaseInfo(List<Component> lines) {
        databaseTooltip = lines == null || lines.isEmpty()
            ? List.of(Component.translatable("allthelogs.meta.unavailable"))
            : List.copyOf(lines);
        if (databaseButton != null) databaseButton.tooltip(databaseTooltip);
    }

    /**
     * Prefers the left side of {@code row} so a menu on the right edge stays on screen.
     */
    private int[] beside(FlowLayout menu, UIComponent row) {
        if (menu.width() <= 0 || menu.height() <= 0) return null;
        int left = row.x() - menu.width() - GAP;
        int right = row.x() + row.width() + GAP;
        int x = left >= 4 ? left : right;
        if (x + menu.width() > screenWidth.getAsInt() - 4) x = Math.max(4, left);
        int y = submenuTop(row.y() + row.height(), menu.padding().get().bottom(), menu.height(), screenHeight.getAsInt());
        return new int[]{x, y};
    }

    /**
     * Top of a side menu that grows upward, so its last row meets the bottom of the row it opened from.
     * {@code paddingBottom} is that menu's own bottom padding. The menu is moved down only when it would
     * leave the top of the screen.
     */
    static int submenuTop(int rowBottom, int paddingBottom, int menuHeight, int screenHeight) {
        int y = rowBottom + paddingBottom - menuHeight;
        if (y < 4) y = 4;
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
