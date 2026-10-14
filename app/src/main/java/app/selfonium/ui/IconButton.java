package app.selfonium.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.RippleDrawable;
import android.view.View;

/** Original vector controls, independent of the phone's emoji font. */
final class IconButton extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private String icon;private int color=Ui.TEXT;private boolean filled;
    public IconButton(Context context){this(context,"play","Відтворити",()->{});}
    IconButton(Context context,String icon,String description,Runnable action){
        super(context);this.icon=icon;setContentDescription(description);setFocusable(true);
        setBackground(new RippleDrawable(ColorStateList.valueOf((Ui.ACCENT&0xFFFFFF)|0x33000000),null,Ui.shape(context,0xFFFFFFFF,30)));
        setOnClickListener(v->action.run());
    }
    void icon(String value){if(value.equals(icon))return;icon=value;invalidate();}
    void active(boolean value){int next=value?Ui.ACCENT:Ui.MUTED;if(color==next&&isSelected()==value)return;color=next;setSelected(value);invalidate();}
    void primary(){filled=true;color=Ui.ON_ACCENT;setBackground(new RippleDrawable(ColorStateList.valueOf(0x33000000),Ui.shape(getContext(),Ui.ACCENT,40),null));invalidate();}
    @Override public CharSequence getAccessibilityClassName(){return android.widget.Button.class.getName();}
    @Override protected void onMeasure(int w,int h){setMeasuredDimension(resolveSize(Ui.dp(getContext(),48),w),resolveSize(Ui.dp(getContext(),48),h));}
    private void line(Canvas c,float x,float y,float xx,float yy){c.drawLine(x,y,xx,yy,paint);}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float size=Ui.dp(getContext(),filled?30:23);c.save();c.translate((getWidth()-size)/2,(getHeight()-size)/2);c.scale(size/24,size/24);
        paint.setColor(color);paint.setStrokeWidth(1.8f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStyle(Paint.Style.STROKE);path.reset();
        switch(icon){
            case "play"->{paint.setStyle(Paint.Style.FILL);path.moveTo(7,4);path.lineTo(20,12);path.lineTo(7,20);path.close();c.drawPath(path,paint);}
            case "pause"->{paint.setStrokeWidth(4);line(c,8,5,8,19);line(c,16,5,16,19);}
            case "next","previous"->{if(icon.equals("previous")){c.translate(24,0);c.scale(-1,1);}paint.setStyle(Paint.Style.FILL);path.moveTo(5,5);path.lineTo(16,12);path.lineTo(5,19);path.close();c.drawPath(path,paint);line(c,19,5,19,19);}
            case "back"->{line(c,15,5,8,12);line(c,8,12,15,19);}
            case "down"->{line(c,5,9,12,16);line(c,12,16,19,9);}
            case "up"->{line(c,5,15,12,8);line(c,12,8,19,15);}
            case "close"->{line(c,6,6,18,18);line(c,18,6,6,18);}
            case "more"->{paint.setStyle(Paint.Style.FILL);for(int y=5;y<=19;y+=7)c.drawCircle(12,y,1.5f,paint);}
            case "shuffle"->{path.moveTo(3,6);path.lineTo(6,6);path.lineTo(17,18);path.lineTo(21,18);c.drawPath(path,paint);line(c,17,14,21,18);line(c,21,18,17,21);line(c,3,18,6,18);line(c,6,18,10,14);line(c,14,10,17,6);line(c,17,6,21,6);line(c,17,3,21,6);line(c,21,6,17,10);}
            case "repeat","repeat1"->{path.moveTo(4,10);path.lineTo(4,6);path.lineTo(20,6);path.lineTo(17,3);c.drawPath(path,paint);path.reset();path.moveTo(20,14);path.lineTo(20,18);path.lineTo(4,18);path.lineTo(7,21);c.drawPath(path,paint);if(icon.equals("repeat1")){line(c,12,10,12,14);}}
            case "heart"->{path.moveTo(12,21);path.cubicTo(-5,10,5,-1,12,7);path.cubicTo(19,-1,29,10,12,21);if(isSelected())paint.setStyle(Paint.Style.FILL);c.drawPath(path,paint);}
            case "equalizer"->{for(int i=0;i<3;i++){float x=5+i*7,y=i==1?15:8;line(c,x,3,x,y-3);line(c,x,y+3,x,21);c.drawCircle(x,y,2.5f,paint);}}
            case "settings"->{c.drawCircle(12,12,7,paint);c.drawCircle(12,12,2.5f,paint);for(int i=0;i<8;i++){c.save();c.rotate(i*45,12,12);line(c,12,2,12,5);c.restore();}}
            case "search"->{c.drawCircle(10,10,6,paint);line(c,15,15,21,21);}
            case "remove"->{line(c,5,12,19,12);}
            case "plus"->{line(c,12,5,12,19);line(c,5,12,19,12);}
            case "filter"->{line(c,3,5,21,5);line(c,6,12,18,12);line(c,10,19,14,19);}
            case "sort"->{line(c,5,3,5,21);line(c,2,17,5,21);line(c,5,21,8,17);line(c,12,5,22,5);line(c,12,12,19,12);line(c,12,19,16,19);}
            case "folder"->{path.moveTo(3,5);path.lineTo(9,5);path.lineTo(11,8);path.lineTo(21,8);path.lineTo(21,20);path.lineTo(3,20);path.close();c.drawPath(path,paint);}
            case "radio"->{c.drawRoundRect(3,8,21,21,2,2,paint);line(c,4,8,18,3);c.drawCircle(8,15,3,paint);line(c,15,13,18,13);line(c,15,17,18,17);}
            case "music"->{line(c,10,18,10,5);line(c,10,5,20,3);line(c,20,3,20,16);c.drawOval(4,16,10,21,paint);c.drawOval(14,14,20,19,paint);}
            case "refresh"->{c.drawArc(4,4,20,20,40,280,false,paint);line(c,19,3,19,9);line(c,19,9,13,9);}
            case "chevron"->{line(c,9,6,15,12);line(c,15,12,9,18);}
            case "home"->{path.moveTo(3,10);path.lineTo(12,3);path.lineTo(21,10);path.lineTo(21,21);path.lineTo(15,21);path.lineTo(15,14);path.lineTo(9,14);path.lineTo(9,21);path.lineTo(3,21);path.close();c.drawPath(path,paint);}
            case "library"->{c.drawRect(3,4,7,21,paint);c.drawRect(11,4,15,21,paint);line(c,18,4,22,20);}
            case "server"->{c.drawRoundRect(3,3,21,10,2,2,paint);c.drawRoundRect(3,14,21,21,2,2,paint);line(c,7,6,8,6);line(c,7,17,8,17);}
            case "clock"->{c.drawCircle(12,12,9,paint);line(c,12,7,12,12);line(c,12,12,16,14);}
            case "download"->{line(c,12,3,12,16);line(c,7,11,12,16);line(c,12,16,17,11);path.moveTo(3,17);path.lineTo(3,21);path.lineTo(21,21);path.lineTo(21,17);c.drawPath(path,paint);}
            case "album"->{c.drawCircle(12,12,9,paint);c.drawCircle(12,12,3,paint);}
            case "artist"->{c.drawCircle(12,7,4,paint);c.drawArc(4,13,20,27,180,180,false,paint);}
            default->{for(int y=5;y<=19;y+=7){line(c,4,y,5,y);line(c,9,y,21,y);}}
        }c.restore();
    }
}
