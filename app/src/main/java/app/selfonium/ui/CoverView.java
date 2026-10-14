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
    private int background=Ui.RAISED;private boolean square,portrait,urgent;
    private java.util.function.IntConsumer palette;
    public CoverView(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    public CoverView(Context context,Track track){this(context);bind(track);}
    public void urgent(){urgent=true;}
    public void square(){square=true;requestLayout();}
    public void portrait(boolean value){portrait=value;updateClip();invalidate();}
    public void palette(java.util.function.IntConsumer listener){palette=listener;if(bitmap!=null)publishPalette();}
    private void publishPalette(){if(palette==null||bitmap==null)return;long red=0,green=0,blue=0;int count=0;
        for(int y=1;y<8;y++)for(int x=1;x<8;x++){int color=bitmap.getPixel(bitmap.getWidth()*x/8,bitmap.getHeight()*y/8);red+=Color.red(color);green+=Color.green(color);blue+=Color.blue(color);count++;}
        palette.accept(Color.rgb((int)(red/count),(int)(green/count),(int)(blue/count)));}
    public void bind(Track value){
        if(track!=null&&value!=null&&track.id.equals(value.id)&&track.artwork.equals(value.artwork)&&(bitmap!=null||request!=null))return;
        if(request!=null){request.cancel();request=null;}
        boolean sameCover=track!=null&&value!=null&&!value.artwork.isEmpty()&&track.sourceId.equals(value.sourceId)&&track.artwork.equals(value.artwork);
        track=value;
        if(!sameCover)bitmap=((SelfoniumApp)getContext().getApplicationContext()).artwork.peek(value);
        int hue=Math.floorMod(value==null?0:(value.album+value.artist).hashCode(),360);
        background=Color.HSVToColor(new float[]{hue,.24f,.27f});invalidate();
        if(bitmap!=null)publishPalette();else if(isAttachedToWindow())load();
    }
    private void load(){
        if(track==null||bitmap!=null||request!=null)return;
        Track requested=track;
        request=((SelfoniumApp)getContext().getApplicationContext()).artwork.load(track,image->{
            if(track==requested){bitmap=image;request=null;publishPalette();invalidate();}
        },urgent);
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();load();}
    @Override protected void onDetachedFromWindow(){if(request!=null){request.cancel();request=null;}super.onDetachedFromWindow();}
    @Override protected void onMeasure(int width,int height){super.onMeasure(width,height);if(square)setMeasuredDimension(getMeasuredWidth(),getMeasuredWidth());}
    private void updateClip(){bounds.set(0,0,getWidth(),getHeight());clip.reset();float radius=portrait?Math.min(getWidth(),getHeight())/2f:Ui.dp(getContext(),8);clip.addRoundRect(bounds,radius,radius,Path.Direction.CW);}
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){updateClip();}
    @Override protected void onDraw(Canvas canvas){
        float w=getWidth(),h=getHeight(),size=Math.min(w,h);canvas.save();canvas.clipPath(clip);
        if(bitmap!=null){float scale=Math.max(w/bitmap.getWidth(),h/bitmap.getHeight());float bw=bitmap.getWidth()*scale,bh=bitmap.getHeight()*scale;imageBounds.set((w-bw)/2,(h-bh)/2,(w+bw)/2,(h+bh)/2);canvas.drawBitmap(bitmap,null,imageBounds,paint);}
        else if(portrait){paint.setStyle(Paint.Style.FILL);paint.setColor(background);canvas.drawRect(bounds,paint);paint.setColor(Ui.MUTED);canvas.drawCircle(w/2,h*.36f,size*.13f,paint);canvas.drawOval(w*.25f,h*.55f,w*.75f,h*.95f,paint);}
        else{paint.setStyle(Paint.Style.FILL);paint.setColor(background);canvas.drawRect(bounds,paint);paint.setColor(0x25000000);canvas.drawCircle(w/2,h/2,size*.33f,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,size*.007f));paint.setColor(0x30FFFFFF);canvas.drawCircle(w/2,h/2,size*.26f,paint);canvas.drawCircle(w/2,h/2,size*.2f,paint);
            paint.setStyle(Paint.Style.FILL);paint.setColor(0x99C5A7FF);canvas.drawCircle(w/2,h/2,size*.075f,paint);paint.setColor(background);canvas.drawCircle(w/2,h/2,size*.025f,paint);}
        canvas.restore();
    }
}
