package me.wolfii.allthelogs.client.ui.screen.browser;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import io.wispforest.owo.ui.util.NinePatchTexture;
import me.wolfii.allthelogs.client.export.MessageExport;
import me.wolfii.allthelogs.client.list.MessageListLayout;
import me.wolfii.allthelogs.client.ui.theme.Colors;
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
 * Menu at the top of the timeline scrollbar. Hovering or clicking the hamburger opens Playtime, Import,
 * and Advanced to its left, top to bottom. A click keeps the menu open until a click outside it. Advanced
 * opens Export, Scripts, DB Cleanup, and Optimize database further to the side, dropping down from its
 * row, with a database info row under them. The row is plain text, not a button, and hovering it shows
 * the database summary. Export opens the export scope beside that, and the chosen scope opens the file
 * format. A row whose side menu is open stays highlighted.
 * <p>
 * The export menus are centered on the row they open from, moved only as far as the screen edge needs.
 * Every menu stays open for {@link #CLOSE_DELAY_MS} after the pointer leaves it, so it can be reached
 * again, unless the pointer moves onto another row of the menu it came from. A click outside closes it
 * at once. Dragging the timeline scrollbar does not count as a hover, including the moment that drag is
 * released on the button.
 */
final class BrowserToolsMenu {
    static final long CLOSE_DELAY_MS = 1000;
    /** Hamburger width, and the height the scrollbar inset is measured from. Matches the message-count chip. */
    static final int BUTTON_SIZE = MessageListLayout.DATE_HEIGHT;
    /** The button is one pixel taller than the chip. */
    static final int BUTTON_EXTRA_HEIGHT = 2;
    static final int BUTTON_EXTRA_WIDTH = 37;
    /** Negative top margin. Sits the hamburger two pixels above the list without moving the scrollbar. */
    static final int BUTTON_LIFT = -1;
    /** Space between the scrubber and the hamburger above it, and the filter button below it. */
    static final int TRACK_GAP = 6;
    private static final int GAP = 2;
    private static final int INFO_ROW_HEIGHT = 14;

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
    private final Runnable optimize;
    private final BiConsumer<Scope, MessageExport.Format> export;

    private ButtonComponent anchor;
    private FlowLayout actions;
    private FlowLayout databaseRow;
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
                     Runnable optimize, BiConsumer<Scope, MessageExport.Format> export) {
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
        this.optimize = optimize;
        this.export = export;
    }

    /**
     * Button at the top of the timeline scrollbar, one pixel taller than the message-count chip
     * and two pixels above the list. The first menu opens to its left, into the screen, instead
     * of off the top or the right edge.
     */
    ButtonComponent button() {
        if (anchor != null) return anchor;
        anchor = UIComponents.button(Component.empty(), pressed -> toggle());
        anchor.sizing(Sizing.fixed(BUTTON_SIZE + BUTTON_EXTRA_WIDTH), Sizing.fixed(BUTTON_SIZE + BUTTON_EXTRA_HEIGHT));
        anchor.margins(Insets.top(BUTTON_LIFT));
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
     * Pins the menu open after the pointer leaves, until a click outside it, another click on the
     * hamburger, or {@link #close()}.
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

    /**
     * Hides the menu when a click lands outside the hamburger and every open menu. The hamburger
     * handles its own clicks, so clicking it still toggles the menu. That also drops the menu before
     * a layout change, such as the filter bar, can leave it at its old position.
     */
    void closeOnClickOutside(double mouseX, double mouseY) {
        if (dismissOnClick(isOpen(), inside((int) mouseX, (int) mouseY))) close();
    }

    /**
     * An open menu hides on a click that is outside it. A click on the hamburger or an open menu does not.
     */
    static boolean dismissOnClick(boolean open, boolean inside) {
        return open && !inside;
    }

    void close() {
        pinned = false;
        closeAt = Long.MAX_VALUE;
        closeAdvanced();
        remove(actions);
        actions = null;
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
            || anchorGap(mouseX, mouseY)
            || advancedHover(mouseX, mouseY);
    }

    /**
     * The strip between the hamburger and the menu counts as inside, so crossing it does not start the
     * close delay. The menu opens to the left, and only sits below or above the button when that side
     * does not fit.
     */
    private boolean anchorGap(int mouseX, int mouseY) {
        if (actions == null || anchor == null) return false;
        return horizontalGap(actions, anchor, mouseX, mouseY)
            || verticalGap(anchor, actions, mouseX, mouseY)
            || verticalGap(actions, anchor, mouseX, mouseY);
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
        actions = menu;
        overlays.child(menu);
        placeActions();
    }

    private void ensureAdvanced() {
        if (advancedMenu != null) {
            place(advancedMenu, advancedButton);
            return;
        }
        int width = labelWidth("allthelogs.menu.export", "allthelogs.menu.cleanup", "allthelogs.menu.scripts",
            "allthelogs.menu.optimize", "allthelogs.meta.marker");
        FlowLayout menu = panel(width);
        exportButton = action(Component.translatable("allthelogs.menu.export"), this::ensureScope);
        menu.child(exportButton);
        menu.child(action(Component.translatable("allthelogs.menu.scripts"), openScripts));
        menu.child(action(Component.translatable("allthelogs.menu.cleanup"), openCleanup));
        menu.child(action(Component.translatable("allthelogs.menu.optimize"), optimize));
        databaseRow = infoRow(Component.translatable("allthelogs.meta.marker"));
        databaseRow.tooltip(databaseTooltip);
        menu.child(databaseRow);
        advancedMenu = menu;
        overlays.child(menu);
        place(advancedMenu, advancedButton);
        markOpen(advancedButton, true);
    }

    private void ensureScope() {
        boolean includeSelection = hasSelection.getAsBoolean();
        if (scopeMenu != null && scopeIncludesSelection == includeSelection) {
            placeCentered(scopeMenu, exportButton);
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
        placeCentered(scopeMenu, exportButton);
        markOpen(exportButton, true);
    }

    private void ensureFormat(Scope scope) {
        if (formatMenu != null && formatScope == scope) {
            placeCentered(formatMenu, scopeButton(formatScope));
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
        placeCentered(formatMenu, scopeButton(formatScope));
        markSelectedScope();
    }

    private void closeAdvanced() {
        closeScope();
        remove(advancedMenu);
        advancedMenu = null;
        exportButton = null;
        databaseRow = null;
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
        int[] at = beside(actions, anchor, false);
        if (at == null) return;
        if (placedActionsX == at[0] && placedActionsY == at[1]) return;
        actions.positioning(Positioning.absolute(at[0], at[1]));
        placedActionsX = at[0];
        placedActionsY = at[1];
    }

    private void place(FlowLayout menu, UIComponent row) {
        place(menu, row, false);
    }

    private void placeCentered(FlowLayout menu, UIComponent row) {
        place(menu, row, true);
    }

    private void place(FlowLayout menu, UIComponent row, boolean centered) {
        if (menu == null || row == null) return;
        int[] at = beside(menu, row, centered);
        if (at == null) return;
        Positioning current = menu.positioning().get();
        if (current.type == Positioning.Type.ABSOLUTE && current.x == at[0] && current.y == at[1]) return;
        menu.positioning(Positioning.absolute(at[0], at[1]));
    }

    /**
     * Tooltip for the database info row at the bottom of Advanced. Safe to call before the menu is open.
     */
    void setDatabaseInfo(List<Component> lines) {
        databaseTooltip = lines == null || lines.isEmpty()
            ? List.of(Component.translatable("allthelogs.meta.unavailable"))
            : List.copyOf(lines);
        if (databaseRow != null) databaseRow.tooltip(databaseTooltip);
    }

    /**
     * Prefers the left side of {@code row} so a menu on the right edge stays on screen.
     */
    private int[] beside(FlowLayout menu, UIComponent row, boolean centered) {
        if (menu.width() <= 0 || menu.height() <= 0) return null;
        int x = sideLeft(row.x(), row.width(), menu.width(), screenWidth.getAsInt());
        int y = centered
            ? centeredTop(row.y() + row.height() / 2, menu.height(), screenHeight.getAsInt())
            : submenuTop(row.y(), menu.padding().get().top(), menu.height(), screenHeight.getAsInt());
        return new int[]{x, y};
    }

    /**
     * Left edge of a menu that opens beside an anchor. The left side is preferred so a control on the right
     * edge stays on screen; the right side is used only when the left side does not fit.
     */
    static int sideLeft(int anchorX, int anchorWidth, int menuWidth, int screenWidth) {
        int left = anchorX - menuWidth - GAP;
        int right = anchorX + anchorWidth + GAP;
        int x = left >= 4 ? left : right;
        if (x + menuWidth > screenWidth - 4) x = Math.max(4, left);
        return x;
    }

    /**
     * Top of a popup that drops down from an anchor. Moved up only when it would leave the bottom of the screen.
     */
    static int dropdownTop(int anchorTop, int anchorHeight, int gap, int popupHeight, int screenHeight) {
        int y = anchorTop + anchorHeight + gap;
        return fitOnScreen(y, popupHeight, screenHeight);
    }

    /**
     * Top of a side menu that grows downward, so its first row meets the top of the row it opened from.
     * {@code paddingTop} is that menu's own top padding. The menu is moved up only when it would
     * leave the bottom of the screen.
     */
    static int submenuTop(int rowTop, int paddingTop, int menuHeight, int screenHeight) {
        return fitOnScreen(rowTop - paddingTop, menuHeight, screenHeight);
    }

    private static int fitOnScreen(int y, int menuHeight, int screenHeight) {
        int limit = screenHeight - 4;
        if (y + menuHeight > limit) y = Math.max(4, limit - menuHeight);
        return Math.max(4, y);
    }

    /**
     * Top of a side menu centered on the row it opened from, at {@code rowCenter}, kept on screen.
     */
    static int centeredTop(int rowCenter, int menuHeight, int screenHeight) {
        int y = rowCenter - menuHeight / 2;
        int limit = screenHeight - 4;
        if (y + menuHeight > limit) y = limit - menuHeight;
        return Math.max(4, y);
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

    /**
     * Muted text row without button chrome, for information that cannot be clicked.
     */
    private static FlowLayout infoRow(Component text) {
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(), Sizing.fixed(INFO_ROW_HEIGHT));
        row.padding(Insets.horizontal(4)).horizontalAlignment(HorizontalAlignment.CENTER)
            .verticalAlignment(VerticalAlignment.CENTER);
        LabelComponent label = UIComponents.label(text);
        label.color(Color.ofArgb(Colors.MUTED));
        row.child(label);
        return row;
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
