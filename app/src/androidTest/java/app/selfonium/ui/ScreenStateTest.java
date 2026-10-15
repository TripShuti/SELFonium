package app.selfonium.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Parcel;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.widget.*;
import java.util.*;

/** Native hierarchy round trips and measured large-font navigation, without changing device settings. */
@SuppressWarnings("deprecation")
public final class ScreenStateTest extends ActivityInstrumentationTestCase2<MainActivity> {
    private MainActivity activity;private final List<Dialog> dialogs=new ArrayList<>();
    public ScreenStateTest(){super(MainActivity.class);}
    @Override protected void setUp()throws Exception{super.setUp();activity=getActivity();getInstrumentation().waitForIdleSync();}
    @Override protected void tearDown()throws Exception{try{getInstrumentation().runOnMainSync(()->{for(Dialog dialog:dialogs)dialog.dismiss();});}finally{super.tearDown();}}
    private Dialog display(View content){Dialog dialog=Ui.screen(activity);dialogs.add(dialog);dialog.setContentView(content);dialog.show();dialog.getWindow().setLayout(Ui.dp(activity,300),Ui.dp(activity,240));return dialog;}
    private ScrollView page(){ScrollView scroll=new ScrollView(activity);TextView content=new TextView(activity);content.setText("Viewport fixture");scroll.addView(content,new ScrollView.LayoutParams(-1,Ui.dp(activity,1200)));return scroll;}
    private Bundle persisted(Bundle source){Parcel parcel=Parcel.obtain();try{parcel.writeBundle(source);parcel.setDataPosition(0);return parcel.readBundle(getClass().getClassLoader());}finally{parcel.recycle();}}
    public void testScrollViewportSurvivesDialogReplacement(){
        ScrollView[] pages=new ScrollView[2];Dialog[] windows=new Dialog[2];Bundle saved=new Bundle();
        getInstrumentation().runOnMainSync(()->{pages[0]=page();windows[0]=display(pages[0]);});getInstrumentation().waitForIdleSync();
        getInstrumentation().runOnMainSync(()->{pages[0].scrollTo(0,Ui.dp(activity,300));ScreenState.save(saved,"storage",windows[0]);windows[0].dismiss();pages[1]=page();windows[1]=display(pages[1]);ScreenState.restore(windows[1],persisted(saved).getBundle("storage"));});getInstrumentation().waitForIdleSync();
        assertEquals(Ui.dp(activity,300),pages[1].getScrollY());assertTrue(windows[1].isShowing());
    }
    public void testDismissedDialogIsNotSaved(){
        Bundle saved=new Bundle();getInstrumentation().runOnMainSync(()->{Dialog dialog=display(page());dialog.dismiss();ScreenState.save(saved,"storage",dialog);ScreenState.save(saved,"player",null);});assertTrue(saved.isEmpty());
    }
    public void testQueueViewportSurvivesDialogReplacement(){
        ListView[] lists=new ListView[2];Dialog[] windows=new Dialog[2];Bundle saved=new Bundle();String[] entries=new String[100];Arrays.fill(entries,"Queue fixture");
        getInstrumentation().runOnMainSync(()->{lists[0]=new ListView(activity);lists[0].setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_list_item_1,entries));windows[0]=display(lists[0]);});getInstrumentation().waitForIdleSync();
        getInstrumentation().runOnMainSync(()->lists[0].setSelectionFromTop(25,0));getInstrumentation().waitForIdleSync();
        getInstrumentation().runOnMainSync(()->{ScreenState.save(saved,"queue",windows[0]);windows[0].dismiss();lists[1]=new ListView(activity);lists[1].setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_list_item_1,entries));windows[1]=display(lists[1]);ScreenState.restore(windows[1],persisted(saved).getBundle("queue"));});getInstrumentation().waitForIdleSync();assertEquals(25,lists[1].getFirstVisiblePosition());
    }
    public void testLargeFontNavigationLabelsFitWithoutEllipsis(){
        getInstrumentation().runOnMainSync(()->{
            Configuration config=new Configuration(activity.getResources().getConfiguration());config.fontScale=1.5f;config.setLocale(new Locale("uk"));Context context=activity.createConfigurationContext(config);
            LinearLayout row=Ui.row(context);String[] names={"Головна","Бібліотека","Пошук","Налаштування"};
            for(String name:names)row.addView(Ui.navigationItem(context,name,"home",false,()->{}),new LinearLayout.LayoutParams(0,-2,1));
            row.measure(View.MeasureSpec.makeMeasureSpec(Ui.dp(context,360),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(Ui.dp(context,300),View.MeasureSpec.AT_MOST));row.layout(0,0,row.getMeasuredWidth(),row.getMeasuredHeight());
            for(int i=0;i<4;i++){LinearLayout cell=(LinearLayout)row.getChildAt(i);TextView label=(TextView)cell.getChildAt(2);assertTrue(label.getLineCount()<=2);for(int line=0;line<label.getLineCount();line++)assertEquals(0,label.getLayout().getEllipsisCount(line));assertTrue(label.getHeight()>=label.getLayout().getHeight());assertTrue(cell.getHeight()>=Ui.dp(context,64));assertEquals(names[i],cell.getContentDescription());}
        });
    }
}
