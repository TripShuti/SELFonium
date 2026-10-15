"""Exercise the actual CREATE statements from Library.java using SQLite."""
from pathlib import Path
import json
import re
import sqlite3

source = (Path(__file__).resolve().parents[1] / 'app/src/main/java/app/selfonium/data/Library.java').read_text()
statements = [json.loads('"' + text + '"') for text in re.findall(r'db\.execSQL\("((?:[^"\\]|\\.)*)"\)', source)]
db = sqlite3.connect(':memory:')
db.execute('PRAGMA foreign_keys=ON')
def method(name):
    declaration=re.search(r'(?:public|private|protected)\s+(?:(?:static|synchronized|final)\s+)*[\w<>?,\[\].]+\s+'+re.escape(name)+r'\(',source)
    assert declaration, name
    start=source.index('{',declaration.end());depth=1;end=start+1
    while depth:
        depth += (source[end]=='{')-(source[end]=='}');end+=1
    return source[start+1:end-1]
def sqls(text):
    return [json.loads('"'+s+'"') for s in re.findall(r'db\.execSQL\("((?:[^"\\]|\\.)*)"',text)]
schema=sqls(method('onCreate'))+sqls(method('createSocialTables'))+sqls(method('createScrobbleTables'))
for sql in schema:db.execute(sql)

db.execute("INSERT INTO tracks(id,source,title,favorite,offline) VALUES ('one','server','Track',1,'/local/file')")
db.execute("INSERT INTO tracks(id,source,title) VALUES ('two','server','Track 2')")
db.execute("INSERT INTO playlists(id,name) VALUES (1,'Mix')")
insert = next(json.loads('"' + text + '"') for text in re.findall(r'execSQL\("((?:[^"\\]|\\.)*)"', source) if text.startswith('INSERT OR IGNORE INTO playlist_tracks'))
db.execute(insert, (1, 'one', 1, 1, 'one'))
db.execute(insert, (1, 'two', 1, 1, 'two'))
db.execute(insert, (1, 'one', 1, 1, 'one'))
assert db.execute('SELECT track,position FROM playlist_tracks ORDER BY position').fetchall() == [('one', 0), ('two', 1)]
db.execute("UPDATE tracks SET title='Updated' WHERE id='one'")
assert db.execute("SELECT favorite,offline FROM tracks WHERE id='one'").fetchone() == (1, '/local/file')
try:
    db.execute("INSERT INTO playlist_tracks VALUES (1,'missing',2)")
    raise AssertionError('Missing track accepted')
except sqlite3.IntegrityError:
    pass
db.execute("DELETE FROM tracks WHERE id='one'")
assert db.execute('SELECT track FROM playlist_tracks').fetchall() == [('two',)]
db.execute('DELETE FROM playlists WHERE id=1')
assert db.execute('SELECT COUNT(*) FROM playlist_tracks').fetchone()[0] == 0
assert db.execute('SELECT COUNT(*) FROM tracks').fetchone()[0] == 1
print('PASS: SQLite schema, playlist ordering/deduplication, favorite preservation and foreign-key cascades')

# Reconstruct the shipped v1 schema, then execute the actual upgrade through v4.
legacy = sqlite3.connect(':memory:')
legacy.execute('PRAGMA foreign_keys=ON')
for sql in sqls(method('onCreate')):
    legacy.execute(sql.replace(",artwork TEXT NOT NULL DEFAULT ''", "").replace('position INTEGER NOT NULL,PRIMARY KEY(playlist,position)', 'position INTEGER,PRIMARY KEY(playlist,track)'))
legacy.execute("INSERT INTO tracks(id,source,title,favorite,offline,played) VALUES ('old','server','Old',1,'/offline/old',123)")
legacy.execute("INSERT INTO playlists(id,name) VALUES (1,'Existing mix')")
legacy.execute("INSERT INTO playlist_tracks VALUES (1,'old',0)")
for sql in sqls(method('onUpgrade'))+sqls(method('createSocialTables'))+sqls(method('createScrobbleTables')):legacy.execute(sql)
assert legacy.execute("SELECT artwork,favorite,offline,played FROM tracks WHERE id='old'").fetchone() == ('',1,'/offline/old',123)
assert legacy.execute("SELECT track,position FROM playlist_tracks").fetchall() == [('old',0)]
legacy.execute("UPDATE tracks SET artwork=? WHERE id='old'", ('al-opaque/id',))
assert legacy.execute("SELECT artwork,favorite,offline FROM tracks WHERE id='old'").fetchone() == ('al-opaque/id',1,'/offline/old')
assert legacy.execute('PRAGMA foreign_key_check').fetchall() == []
print('PASS: v1 -> v4 social migration preserves favorites, offline paths, history and playlists')

# Server playlists preserve repeated entries; legacy local insertion still deduplicates.
legacy.execute("INSERT INTO playlist_tracks VALUES (1,'old',1)")
assert legacy.execute('SELECT track FROM playlist_tracks ORDER BY position').fetchall()==[('old',),('old',)]
legacy.execute("INSERT INTO remote_playlists VALUES (1,'server','remote','Server mix',1)")
legacy.execute("INSERT INTO playlists VALUES (2,'Other source mix')")
legacy.execute("INSERT INTO remote_playlists VALUES (2,'other','remote','Server mix',0)")
assert legacy.execute('SELECT COUNT(*) FROM remote_playlists').fetchone()[0]==2
# Coalesced offline likes: an old successful request must not acknowledge a newer value.
legacy.execute("INSERT INTO favorite_outbox VALUES ('old',1,1)")
legacy.execute("UPDATE favorite_outbox SET value=0,revision=revision+1 WHERE track='old'")
legacy.execute("UPDATE tracks SET favorite=0 WHERE id='old'")
ack=re.search(r'delete\("favorite_outbox","([^"]+)"',method('acknowledgeFavorite')).group(1)
legacy.execute('DELETE FROM favorite_outbox WHERE '+ack,('old','1','1'))
assert legacy.execute('SELECT value,revision FROM favorite_outbox').fetchall()==[(0,2)]
# An incoming server star cannot undo the user's pending unstar.
for sql in sqls(method('importFavorites')):
    if sql.startswith('UPDATE tracks SET favorite=0'):legacy.execute(sql,('server',))
    elif sql.startswith('UPDATE tracks SET favorite=1'):legacy.execute(sql,('old',))
assert legacy.execute("SELECT favorite FROM tracks WHERE id='old'").fetchone()[0]==0 # pending local unstar retained
legacy.execute('DELETE FROM favorite_outbox WHERE '+ack,('old','2','0'))
assert legacy.execute('SELECT COUNT(*) FROM favorite_outbox').fetchone()[0]==0
# Removing a remote mapping does not delete unrelated local playlists or tracks.
legacy.execute('DELETE FROM playlists WHERE id=2')
assert legacy.execute('SELECT COUNT(*) FROM remote_playlists').fetchone()[0]==1
assert legacy.execute('PRAGMA foreign_key_check').fetchall()==[]
print('PASS: duplicate server entries, source-scoped playlist IDs, outbox revisions and pending-like protection')

# The shipped v2 database already has artwork; migration must not add it twice.
v2=sqlite3.connect(':memory:');v2.execute('PRAGMA foreign_keys=ON')
for sql in sqls(method('onCreate')):
    v2.execute(sql.replace('position INTEGER NOT NULL,PRIMARY KEY(playlist,position)','position INTEGER,PRIMARY KEY(playlist,track)'))
v2.execute("INSERT INTO tracks(id,source,artwork,favorite,offline,played) VALUES ('v2','server','cover',1,'/saved',42)")
v2.execute("INSERT INTO playlists VALUES (1,'Keep')");v2.execute("INSERT INTO playlist_tracks VALUES (1,'v2',0)")
for sql in sqls(method('onUpgrade'))+sqls(method('createSocialTables'))+sqls(method('createScrobbleTables')):
    if not sql.startswith('ALTER TABLE tracks'):v2.execute(sql)
assert v2.execute('SELECT artwork,favorite,offline,played FROM tracks').fetchone()==('cover',1,'/saved',42)
assert v2.execute('SELECT * FROM playlist_tracks').fetchone()==(1,'v2',0)
assert v2.execute('PRAGMA foreign_key_check').fetchall()==[]
print('PASS: v2 -> v4 preserves artwork, favorites, downloads, history and existing playlist order')

# Catalog pruning must not cascade-delete offline social actions or saved collections.
prune=re.search(r'delete\("tracks","([^"\n]+sync_ids[^"\n]+)"',method('merge')).group(1)
protected=sqlite3.connect(':memory:');protected.execute('PRAGMA foreign_keys=ON')
for sql in sqls(method('onCreate'))+sqls(method('createSocialTables')):protected.execute(sql)
protected.execute('CREATE TEMP TABLE sync_ids(id TEXT PRIMARY KEY)')
for track in ['pending','liked','downloaded','listed','gone','other']:
 protected.execute('INSERT INTO tracks(id,source,title) VALUES (?,?,?)',(track,'other' if track=='other' else 'server',track))
protected.execute("INSERT INTO favorite_outbox VALUES ('pending',0,2)")
protected.execute("UPDATE tracks SET favorite=1 WHERE id='liked'")
protected.execute("UPDATE tracks SET offline='/saved' WHERE id='downloaded'")
protected.execute("INSERT INTO playlists VALUES (1,'Local')")
protected.execute("INSERT INTO playlist_tracks VALUES (1,'listed',0)")
protected.execute('DELETE FROM tracks WHERE '+prune,('server',))
assert {row[0] for row in protected.execute('SELECT id FROM tracks')}=={'pending','liked','downloaded','listed','other'}
assert protected.execute('SELECT value,revision FROM favorite_outbox').fetchall()==[(0,2)]
assert protected.execute('PRAGMA foreign_key_check').fetchall()==[]
print('PASS: catalog pruning preserves pending likes, favorites, playlist entries and downloads')

# Shipped v3 -> v4 only adds reporting tables; existing data is not rewritten.
v3=sqlite3.connect(':memory:')
for sql in sqls(method('onCreate'))+sqls(method('createSocialTables')):v3.execute(sql)
v3.execute("INSERT INTO tracks(id,source,favorite,offline,played,artwork) VALUES ('kept','server',1,'/saved',55,'cover')")
for sql in sqls(method('createScrobbleTables')):v3.execute(sql)
assert v3.execute('SELECT favorite,offline,played,artwork FROM tracks').fetchone()==(1,'/saved',55,'cover')
assert v3.execute('SELECT COUNT(*) FROM scrobble_outbox').fetchone()==(0,)
assert v3.execute('SELECT COUNT(*) FROM listening_checkpoint').fetchone()==(0,)
# Execute production pruning SQL against an oversized fixture.
now=100_000_000_000
v3.executemany('INSERT INTO scrobble_outbox(session,source,remote_id,account,started) VALUES (?,?,?,?,?)',[(str(i),'server','track','identity',now-i) for i in range(10005)])
prune=sqls(method('pruneScrobbles'))
for sql in prune:v3.execute(sql)
assert v3.execute('SELECT COUNT(*) FROM scrobble_outbox').fetchone()==(10000,)
assert v3.execute("SELECT COUNT(*) FROM scrobble_outbox WHERE session='10004'").fetchone()==(0,)
v3.execute("DELETE FROM tracks WHERE id='kept'")
assert v3.execute('SELECT COUNT(*) FROM scrobble_outbox').fetchone()==(10000,) # catalog pruning must not lose unsent plays
age=re.search(r'delete\("scrobble_outbox","(started<\?)"',method('pruneScrobbles')).group(1)
v3.execute('DELETE FROM scrobble_outbox WHERE '+age,(now-100,))
assert v3.execute('SELECT COUNT(*) FROM scrobble_outbox').fetchone()==(101,)
print('PASS: v3 -> v4 additive reporting migration, bounded queue, oldest-first pruning and catalog-independent submissions')
