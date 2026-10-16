package me.wolfii.allthelogs.client.ui.widget;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import me.wolfii.allthelogs.client.search.DateParser;
import net.minecraft.network.chat.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Label with a narrow X, above a text box for one end of a period. Text that is not a date yet is ignored;
 * empty text, or the X, removes that bound.
 */
public final class DateField {
    /** Shorter than a normal 20px button, and only as wide as the X plus the button border. */
    private static final int RESET_BUTTON_SIZE = 14;
    /** 4px shorter than a normal 20px text box to keep the filter panels compact. */
    private static final int BOX_HEIGHT = 16;

    private final FlowLayout row = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
    private final TextBoxComponent box;

    public DateField(String key, String value, Function<String, Optional<LocalDateTime>> parse,
                     Consumer<LocalDateTime> apply) {
        row.gap(2);
        box = UIComponents.textBox(Sizing.fill(), value);
        row.child(resetHeader(key, () -> box.setValue("")));
        box.verticalSizing(Sizing.fixed(BOX_HEIGHT));
        box.setMaxLength(32);
        box.setHint(Component.translatable("allthelogs.filter.date_hint"));
        box.onChanged().subscribe(text -> {
            if (!DateParser.isBlankOrValid(text)) return;
            apply.accept(parse.apply(text).orElse(null));
        });
        row.child(box);
    }

    /**
     * Label with a narrow X on the right. The X is a Minecraft font glyph, and the button clears that one field.
     */
    public static FlowLayout resetHeader(String key, Runnable reset) {
        FlowLayout header = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        header.gap(4).verticalAlignment(VerticalAlignment.CENTER);
        header.child(UIComponents.label(Component.translatable(key)).horizontalSizing(Sizing.expand()));
        ButtonComponent clear = UIComponents.button(Component.literal("❌"), ignored -> reset.run());
        clear.horizontalSizing(Sizing.fixed(RESET_BUTTON_SIZE));
        clear.verticalSizing(Sizing.fixed(RESET_BUTTON_SIZE));
        header.child(clear);
        return header;
    }

    public FlowLayout row() {
        return row;
    }

    public TextBoxComponent box() {
        return box;
    }
}
