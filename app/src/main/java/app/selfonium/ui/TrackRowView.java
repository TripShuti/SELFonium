package app.selfonium.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import app.selfonium.core.Protocol;
import app.selfonium.core.Track;
import java.util.function.Consumer;

/** Both actions belong to the bound row; an AdapterView item-click is not required. */
final class TrackRowView extends LinearLayout {
    private final CoverView cover;
    private final TextView title, artist, duration,number;
    final IconButton overflow;
    private Track track;
    private Runnable playAction;
    private Consumer<Track> menuAction;

    public TrackRowView(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(Ui.dp(context, 64));
        Ui.pad(this, 0, 6);
        setBackground(new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), null,
                Ui.shape(context, 0xFFFFFFFF, 8)));
        setFocusable(true);
        // Long-clickable views consume taps themselves, so do not depend on ListView's listener.
        setOnClickListener(view -> { if (playAction != null) playAction.run(); });
        setOnLongClickListener(view -> { openMenu(); return true; });

        number=Ui.text(context,"",12,Ui.MUTED);number.setGravity(Gravity.CENTER);number.setVisibility(GONE);
        addView(number,new LayoutParams(Ui.dp(context,32),Ui.dp(context,48)));
        cover = new CoverView(context);
        addView(cover, new LayoutParams(Ui.dp(context, 48), Ui.dp(context, 48)));
        LinearLayout labels = Ui.column(context);
        Ui.pad(labels, 12, 0);
        title = Ui.title(context, "", 14);
        artist = Ui.text(context, "", 12, Ui.MUTED);
        for (TextView label : new TextView[]{title, artist}) {
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        labels.addView(title);
        Ui.space(labels, 3);
        labels.addView(artist);
        addView(labels, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));

        duration = Ui.text(context, "", 11, Ui.MUTED);
        duration.setSingleLine(true);
        duration.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(duration, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        overflow = new IconButton(context, "more", "Дії з композицією", this::openMenu);
        // A real child target receives its own touch stream and never invokes playback.
        addView(overflow, new LayoutParams(Ui.dp(context, 48), Ui.dp(context, 48)));
    }

    void bind(Track value, Runnable play, Consumer<Track> menu) {
        track = value;
        setTag(value.id);
        playAction = play;
        menuAction = menu;
        cover.bind(value);
        title.setText(Ui.name(value.title));
        artist.setText(value.artist + " · " + Ui.album(value.album));
        albumPosition(0);
        duration.setText(value.durationMs > 0 ? Protocol.time(value.durationMs) : "");
        setContentDescription(value.title + ", " + value.artist);
        overflow.setContentDescription("Дії з композицією «" + value.title + "»");
    }

    void albumPosition(int position){number.setText(Integer.toString(position));number.setVisibility(position>0?VISIBLE:GONE);cover.setVisibility(position>0?GONE:VISIBLE);if(position>0&&track!=null)artist.setText(track.artist);}
    void nowPlaying(boolean playing) { setActivated(playing); title.setTextColor(playing ? Ui.ACCENT : Ui.TEXT);number.setTextColor(playing?Ui.ACCENT:Ui.MUTED); }

    private void openMenu() {
        if (track != null && menuAction != null) menuAction.accept(track);
    }
}
