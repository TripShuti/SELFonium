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
import app.selfonium.R;
import app.selfonium.core.Protocol;
import app.selfonium.core.Source;
import app.selfonium.core.Track;
import app.selfonium.data.DownloadService;
import app.selfonium.data.LocalMusic;
import app.selfonium.data.Library;
import app.selfonium.playback.PlaybackService;
import java.io.File;
import java.util.*;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class MainActivity extends Activity {
    private UpdateDialog updateDialog;private LinearLayout updateBanner;
    private final java.util.function.Consumer<app.selfonium.update.UpdateManager.State> updatesChanged=this::onUpdateState;
    private SelfoniumApp app;private MediaController player;private ListenableFuture<MediaController> controllerFuture;
    private SharedPreferences settings;private final Handler handler=new Handler(Looper.getMainLooper());
    private LinearLayout root,mini,playerCard;private IconButton backButton;private boolean detailShowing;private int sortOrder;private FrameLayout content;private TextView heading,subtitle,miniTitle,miniArtist;private IconButton miniPlay,playerPlay,shuffleButton,repeatButton,favoriteButton;private CoverView miniCover;private ProgressBar miniProgress;private String miniId="";private boolean seeking;
    private TextView playerTitle,playerArtist,playerAlbum,playerTime,playerRemaining;private SeekBar playerSeek;private FrameLayout playerCover;private CoverView playerArtwork;private long activePlaylist=-1;
    private Dialog playerDialog,equalizerDialog,actionSheet,queueDialog,appearanceDialog;private String displayedId="",query="",category="Композиції",section="",sourceFilter="";
    private final Deque<Runnable> detailHistory=new ArrayDeque<>();private Runnable detailPage;
    private LinearLayout navigation;private String libraryCategory="Альбоми";private int tab;private List<Track> library=new ArrayList<>();private boolean syncing;
    private final java.util.function.Consumer<String> socialChanged=status->{
        if(isDestroyed())return;if(!status.isEmpty())message(status);updatePlayer();
        if(!detailShowing&&(section.equals("playlists")||category.equals("Улюблене")))reload();
    };
    private Track pendingArtwork;
    private final Runnable bindArtwork=()->{Track track=pendingArtwork;if(track==null)return;miniCover.bind(track);if(playerArtwork!=null)playerArtwork.bind(track);};
    private void queueArtwork(Track track){pendingArtwork=track;handler.removeCallbacks(bindArtwork);handler.postDelayed(bindArtwork,300);}
    private final Runnable refreshSocial=new Runnable(){public void run(){app.social.refreshAll();handler.postDelayed(this,60_000);}};
    private final Runnable progress=new Runnable(){@Override public void run(){updatePlayer();handler.postDelayed(this,500);}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);Ui.load(this);app=(SelfoniumApp)getApplication();settings=getSharedPreferences("player",MODE_PRIVATE);
        if(state!=null){tab=state.getInt("tab");category=state.getString("category","Композиції");section=state.getString("section","");query=state.getString("query","");libraryCategory=state.getString("libraryCategory","Альбоми");}
        build();reload();
        controllerFuture=new MediaController.Builder(this,new SessionToken(this,new ComponentName(this,PlaybackService.class))).buildAsync();
        controllerFuture.addListener(()->{
            try{player=controllerFuture.get();player.addListener(new Player.Listener(){
                @Override public void onEvents(Player p,Player.Events events){updatePlayer();}
                @Override public void onPlayerError(PlaybackException e){message("Не вдалося відтворити. Перевірте мережу або доступ до файлу й натисніть відтворення, щоб повторити.");}
            });updatePlayer();}catch(Exception e){message("Не вдалося під’єднати плеєр");}
        },command->handler.post(command));
    }
    @Override protected void onStart(){super.onStart();app.updates.foreground(true);app.updates.listen(updatesChanged);app.social.listen(socialChanged);app.social.requestFlush();app.social.refreshAll();handler.postDelayed(refreshSocial,60_000);handler.post(progress);}
    @Override protected void onStop(){app.updates.unlisten(updatesChanged);app.updates.foreground(false);app.social.unlisten(socialChanged);handler.removeCallbacks(refreshSocial);handler.removeCallbacks(progress);super.onStop();}
    @Override protected void onDestroy(){if(updateDialog!=null)updateDialog.dismiss();handler.removeCallbacksAndMessages(null);if(playerDialog!=null)playerDialog.dismiss();if(equalizerDialog!=null)equalizerDialog.dismiss();if(actionSheet!=null)actionSheet.dismiss();if(queueDialog!=null)queueDialog.dismiss();if(appearanceDialog!=null)appearanceDialog.dismiss();if(controllerFuture!=null)MediaController.releaseFuture(controllerFuture);super.onDestroy();}
    @Override protected void onResume(){super.onResume();if(updateDialog!=null)updateDialog.onResume();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus&&app!=null)updatesChanged.accept(app.updates.state());}
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("tab",tab);state.putString("category",category);state.putString("section",section);state.putString("query",query);state.putString("libraryCategory",libraryCategory);super.onSaveInstanceState(state);}
    private boolean landscape(){return getResources().getConfiguration().orientation==2;}
    private int dp(float n){return Ui.dp(this,n);}
    private IconButton icon(String name,String description,Runnable action){return new IconButton(this,name,description,action);}
    private void build(){
        Ui.edgeToEdge(getWindow());root=Ui.column(this);root.setBackgroundColor(Ui.BG);Ui.insets(root);
        LinearLayout top=Ui.row(this);Ui.pad(top,8,0);top.setMinimumHeight(dp(landscape()?48:56));
        backButton=icon("back","Назад",this::goBack);top.addView(backButton,new LinearLayout.LayoutParams(dp(48),dp(48)));backButton.setVisibility(View.GONE);
        LinearLayout titles=Ui.column(this);Ui.pad(titles,8,0);heading=Ui.title(this,"SELFonium",20);heading.setSingleLine(true);heading.setEllipsize(android.text.TextUtils.TruncateAt.END);titles.addView(heading);
        subtitle=Ui.text(this,"",12,Ui.MUTED);subtitle.setVisibility(View.GONE);titles.addView(subtitle);top.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        top.addView(icon("filter","Фільтр джерел",this::filterSources),new LinearLayout.LayoutParams(dp(48),dp(48)));
        top.addView(icon("more","Меню",this::mainMenu),new LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(top);
        content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        playerCard=Ui.column(this);playerCard.setVisibility(View.GONE);playerCard.setBackground(Ui.shape(this,Ui.RAISED,12));playerCard.setClipToOutline(true);
        mini=Ui.row(this);Ui.pad(mini,8,landscape()?2:6);miniCover=new CoverView(this);miniCover.urgent();mini.addView(miniCover,new LinearLayout.LayoutParams(dp(40),dp(40)));miniCover.setOnClickListener(v->showPlayer());
        LinearLayout miniText=Ui.column(this);Ui.pad(miniText,10,0);miniTitle=Ui.title(this,"Оберіть композицію",14);miniTitle.setSingleLine(true);miniTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        miniArtist=Ui.text(this,"Відкрити бібліотеку",12,Ui.MUTED);miniArtist.setSingleLine(true);miniArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);miniText.addView(miniTitle);Ui.space(miniText,3);miniText.addView(miniArtist);
        mini.addView(miniText,new LinearLayout.LayoutParams(0,-2,1));miniText.setOnClickListener(v->showPlayer());mini.setOnClickListener(v->showPlayer());
        miniPlay=icon("play","Відтворити",this::toggle);mini.addView(miniPlay,new LinearLayout.LayoutParams(dp(48),dp(48)));playerCard.addView(mini);
        miniProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);miniProgress.setMax(1000);miniProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));miniProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.RAISED));playerCard.addView(miniProgress,new LinearLayout.LayoutParams(-1,dp(2)));
        LinearLayout.LayoutParams cardSize=new LinearLayout.LayoutParams(-1,-2);cardSize.setMargins(dp(12),dp(4),dp(12),dp(6));root.addView(playerCard,cardSize);
        navigation=Ui.row(this);navigation.setBackgroundColor(Ui.SURFACE);String[] names={"Головна","Бібліотека","Пошук","Налаштування"},icons={"home","library","search","settings"};
        for(int i=0;i<names.length;i++){final int selected=i;LinearLayout cell=Ui.column(this);cell.setGravity(Gravity.CENTER);Ui.pad(cell,0,landscape()?2:6);Runnable open=()->navigate(selected);
            IconButton button=icon(icons[i],names[i],open);cell.addView(button,new LinearLayout.LayoutParams(dp(56),dp(30)));Ui.space(cell,5);
            TextView label=Ui.text(this,names[i],11,Ui.MUTED);label.setGravity(Gravity.CENTER);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);cell.addView(label,new LinearLayout.LayoutParams(-1,-2));cell.setOnClickListener(v->open.run());navigation.addView(cell,new LinearLayout.LayoutParams(0,dp(landscape()?56:64),1));}
        root.addView(navigation);setContentView(root);
    }
    private void navigate(int selected){hideKeyboard();tab=selected;section="";query="";if(selected==2)category="Композиції";if(selected==1)category=libraryCategory;detailShowing=false;render();}
    private void hideKeyboard(){View focus=getCurrentFocus();if(focus!=null){((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(),0);focus.clearFocus();}}
    private void goBack(){hideKeyboard();if(detailShowing){if(!detailHistory.isEmpty()){detailShowing=false;detailHistory.removeLast().run();}else render();return;}if(!section.isEmpty()){section="";render();return;}if(tab!=0){navigate(0);return;}super.onBackPressed();}
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
        if(content==null)return;activePlaylist=-1;content.removeAllViews();detailShowing=false;detailHistory.clear();detailPage=null;heading.setText(new String[]{"SELFonium","Бібліотека","Пошук","Налаштування"}[tab]);
        subtitle.setText("Синхронізація…");subtitle.setVisibility(syncing?View.VISIBLE:View.GONE);backButton.setVisibility(section.isEmpty()?View.GONE:View.VISIBLE);
        for(int i=0;i<navigation.getChildCount();i++){LinearLayout cell=(LinearLayout)navigation.getChildAt(i);IconButton button=(IconButton)cell.getChildAt(0);button.active(i==tab);button.setBackground(Ui.ripple(this,i==tab?Ui.TONAL:Ui.SURFACE,18));((TextView)cell.getChildAt(2)).setTextColor(i==tab?Ui.TEXT:Ui.MUTED);}
        switch(tab){case 0->home();case 1->{if(section.equals("category"))libraryPage();else if(section.equals("playlists"))playlists();else libraryRoot();}case 2->libraryPage();case 3->{if(section.equals("sources"))sources();else settingsPage();}}
    }
    private LinearLayout scroll(){ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout column=Ui.column(this);Ui.pad(column,16,8);scroll.addView(column);content.addView(scroll);return column;}
    private void label(LinearLayout parent,String text){TextView label=Ui.title(this,text,18);Ui.pad(label,0,12);parent.addView(label);}
    private void home(){
        LinearLayout column=scroll();List<Track> visible=visibleLibrary();
        if(visible.isEmpty()){label(column,"Ваша музика");column.addView(Ui.text(this,"Під’єднайте медіасервер або додайте файли з пристрою.",14,Ui.MUTED));Ui.space(column,16);column.addView(Ui.button(this,"Додати джерело",true,this::openSources));return;}
        LinearLayout actions=Ui.row(this);homeAction(actions,"shuffle","Мікс треків",()->{settings.edit().putBoolean("shuffle",true).apply();play(visible,new Random().nextInt(visible.size()));});
        homeAction(actions,"album","Мікс альбомів",()->{Track album=visible.get(new Random().nextInt(visible.size()));settings.edit().putBoolean("shuffle",false).apply();play(albumTracks(album),0);});
        homeAction(actions,"queue","Черга",this::queue);column.addView(actions);Ui.space(column,12);
        List<Track> favorites=app.library.favorites();if(!sourceFilter.isEmpty())favorites.removeIf(t->!t.sourceId.equals(sourceFilter));
        if(!favorites.isEmpty()){sectionHeader(column,"Улюблені композиції",()->openCategory("Улюблене"));albumShelf(column,favorites,true);}
        List<Track> recent=app.library.recent();if(!sourceFilter.isEmpty())recent.removeIf(t->!t.sourceId.equals(sourceFilter));
        if(!recent.isEmpty()){sectionHeader(column,"Нещодавно відтворені",()->showTracks("Нещодавно відтворені",recent));for(int i=0;i<Math.min(3,recent.size());i++)column.addView(trackRow(recent,i));}
        LinkedHashMap<String,Track> albums=new LinkedHashMap<>();for(Track t:visible)albums.putIfAbsent(t.sourceId+"\u0000"+t.artist+"\u0000"+t.album,t);
        sectionHeader(column,"Альбоми",()->openCategory("Альбоми"));albumShelf(column,new ArrayList<>(albums.values()),false);
        sectionHeader(column,"Композиції",()->openCategory("Композиції"));
        for(int i=0;i<Math.min(6,visible.size());i++){column.addView(trackRow(visible,i));}Ui.space(column,16);
    }
    private void sectionHeader(LinearLayout column,String name,Runnable action){LinearLayout row=Ui.row(this);Ui.pad(row,0,6);TextView title=Ui.title(this,name,18);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);row.addView(title,new LinearLayout.LayoutParams(0,-2,1));row.addView(icon("chevron","Переглянути: "+name,action),new LinearLayout.LayoutParams(dp(48),dp(48)));column.addView(row);}
    private void homeAction(LinearLayout row,String symbol,String name,Runnable action){
        LinearLayout tile=Ui.column(this);tile.setGravity(Gravity.CENTER);tile.setBackground(Ui.ripple(this,Ui.SURFACE,12));Ui.pad(tile,4,6);
        IconButton button=icon(symbol,name,action);button.active(true);tile.addView(button,new LinearLayout.LayoutParams(dp(48),dp(36)));
        TextView title=Ui.text(this,name,11,Ui.TEXT);title.setGravity(Gravity.CENTER);title.setMaxLines(2);tile.addView(title,new LinearLayout.LayoutParams(-1,-2));tile.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,dp(64),1);size.setMargins(0,0,dp(6),0);row.addView(tile,size);
    }
    private void openCategory(String name){hideKeyboard();tab=1;category=name;section=name.equals("Плейлисти")?"playlists":"category";query="";render();}
    private void openSources(){tab=3;section="sources";query="";render();}
    private void libraryRoot(){category=libraryCategory;libraryPage();}
    private void categoryTabs(LinearLayout page,boolean primary){
        LinearLayout choices=Ui.row(this);for(String name:new String[]{"Альбоми","Виконавці","Композиції"}){
            TextView chip=Ui.button(this,name,category.equals(name),()->{category=name;if(tab==1)libraryCategory=name;render();});Ui.pad(chip,4,8);chip.setSingleLine(true);chip.setEllipsize(android.text.TextUtils.TruncateAt.END);chip.setAutoSizeTextTypeUniformWithConfiguration(10,13,1,android.util.TypedValue.COMPLEX_UNIT_SP);chip.setSelected(category.equals(name));LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,dp(48),1);size.rightMargin=name.equals("Композиції")?0:dp(6);choices.addView(chip,size);
        }page.addView(choices);Ui.space(page,8);
    }
    private void librarySections(){String[] names={"Улюблене","Плейлисти","Офлайн","Файли","Радіо"};new AlertDialog.Builder(this).setTitle("Розділи бібліотеки").setItems(names,(d,at)->openCategory(names[at])).show();}
    private View emptyState(String symbol,String title,String summary,String action,Runnable run){
        LinearLayout box=Ui.column(this);box.setGravity(Gravity.CENTER);Ui.pad(box,24,32);
        IconButton glyph=icon(symbol,title,()->{});glyph.active(true);glyph.setFocusable(false);glyph.setClickable(false);glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);box.addView(glyph,new LinearLayout.LayoutParams(dp(48),dp(48)));Ui.space(box,12);
        TextView heading=Ui.title(this,title,18);heading.setGravity(Gravity.CENTER);box.addView(heading);Ui.space(box,8);TextView description=Ui.text(this,summary,13,Ui.MUTED);description.setGravity(Gravity.CENTER);box.addView(description);
        if(run!=null){Ui.space(box,20);box.addView(Ui.button(this,action,true,run));}return box;
    }
    private void albumShelf(LinearLayout parent,List<Track> tracks,boolean songs){
        HorizontalScrollView shelf=new HorizontalScrollView(this);shelf.setHorizontalScrollBarEnabled(false);LinearLayout cards=Ui.row(this);
        for(int i=0;i<Math.min(16,tracks.size());i++){Track track=tracks.get(i);final int at=i;LinearLayout card=albumCard(track,songs?track.title:track.album);
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(dp(136),-2);size.setMargins(0,0,dp(8),0);cards.addView(card,size);
            card.setOnClickListener(v->{if(songs)play(tracks,at);else showAlbum(track);});}
        shelf.addView(cards);parent.addView(shelf);
    }
    private LinearLayout albumCard(Track track,String name){
        LinearLayout card=Ui.column(this);CoverView cover=new CoverView(this,track);cover.square();card.addView(cover,new LinearLayout.LayoutParams(-1,-2));Ui.space(card,8);
        TextView title=Ui.title(this,Ui.name(Ui.album(name)),14);title.setMaxLines(2);title.setMinLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(title);
        TextView artist=Ui.text(this,track.artist,12,Ui.MUTED);artist.setSingleLine(true);artist.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(artist);return card;
    }
    private List<Track> albumTracks(Track album){List<Track> tracks=matching(t->t.album.equals(album.album)&&t.artist.equals(album.artist)&&t.sourceId.equals(album.sourceId));tracks.sort(Comparator.comparingInt(t->t.number));return tracks;}
    private List<Track> matching(java.util.function.Predicate<Track> filter){List<Track> result=new ArrayList<>();for(Track t:library)if(filter.test(t))result.add(t);return result;}
    private EditText input(String hint,boolean secret){EditText input=new EditText(this);input.setHint(hint);input.setTextColor(Ui.TEXT);input.setHintTextColor(Ui.MUTED);input.setSingleLine(true);input.setTextSize(15);Ui.pad(input,12,8);input.setInputType(secret?129:1);return input;}
    private void libraryPage(){
        boolean searching=tab==2,rootLibrary=tab==1&&section.isEmpty();if(!searching&&!rootLibrary)heading.setText(category);
        LinearLayout page=Ui.column(this);Ui.pad(page,16,4);LinearLayout categoryBar=Ui.column(this);EditText search=null;
        if(searching){
            LinearLayout field=Ui.row(this);field.setBackground(Ui.shape(this,Ui.SURFACE,16));IconButton magnifier=icon("search","Шукати",()->{});magnifier.setClickable(false);magnifier.setFocusable(false);field.addView(magnifier,new LinearLayout.LayoutParams(dp(44),dp(48)));
            search=input("Композиції, альбоми, виконавці",false);search.setBackground(null);search.setText(query);search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);field.addView(search,new LinearLayout.LayoutParams(0,dp(48),1));
            EditText editor=search;field.addView(icon("close","Очистити пошук",()->editor.setText("")),new LinearLayout.LayoutParams(dp(48),dp(48)));page.addView(field);Ui.space(page,8);
            search.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){rememberSearch();hideKeyboard();return true;}return false;});
            categoryTabs(categoryBar,true);
        }else if(rootLibrary){categoryTabs(categoryBar,true);}
        LinearLayout tools=Ui.row(this);TextView count=Ui.text(this,"",12,Ui.MUTED);tools.addView(count,new LinearLayout.LayoutParams(0,-2,1));
        if(category.equals("Альбоми")||category.equals("Виконавці"))tools.addView(icon("library","Змінити кількість колонок",()->{settings.edit().putInt("gridColumns",settings.getInt("gridColumns",2)==2?3:2).apply();render();}),new LinearLayout.LayoutParams(dp(48),dp(48)));
        if(rootLibrary){TextView more=Ui.button(this,"Ще ▾",false,this::librarySections);Ui.pad(more,10,6);more.setTextSize(12);more.setContentDescription("Ще: розділи бібліотеки");tools.addView(more,new LinearLayout.LayoutParams(-2,dp(44)));}
        tools.addView(icon("sort","Сортування",()->new AlertDialog.Builder(this).setTitle("Сортування").setSingleChoiceItems(new String[]{"За назвою","За виконавцем","За альбомом","За тривалістю"},sortOrder,(d,at)->{sortOrder=at;d.dismiss();render();}).show()),new LinearLayout.LayoutParams(dp(48),dp(48)));
        if(landscape()&&(searching||rootLibrary)){LinearLayout bar=Ui.row(this);bar.addView(categoryBar,new LinearLayout.LayoutParams(0,-2,1));bar.addView(tools,new LinearLayout.LayoutParams(-2,dp(48)));page.addView(bar);}else{page.addView(categoryBar);page.addView(tools);}
        FrameLayout results=new FrameLayout(this);page.addView(results,new LinearLayout.LayoutParams(-1,0,1));content.addView(page);
        Runnable update=()->{
            results.removeAllViews();String needle=query.trim().toLowerCase(Locale.ROOT);
            tools.setVisibility(searching&&needle.isEmpty()?View.GONE:View.VISIBLE);if(searching&&needle.isEmpty()){count.setText("Пошук у вашій музиці");LinearLayout prompt=Ui.column(this);prompt.addView(emptyState("search","Знайдіть свою музику","Введіть назву композиції, альбому або виконавця.","",null));
                for(String previous:settings.getString("searchHistory","").split("\n"))if(!previous.isBlank()){
                    TextView entry=Ui.button(this,previous,false,()->{EditText field=(EditText)findSearchField(page);if(field!=null){field.setText(previous);field.setSelection(previous.length());hideKeyboard();}});prompt.addView(entry);Ui.space(prompt,6);}
                ScrollView recentScroll=new ScrollView(this);recentScroll.addView(prompt);results.addView(recentScroll);return;}
            List<Track> base=category.equals("Улюблене")?app.library.favorites():category.equals("Офлайн")?app.library.offlineTracks():visibleLibrary();
            List<Track> tracks=new ArrayList<>();for(Track t:base){boolean type=(!category.equals("Файли")||t.sourceId.equals("device")||t.sourceId.equals("files"))&&(!category.equals("Радіо")||t.sourceId.equals("radio"));if(type&&(sourceFilter.isEmpty()||sourceFilter.equals(t.sourceId))&&(t.title+" "+t.artist+" "+t.album).toLowerCase(Locale.ROOT).contains(needle))tracks.add(t);}
            Comparator<Track> order=switch(sortOrder){case 1->Comparator.comparing(t->t.artist,String.CASE_INSENSITIVE_ORDER);case 2->Comparator.comparing((Track t)->t.album,String.CASE_INSENSITIVE_ORDER).thenComparingInt(t->t.number);case 3->Comparator.comparingLong(t->t.durationMs);default->Comparator.comparing(t->t.title,String.CASE_INSENSITIVE_ORDER);};tracks.sort(order);count.setText(Ui.tracks(tracks.size()));
            if(tracks.isEmpty()){results.addView(emptyState(searching?"search":"music",searching?"Нічого не знайдено":"Тут поки порожньо",searching?"Спробуйте інший запит або змініть джерело.":"Додайте музику або змініть фільтр джерел.",searching?"":"Джерела музики",searching?null:this::openSources));return;}
            if(category.equals("Альбоми")||category.equals("Виконавці")){
                boolean albums=category.equals("Альбоми");LinkedHashMap<String,List<Track>> groups=new LinkedHashMap<>();
                for(Track t:tracks)groups.computeIfAbsent(albums?t.album+"\u0000"+t.artist+"\u0000"+t.sourceId:t.artist,k->new ArrayList<>()).add(t);
                List<String> names=new ArrayList<>(groups.keySet());Comparator<String> namesOrder=String.CASE_INSENSITIVE_ORDER;
                if(sortOrder==1)namesOrder=Comparator.comparing(k->groups.get(k).get(0).artist,String.CASE_INSENSITIVE_ORDER);
                else if(sortOrder==3)namesOrder=Comparator.comparingLong(k->groups.get(k).stream().mapToLong(t->t.durationMs).sum());names.sort(namesOrder);
                count.setText(albums?Ui.count(names.size(),"альбом","альбоми","альбомів"):Ui.count(names.size(),"виконавець","виконавці","виконавців"));
                GridView grid=new GridView(this);grid.setNumColumns(getResources().getConfiguration().orientation==2?3:settings.getInt("gridColumns",2));grid.setHorizontalSpacing(dp(12));grid.setVerticalSpacing(dp(16));grid.setPadding(0,dp(4),0,dp(16));grid.setClipToPadding(false);grid.setSelector(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                grid.setAdapter(new BaseAdapter(){public int getCount(){return names.size();}public Object getItem(int at){return names.get(at);}public long getItemId(int at){return at;}
                    public View getView(int at,View recycled,ViewGroup parent){List<Track> group=groups.get(names.get(at));Track track=group.get(0);LinearLayout card;
                        if(landscape()){
                            LinearLayout compact;if(recycled instanceof LinearLayout)compact=(LinearLayout)recycled;else{compact=Ui.row(MainActivity.this);Ui.pad(compact,0,6);compact.addView(new CoverView(MainActivity.this),new LinearLayout.LayoutParams(dp(60),dp(60)));LinearLayout labels=Ui.column(MainActivity.this);Ui.pad(labels,10,0);TextView title=Ui.title(MainActivity.this,"",14);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(title);Ui.space(labels,4);TextView summary=Ui.text(MainActivity.this,"",12,Ui.MUTED);summary.setSingleLine(true);summary.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(summary);compact.addView(labels,new LinearLayout.LayoutParams(0,-2,1));}
                            CoverView cover=(CoverView)compact.getChildAt(0);cover.bind(track);cover.portrait(!albums);LinearLayout labels=(LinearLayout)compact.getChildAt(1);((TextView)labels.getChildAt(0)).setText(albums?Ui.album(track.album):track.artist);((TextView)labels.getChildAt(2)).setText(albums?track.artist:Ui.tracks(group.size()));compact.setBackground(Ui.ripple(MainActivity.this,Ui.BG,8));compact.setOnClickListener(v->{rememberSearch();if(albums)showAlbum(track);else showArtist(track.artist);});return compact;
                        }
                        if(recycled instanceof LinearLayout){card=(LinearLayout)recycled;((CoverView)card.getChildAt(0)).bind(track);((TextView)card.getChildAt(2)).setText(albums?Ui.album(track.album):track.artist);}else card=albumCard(track,albums?track.album:track.artist);
                        ((CoverView)card.getChildAt(0)).portrait(!albums);((TextView)card.getChildAt(3)).setText(albums?track.artist:Ui.tracks(group.size()));card.setOnClickListener(v->{rememberSearch();if(albums)showAlbum(track);else showArtist(track.artist);});return card;}});results.addView(grid);
            }else results.addView(trackList(tracks));
        };
        if(search!=null)search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){query=s.toString();update.run();}public void afterTextChanged(Editable e){}});update.run();
    }
    private View findSearchField(View view){if(view instanceof EditText)return view;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View result=findSearchField(group.getChildAt(i));if(result!=null)return result;}return null;}
    private void rememberSearch(){if(tab!=2||query.isBlank())return;LinkedHashSet<String> history=new LinkedHashSet<>();history.add(query.trim().replace('\n',' '));history.addAll(Arrays.asList(settings.getString("searchHistory","").split("\n")));history.remove("");settings.edit().putString("searchHistory",String.join("\n",new ArrayList<>(history).subList(0,Math.min(5,history.size())))).apply();}
    private View trackRow(List<Track> tracks,int at){TrackRowView row=new TrackRowView(this);row.bind(tracks.get(at),()->play(tracks,at),this::trackMenu);row.nowPlaying(tracks.get(at).id.equals(miniId));return row;}
    private View trackList(List<Track> tracks){return trackList(tracks,false,null);}
    private View trackList(List<Track> tracks,boolean album,View header){
        if(tracks.isEmpty()&&header==null)return emptyState("music","Поки немає композицій","Додайте джерело або змініть фільтр.","",null);
        ListView list=new ListView(this);list.setDivider(null);list.setItemsCanFocus(true);list.setClipToPadding(false);list.setPadding(0,0,0,dp(12));if(header!=null)list.addHeaderView(header,null,false);
        list.setAdapter(new BaseAdapter(){public int getCount(){return tracks.size();}public Track getItem(int at){return tracks.get(at);}public long getItemId(int at){return at;}
            public View getView(int at,View old,ViewGroup parent){TrackRowView row=old instanceof TrackRowView?(TrackRowView)old:new TrackRowView(MainActivity.this);row.bind(tracks.get(at),()->play(tracks,at),ignored->trackMenu(tracks.get(at),at));row.albumPosition(album?at+1:0);row.nowPlaying(tracks.get(at).id.equals(miniId));return row;}});return list;
    }
    private void showAlbum(Track album){List<Track> tracks=albumTracks(album);showCollection(Ui.album(album.album),tracks,true,false);}
    private void showArtist(String artist){List<Track> tracks=matching(t->t.artist.equals(artist)&&(sourceFilter.isEmpty()||sourceFilter.equals(t.sourceId)));tracks.sort(Comparator.comparing((Track t)->t.album,String.CASE_INSENSITIVE_ORDER).thenComparingInt(t->t.number));showCollection(artist,tracks,false,true);}
    private void showTracks(String name,List<Track> tracks){showCollection(name,tracks,false,false);}
    private void showCollection(String name,List<Track> tracks,boolean album,boolean artist){
        activePlaylist=-1;hideKeyboard();if(detailShowing&&detailPage!=null)detailHistory.addLast(detailPage);detailPage=()->showCollection(name,tracks,album,artist);detailShowing=true;backButton.setVisibility(View.VISIBLE);content.removeAllViews();heading.setText(album?"Альбом":artist?"Виконавець":name);subtitle.setVisibility(View.GONE);
        LinearLayout page=Ui.column(this);Ui.pad(page,16,0);LinearLayout header=Ui.column(this);header.setGravity(Gravity.CENTER_HORIZONTAL);Ui.pad(header,0,12);
        if(!tracks.isEmpty()){
            CoverView cover=new CoverView(this,tracks.get(0));cover.portrait(artist);cover.palette(color->page.setBackground(Ui.atmosphere(color)));LinearLayout.LayoutParams artSize=new LinearLayout.LayoutParams(dp(artist?120:160),dp(artist?120:160));artSize.gravity=Gravity.CENTER_HORIZONTAL;header.addView(cover,artSize);Ui.space(header,16);
        }
        TextView title=Ui.title(this,name,24);title.setGravity(Gravity.CENTER);title.setMaxLines(3);header.addView(title,new LinearLayout.LayoutParams(-1,-2));Ui.space(header,8);
        if(album&&!tracks.isEmpty()){String performer=tracks.get(0).artist;TextView link=Ui.text(this,performer,14,Ui.ACCENT);link.setGravity(Gravity.CENTER);link.setMinHeight(dp(40));link.setBackground(Ui.ripple(this,android.graphics.Color.TRANSPARENT,8));link.setOnClickListener(v->showArtist(performer));header.addView(link,new LinearLayout.LayoutParams(-1,-2));}
        long length=0;for(Track track:tracks)length+=track.durationMs;TextView metadata=Ui.text(this,Ui.tracks(tracks.size())+" · "+Protocol.time(length),12,Ui.MUTED);metadata.setGravity(Gravity.CENTER);header.addView(metadata);Ui.space(header,16);
        LinearLayout actions=Ui.row(this);TextView play=Ui.button(this,"Відтворити",true,()->{settings.edit().putBoolean("shuffle",false).apply();play(tracks,0);});LinearLayout.LayoutParams playSize=new LinearLayout.LayoutParams(0,dp(48),1);playSize.rightMargin=dp(8);actions.addView(play,playSize);
        actions.addView(Ui.button(this,"Перемішати",false,()->{settings.edit().putBoolean("shuffle",true).apply();play(tracks,0);}),new LinearLayout.LayoutParams(0,dp(48),1));header.addView(actions,new LinearLayout.LayoutParams(-1,-2));Ui.space(header,16);
        if(artist){LinkedHashMap<String,Track> albums=new LinkedHashMap<>();for(Track t:tracks)albums.putIfAbsent(t.sourceId+"\u0000"+t.album,t);label(header,"Альбоми");albumShelf(header,new ArrayList<>(albums.values()),false);Ui.space(header,12);label(header,"Композиції");}
        page.addView(trackList(tracks,album,header),new LinearLayout.LayoutParams(-1,-1));content.addView(page);
    }
    private boolean ready(){if(player==null){message("Плеєр під’єднується…");return false;}return true;}
    private void play(List<Track> tracks,int at){
        if(!ready()||tracks.isEmpty())return;rememberSearch();hideKeyboard();
        boolean same=player.getMediaItemCount()==tracks.size();
        if(same)for(int i=0;i<tracks.size();i++)if(!player.getMediaItemAt(i).mediaId.equals(tracks.get(i).id)){same=false;break;}
        if(same)player.seekTo(at,0);else{List<MediaItem> items=new ArrayList<>();for(Track track:tracks)items.add(PlaybackService.item(track));player.setMediaItems(items,at,0);}
        if(player.getPlaybackState()==Player.STATE_IDLE)player.prepare();player.play();
    }
    private void toggle(){if(!ready())return;if(player.getPlayerError()==null&&player.getPlayWhenReady()&&player.getPlaybackState()!=Player.STATE_ENDED)player.pause();else if(player.getMediaItemCount()>0){if(player.getPlaybackState()==Player.STATE_ENDED)player.seekToDefaultPosition();player.prepare();player.play();}else{openCategory("Композиції");}}
    private void trackMenu(Track track){trackMenu(track,-1);}
    private void trackMenu(Track track,int playlistPosition){
        if(actionSheet!=null)actionSheet.dismiss();actionSheet=new Dialog(this);actionSheet.requestWindowFeature(Window.FEATURE_NO_TITLE);Dialog sheet=actionSheet;
        LinearLayout page=Ui.column(this);Ui.pad(page,16,12);page.setBackground(Ui.shape(this,Ui.SURFACE,24));
        View handle=new View(this);handle.setBackground(Ui.shape(this,Ui.MUTED,2));LinearLayout.LayoutParams handleSize=new LinearLayout.LayoutParams(dp(32),dp(4));handleSize.gravity=Gravity.CENTER_HORIZONTAL;handleSize.bottomMargin=dp(16);page.addView(handle,handleSize);
        LinearLayout header=Ui.row(this);header.addView(new CoverView(this,track),new LinearLayout.LayoutParams(dp(52),dp(52)));LinearLayout labels=Ui.column(this);Ui.pad(labels,12,0);TextView title=Ui.title(this,Ui.name(track.title),16);title.setMaxLines(2);labels.addView(title);Ui.space(labels,4);labels.addView(Ui.text(this,track.artist,13,Ui.MUTED));header.addView(labels,new LinearLayout.LayoutParams(0,-2,1));page.addView(header);Ui.space(page,12);
        sheetAction(page,sheet,"play","Відтворити",()->play(List.of(track),0));sheetAction(page,sheet,"next","Відтворити наступною",()->{if(ready())player.addMediaItem(Math.max(0,player.getCurrentMediaItemIndex()+1),PlaybackService.item(track));});
        sheetAction(page,sheet,"queue","Додати в чергу",()->{if(ready())player.addMediaItem(PlaybackService.item(track));});Ui.divider(page);sheetAction(page,sheet,"heart",app.library.favorite(track.id)?"Прибрати з улюбленого":"Додати в улюблене",()->{app.social.toggleFavorite(track);updatePlayer();});
        Library.Playlist playlist=playlistPosition>=0?app.library.playlistInfo(activePlaylist):null;
        if(playlist!=null&&playlist.writable())sheetAction(page,sheet,"remove","Прибрати з цього плейлиста",()->playlistWork(()->{
            if(playlist.remote())app.social.remove(playlist,playlistPosition,track);else app.library.removeFromPlaylist(playlist.id(),track.id);
        },()->refreshPlaylist(playlist.id())));
        sheetAction(page,sheet,"plus","Додати до плейлиста",()->choosePlaylist(track));boolean offline=!app.library.offline(track.id).isEmpty();
        sheetAction(page,sheet,"download",offline?"Видалити офлайн-копію":"Завантажити офлайн",()->{if(!offline)download(track);else new AlertDialog.Builder(this).setMessage("Видалити завантажену копію? Оригінал залишиться у джерелі.").setNegativeButton("Скасувати",null).setPositiveButton("Видалити",(x,y)->{new File(app.library.offline(track.id)).delete();app.library.offline(track.id,"");render();}).show();});
        Ui.divider(page);sheetAction(page,sheet,"album","Перейти до альбому",()->{if(playerDialog!=null)playerDialog.dismiss();showAlbum(track);});
        sheetAction(page,sheet,"more","Інформація",()->new AlertDialog.Builder(this).setTitle(track.title).setMessage(track.artist+"\n"+track.album+"\nТривалість: "+Protocol.time(track.durationMs)+"\nДжерело: "+sourceName(track.sourceId)).setPositiveButton("Готово",null).show());
        ScrollView scroll=new ScrollView(this);scroll.addView(page);sheet.setContentView(scroll);sheet.show();Window window=sheet.getWindow();window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));window.getDecorView().setPadding(0,0,0,0);window.setGravity(Gravity.BOTTOM);window.setLayout(-1,Math.min(dp(648),(int)(getResources().getDisplayMetrics().heightPixels*.82f)));window.setNavigationBarColor(Ui.SURFACE);
    }
    private void sheetAction(LinearLayout page,Dialog sheet,String symbol,String title,Runnable action){
        Runnable run=()->{sheet.dismiss();action.run();};LinearLayout row=Ui.row(this);row.setBackground(Ui.ripple(this,Ui.SURFACE,10));row.addView(icon(symbol,title,run),new LinearLayout.LayoutParams(dp(48),dp(48)));TextView label=Ui.text(this,title,14,Ui.TEXT);Ui.pad(label,8,0);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));row.setOnClickListener(v->run.run());page.addView(row,new LinearLayout.LayoutParams(-1,dp(48)));
    }
    private String sourceName(String id){if(id.equals("device"))return "На пристрої";if(id.equals("files"))return "Імпортовані файли";if(id.equals("radio"))return "Радіо";try{Source s=app.sources.get(id);return s==null?"Видалене джерело":s.name;}catch(Exception e){return "Джерело";}}
    private void download(Track track){if(track.sourceId.equals("radio")){message("Радіопотік не має кінця й не завантажується як файл");return;}notificationPermission();startForegroundService(new Intent(this,DownloadService.class).putExtra("track",track.id));message("Додано до завантажень");}
    private void playlists(){
        heading.setText("Плейлисти");LinearLayout column=scroll();List<Library.Playlist> entries=app.library.playlistInfo();
        settingRow(column,"plus","Створити плейлист","На телефоні або на сервері Subsonic",()->newPlaylist(null));
        if(entries.isEmpty())column.addView(emptyState("queue","Ваші добірки","Оновіть Subsonic-джерело, щоб отримати плейлисти із сервера, або створіть власний.","",null));
        for(Library.Playlist playlist:entries){Track first=app.library.playlistFirst(playlist.id());LinearLayout card=Ui.row(this);Ui.pad(card,0,8);
            card.addView(new CoverView(this,first),new LinearLayout.LayoutParams(dp(56),dp(56)));
            LinearLayout text=Ui.column(this);Ui.pad(text,12,0);text.addView(Ui.title(this,playlist.name(),16));Ui.space(text,4);
            Source source=playlist.remote()?app.sources.get(playlist.source()):null;
            text.addView(Ui.text(this,Ui.tracks(playlist.count())+" · "+(source==null?"На телефоні":source.name)+(playlist.writable()?"":" · лише читання"),12,Ui.MUTED));
            card.addView(text,new LinearLayout.LayoutParams(0,-2,1));card.addView(icon("more","Меню плейлиста",()->playlistMenu(playlist)),new LinearLayout.LayoutParams(dp(48),dp(48)));
            card.setOnClickListener(v->playlistPage(playlist.id(),playlist.name()));card.setOnLongClickListener(v->{playlistMenu(playlist);return true;});column.addView(card);
        }
    }
    private void playlistMenu(Library.Playlist playlist){
        String[] actions=playlist.writable()?new String[]{"Відкрити","Перейменувати","Видалити"}:new String[]{"Відкрити"};
        new AlertDialog.Builder(this).setTitle(playlist.name()).setItems(actions,(d,at)->{
            if(at==0)playlistPage(playlist.id(),playlist.name());
            else if(at==1){EditText name=input("Назва плейлиста",false);name.setText(playlist.name());
                new AlertDialog.Builder(this).setTitle("Перейменувати плейлист").setView(name).setNegativeButton("Скасувати",null).setPositiveButton("Зберегти",(x,y)->{
                    String value=name.getText().toString().trim();if(value.isEmpty()){message("Введіть назву");return;}
                    playlistWork(()->{if(playlist.remote())app.social.rename(playlist,value);else app.library.renamePlaylist(playlist.id(),value);},this::reload);
                }).show();
            }else new AlertDialog.Builder(this).setTitle("Видалити «"+playlist.name()+"»?").setMessage(playlist.remote()?"Плейлист буде видалено і на сервері. Аудіофайли залишаться.":"Аудіофайли залишаться в бібліотеці.").setNegativeButton("Скасувати",null).setPositiveButton("Видалити",(x,y)->playlistWork(()->{if(playlist.remote())app.social.delete(playlist);else app.library.deletePlaylist(playlist.id());},this::reload)).show();
        }).show();
    }
    private void playlistPage(long id,String name){showTracks(name,app.library.playlist(id));activePlaylist=id;detailPage=()->playlistPage(id,name);}
    private void refreshPlaylist(long id){render();Library.Playlist playlist=app.library.playlistInfo(id);if(playlist!=null)playlistPage(id,playlist.name());}
    private interface PlaylistWork {void run() throws Exception;}
    private void playlistWork(PlaylistWork work,Runnable after){
        message("Оновлення плейлиста…");app.sync.execute(()->{try{work.run();app.main.post(()->{if(!isDestroyed()){after.run();message("Плейлист оновлено");}});}
            catch(Exception error){app.main.post(()->{if(!isDestroyed())message("Не вдалося оновити плейлист. "+safeError(error)+" Оновіть джерело перед повторенням дії.");});}});
    }
    private void newPlaylist(Track initial){
        LinearLayout form=Ui.column(this);Ui.pad(form,20,12);EditText name=input("Назва плейлиста",false);form.addView(name,new LinearLayout.LayoutParams(-1,-2));Ui.space(form,12);
        List<Source> destinations=new ArrayList<>();List<String> labels=new ArrayList<>();labels.add("На телефоні");
        for(Source source:app.sources.all())if(source.type==Source.Type.SUBSONIC&&(initial==null||source.id.equals(initial.sourceId))){destinations.add(source);labels.add(source.name+" · Subsonic");}
        Spinner destination=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);destination.setAdapter(adapter);form.addView(destination,new LinearLayout.LayoutParams(-1,dp(56)));if(initial!=null&&!destinations.isEmpty())destination.setSelection(1);
        new AlertDialog.Builder(this).setTitle("Новий плейлист").setView(form).setNegativeButton("Скасувати",null).setPositiveButton("Створити",(d,w)->{
            String value=name.getText().toString().trim();if(value.isEmpty()){message("Введіть назву");return;}
            Source selected=destination.getSelectedItemPosition()==0?null:destinations.get(destination.getSelectedItemPosition()-1);
            playlistWork(()->{if(selected!=null)app.social.create(selected,value,initial);else{long id=app.library.createPlaylist(value);if(initial!=null)app.library.addToPlaylist(id,initial.id);}},this::reload);
        }).show();
    }
    private void choosePlaylist(Track track){
        List<Library.Playlist> playlists=new ArrayList<>();List<String> names=new ArrayList<>();names.add("＋ Новий плейлист");
        for(Library.Playlist playlist:app.library.playlistInfo())if(playlist.writable()&&(!playlist.remote()||playlist.source().equals(track.sourceId))){playlists.add(playlist);Source source=playlist.remote()?app.sources.get(playlist.source()):null;names.add(playlist.name()+" · "+(source==null?"На телефоні":source.name));}
        new AlertDialog.Builder(this).setTitle("Додати до плейлиста").setItems(names.toArray(new String[0]),(d,at)->{
            if(at==0)newPlaylist(track);else{Library.Playlist playlist=playlists.get(at-1);playlistWork(()->{if(playlist.remote())app.social.add(playlist,track);else app.library.addToPlaylist(playlist.id(),track.id);},this::reload);}
        }).show();
    }
    private void sources(){
        heading.setText("Джерела музики");LinearLayout column=scroll();if(app.social.pendingCount>0)settingRow(column,"refresh","Очікують синхронізації","Улюблене: "+app.social.pendingCount+" змін · натисніть, щоб повторити",app.social::requestFlush);
        try{List<Source> sources=app.sources.all();if(!sources.isEmpty())label(column,"Медіасервери");for(Source source:sources){LinearLayout card=Ui.row(this);card.setBackground(Ui.ripple(this,Ui.SURFACE,16));Ui.pad(card,4,10);card.addView(icon("server",source.name,()->showTracks(source.name,matching(t->source.id.equals(t.sourceId)))),new LinearLayout.LayoutParams(dp(48),dp(48)));LinearLayout text=Ui.column(this);Ui.pad(text,8,0);text.addView(Ui.title(this,source.name,16));Ui.space(text,5);text.addView(Ui.text(this,source.type.name()+" · "+Ui.tracks(matching(t->source.id.equals(t.sourceId)).size()),12,Ui.MUTED));card.addView(text,new LinearLayout.LayoutParams(0,-2,1));card.addView(icon("refresh","Синхронізувати "+source.name,()->sync(source)),new LinearLayout.LayoutParams(dp(48),dp(48)));card.addView(icon("more","Меню джерела",()->sourceActions(source)),new LinearLayout.LayoutParams(dp(48),dp(48)));card.setOnClickListener(v->showTracks(source.name,matching(t->source.id.equals(t.sourceId))));column.addView(card);Ui.space(column,8);}}catch(Exception e){message(e.getMessage());}
        settingRow(column,"plus","Додати Subsonic-сервер","Subsonic / OpenSubsonic · перевірено з Navidrome",()->sourceDialog(null));label(column,"На пристрої");settingRow(column,"music","Сканувати музику","Аудіофайли з медіатеки Android",this::scan);settingRow(column,"folder","Додати файли","Вибрати через файловий менеджер",this::importFiles);settingRow(column,"radio","Додати радіостанцію","Пряме посилання на аудіопотік",this::radio);
    }
    private void sourceActions(Source source){new AlertDialog.Builder(this).setTitle(source.name).setItems(new String[]{"Композиції джерела","Редагувати","Видалити джерело"},(d,at)->{if(at==0)showTracks(source.name,matching(t->source.id.equals(t.sourceId)));else if(at==1)sourceDialog(source);else new AlertDialog.Builder(this).setMessage("Видалити джерело, його записи та офлайн-копії?").setNegativeButton("Скасувати",null).setPositiveButton("Видалити",(x,y)->{for(Track t:matching(t->source.id.equals(t.sourceId))){String path=app.library.offline(t.id);if(!path.isEmpty())new File(path).delete();}app.sources.remove(source.id);app.library.removeSource(source.id);reload();}).show();}).show();}
    private void sourceDialog(Source existing){
        SourceDialog.show(this,existing,source->{app.sources.save(source);sync(source);});
    }

    private void sync(Source source){if(syncing){message("Дочекайтеся поточної синхронізації");return;}syncing=true;render();app.catalog.execute(()->{try{
        List<Track> tracks=app.subsonic.synchronize(source,status->app.main.post(()->{if(!isDestroyed())subtitle.setText(status);}));app.library.merge(source.id,tracks,true);app.artwork.retryFailures();
        app.sync.execute(()->{try{app.social.synchronize(source);app.main.post(()->{syncing=false;message("Синхронізовано: "+Ui.tracks(tracks.size()));reload();});}catch(Exception error){syncFailure(error);}});
    }catch(Exception e){syncFailure(e);}});}
    private void syncFailure(Exception error){app.main.post(()->{syncing=false;message("Не вдалося синхронізувати. "+safeError(error));reload();});}
    private String safeError(Exception e){
        if(e instanceof app.selfonium.net.SubsonicClient.ApiException api)return switch(api.code){
            case 40 -> "Сервер відхилив логін або пароль.";
            case 50 -> "Обліковий запис не має дозволу на цю дію.";
            case 70 -> "Запис більше не доступний на сервері.";
            default -> "Сервер відхилив запит Subsonic (код "+api.code+").";
        };
        if(e instanceof java.net.UnknownHostException)return "Не вдалося знайти сервер. Перевірте адресу та DNS у поточній мережі.";
        if(e instanceof javax.net.ssl.SSLException)return "Не вдалося перевірити HTTPS. Перевірте ім’я сервера, строк дії сертифіката та встановлення його CA на телефоні.";
        if(e instanceof java.net.SocketTimeoutException)return "Сервер не відповів вчасно. Перевірте мережу та порт.";
        if(e instanceof java.net.ConnectException)return "Не вдалося з’єднатися із сервером. Перевірте адресу, порт та доступність сервера.";
        if(e instanceof app.selfonium.net.Http.StatusException status)return switch(status.status){
            case 401 -> "Сервер відхилив авторизацію. Перевірте логін і пароль або повторно збережіть джерело.";
            case 403 -> "Сервер заборонив доступ. Перевірте права користувача на музичну бібліотеку.";
            case 400 -> "Сервер відхилив запит (HTTP 400). Перевірте тип джерела та сумісність API.";
            case 404 -> "API не знайдено (HTTP 404). Перевірте адресу Subsonic-сервера без /rest.";
            default -> "Сервер повернув HTTP "+status.status+". Перевірте його стан і налаштування.";
        };
        return e instanceof java.io.IOException?"Перевірте адресу, мережу й облікові дані.":"Перевірте налаштування та сумісність API сервера.";
    }
    private void scan(){String permission=Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_AUDIO:Manifest.permission.READ_EXTERNAL_STORAGE;
        if(checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{permission},100);return;}subtitle.setVisibility(View.VISIBLE);subtitle.setText("Сканування пристрою…");app.io.execute(()->{try{List<Track> tracks=LocalMusic.scan(this);app.library.merge("device",tracks,true);app.main.post(()->{message("Знайдено: "+Ui.tracks(tracks.size()));reload();});}catch(Exception e){app.main.post(()->message("Не вдалося прочитати медіатеку"));}});}
    private void importFiles(){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(intent,101);}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==100){if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)scan();else message("Можна додати окремі файли через «Відкрити аудіофайли»");}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=101||result!=RESULT_OK||data==null)return;List<Uri> uris=new ArrayList<>();if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());else if(data.getData()!=null)uris.add(data.getData());
        for(Uri uri:uris)try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
        app.io.execute(()->{try{List<Track> tracks=new ArrayList<>();for(Uri uri:uris)tracks.add(LocalMusic.importUri(this,uri));app.library.merge("files",tracks,false);app.main.post(this::reload);}catch(Exception e){app.main.post(()->message("Не вдалося імпортувати вибрані файли"));}});}
    private void radio(){LinearLayout form=Ui.column(this);EditText name=input("Назва станції",false),url=input("Пряме http(s) посилання на аудіопотік",false);form.addView(name);form.addView(url);new AlertDialog.Builder(this).setTitle("Інтернет-радіо").setView(form).setNegativeButton("Скасувати",null).setPositiveButton("Додати",(d,w)->{try{String uri=url.getText().toString().trim();java.net.URI parsed=java.net.URI.create(uri);if(!List.of("http","https").contains(parsed.getScheme())||parsed.getHost()==null||parsed.getUserInfo()!=null)throw new IllegalArgumentException();String id=UUID.randomUUID().toString();Track t=new Track("radio:"+id,"radio",id,name.getText().toString(),"Інтернет-радіо","Радіо",uri,0,0);app.library.merge("radio",List.of(t),false);reload();}catch(Exception e){message("Вкажіть коректне пряме посилання на потік");}}).show();}
    private void notificationPermission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},102);}
    private void playerGap(LinearLayout page,int minimum){View gap=new View(this);page.addView(gap,new LinearLayout.LayoutParams(-1,dp(minimum),landscape()?0:1));}
    private void showPlayer(){
        if(!ready()||player.getCurrentMediaItem()==null){openCategory("Композиції");return;}
        if(playerDialog!=null&&playerDialog.isShowing())return;
        playerDialog=Ui.screen(this);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Ui.BG);LinearLayout page=Ui.column(this);Ui.pad(page,20,0);scroll.addView(page);
        scroll.setOnApplyWindowInsetsListener((view,insets)->{view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        LinearLayout top=Ui.row(this);top.addView(icon("down","Згорнути плеєр",()->playerDialog.dismiss()));TextView caption=Ui.text(this,"Зараз відтворюється",14,Ui.MUTED);caption.setGravity(Gravity.CENTER);top.addView(caption,new LinearLayout.LayoutParams(0,-2,1));top.addView(icon("more","Дії з композицією",()->{MediaItem item=player.getCurrentMediaItem();if(item!=null){Track track=app.library.get(item.mediaId);if(track!=null)trackMenu(track);}}));page.addView(top);Ui.space(page,8);
        playerCover=new FrameLayout(this){@Override protected void onMeasure(int width,int height){int available=getResources().getDisplayMetrics().heightPixels-dp(landscape()?140:340);int size=Math.min(MeasureSpec.getSize(width),Math.min(dp(420),Math.max(dp(140),available)));super.onMeasure(MeasureSpec.makeMeasureSpec(size,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(size,MeasureSpec.EXACTLY));}};
        LinearLayout.LayoutParams coverSize=new LinearLayout.LayoutParams(-1,-2);coverSize.gravity=Gravity.CENTER_HORIZONTAL;page.addView(playerCover,coverSize);playerArtwork=new CoverView(this);playerArtwork.urgent();playerArtwork.palette(color->page.setBackground(Ui.atmosphere(color)));playerCover.addView(playerArtwork,new FrameLayout.LayoutParams(-1,-1));playerGap(page,16);
        LinearLayout titleRow=Ui.row(this);LinearLayout labels=Ui.column(this);playerTitle=Ui.title(this,"",22);playerTitle.setSingleLine(true);playerTitle.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);playerTitle.setMarqueeRepeatLimit(-1);playerTitle.setSelected(true);labels.addView(playerTitle);Ui.space(labels,4);playerArtist=Ui.text(this,"",15,Ui.ACCENT);playerArtist.setSingleLine(true);playerArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(playerArtist);Ui.space(labels,5);playerAlbum=Ui.text(this,"",13,Ui.MUTED);playerAlbum.setSingleLine(true);playerAlbum.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(playerAlbum);titleRow.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        favoriteButton=icon("heart","Додати або прибрати з улюбленого",()->{MediaItem item=player.getCurrentMediaItem();if(item!=null){Track track=app.library.get(item.mediaId);if(track!=null)app.social.toggleFavorite(track);updatePlayer();}});titleRow.addView(favoriteButton);page.addView(titleRow);playerGap(page,16);
        playerSeek=new SeekBar(this);playerSeek.setMax(1000);playerSeek.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));playerSeek.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));playerSeek.setContentDescription("Позиція відтворення");page.addView(playerSeek);
        LinearLayout times=Ui.row(this);playerTime=Ui.text(this,"",12,Ui.MUTED);playerRemaining=Ui.text(this,"",12,Ui.MUTED);times.addView(playerTime,new LinearLayout.LayoutParams(0,-2,1));times.addView(playerRemaining);Ui.pad(times,8,0);page.addView(times);
        playerSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int value,boolean user){if(user&&player!=null&&player.getDuration()>0)updateTimes(player.getDuration()*value/1000,player.getDuration());}public void onStartTrackingTouch(SeekBar s){seeking=true;}public void onStopTrackingTouch(SeekBar s){if(player!=null&&player.getDuration()>0)player.seekTo(player.getDuration()*s.getProgress()/1000);seeking=false;}});
        playerGap(page,16);LinearLayout controls=Ui.row(this);controls.setGravity(Gravity.CENTER);
        shuffleButton=icon("shuffle","Перемішування",()->{settings.edit().putBoolean("shuffle",!settings.getBoolean("shuffle",false)).apply();updatePlayer();});controls.addView(shuffleButton,new LinearLayout.LayoutParams(0,dp(52),1));
        controls.addView(icon("previous","Попередня композиція",()->player.seekToPrevious()),new LinearLayout.LayoutParams(0,dp(56),1));playerPlay=icon("play","Відтворити",this::toggle);playerPlay.primary();controls.addView(playerPlay,new LinearLayout.LayoutParams(dp(64),dp(64)));
        controls.addView(icon("next","Наступна композиція",()->player.seekToNext()),new LinearLayout.LayoutParams(0,dp(56),1));repeatButton=icon("repeat","Повтор",this::repeat);controls.addView(repeatButton,new LinearLayout.LayoutParams(0,dp(52),1));page.addView(controls);playerGap(page,16);
        LinearLayout extra=Ui.row(this);extra.setGravity(Gravity.CENTER);extra.addView(icon("clock","Таймер сну",this::sleep),new LinearLayout.LayoutParams(0,dp(48),1));extra.addView(icon("equalizer","Еквалайзер",this::showEqualizer),new LinearLayout.LayoutParams(0,dp(48),1));extra.addView(icon("queue","Черга відтворення",this::queue),new LinearLayout.LayoutParams(0,dp(48),1));page.addView(extra);Ui.space(page,16);
        if(landscape()){
            LinearLayout controlsPage=Ui.column(this);boolean afterCover=false;
            while(page.getChildCount()>2){View child=page.getChildAt(2);page.removeViewAt(2);if(child==playerCover){afterCover=true;continue;}if(afterCover){afterCover=false;continue;}controlsPage.addView(child);}
            LinearLayout split=Ui.row(this);int art=Math.min(dp(260),Math.max(dp(140),getResources().getDisplayMetrics().heightPixels-dp(140)));split.addView(playerCover,new LinearLayout.LayoutParams(art,art));LinearLayout.LayoutParams bodySize=new LinearLayout.LayoutParams(0,-2,1);bodySize.leftMargin=dp(24);split.addView(controlsPage,bodySize);page.addView(split);
        }
        playerDialog.setContentView(scroll);playerDialog.setOnDismissListener(d->{root.setVisibility(View.VISIBLE);miniId="";playerSeek=null;playerTitle=null;playerCover=null;playerArtwork=null;displayedId="";seeking=false;if(!isDestroyed())updatePlayer();});playerDialog.show();root.setVisibility(View.INVISIBLE);playerDialog.getWindow().setLayout(-1,-1);displayedId="";updatePlayer();
    }
    private void highlightPlaying(View view,String id){
        if(view instanceof TrackRowView row){row.nowPlaying(id.equals(row.getTag()));return;}
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)highlightPlaying(group.getChildAt(i),id);
    }
    private void updateTimes(long position,long duration){String elapsed=Protocol.time(position),remaining="−"+Protocol.time(Math.max(0,duration-position));if(!elapsed.contentEquals(playerTime.getText()))playerTime.setText(elapsed);if(!remaining.contentEquals(playerRemaining.getText()))playerRemaining.setText(remaining);}
    private void updatePlayer(){
        if(player==null)return;MediaItem item=player.getCurrentMediaItem();boolean playing=player.getPlayerError()==null&&player.getPlayWhenReady()&&player.getPlaybackState()!=Player.STATE_ENDED;boolean buffering=player.getPlaybackState()==Player.STATE_BUFFERING;if(miniProgress.isIndeterminate()!=buffering)miniProgress.setIndeterminate(buffering);miniPlay.icon(playing?"pause":"play");if(!android.text.TextUtils.equals(miniPlay.getContentDescription(),playing?"Пауза":"Відтворити"))miniPlay.setContentDescription(playing?"Пауза":"Відтворити");
        playerCard.setVisibility(item==null?View.GONE:View.VISIBLE);if(item==null){miniTitle.setText("Оберіть композицію");miniArtist.setText("Відкрити бібліотеку");miniProgress.setProgress(0);handler.removeCallbacks(bindArtwork);pendingArtwork=null;miniCover.bind(null);miniId="";highlightPlaying(content,"");if(playerDialog!=null&&playerDialog.isShowing())playerDialog.dismiss();return;}
        Track current=app.library.get(item.mediaId);long duration=player.getDuration()>0?player.getDuration():current==null?0:current.durationMs,position=Math.max(0,player.getCurrentPosition());int percent=duration>0?(int)(position*1000/duration):0;miniProgress.setProgress(percent);
        if(!miniId.equals(item.mediaId)){miniId=item.mediaId;miniTitle.setText(Ui.name(item.mediaMetadata.title));miniArtist.setText(item.mediaMetadata.artist);queueArtwork(current);highlightPlaying(content,miniId);}
        if(playerTitle!=null){if(!displayedId.equals(item.mediaId)){playerTitle.setText(Ui.name(item.mediaMetadata.title));playerArtist.setText(item.mediaMetadata.artist);playerAlbum.setText(Ui.album(item.mediaMetadata.albumTitle==null?"":item.mediaMetadata.albumTitle.toString()));}playerPlay.icon(playing?"pause":"play");if(!android.text.TextUtils.equals(playerPlay.getContentDescription(),playing?"Пауза":"Відтворити"))playerPlay.setContentDescription(playing?"Пауза":"Відтворити");
            if(!seeking){playerSeek.setProgress(percent);updateTimes(position,duration);}playerSeek.setEnabled(player.getDuration()>0&&player.isCurrentMediaItemSeekable());
            shuffleButton.active(settings.getBoolean("shuffle",false));int repeat=settings.getInt("repeat",0);repeatButton.active(repeat!=0);repeatButton.icon(repeat==1?"repeat1":"repeat");String description=new String[]{"Повтор вимкнено","Повтор композиції","Повтор черги"}[repeat];if(!android.text.TextUtils.equals(repeatButton.getContentDescription(),description))repeatButton.setContentDescription(description);favoriteButton.active(app.library.favorite(item.mediaId));
            if(!displayedId.equals(item.mediaId)){displayedId=item.mediaId;queueArtwork(current);}}
    }
    private void repeat(){int repeat=(settings.getInt("repeat",0)+1)%3;settings.edit().putInt("repeat",repeat).apply();updatePlayer();message(new String[]{"Повтор вимкнено","Повтор композиції","Повтор черги"}[repeat]);}
    private void queue(){if(ready()&&(queueDialog==null||!queueDialog.isShowing()))queueDialog=QueueDialog.show(this,player);}
    private void sleep(){String[] values={"Вимкнути","15 хвилин","30 хвилин","45 хвилин","60 хвилин","90 хвилин"};int[] minutes={0,15,30,45,60,90};new AlertDialog.Builder(this).setTitle("Таймер сну").setItems(values,(d,at)->{settings.edit().putLong("sleepUntil",at==0?0:System.currentTimeMillis()+minutes[at]*60000L).apply();message(values[at]);}).show();}
    private void showEqualizer(){if(equalizerDialog==null||!equalizerDialog.isShowing())equalizerDialog=EqualizerDialog.show(this);}
    private void settingRow(LinearLayout parent,String symbol,String name,String summary,Runnable action){
        LinearLayout row=Ui.row(this);row.setBackground(Ui.ripple(this,Ui.SURFACE,12));Ui.pad(row,4,4);IconButton glyph=icon(symbol,name,action);glyph.active(true);row.addView(glyph,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout labels=Ui.column(this);Ui.pad(labels,10,0);labels.addView(Ui.title(this,name,16));if(!summary.isEmpty()){Ui.space(labels,5);labels.addView(Ui.text(this,summary,12,Ui.MUTED));}row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));row.addView(icon("chevron",name,action),new LinearLayout.LayoutParams(dp(48),dp(48)));row.setOnClickListener(v->action.run());parent.addView(row);Ui.space(parent,6);
    }
    private void settingsPage(){LinearLayout column=scroll();label(column,"Персоналізація");settingRow(column,"settings","Вигляд","Акцентний колір, фон і сітка обкладинок",()->appearanceDialog=AppearanceDialog.show(this));label(column,"Медіатека");settingRow(column,"server","Джерела музики","Сервери, файли та синхронізація",this::openSources);settingRow(column,"download","Офлайн","Завантажені композиції",()->openCategory("Офлайн"));
        label(column,"Відтворення");settingRow(column,"equalizer","Еквалайзер","Частоти, підсилення та пресети",this::showEqualizer);settingRow(column,"play","Швидкість",settings.getFloat("speed",1)+"×",()->{String[] speeds={"0.75×","1×","1.25×","1.5×","2×"};float[] values={.75f,1f,1.25f,1.5f,2f};new AlertDialog.Builder(this).setTitle("Швидкість відтворення").setItems(speeds,(d,at)->{settings.edit().putFloat("speed",values[at]).apply();render();}).show();});settingRow(column,"clock","Таймер сну","Зупинка відтворення за таймером",this::sleep);
        label(column,getString(R.string.updates_title));settingRow(column,"download",getString(R.string.updates_settings),getString(R.string.updates_summary),this::showUpdates);
        Ui.space(column,24);TextView version=Ui.text(this,"SELFonium "+versionName(),12,Ui.MUTED);Ui.pad(version,16,8);column.addView(version);
    }
    private void onUpdateState(app.selfonium.update.UpdateManager.State state){
        if(isDestroyed()||root==null||root.getVisibility()!=View.VISIBLE)return;
        if(updateDialog!=null&&updateDialog.isShowing()){app.updates.announcement();return;}
        var notice=app.updates.announcement();if(notice!=null)showUpdateBanner(notice.version);
    }
    private void showUpdates(){if(updateBanner!=null){root.removeView(updateBanner);updateBanner=null;}if(updateDialog==null||!updateDialog.isShowing())updateDialog=UpdateDialog.show(this,app.updates);}
    private void showUpdateBanner(String version){
        if(updateBanner!=null)root.removeView(updateBanner);updateBanner=Ui.row(this);Ui.pad(updateBanner,12,8);updateBanner.setBackground(Ui.shape(this,Ui.TONAL,12));
        TextView open=Ui.button(this,getString(R.string.updates_banner,version),false,this::showUpdates);open.setContentDescription(getString(R.string.updates_open));updateBanner.addView(open,new LinearLayout.LayoutParams(0,-2,1));
        updateBanner.addView(Ui.button(this,getString(R.string.updates_later),false,()->{root.removeView(updateBanner);updateBanner=null;}));root.addView(updateBanner,1);
    }
    private String versionName(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(PackageManager.NameNotFoundException e){return "";}}
    private void message(String text){if(!isDestroyed())Toast.makeText(this,text==null?"Помилка":text,Toast.LENGTH_LONG).show();}
}
