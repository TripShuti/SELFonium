package app.selfonium.ui;

import android.content.Context;
import android.view.View;
import android.widget.*;
import app.selfonium.locale.Texts;
import java.util.function.Consumer;

/** Natural-width single-row tabs: never shrink labels or wrap them into a second row. */
final class LibraryTabs extends HorizontalScrollView {
    static final String[] SECTIONS={"Альбоми","Виконавці","Композиції","Улюблене","Плейлисти","Офлайн","Файли","Радіо"};
    LibraryTabs(Context context,String selected,boolean all,Consumer<String> choose){
        super(context);setHorizontalScrollBarEnabled(false);setFillViewport(false);setClipToPadding(false);
        LinearLayout row=Ui.row(context);addView(row,new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT));
        for(int i=0;i<(all?SECTIONS.length:3);i++){
            String key=SECTIONS[i];TextView chip=Ui.button(context,Texts.known(context,key),key.equals(selected),()->choose.accept(key));
            chip.setTag(key);chip.setSingleLine(true);chip.setTextSize(14);chip.setMinWidth(Ui.dp(context,88));chip.setMinHeight(Ui.dp(context,48));Ui.pad(chip,16,8);chip.setSelected(key.equals(selected));
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT);size.rightMargin=Ui.dp(context,8);row.addView(chip,size);
            if(key.equals(selected))addOnLayoutChangeListener(new View.OnLayoutChangeListener(){
                @Override public void onLayoutChange(View view,int left,int top,int right,int bottom,int oldLeft,int oldTop,int oldRight,int oldBottom){
                    if(getWidth()>0&&chip.getWidth()>0){scrollTo(Math.max(0,chip.getLeft()-(getWidth()-chip.getWidth())/2),0);removeOnLayoutChangeListener(this);}
                }
            });
        }
    }
}
