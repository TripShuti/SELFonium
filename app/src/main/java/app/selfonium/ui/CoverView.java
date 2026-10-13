package app.selfonium.ui;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import app.selfonium.SelfoniumApp;
import app.selfonium.core.Track;
import app.selfonium.data.ArtworkRepository;

/** A recycled cover keeps its request tied to the current track and attachment. */
public final class CoverView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF bounds=new RectF(),imageBounds=new RectF();
    private final Path clip=new Path();
    private Track track;private Bitmap bitmap;private ArtworkRepository.Ticket request;
    private int background=Ui.RAISED;private boolean square;
    public CoverView(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    public CoverView(Context context,Track track){this(context);bind(track);}
    public void square(){square=true;requestLayout();}
    public void bind(Track value){
        if(track!=null&&value!=null&&track.id.equals(value.id)&&track.artwork.equals(value.artwork)&&(bitmap!=null||request!=null))return;
        if(request!=null){request.cancel();request=null;}
        track=value;bitmap=null;
        int hue=Math.floorMod(value==null?0:(value.album+value.artist).hashCode(),360);
        background=Color.HSVToColor(new float[]{hue,.24f,.27f});invalidate();
        if(isAttachedToWindow())load();
    }
    private void load(){
        if(track==null||bitmap!=null||request!=null)return;
        Track requested=track;
        request=((SelfoniumApp)getContext().getApplicationContext()).artwork.load(track,image->{
            if(track==requested){bitmap=image;request=null;invalidate();}
        });
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();load();}
    @Override protected void onDetachedFromWindow(){if(request!=null){request.cancel();request=null;}super.onDetachedFromWindow();}
    @Override protected void onMeasure(int width,int height){super.onMeasure(width,height);if(square)setMeasuredDimension(getMeasuredWidth(),getMeasuredWidth());}
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){bounds.set(0,0,w,h);clip.reset();float radius=Math.min(w,h)*.06f;clip.addRoundRect(bounds,radius,radius,Path.Direction.CW);}
    @Override protected void onDraw(Canvas canvas){
        float w=getWidth(),h=getHeight(),size=Math.min(w,h);canvas.save();canvas.clipPath(clip);
        if(bitmap!=null){float scale=Math.max(w/bitmap.getWidth(),h/bitmap.getHeight());float bw=bitmap.getWidth()*scale,bh=bitmap.getHeight()*scale;imageBounds.set((w-bw)/2,(h-bh)/2,(w+bw)/2,(h+bh)/2);canvas.drawBitmap(bitmap,null,imageBounds,paint);}
        else{paint.setStyle(Paint.Style.FILL);paint.setColor(background);canvas.drawRect(bounds,paint);paint.setColor(0x25000000);canvas.drawCircle(w/2,h/2,size*.33f,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,size*.007f));paint.setColor(0x30FFFFFF);canvas.drawCircle(w/2,h/2,size*.26f,paint);canvas.drawCircle(w/2,h/2,size*.2f,paint);
            paint.setStyle(Paint.Style.FILL);paint.setColor(0x99C5A7FF);canvas.drawCircle(w/2,h/2,size*.075f,paint);paint.setColor(background);canvas.drawCircle(w/2,h/2,size*.025f,paint);}
        canvas.restore();
    }
}
