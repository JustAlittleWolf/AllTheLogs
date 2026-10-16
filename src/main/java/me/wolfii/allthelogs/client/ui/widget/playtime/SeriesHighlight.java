package me.wolfii.allthelogs.client.ui.widget.playtime;

/**
 * Series a playtime screen brings forward. A section under the pointer in a chart wins over a hovered
 * legend row; -1 means nothing is highlighted.
 */
public final class SeriesHighlight {
    private int chart = -1;
    private int legend = -1;

    public int active() {
        return chart >= 0 ? chart : legend;
    }

    void chart(int series) {
        chart = series;
    }

    void legend(int series) {
        legend = series;
    }
}
