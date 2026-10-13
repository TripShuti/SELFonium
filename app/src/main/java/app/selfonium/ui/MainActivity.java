package app.selfonium.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.*;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import app.selfonium.SelfoniumApp;
import app.selfonium.core.Protocol;
import app.selfonium.core.Source;
import app.selfonium.core.Track;
import app.selfonium.data.DownloadService;
import app.selfonium.data.LocalMusic;
import app.selfonium.playback.PlaybackService;
import java.io.File;
import java.util.*;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class MainActivity extends Activity {
    private SelfoniumApp app;private MediaController player;private ListenableFuture<MediaController> controllerFuture;
    private SharedPreferences settings;private final Handler handler=new Handler(Looper.getMainLooper());
    private LinearLayout root,mini;private IconButton backButton;private boolean detailShowing;private int sortOrder;private FrameLayout content;private TextView heading,subtitle,miniTitle,miniArtist;private IconButton miniPlay,playerPlay,shuffleButton,repeatButton,favoriteButton;private CoverView miniCover;private ProgressBar miniProgress;private String miniId="";private boolean seeking;
    private TextView playerTitle,playerArtist,playerAlbum,playerTime,playerRemaining;private SeekBar playerSeek;private FrameLayout playerCover;
    private Dialog playerDialog,equalizerDialog,actionSheet;private String displayedId="",query="",category="Композиції",section="",sourceFilter="";
    private LinearLayout navigation;private int tab;private List<Track> library=new ArrayList<>();private boolean syncing;
    private final Runnable progress=new Runnable(){@Override public void run(){updatePlayer();handler.postDelayed(this,500);}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);app=(SelfoniumApp)getApplication();settings=getSharedPreferences("player",MODE_PRIVATE);
        if(state!=null){tab=state.getInt("tab");category=state.getString("category","Композиції");section=state.getString("section","");query=state.getString("query","");}
        build();reload();
        controllerFuture=new MediaController.Builder(this,new SessionToken(this,new ComponentName(this,PlaybackService.class))).buildAsync();
        controllerFuture.addListener(()->{
            try{player=controllerFuture.get();player.addListener(new Player.Listener(){
                @Override public void onEvents(Player p,Player.Events events){updatePlayer();}
                @Override public void onPlayerError(PlaybackException e){message("Не вдалося відтворити. Перевірте доступ до файлу або синхронізуйте джерело повторно.");}
            });updatePlayer();}catch(Exception e){message("Не вдалося під’єднати плеєр");}
        },command->handler.post(command));
    }
    @Override protected void onStart(){super.onStart();handler.post(progress);}
    @Override protected void onStop(){handler.removeCallbacks(progress);super.onStop();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(playerDialog!=null)playerDialog.dismiss();if(equalizerDialog!=null)equalizerDialog.dismiss();if(actionSheet!=null)actionSheet.dismiss();if(controllerFuture!=null)MediaController.releaseFuture(controllerFuture);super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("tab",tab);state.putString("category",category);state.putString("section",section);state.putString("query",query);super.onSaveInstanceState(state);}
    private int dp(float n){return Ui.dp(this,n);}
    private IconButton icon(String name,String description,Runnable action){return new IconButton(this,name,description,action);}
    private void build(){
        Ui.edgeToEdge(getWindow());root=Ui.column(this);root.setBackgroundColor(Ui.BG);Ui.insets(root);
        LinearLayout top=Ui.row(this);Ui.pad(top,8,0);top.setMinimumHeight(dp(56));
        backButton=icon("back","Назад",this::goBack);top.addView(backButton,new LinearLayout.LayoutParams(dp(48),dp(48)));backButton.setVisibility(View.GONE);
        LinearLayout titles=Ui.column(this);Ui.pad(titles,8,0);heading=Ui.title(this,"SELFonium",22);heading.setSingleLine(true);heading.setEllipsize(android.text.TextUtils.TruncateAt.END);titles.addView(heading);
        subtitle=Ui.text(this,"",12,Ui.MUTED);subtitle.setVisibility(View.GONE);titles.addView(subtitle);top.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        top.addView(icon("filter","Фільтр джерел",this::filterSources),new LinearLayout.LayoutParams(dp(48),dp(48)));
        top.addView(icon("more","Меню",this::mainMenu),new LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(top);
        content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout playerCard=Ui.column(this);playerCard.setBackground(Ui.shape(this,Ui.RAISED,12));playerCard.setClipToOutline(true);
        mini=Ui.row(this);Ui.pad(mini,8,6);miniCover=new CoverView(this);mini.addView(miniCover,new LinearLayout.LayoutParams(dp(44),dp(44)));miniCover.setOnClickListener(v->showPlayer());
        LinearLayout miniText=Ui.column(this);Ui.pad(miniText,10,0);miniTitle=Ui.title(this,"Оберіть композицію",14);miniTitle.setSingleLine(true);miniTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        miniArtist=Ui.text(this,"Відкрити бібліотеку",12,Ui.MUTED);miniArtist.setSingleLine(true);miniArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);miniText.addView(miniTitle);Ui.space(miniText,4);miniText.addView(miniArtist);
        mini.addView(miniText,new LinearLayout.LayoutParams(0,-2,1));miniText.setOnClickListener(v->showPlayer());mini.setOnClickListener(v->showPlayer());
        miniPlay=icon("play","Відтворити",this::toggle);mini.addView(miniPlay,new LinearLayout.LayoutParams(dp(48),dp(48)));playerCard.addView(mini);
        miniProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);miniProgress.setMax(1000);miniProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));miniProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.RAISED));playerCard.addView(miniProgress,new LinearLayout.LayoutParams(-1,dp(2)));
        LinearLayout.LayoutParams cardSize=new LinearLayout.LayoutParams(-1,-2);cardSize.setMargins(dp(8),dp(4),dp(8),dp(8));root.addView(playerCard,cardSize);
        navigation=Ui.row(this);navigation.setBackgroundColor(Ui.SURFACE);String[] names={"Головна","Бібліотека","Пошук","Налаштування"},icons={"home","library","search","settings"};
        for(int i=0;i<names.length;i++){final int selected=i;LinearLayout cell=Ui.column(this);cell.setGravity(Gravity.CENTER);Ui.pad(cell,0,6);Runnable open=()->navigate(selected);
            IconButton button=icon(icons[i],names[i],open);cell.addView(button,new LinearLayout.LayoutParams(dp(56),dp(30)));Ui.space(cell,5);
            TextView label=Ui.text(this,names[i],11,Ui.MUTED);label.setGravity(Gravity.CENTER);label.setSingleLine(true);cell.addView(label,new LinearLayout.LayoutParams(-1,-2));cell.setOnClickListener(v->open.run());navigation.addView(cell,new LinearLayout.LayoutParams(0,dp(64),1));}
        root.addView(navigation);setContentView(root);
    }
    private void navigate(int selected){hideKeyboard();tab=selected;section="";query="";if(selected==2)category="Композиції";detailShowing=false;render();}
    private void hideKeyboard(){View focus=getCurrentFocus();if(focus!=null){((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(),0);focus.clearFocus();}}
    private void goBack(){hideKeyboard();if(detailShowing){render();return;}if(!section.isEmpty()){section="";render();return;}if(tab!=0){navigate(0);return;}super.onBackPressed();}
    @Override public void onBackPressed(){goBack();}
    private void mainMenu(){new AlertDialog.Builder(this).setItems(new String[]{"Черга відтворення","Джерела музики","Еквалайзер","Налаштування"},(d,at)->{switch(at){case 0->queue();case 1->openSources();case 2->showEqualizer();case 3->navigate(3);}}).show();}
    private void filterSources(){
        List<String> ids=new ArrayList<>(),names=new ArrayList<>();ids.add("");names.add("Усі джерела");ids.add("device");names.add("На пристрої");ids.add("files");names.add("Імпортовані файли");ids.add("radio");names.add("Радіо");
        try{for(Source source:app.sources.all()){ids.add(source.id);names.add(source.name);}}catch(Exception ignored){}
        new AlertDialog.Builder(this).setTitle("Джерела бібліотеки").setSingleChoiceItems(names.toArray(new String[0]),Math.max(0,ids.indexOf(sourceFilter)),(d,at)->{sourceFilter=ids.get(at);d.dismiss();render();}).setNegativeButton("Скасувати",null).show();
    }
    private List<Track> visibleLibrary(){return sourceFilter.isEmpty()?library:matching(t->t.sourceId.equals(sourceFilter));}
    private void reload(){app.io.execute(()->{List<Track> tracks=app.library.all();app.main.post(()->{if(isDestroyed())return;library=tracks;render();});});}
    private void render(){
        if(content==null)return;content.removeAllViews();detailShowing=false;heading.setText(new String[]{"SELFonium","Бібліотека","Пошук","Налаштування"}[tab]);
        subtitle.setText("Синхронізація…");subtitle.setVisibility(syncing?View.VISIBLE:View.GONE);backButton.setVisibility(section.isEmpty()?View.GONE:View.VISIBLE);
        for(int i=0;i<navigation.getChildCount();i++){LinearLayout cell=(LinearLayout)navigation.getChildAt(i);IconButton button=(IconButton)cell.getChildAt(0);button.active(i==tab);button.setBackground(Ui.ripple(this,i==tab?0xFF4A4458:Ui.SURFACE,18));((TextView)cell.getChildAt(2)).setTextColor(i==tab?Ui.TEXT:Ui.MUTED);}
        switch(tab){case 0->home();case 1->{if(section.equals("category"))libraryPage();else if(section.equals("playlists"))playlists();else libraryRoot();}case 2->libraryPage();case 3->{if(section.equals("sources"))sources();else settingsPage();}}
    }
    private LinearLayout scroll(){ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout column=Ui.column(this);Ui.pad(column,16,8);scroll.addView(column);content.addView(scroll);return column;}
    private void label(LinearLayout parent,String text){TextView label=Ui.title(this,text,24);Ui.pad(label,0,10);parent.addView(label);}
    private void home(){
        LinearLayout column=scroll();List<Track> visible=visibleLibrary();
        if(visible.isEmpty()){label(column,"Ваша музика");column.addView(Ui.text(this,"Під’єднайте медіасервер або додайте файли з пристрою.",14,Ui.MUTED));Ui.space(column,16);column.addView(Ui.button(this,"Додати джерело",true,this::openSources));return;}
        LinearLayout actions=Ui.row(this);homeAction(actions,"shuffle","Мікс треків",()->{settings.edit().putBoolean("shuffle",true).apply();play(visible,new Random().nextInt(visible.size()));});
        homeAction(actions,"album","Мікс альбомів",()->{Track album=visible.get(new Random().nextInt(visible.size()));settings.edit().putBoolean("shuffle",false).apply();play(albumTracks(album),0);});
        homeAction(actions,"queue","Черга",this::queue);column.addView(actions);Ui.space(column,12);
        List<Track> favorites=app.library.favorites();if(!sourceFilter.isEmpty())favorites.removeIf(t->!t.sourceId.equals(sourceFilter));
        if(!favorites.isEmpty()){sectionHeader(column,"Улюблені композиції",()->openCategory("Улюблене"));albumShelf(column,favorites,true);}
        LinkedHashMap<String,Track> albums=new LinkedHashMap<>();for(Track t:visible)albums.putIfAbsent(t.sourceId+"\u0000"+t.artist+"\u0000"+t.album,t);
        sectionHeader(column,"Альбоми",()->openCategory("Альбоми"));albumShelf(column,new ArrayList<>(albums.values()),false);
        List<Track> recent=app.library.recent();if(!sourceFilter.isEmpty())recent.removeIf(t->!t.sourceId.equals(sourceFilter));
        if(!recent.isEmpty()){sectionHeader(column,"Нещодавно відтворені",()->showTracks("Нещодавно відтворені",app.library.recent()));albumShelf(column,recent,true);}
        sectionHeader(column,"Композиції",()->openCategory("Композиції"));
        for(int i=0;i<Math.min(6,visible.size());i++){column.addView(trackRow(visible,i));}Ui.space(column,16);
    }
    private void sectionHeader(LinearLayout column,String name,Runnable action){LinearLayout row=Ui.row(this);Ui.pad(row,0,6);TextView title=Ui.title(this,name,24);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);row.addView(title,new LinearLayout.LayoutParams(0,-2,1));row.addView(icon("chevron","Переглянути: "+name,action),new LinearLayout.LayoutParams(dp(48),dp(48)));column.addView(row);}
    private void homeAction(LinearLayout row,String symbol,String name,Runnable action){LinearLayout tile=Ui.column(this);tile.setGravity(Gravity.CENTER);Ui.pad(tile,0,8);IconButton button=icon(symbol,name,action);button.active(true);tile.addView(button,new LinearLayout.LayoutParams(dp(48),dp(42)));TextView title=Ui.text(this,name,12,Ui.TEXT);title.setGravity(Gravity.CENTER);title.setMaxLines(2);tile.addView(title,new LinearLayout.LayoutParams(-1,-2));tile.setOnClickListener(v->action.run());row.addView(tile,new LinearLayout.LayoutParams(0,dp(76),1));}
    private void openCategory(String name){hideKeyboard();tab=1;category=name;section=name.equals("Плейлисти")?"playlists":"category";query="";render();}
    private void openSources(){tab=3;section="sources";query="";render();}
    private void libraryRoot(){
        LinearLayout column=scroll();LinearLayout summary=Ui.row(this);TextView count=Ui.text(this,visibleLibrary().size()+" композицій",13,Ui.MUTED);summary.addView(count,new LinearLayout.LayoutParams(0,-2,1));summary.addView(icon("server","Джерела музики",this::openSources),new LinearLayout.LayoutParams(dp(48),dp(48)));column.addView(summary);
        String[] titles={"Альбоми","Виконавці","Композиції","Плейлисти","Файли","Радіо","Улюблене","Офлайн","Джерела"};String[] icons={"album","artist","music","queue","folder","radio","heart","download","server"};
        for(int offset=0;offset<titles.length;offset+=3){LinearLayout row=Ui.row(this);for(int i=offset;i<offset+3;i++){String name=titles[i];Runnable open=name.equals("Джерела")?this::openSources:()->openCategory(name);
                LinearLayout tile=Ui.column(this);tile.setGravity(Gravity.CENTER);tile.setBackground(Ui.ripple(this,Ui.SURFACE,16));Ui.pad(tile,4,12);IconButton glyph=icon(icons[i],name,open);glyph.active(true);tile.addView(glyph,new LinearLayout.LayoutParams(dp(48),dp(48)));Ui.space(tile,8);TextView title=Ui.title(this,name,13);title.setGravity(Gravity.CENTER);title.setMaxLines(2);tile.addView(title,new LinearLayout.LayoutParams(-1,-2));tile.setOnClickListener(v->open.run());
                LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,dp(114),1);size.setMargins(i%3==0?0:dp(4),0,i%3==2?0:dp(4),0);row.addView(tile,size);}
            column.addView(row);Ui.space(column,8);}
    }
    private void albumShelf(LinearLayout parent,List<Track> tracks,boolean songs){
        HorizontalScrollView shelf=new HorizontalScrollView(this);shelf.setHorizontalScrollBarEnabled(false);LinearLayout cards=Ui.row(this);
        for(int i=0;i<Math.min(16,tracks.size());i++){Track track=tracks.get(i);final int at=i;LinearLayout card=albumCard(track,songs?track.title:track.album);
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(dp(144),-2);size.setMargins(0,0,dp(8),0);cards.addView(card,size);
            card.setOnClickListener(v->{if(songs)play(tracks,at);else showTracks(track.album,albumTracks(track));});}
        shelf.addView(cards);parent.addView(shelf);
    }
    private LinearLayout albumCard(Track track,String name){
        LinearLayout card=Ui.column(this);CoverView cover=new CoverView(this,track);cover.square();card.addView(cover,new LinearLayout.LayoutParams(-1,-2));Ui.space(card,8);
        TextView title=Ui.title(this,name,14);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(title);
        TextView artist=Ui.text(this,track.artist,12,Ui.MUTED);artist.setSingleLine(true);artist.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(artist);return card;
    }
    private List<Track> albumTracks(Track album){List<Track> tracks=matching(t->t.album.equals(album.album)&&t.artist.equals(album.artist)&&t.sourceId.equals(album.sourceId));tracks.sort(Comparator.comparingInt(t->t.number));return tracks;}
    private List<Track> matching(java.util.function.Predicate<Track> filter){List<Track> result=new ArrayList<>();for(Track t:library)if(filter.test(t))result.add(t);return result;}
    private EditText input(String hint,boolean secret){EditText input=new EditText(this);input.setHint(hint);input.setTextColor(Ui.TEXT);input.setHintTextColor(Ui.MUTED);input.setSingleLine(true);input.setTextSize(15);Ui.pad(input,12,8);input.setInputType(secret?129:1);return input;}
    private void libraryPage(){
        boolean searching=tab==2;if(!searching)heading.setText(category);
        LinearLayout page=Ui.column(this);Ui.pad(page,12,0);EditText search=null;
        if(searching){LinearLayout field=Ui.row(this);field.setBackground(Ui.shape(this,Ui.SURFACE,24));field.addView(icon("search","Шукати",()->{}),new LinearLayout.LayoutParams(dp(44),dp(48)));search=input("Композиції, альбоми, виконавці",false);search.setBackground(null);search.setText(query);field.addView(search,new LinearLayout.LayoutParams(0,dp(48),1));page.addView(field);Ui.space(page,8);
            HorizontalScrollView tabs=new HorizontalScrollView(this);tabs.setHorizontalScrollBarEnabled(false);LinearLayout choices=Ui.row(this);for(String name:List.of("Композиції","Альбоми","Виконавці")){TextView chip=Ui.text(this,name,14,category.equals(name)?Ui.ACCENT:Ui.MUTED);Ui.pad(chip,14,12);chip.setBackground(Ui.ripple(this,category.equals(name)?Ui.RAISED:Ui.BG,8));chip.setOnClickListener(v->{category=name;render();});choices.addView(chip);}tabs.addView(choices);page.addView(tabs);}
        LinearLayout tools=Ui.row(this);TextView count=Ui.text(this,"",12,Ui.MUTED);Ui.pad(count,4,0);tools.addView(count,new LinearLayout.LayoutParams(0,-2,1));tools.addView(icon("sort","Сортування",()->new AlertDialog.Builder(this).setTitle("Сортування").setSingleChoiceItems(new String[]{"За назвою","За виконавцем","За альбомом","За тривалістю"},sortOrder,(d,at)->{sortOrder=at;d.dismiss();render();}).show()),new LinearLayout.LayoutParams(dp(48),dp(48)));page.addView(tools);
        FrameLayout results=new FrameLayout(this);page.addView(results,new LinearLayout.LayoutParams(-1,0,1));content.addView(page);
        Runnable update=()->{
            results.removeAllViews();String needle=query.toLowerCase(Locale.ROOT);List<Track> base=category.equals("Улюблене")?app.library.favorites():category.equals("Офлайн")?app.library.offlineTracks():visibleLibrary();
            List<Track> tracks=new ArrayList<>();for(Track t:base){boolean type=(!category.equals("Файли")||t.sourceId.equals("device")||t.sourceId.equals("files"))&&(!category.equals("Радіо")||t.sourceId.equals("radio"));if(type&&(sourceFilter.isEmpty()||sourceFilter.equals(t.sourceId))&&(t.title+" "+t.artist+" "+t.album).toLowerCase(Locale.ROOT).contains(needle))tracks.add(t);}
            Comparator<Track> order=switch(sortOrder){case 1->Comparator.comparing(t->t.artist,String.CASE_INSENSITIVE_ORDER);case 2->Comparator.comparing((Track t)->t.album,String.CASE_INSENSITIVE_ORDER).thenComparingInt(t->t.number);case 3->Comparator.comparingLong(t->t.durationMs);default->Comparator.comparing(t->t.title,String.CASE_INSENSITIVE_ORDER);};tracks.sort(order);count.setText(tracks.size()+" композицій");
            if(category.equals("Альбоми")||category.equals("Виконавці")){
                boolean albums=category.equals("Альбоми");LinkedHashMap<String,List<Track>> groups=new LinkedHashMap<>();
                for(Track t:tracks)groups.computeIfAbsent(albums?t.album+"\u0000"+t.artist+"\u0000"+t.sourceId:t.artist,k->new ArrayList<>()).add(t);
                List<String> names=new ArrayList<>(groups.keySet());names.sort(String.CASE_INSENSITIVE_ORDER);count.setText(names.size()+(albums?" альбомів":" виконавців"));
                GridView grid=new GridView(this);grid.setNumColumns(getResources().getConfiguration().orientation==2?5:3);grid.setHorizontalSpacing(dp(4));grid.setVerticalSpacing(dp(16));grid.setPadding(0,dp(4),0,dp(12));grid.setClipToPadding(false);
                grid.setAdapter(new BaseAdapter(){public int getCount(){return names.size();}public Object getItem(int at){return names.get(at);}public long getItemId(int at){return at;}
                    public View getView(int at,View recycled,ViewGroup parent){List<Track> group=groups.get(names.get(at));Track track=group.get(0);LinearLayout card;
                        if(recycled instanceof LinearLayout){card=(LinearLayout)recycled;((CoverView)card.getChildAt(0)).bind(track);((TextView)card.getChildAt(2)).setText(albums?track.album:track.artist);}else card=albumCard(track,albums?track.album:track.artist);
                        ((TextView)card.getChildAt(3)).setText(albums?track.artist:group.size()+" композицій");card.setOnClickListener(v->{group.sort(Comparator.comparing((Track t)->t.album).thenComparingInt(t->t.number));showTracks(albums?track.album:track.artist,group);});return card;}});
                results.addView(grid);
            }else results.addView(trackList(tracks));
        };
        if(search!=null)search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){query=s.toString();update.run();}public void afterTextChanged(Editable e){}});update.run();
    }
    private View trackRow(List<Track> tracks,int at){TrackRowView row=new TrackRowView(this);row.bind(tracks.get(at),()->play(tracks,at),this::trackMenu);row.nowPlaying(tracks.get(at).id.equals(miniId));return row;}
    private View trackList(List<Track> tracks){
        if(tracks.isEmpty()){TextView empty=Ui.text(this,"Поки що немає композицій.\nДодайте джерело або змініть фільтр.",15,Ui.MUTED);empty.setGravity(Gravity.CENTER);Ui.pad(empty,20,24);return empty;}
        ListView list=new ListView(this);list.setDivider(null);list.setItemsCanFocus(true);list.setFastScrollEnabled(false);list.setAdapter(new BaseAdapter(){
            public int getCount(){return tracks.size();}public Track getItem(int at){return tracks.get(at);}public long getItemId(int at){return at;}
            public View getView(int at,View old,ViewGroup parent){TrackRowView row=old instanceof TrackRowView?(TrackRowView)old:new TrackRowView(MainActivity.this);row.bind(tracks.get(at),()->play(tracks,at),MainActivity.this::trackMenu);row.nowPlaying(tracks.get(at).id.equals(miniId));return row;}
        });return list;
    }
    private ListView plainList(List<String> values){ListView list=new ListView(this);list.setDividerHeight(1);list.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,values){@Override public View getView(int p,View old,ViewGroup parent){TextView view=(TextView)super.getView(p,old,parent);view.setTextColor(Ui.TEXT);view.setMinHeight(dp(64));return view;}});return list;}
    private void showTracks(String name,List<Track> tracks){
        hideKeyboard();detailShowing=true;backButton.setVisibility(View.VISIBLE);content.removeAllViews();heading.setText(name);subtitle.setVisibility(View.GONE);LinearLayout page=Ui.column(this);Ui.pad(page,16,0);
        LinearLayout hero=Ui.row(this);Ui.pad(hero,0,10);
        if(!tracks.isEmpty()){CoverView cover=new CoverView(this,tracks.get(0));hero.addView(cover,new LinearLayout.LayoutParams(dp(84),dp(84)));}
        LinearLayout description=Ui.column(this);Ui.pad(description,14,0);TextView title=Ui.title(this,name,20);title.setMaxLines(2);description.addView(title);
        long length=0;for(Track track:tracks)length+=track.durationMs;description.addView(Ui.text(this,tracks.size()+" композицій · "+Protocol.time(length),12,Ui.MUTED));hero.addView(description,new LinearLayout.LayoutParams(0,-2,1));page.addView(hero);
        LinearLayout buttons=Ui.row(this);buttons.addView(Ui.button(this,"Відтворити",true,()->{settings.edit().putBoolean("shuffle",false).apply();play(tracks,0);}),new LinearLayout.LayoutParams(0,-2,1));
        buttons.addView(icon("shuffle","Перемішати",()->{settings.edit().putBoolean("shuffle",true).apply();play(tracks,0);}));page.addView(buttons);Ui.space(page,8);page.addView(trackList(tracks),new LinearLayout.LayoutParams(-1,0,1));content.addView(page);
    }
    private boolean ready(){if(player==null){message("Плеєр під’єднується…");return false;}return true;}
    private void play(List<Track> tracks,int at){if(!ready()||tracks.isEmpty())return;hideKeyboard();List<MediaItem> items=new ArrayList<>();for(Track track:tracks)items.add(PlaybackService.item(track));player.setMediaItems(items,at,0);player.prepare();player.play();}
    private void toggle(){if(!ready())return;if(player.isPlaying())player.pause();else if(player.getMediaItemCount()>0){player.prepare();player.play();}else{openCategory("Композиції");}}
    private void trackMenu(Track track){
        if(actionSheet!=null)actionSheet.dismiss();actionSheet=new Dialog(this);actionSheet.requestWindowFeature(Window.FEATURE_NO_TITLE);Dialog sheet=actionSheet;
        LinearLayout page=Ui.column(this);Ui.pad(page,16,12);page.setBackground(Ui.shape(this,Ui.SURFACE,24));
        View handle=new View(this);handle.setBackground(Ui.shape(this,Ui.MUTED,2));LinearLayout.LayoutParams handleSize=new LinearLayout.LayoutParams(dp(32),dp(4));handleSize.gravity=Gravity.CENTER_HORIZONTAL;handleSize.bottomMargin=dp(16);page.addView(handle,handleSize);
        LinearLayout header=Ui.row(this);header.addView(new CoverView(this,track),new LinearLayout.LayoutParams(dp(52),dp(52)));LinearLayout labels=Ui.column(this);Ui.pad(labels,12,0);TextView title=Ui.title(this,track.title,16);title.setMaxLines(2);labels.addView(title);Ui.space(labels,4);labels.addView(Ui.text(this,track.artist,13,Ui.MUTED));header.addView(labels,new LinearLayout.LayoutParams(0,-2,1));page.addView(header);Ui.space(page,12);
        sheetAction(page,sheet,"play","Відтворити",()->play(List.of(track),0));sheetAction(page,sheet,"next","Відтворити наступною",()->{if(ready())player.addMediaItem(Math.max(0,player.getCurrentMediaItemIndex()+1),PlaybackService.item(track));});
        sheetAction(page,sheet,"queue","Додати в чергу",()->{if(ready())player.addMediaItem(PlaybackService.item(track));});sheetAction(page,sheet,"heart",app.library.favorite(track.id)?"Прибрати з улюбленого":"Додати в улюблене",()->{app.library.toggleFavorite(track.id);updatePlayer();});
        sheetAction(page,sheet,"plus","Додати до плейлиста",()->choosePlaylist(track));boolean offline=!app.library.offline(track.id).isEmpty();
        sheetAction(page,sheet,"download",offline?"Видалити офлайн-копію":"Завантажити офлайн",()->{if(!offline)download(track);else new AlertDialog.Builder(this).setMessage("Видалити завантажену копію? Оригінал залишиться у джерелі.").setNegativeButton("Скасувати",null).setPositiveButton("Видалити",(x,y)->{new File(app.library.offline(track.id)).delete();app.library.offline(track.id,"");render();}).show();});
        sheetAction(page,sheet,"album","Перейти до альбому",()->{if(playerDialog!=null)playerDialog.dismiss();showTracks(track.album,albumTracks(track));});
        sheetAction(page,sheet,"more","Інформація",()->new AlertDialog.Builder(this).setTitle(track.title).setMessage(track.artist+"\n"+track.album+"\nТривалість: "+Protocol.time(track.durationMs)+"\nДжерело: "+sourceName(track.sourceId)).setPositiveButton("Готово",null).show());
        ScrollView scroll=new ScrollView(this);scroll.addView(page);sheet.setContentView(scroll);sheet.show();Window window=sheet.getWindow();window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));window.getDecorView().setPadding(0,0,0,0);window.setGravity(Gravity.BOTTOM);window.setLayout(-1,Math.min(dp(608),(int)(getResources().getDisplayMetrics().heightPixels*.82f)));window.setNavigationBarColor(Ui.SURFACE);
    }
    private void sheetAction(LinearLayout page,Dialog sheet,String symbol,String title,Runnable action){
        Runnable run=()->{sheet.dismiss();action.run();};LinearLayout row=Ui.row(this);row.setBackground(Ui.ripple(this,Ui.SURFACE,10));row.addView(icon(symbol,title,run),new LinearLayout.LayoutParams(dp(48),dp(48)));TextView label=Ui.text(this,title,14,Ui.TEXT);Ui.pad(label,8,0);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));row.setOnClickListener(v->run.run());page.addView(row,new LinearLayout.LayoutParams(-1,dp(48)));
    }
    private String sourceName(String id){if(id.equals("device"))return "На пристрої";if(id.equals("files"))return "Імпортовані файли";if(id.equals("radio"))return "Радіо";try{Source s=app.sources.get(id);return s==null?"Видалене джерело":s.name;}catch(Exception e){return "Джерело";}}
    private void download(Track track){if(track.sourceId.equals("radio")){message("Радіопотік не має кінця й не завантажується як файл");return;}notificationPermission();startForegroundService(new Intent(this,DownloadService.class).putExtra("track",track.id));message("Додано до завантажень");}
    private void playlists(){
        heading.setText("Плейлисти");LinearLayout column=scroll();settingRow(column,"plus","Створити плейлист","",()->newPlaylist(null));List<String[]> entries=app.library.playlists();
        if(entries.isEmpty()){Ui.space(column,32);column.addView(Ui.title(this,"Ваші добірки",20));Ui.space(column,8);column.addView(Ui.text(this,"Створіть плейлист і додавайте композиції через меню поруч із треком.",14,Ui.MUTED));}
        for(String[] playlist:entries){long id=Long.parseLong(playlist[0]);List<Track> tracks=app.library.playlist(id);LinearLayout card=Ui.row(this);Ui.pad(card,0,8);CoverView art=new CoverView(this,tracks.isEmpty()?null:tracks.get(0));card.addView(art,new LinearLayout.LayoutParams(dp(56),dp(56)));LinearLayout text=Ui.column(this);Ui.pad(text,12,0);text.addView(Ui.title(this,playlist[1],16));Ui.space(text,4);text.addView(Ui.text(this,playlist[2]+" композицій",12,Ui.MUTED));card.addView(text,new LinearLayout.LayoutParams(0,-2,1));
            Runnable remove=()->new AlertDialog.Builder(this).setTitle("Видалити «"+playlist[1]+"»?").setMessage("Аудіофайли залишаться в бібліотеці.").setNegativeButton("Скасувати",null).setPositiveButton("Видалити",(d,w)->{app.library.deletePlaylist(id);render();}).show();card.addView(icon("more","Меню плейлиста",()->new AlertDialog.Builder(this).setItems(new String[]{"Відкрити","Видалити"},(d,at)->{if(at==0)playlistPage(id,playlist[1]);else remove.run();}).show()),new LinearLayout.LayoutParams(dp(48),dp(48)));card.setOnClickListener(v->playlistPage(id,playlist[1]));card.setOnLongClickListener(v->{remove.run();return true;});column.addView(card);}
    }
    private void playlistPage(long id,String name){List<Track> tracks=app.library.playlist(id);showTracks(name,tracks);}
    private void newPlaylist(Track initial){EditText name=input("Назва плейлиста",false);new AlertDialog.Builder(this).setTitle("Новий плейлист").setView(name).setNegativeButton("Скасувати",null).setPositiveButton("Створити",(d,w)->{try{long id=app.library.createPlaylist(name.getText().toString());if(initial!=null)app.library.addToPlaylist(id,initial.id);render();}catch(Exception e){message("Назва порожня або вже використовується");}}).show();}
    private void choosePlaylist(Track track){List<String[]> playlists=app.library.playlists();List<String> names=new ArrayList<>();names.add("＋ Новий плейлист");for(String[] p:playlists)names.add(p[1]);new AlertDialog.Builder(this).setTitle("Додати до плейлиста").setItems(names.toArray(new String[0]),(d,at)->{if(at==0)newPlaylist(track);else{app.library.addToPlaylist(Long.parseLong(playlists.get(at-1)[0]),track.id);message("Додано");}}).show();}
    private void sources(){
        heading.setText("Джерела музики");LinearLayout column=scroll();
        try{List<Source> sources=app.sources.all();if(!sources.isEmpty())label(column,"Медіасервери");for(Source source:sources){LinearLayout card=Ui.row(this);card.setBackground(Ui.ripple(this,Ui.SURFACE,16));Ui.pad(card,4,10);card.addView(icon("server",source.name,()->showTracks(source.name,matching(t->source.id.equals(t.sourceId)))),new LinearLayout.LayoutParams(dp(48),dp(48)));LinearLayout text=Ui.column(this);Ui.pad(text,8,0);text.addView(Ui.title(this,source.name,16));Ui.space(text,5);text.addView(Ui.text(this,source.type.name()+" · "+matching(t->source.id.equals(t.sourceId)).size()+" композицій",12,Ui.MUTED));card.addView(text,new LinearLayout.LayoutParams(0,-2,1));card.addView(icon("refresh","Синхронізувати "+source.name,()->sync(source)),new LinearLayout.LayoutParams(dp(48),dp(48)));card.addView(icon("more","Меню джерела",()->sourceActions(source)),new LinearLayout.LayoutParams(dp(48),dp(48)));card.setOnClickListener(v->showTracks(source.name,matching(t->source.id.equals(t.sourceId))));column.addView(card);Ui.space(column,8);}}catch(Exception e){message(e.getMessage());}
        settingRow(column,"plus","Під’єднати сервер","Subsonic, Jellyfin, Plex та інші",()->sourceDialog(null));label(column,"На пристрої");settingRow(column,"music","Сканувати музику","Аудіофайли з медіатеки Android",this::scan);settingRow(column,"folder","Додати файли","Вибрати через файловий менеджер",this::importFiles);settingRow(column,"radio","Додати радіостанцію","Пряме посилання на аудіопотік",this::radio);
    }
    private void sourceActions(Source source){new AlertDialog.Builder(this).setTitle(source.name).setItems(new String[]{"Композиції джерела","Редагувати","Видалити джерело"},(d,at)->{if(at==0)showTracks(source.name,matching(t->source.id.equals(t.sourceId)));else if(at==1)sourceDialog(source);else new AlertDialog.Builder(this).setMessage("Видалити джерело, його записи та офлайн-копії?").setNegativeButton("Скасувати",null).setPositiveButton("Видалити",(x,y)->{for(Track t:matching(t->source.id.equals(t.sourceId))){String path=app.library.offline(t.id);if(!path.isEmpty())new File(path).delete();}app.sources.remove(source.id);app.library.removeSource(source.id);reload();}).show();}).show();}
    private void sourceDialog(Source existing){
        LinearLayout form=Ui.column(this);Ui.pad(form,20,12);Spinner type=new Spinner(this);Source.Type[] types=Source.Type.values();type.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,types));form.addView(type);
        EditText name=input("Назва джерела",false),url=input("https://music.example.org",false),user=input("Ім’я користувача",false),password=input("Пароль; для Plex — X-Plex-Token",true);
        form.addView(name);form.addView(url);form.addView(user);form.addView(password);form.addView(Ui.text(this,"Plex: адреса самого Plex Media Server і його token. Kodi: адреса HTTP-сервера. WebDAV: адреса папки з музикою.",12,Ui.MUTED));
        if(existing!=null){name.setText(existing.name);url.setText(existing.baseUrl);user.setText(existing.username);password.setText(existing.password);type.setSelection(existing.type.ordinal());type.setEnabled(false);}
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(existing==null?"Нове джерело":"Редагувати джерело").setView(form).setNegativeButton("Скасувати",null).setPositiveButton("Зберегти й синхронізувати",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{
            if(name.getText().toString().trim().isEmpty())throw new IllegalArgumentException("Введіть назву");
            Source source=new Source(existing==null?UUID.randomUUID().toString():existing.id,name.getText().toString().trim(),types[type.getSelectedItemPosition()],url.getText().toString(),user.getText().toString(),password.getText().toString());
            app.sources.save(source);dialog.dismiss();sync(source);
        }catch(Exception e){message(e.getMessage());}}));dialog.show();
    }
    private void sync(Source source){if(syncing){message("Дочекайтеся поточної синхронізації");return;}syncing=true;render();app.io.execute(()->{try{
        List<Track> tracks=app.providers.synchronize(source,status->app.main.post(()->{if(!isDestroyed())subtitle.setText(status);}));app.library.merge(source.id,tracks,true);app.artwork.retryFailures();
        app.main.post(()->{syncing=false;message("Синхронізовано: "+tracks.size()+" композицій");reload();});
    }catch(Exception e){app.main.post(()->{syncing=false;message("Не вдалося синхронізувати. "+safeError(e));render();});}});}
    private String safeError(Exception e){return e instanceof java.io.IOException?"Перевірте адресу, мережу й облікові дані.":"Перевірте налаштування та сумісність API сервера.";}
    private void scan(){String permission=Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_AUDIO:Manifest.permission.READ_EXTERNAL_STORAGE;
        if(checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{permission},100);return;}subtitle.setVisibility(View.VISIBLE);subtitle.setText("Сканування пристрою…");app.io.execute(()->{try{List<Track> tracks=LocalMusic.scan(this);app.library.merge("device",tracks,true);app.main.post(()->{message("Знайдено "+tracks.size()+" композицій");reload();});}catch(Exception e){app.main.post(()->message("Не вдалося прочитати медіатеку"));}});}
    private void importFiles(){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(intent,101);}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==100){if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)scan();else message("Можна додати окремі файли через «Відкрити аудіофайли»");}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=101||result!=RESULT_OK||data==null)return;List<Uri> uris=new ArrayList<>();if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());else if(data.getData()!=null)uris.add(data.getData());
        for(Uri uri:uris)try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
        app.io.execute(()->{try{List<Track> tracks=new ArrayList<>();for(Uri uri:uris)tracks.add(LocalMusic.importUri(this,uri));app.library.merge("files",tracks,false);app.main.post(this::reload);}catch(Exception e){app.main.post(()->message("Не вдалося імпортувати вибрані файли"));}});}
    private void radio(){LinearLayout form=Ui.column(this);EditText name=input("Назва станції",false),url=input("Пряме http(s) посилання на аудіопотік",false);form.addView(name);form.addView(url);new AlertDialog.Builder(this).setTitle("Інтернет-радіо").setView(form).setNegativeButton("Скасувати",null).setPositiveButton("Додати",(d,w)->{try{String uri=url.getText().toString().trim();java.net.URI parsed=java.net.URI.create(uri);if(!List.of("http","https").contains(parsed.getScheme())||parsed.getHost()==null||parsed.getUserInfo()!=null)throw new IllegalArgumentException();String id=UUID.randomUUID().toString();Track t=new Track("radio:"+id,"radio",id,name.getText().toString(),"Інтернет-радіо","Радіо",uri,0,0);app.library.merge("radio",List.of(t),false);reload();}catch(Exception e){message("Вкажіть коректне пряме посилання на потік");}}).show();}
    private void notificationPermission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},102);}
    private void showPlayer(){
        if(!ready()||player.getCurrentMediaItem()==null){openCategory("Композиції");return;}
        if(playerDialog!=null&&playerDialog.isShowing())return;
        playerDialog=Ui.screen(this);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Ui.BG);LinearLayout page=Ui.column(this);Ui.pad(page,16,0);scroll.addView(page);
        scroll.setOnApplyWindowInsetsListener((view,insets)->{view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        LinearLayout top=Ui.row(this);top.addView(icon("down","Згорнути плеєр",()->playerDialog.dismiss()));TextView caption=Ui.text(this,"Зараз відтворюється",14,Ui.MUTED);caption.setGravity(Gravity.CENTER);top.addView(caption,new LinearLayout.LayoutParams(0,-2,1));top.addView(icon("more","Дії з композицією",()->{MediaItem item=player.getCurrentMediaItem();if(item!=null){Track track=app.library.get(item.mediaId);if(track!=null)trackMenu(track);}}));page.addView(top);Ui.space(page,8);
        playerCover=new FrameLayout(this){@Override protected void onMeasure(int width,int height){int size=Math.min(MeasureSpec.getSize(width),dp(420));super.onMeasure(MeasureSpec.makeMeasureSpec(size,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(size,MeasureSpec.EXACTLY));}};
        LinearLayout.LayoutParams coverSize=new LinearLayout.LayoutParams(-1,-2);coverSize.gravity=Gravity.CENTER_HORIZONTAL;page.addView(playerCover,coverSize);Ui.space(page,16);
        LinearLayout titleRow=Ui.row(this);LinearLayout labels=Ui.column(this);playerTitle=Ui.title(this,"",22);playerTitle.setSingleLine(true);playerTitle.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);playerTitle.setMarqueeRepeatLimit(-1);playerTitle.setSelected(true);labels.addView(playerTitle);Ui.space(labels,4);playerArtist=Ui.text(this,"",15,Ui.ACCENT);playerArtist.setSingleLine(true);playerArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(playerArtist);Ui.space(labels,5);playerAlbum=Ui.text(this,"",13,Ui.MUTED);playerAlbum.setSingleLine(true);playerAlbum.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(playerAlbum);titleRow.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        favoriteButton=icon("heart","Додати або прибрати з улюбленого",()->{MediaItem item=player.getCurrentMediaItem();if(item!=null){app.library.toggleFavorite(item.mediaId);updatePlayer();}});titleRow.addView(favoriteButton);page.addView(titleRow);Ui.space(page,24);
        playerSeek=new SeekBar(this);playerSeek.setMax(1000);playerSeek.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));playerSeek.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));playerSeek.setContentDescription("Позиція відтворення");page.addView(playerSeek);
        LinearLayout times=Ui.row(this);playerTime=Ui.text(this,"",12,Ui.MUTED);playerRemaining=Ui.text(this,"",12,Ui.MUTED);times.addView(playerTime,new LinearLayout.LayoutParams(0,-2,1));times.addView(playerRemaining);Ui.pad(times,8,0);page.addView(times);
        playerSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int value,boolean user){if(user&&player!=null&&player.getDuration()>0)updateTimes(player.getDuration()*value/1000,player.getDuration());}public void onStartTrackingTouch(SeekBar s){seeking=true;}public void onStopTrackingTouch(SeekBar s){if(player!=null&&player.getDuration()>0)player.seekTo(player.getDuration()*s.getProgress()/1000);seeking=false;}});
        Ui.space(page,16);LinearLayout controls=Ui.row(this);controls.setGravity(Gravity.CENTER);
        shuffleButton=icon("shuffle","Перемішування",()->{settings.edit().putBoolean("shuffle",!settings.getBoolean("shuffle",false)).apply();updatePlayer();});controls.addView(shuffleButton,new LinearLayout.LayoutParams(0,dp(52),1));
        controls.addView(icon("previous","Попередня композиція",()->player.seekToPrevious()),new LinearLayout.LayoutParams(0,dp(56),1));playerPlay=icon("play","Відтворити",this::toggle);playerPlay.primary();controls.addView(playerPlay,new LinearLayout.LayoutParams(dp(64),dp(64)));
        controls.addView(icon("next","Наступна композиція",()->player.seekToNext()),new LinearLayout.LayoutParams(0,dp(56),1));repeatButton=icon("repeat","Повтор",this::repeat);controls.addView(repeatButton,new LinearLayout.LayoutParams(0,dp(52),1));page.addView(controls);Ui.space(page,16);
        LinearLayout extra=Ui.row(this);extra.setGravity(Gravity.CENTER);extra.addView(icon("clock","Таймер сну",this::sleep),new LinearLayout.LayoutParams(0,dp(48),1));extra.addView(icon("equalizer","Еквалайзер",this::showEqualizer),new LinearLayout.LayoutParams(0,dp(48),1));extra.addView(icon("queue","Черга відтворення",this::queue),new LinearLayout.LayoutParams(0,dp(48),1));page.addView(extra);
        playerDialog.setContentView(scroll);playerDialog.setOnDismissListener(d->{playerSeek=null;playerTitle=null;playerCover=null;displayedId="";seeking=false;});playerDialog.show();playerDialog.getWindow().setLayout(-1,-1);displayedId="";updatePlayer();
    }
    private void highlightPlaying(View view,String id){
        if(view instanceof TrackRowView row){row.nowPlaying(id.equals(row.getTag()));return;}
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)highlightPlaying(group.getChildAt(i),id);
    }
    private void updateTimes(long position,long duration){playerTime.setText(Protocol.time(position));playerRemaining.setText("−"+Protocol.time(Math.max(0,duration-position)));}
    private void updatePlayer(){
        if(player==null)return;MediaItem item=player.getCurrentMediaItem();boolean playing=player.isPlaying();miniPlay.icon(playing?"pause":"play");miniPlay.setContentDescription(playing?"Пауза":"Відтворити");
        if(item==null){miniTitle.setText("Оберіть композицію");miniArtist.setText("Відкрити бібліотеку");miniProgress.setProgress(0);miniCover.bind(null);miniId="";highlightPlaying(content,"");if(playerDialog!=null&&playerDialog.isShowing())playerDialog.dismiss();return;}
        miniTitle.setText(item.mediaMetadata.title);miniArtist.setText(item.mediaMetadata.artist);long duration=Math.max(0,player.getDuration()),position=Math.max(0,player.getCurrentPosition());int percent=duration>0?(int)(position*1000/duration):0;miniProgress.setProgress(percent);
        if(!miniId.equals(item.mediaId)){miniId=item.mediaId;miniCover.bind(app.library.get(item.mediaId));highlightPlaying(content,miniId);}
        if(playerTitle!=null){playerTitle.setText(item.mediaMetadata.title);playerArtist.setText(item.mediaMetadata.artist);playerAlbum.setText(item.mediaMetadata.albumTitle);playerPlay.icon(playing?"pause":"play");playerPlay.setContentDescription(playing?"Пауза":"Відтворити");
            if(!seeking){playerSeek.setProgress(percent);updateTimes(position,duration);}playerSeek.setEnabled(duration>0&&player.isCurrentMediaItemSeekable());
            shuffleButton.active(settings.getBoolean("shuffle",false));int repeat=settings.getInt("repeat",0);repeatButton.active(repeat!=0);repeatButton.icon(repeat==1?"repeat1":"repeat");repeatButton.setContentDescription(new String[]{"Повтор вимкнено","Повтор композиції","Повтор черги"}[repeat]);favoriteButton.active(app.library.favorite(item.mediaId));
            if(!displayedId.equals(item.mediaId)){displayedId=item.mediaId;playerCover.removeAllViews();playerCover.addView(new CoverView(this,app.library.get(item.mediaId)),new FrameLayout.LayoutParams(-1,-1));}}
    }
    private void repeat(){int repeat=(settings.getInt("repeat",0)+1)%3;settings.edit().putInt("repeat",repeat).apply();updatePlayer();message(new String[]{"Повтор вимкнено","Повтор композиції","Повтор черги"}[repeat]);}
    private void queue(){if(!ready())return;List<String> names=new ArrayList<>();for(int i=0;i<player.getMediaItemCount();i++)names.add((i==player.getCurrentMediaItemIndex()?"▶ ":"")+player.getMediaItemAt(i).mediaMetadata.title);
        ListView list=plainList(names);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Черга · утримуйте для видалення").setView(list).setPositiveButton("Готово",null).create();list.setOnItemClickListener((p,v,at,id)->{player.seekTo(at,0);player.prepare();player.play();dialog.dismiss();});list.setOnItemLongClickListener((p,v,at,id)->{player.removeMediaItem(at);dialog.dismiss();return true;});dialog.show();}
    private void sleep(){String[] values={"Вимкнути","15 хвилин","30 хвилин","45 хвилин","60 хвилин","90 хвилин"};int[] minutes={0,15,30,45,60,90};new AlertDialog.Builder(this).setTitle("Таймер сну").setItems(values,(d,at)->{settings.edit().putLong("sleepUntil",at==0?0:System.currentTimeMillis()+minutes[at]*60000L).apply();message(values[at]);}).show();}
    private void showEqualizer(){if(equalizerDialog==null||!equalizerDialog.isShowing())equalizerDialog=EqualizerDialog.show(this);}
    private void settingRow(LinearLayout parent,String symbol,String name,String summary,Runnable action){
        LinearLayout row=Ui.row(this);row.setBackground(Ui.ripple(this,Ui.BG,8));Ui.pad(row,0,10);IconButton glyph=icon(symbol,name,action);glyph.active(true);row.addView(glyph,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout labels=Ui.column(this);Ui.pad(labels,10,0);labels.addView(Ui.title(this,name,16));if(!summary.isEmpty()){Ui.space(labels,5);labels.addView(Ui.text(this,summary,12,Ui.MUTED));}row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));row.addView(icon("chevron",name,action),new LinearLayout.LayoutParams(dp(48),dp(48)));row.setOnClickListener(v->action.run());parent.addView(row);
    }
    private void settingsPage(){LinearLayout column=scroll();label(column,"Медіатека");settingRow(column,"server","Джерела музики","Сервери, файли та синхронізація",this::openSources);settingRow(column,"download","Офлайн","Завантажені композиції",()->openCategory("Офлайн"));
        label(column,"Відтворення");settingRow(column,"equalizer","Еквалайзер","Частоти, підсилення та пресети",this::showEqualizer);settingRow(column,"play","Швидкість",settings.getFloat("speed",1)+"×",()->{String[] speeds={"0.75×","1×","1.25×","1.5×","2×"};float[] values={.75f,1f,1.25f,1.5f,2f};new AlertDialog.Builder(this).setTitle("Швидкість відтворення").setItems(speeds,(d,at)->{settings.edit().putFloat("speed",values[at]).apply();render();}).show();});settingRow(column,"clock","Таймер сну","Зупинка відтворення за таймером",this::sleep);
        Ui.space(column,24);TextView version=Ui.text(this,"SELFonium 0.3.0",12,Ui.MUTED);Ui.pad(version,16,8);column.addView(version);
    }
    private void message(String text){if(!isDestroyed())Toast.makeText(this,text==null?"Помилка":text,Toast.LENGTH_LONG).show();}
}
