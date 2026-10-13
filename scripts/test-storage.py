"""Exercise the actual CREATE statements from Library.java using SQLite."""
from pathlib import Path
import json
import re
import sqlite3

source = (Path(__file__).resolve().parents[1] / 'app/src/main/java/app/selfonium/data/Library.java').read_text()
statements = [json.loads('"' + text + '"') for text in re.findall(r'db\.execSQL\("((?:[^"\\]|\\.)*)"\)', source)]
db = sqlite3.connect(':memory:')
db.execute('PRAGMA foreign_keys=ON')
for sql in statements:
    if sql.startswith(('CREATE TABLE', 'CREATE INDEX')):
        db.execute(sql)

db.execute("INSERT INTO tracks(id,source,title,favorite,offline) VALUES ('one','server','Track',1,'/local/file')")
db.execute("INSERT INTO tracks(id,source,title) VALUES ('two','server','Track 2')")
db.execute("INSERT INTO playlists(id,name) VALUES (1,'Mix')")
insert = next(json.loads('"' + text + '"') for text in re.findall(r'execSQL\("((?:[^"\\]|\\.)*)"', source) if text.startswith('INSERT OR IGNORE INTO playlist_tracks'))
db.execute(insert, (1, 'one', 1))
db.execute(insert, (1, 'two', 1))
db.execute(insert, (1, 'one', 1))
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

# Reconstruct the shipped v1 schema, then execute the actual v2 migration.
legacy = sqlite3.connect(':memory:')
legacy.execute('PRAGMA foreign_keys=ON')
for sql in statements:
    if sql.startswith(('CREATE TABLE', 'CREATE INDEX')):
        legacy.execute(sql.replace(",artwork TEXT NOT NULL DEFAULT ''", ""))
legacy.execute("INSERT INTO tracks(id,source,title,favorite,offline,played) VALUES ('old','server','Old',1,'/offline/old',123)")
legacy.execute("INSERT INTO playlists(id,name) VALUES (1,'Existing mix')")
legacy.execute("INSERT INTO playlist_tracks VALUES (1,'old',0)")
for sql in statements:
    if sql.startswith('ALTER TABLE'):
        legacy.execute(sql)
assert legacy.execute("SELECT artwork,favorite,offline,played FROM tracks WHERE id='old'").fetchone() == ('',1,'/offline/old',123)
assert legacy.execute("SELECT track,position FROM playlist_tracks").fetchall() == [('old',0)]
legacy.execute("UPDATE tracks SET artwork=? WHERE id='old'", ('al-opaque/id',))
assert legacy.execute("SELECT artwork,favorite,offline FROM tracks WHERE id='old'").fetchone() == ('al-opaque/id',1,'/offline/old')
assert legacy.execute('PRAGMA foreign_key_check').fetchall() == []
print('PASS: v1 -> v2 artwork migration preserves favorites, offline paths, history and playlists')
