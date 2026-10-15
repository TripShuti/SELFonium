package app.selfonium.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.widget.*;
import java.util.*;

/** Actual view layout/callback checks, without editing sources or player settings. */
@SuppressWarnings("deprecation")
public final class LibraryNavigationTest extends ActivityInstrumentationTestCase2<MainActivity> {
    private MainActivity activity;private LibraryTabs tabs;private final List<String> chosen=new ArrayList<>();
    public LibraryNavigationTest(){super(MainActivity.class);}
    @Override protected void setUp()throws Exception{super.setUp();activity=getActivity();getInstrumentation().waitForIdleSync();}
    private void display(String selected,boolean all,float scale){
        getInstrumentation().runOnMainSync(()->{
            Configuration configuration=new Configuration(activity.getResources().getConfiguration());configuration.fontScale=scale;Context context=activity.createConfigurationContext(configuration);
            tabs=new LibraryTabs(context,selected,all,chosen::add);FrameLayout frame=new FrameLayout(activity);frame.addView(tabs,new FrameLayout.LayoutParams(-1,-2));activity.setContentView(frame);
        });getInstrumentation().waitForIdleSync();
    }
    private LinearLayout row(){return (LinearLayout)tabs.getChildAt(0);}
    public void testAllSectionsShareOneRowAndSelectedLastTabIsVisible(){
        display("Радіо",true,1);assertEquals(8,row().getChildCount());int top=row().getChildAt(0).getTop();
        for(int i=0;i<8;i++){TextView chip=(TextView)row().getChildAt(i);assertEquals(top,chip.getTop());assertEquals(1,chip.getLineCount());}
        View selected=row().getChildAt(7);assertTrue(selected.isSelected());Rect visible=new Rect();assertTrue(selected.getGlobalVisibleRect(visible));assertEquals(selected.getWidth(),visible.width());
    }
    public void testLargeFontLabelsKeepTheirNaturalWidth(){
        display("Плейлисти",true,1.6f);
        for(int i=0;i<row().getChildCount();i++){TextView chip=(TextView)row().getChildAt(i);assertEquals(1,chip.getLineCount());assertEquals(0,chip.getLayout().getEllipsisCount(0));assertTrue(chip.getPaint().measureText(chip.getText().toString())<=chip.getWidth()-chip.getCompoundPaddingLeft()-chip.getCompoundPaddingRight()+1);}
    }
    public void testSearchUsesThreeViewsAndCallbacksKeepStableKeys(){
        display("Композиції",false,1);assertEquals(3,row().getChildCount());
        getInstrumentation().runOnMainSync(()->row().getChildAt(1).performClick());assertEquals(List.of("Виконавці"),chosen);
    }
    public void testEveryLibrarySectionIsDirectlySelectable(){
        display("Альбоми",true,1);
        getInstrumentation().runOnMainSync(()->{for(int i=0;i<row().getChildCount();i++)row().getChildAt(i).performClick();});
        assertEquals(Arrays.asList(LibraryTabs.SECTIONS),chosen);
    }
}
