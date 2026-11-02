"""Deterministic S-063 fixtures. Only rooted emulator + perfdebug/perfbenchmark packages.

Generate: python scripts/perf_fixtures.py generate
Serve: python scripts/perf_fixtures.py serve
Load/verify: python scripts/perf_fixtures.py load
All generated binaries and results stay in ignored build/perf/fixtures.
"""
import argparse
import hashlib
import html
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import re
import shlex
import sqlite3
import struct
import subprocess
import time
from urllib.parse import parse_qs, urlparse
import zlib

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "build/perf/fixtures"
BASE = "http://127.0.0.1:8765"
PACKAGES = ("com.arny.mobilecinema.perfdebug", "com.arny.mobilecinema.perfbenchmark")
STAMP = 1800000000000


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def insert(db, table, data):
    columns = [x[1:3] for x in db.execute(f"PRAGMA table_info({table})")]
    row = {name: ("" if kind == "TEXT" else 0) for name, kind in columns}
    row.update(data)
    db.execute(f"INSERT INTO {table} ({','.join(row)}) VALUES ({','.join('?' for _ in row)})", list(row.values()))


def generate():
    OUT.mkdir(parents=True, exist_ok=True)
    # Original synthetic image: blue 160x90 RGB PNG, no external rights/content.
    def chunk(kind, body):
        return struct.pack('!I', len(body)) + kind + body + struct.pack('!I', zlib.crc32(kind + body))
    image = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('!2I5B', 160, 90, 8, 2, 0, 0, 0))
    image += chunk(b'IDAT', zlib.compress((b'\0' + b'\x30\x50\x90' * 160) * 90)) + chunk(b'IEND', b'')
    (OUT / 'poster.png').write_bytes(image)
    # Original synthetic test media: 30-second blue video, H.264, no external assets.
    subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-f', 'lavfi',
                    '-i', 'color=c=blue:s=160x90:r=15:d=30', '-an', '-c:v', 'libx264',
                    '-pix_fmt', 'yuv420p', '-movflags', '+faststart', str(OUT / 'test.mp4')], check=True)
    # S-074: same blue video, but with two audio tracks and an embedded subtitle track, so track
    # selection (audio languages, subtitles) can be checked for real instead of being assumed.
    (OUT / 'subtitles.srt').write_text(
        '1' + NL + '00:00:00,000 --> 00:00:05,000' + NL + 'fixture subtitle one' + NL + NL +
        '2' + NL + '00:00:05,000 --> 00:00:10,000' + NL + 'fixture subtitle two' + NL,
        encoding='utf-8')
    subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y',
                    '-f', 'lavfi', '-i', 'color=c=blue:s=160x90:r=15:d=30',
                    '-f', 'lavfi', '-i', 'sine=frequency=440:duration=30',
                    '-f', 'lavfi', '-i', 'sine=frequency=880:duration=30',
                    '-i', str(OUT / 'subtitles.srt'),
                    '-map', '0:v', '-map', '1:a', '-map', '2:a', '-map', '3:s',
                    '-c:v', 'libx264', '-pix_fmt', 'yuv420p',
                    '-c:a', 'aac', '-b:a', '48k',
                    '-c:s', 'srt',
                    '-metadata:s:a:0', 'language=rus', '-metadata:s:a:1', 'language=eng',
                    '-metadata:s:s:0', 'language=rus',
                    str(OUT / 'multitrack.mkv')], check=True)
    schema = json.loads((ROOT / 'app/schemas/com.arny.mobilecinema.data.db.AppDatabase/8.json').read_text())['database']
    manifest = {'seed': 's063-v1-no-random', 'schema_version': schema['version'], 'databases': {}}
    for size in (100, 1000, 10000):
        path = OUT / f'catalog-{size}.db'
        if path.exists():
            path.unlink()
        with sqlite3.connect(path) as db:
            for entity in schema['entities']:
                db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
                for index in entity['indices']:
                    db.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))
            for sql in schema['setupQueries']:
                db.execute(sql)
            db.execute(f"PRAGMA user_version={schema['version']}")
            for i in range(1, size + 1):
                serial = i % 2 == 0
                title = {1: 'Матрица', 2: 'Тестовый сериал', 3: 'Матрица: Перезагрузка', 5: 'Матрица'}.get(i, f'Fixture {i:05d}')
                seasons = [{'season': s, 'episodes': [
                    {'id': s * 100 + e, 'episode': str(e), 'title': f'Episode {s}-{e}',
                     'hls': BASE + '/test.mp4', 'dash': '', 'poster': BASE + '/poster.png'}
                    for e in (2, 1)]} for s in (2, 1)] if serial else []
                insert(db, 'movies', {
                    'dbId': i, 'movie_id': i, 'title': title,
                    'origTitle': {1: 'The Matrix', 3: 'Matrix Reloaded', 5: 'Matrix Remake'}.get(i, f'Original {i:05d}'),
                    'type': 2 if serial else 1, 'pageUrl': f"/{'serials' if serial else 'films'}/{i}",
                    'img': '' if i % 11 == 0 else BASE + ('/missing.png' if i % 7 == 0 else '/poster.png'),
                    'year': 1999 if i == 1 else 2000 + i % 25, 'genre': 'Драма' if serial else 'Фантастика',
                    'countries': 'Тест', 'directors': 'Fixture Director', 'actors': 'Fixture Actor',
                    'description': 'Artificial test title. Not a real catalogue item.', 'quality': 'TEST',
                    'durationSec': 30, 'ratingImdb': 5.0 + (i % 40) / 10,
                    'updated': STAMP + i, 'detailsFetchedAt': 4102444800000,
                    'seasons': json.dumps(seasons, ensure_ascii=False),
                    'hdUrls': '' if serial else BASE + '/test.mp4', 'addedToHistory': 1 if i <= 2 else 0,
                })
            for i in (1, 2):
                playable = 'film:1' if i == 1 else 'ep:2:2:201'
                insert(db, 'history', {'dbId': i, 'movie_dbid': i, 'position': 10000,
                                      'season': 1 if i == 2 else 0, 'episode': 0,
                                      'latest_time': STAMP, 'last_playable_id': playable, 'duration_ms': 30000})
                insert(db, 'playback_progress', {'dbId': i, 'movie_dbid': i, 'playable_id': playable,
                       'profile_id': 'default', 'position_ms': 10000, 'duration_ms': 30000, 'played_ms': 10000,
                       'latest_time': STAMP, 'updated_at': STAMP, 'season': 1 if i == 2 else 0,
                       'season_number': 2 if i == 2 else 0, 'episode_number': 1 if i == 2 else 0})
            insert(db, 'favorites', {'dbId': 1, 'movie_dbid': 3, 'latest_time': STAMP})
            assert db.execute('select count(*) from movies').fetchone()[0] == size
            assert db.execute('pragma integrity_check').fetchone()[0] == 'ok'
        manifest['databases'][str(size)] = {'sha256': digest(path), 'count': size}
    manifest['assets'] = {name: digest(OUT / name) for name in ('poster.png', 'test.mp4', 'multitrack.mkv')}
    (OUT / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(manifest, indent=2))


NL = chr(10)


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(OUT), **kwargs)

    def log_message(self, *args):
        pass  # Do not persist request queries or user content.

    def do_GET(self):
        url = urlparse(self.path)
        # S-071: endpoints for the playback failure matrix (S-010). Only for the local stand.
        if url.path == '/forbidden':
            self.send_error(403, 'fixture forbidden'); return
        if url.path == '/redirect':
            self.send_response(302)
            self.send_header('Location', '/test.mp4')
            self.send_header('Content-Length', '0')
            self.end_headers(); return
        if url.path == '/slow':
            query = parse_qs(url.query)
            delay_ms = min(int(query.get('ms', ['3000'])[0]), 10_000)
            time.sleep(delay_ms / 1000)
            data = b'fixture slow body'
            self.send_response(200); self.send_header('Content-Type', 'text/plain')
            self.send_header('Content-Length', str(len(data))); self.end_headers()
            self.wfile.write(data); return
        if url.path in ('/test.m3u8', '/test-hls/index.m3u8'):
            body = (
                '#EXTM3U' + NL +
                '#EXT-X-VERSION:3' + NL +
                '#EXT-X-TARGETDURATION:30' + NL +
                '#EXT-X-MEDIA-SEQUENCE:0' + NL +
                '#EXTINF:30.0,' + NL +
                '/test-seg0.ts' + NL +
                '#EXT-X-ENDLIST' + NL
            ).encode('ascii')
            self.send_response(200)
            self.send_header('Content-Type', 'application/vnd.apple.mpegurl')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers(); self.wfile.write(body); return
        if url.path == '/test-seg0.ts':
            segment = OUT / 'test-seg0.ts'
            if not segment.exists():
                subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y',
                                '-i', str(OUT / 'test.mp4'), '-c', 'copy',
                                '-f', 'mpegts', str(segment)], check=True)
            data = segment.read_bytes()
            self.send_response(200); self.send_header('Content-Type', 'video/mp2t')
            self.send_header('Content-Length', str(len(data)))
            self.end_headers(); self.wfile.write(data); return
        if url.path in ('/films/search/', '/serials/search/'):
            query = parse_qs(url.query)
            word = query.get('slv', query.get('word', ['']))[0].casefold()
            # 'matrx' — намеренная опечатка: проверяет fuzzy-ранжирование (S-014).
            matches = word in ('m', 'matrix', 'матрица', 'the matrix', 'matrx')
            body = ('<div class="my_razdel film"><a href="/films/1">'
                    '<img class="screenf" src="/poster.png" alt="Матрица / The Matrix">'
                    '<span class="namefilm">Матрица</span></a><span class="in year">1999</span>'
                    '<span class="rei">8.7</span><div class="discripfilm">Fixture result</div></div>'
                    # Site-only title (absent from the fixture catalogue): lets the «Везде» screen
                    # show both sections «В каталоге» and «На сайте» (S-013).
                    '<div class="my_razdel film"><a href="/films/9001">'
                    '<img class="screenf" src="/poster.png" alt="Матрица: Воскрешение / The Matrix Resurrections">'
                    '<span class="namefilm">Матрица: Воскрешение</span></a><span class="in year">2021</span>'
                    '<span class="rei">5.7</span><div class="discripfilm">Fixture site-only result</div></div>'
                    # Тайтл, который содержит опечаточный запрос буквально: должен стоять выше fuzzy.
                    '<div class="my_razdel film"><a href="/films/9002">'
                    '<img class="screenf" src="/poster.png" alt="Matrx Legacy">'
                    '<span class="namefilm">Matrx Legacy</span></a><span class="in year">2020</span>'
                    '<span class="rei">4.0</span><div class="discripfilm">Fixture contains result</div></div>')
            if not matches or url.path.startswith('/serials'):
                body = '<div class="err">ничего не найдено</div>'
            data = body.encode('utf-8')
            self.send_response(200); self.send_header('Content-Type', 'text/html; charset=utf-8')
        elif url.path == '/update.txt':
            data = b'2030.01.01 00:00'
            self.send_response(200); self.send_header('Content-Type', 'text/plain')
        else:
            # Whitelist prevents directory listing or exposure of generated databases.
            if url.path not in ('/poster.png', '/test.mp4', '/multitrack.mkv'):
                self.send_error(404); return
            if url.path == '/test.mp4' and self.headers.get('Range'):
                media = (OUT / 'test.mp4').read_bytes()
                match = re.fullmatch(r'bytes=(\d+)-(\d*)', self.headers['Range'])
                if not match:
                    self.send_error(416); return
                start = int(match[1]); end = min(int(match[2]) if match[2] else len(media)-1, len(media)-1)
                if start > end:
                    self.send_error(416); return
                data = media[start:end+1]
                self.send_response(206); self.send_header('Content-Type', 'video/mp4')
                self.send_header('Content-Range', f'bytes {start}-{end}/{len(media)}')
                self.send_header('Content-Length', str(len(data)))
                self.end_headers(); self.wfile.write(data); return
            return super().do_GET()
        self.send_header('Content-Length', str(len(data))); self.end_headers(); self.wfile.write(data)


def load(serial, sizes=(100, 1000, 10000), db_override=None, packages=PACKAGES):
    # Never accept a phone or an arbitrary package, even if rooted.
    if not re.fullmatch(r'emulator-\d+', serial):
        raise ValueError('Only emulator serials accepted')
    def adb(*cmd):
        return subprocess.check_output(['adb', '-s', serial, *cmd], text=True,
             env=dict(os.environ, MSYS_NO_PATHCONV='1'), timeout=60).strip()
    assert adb('shell', 'getprop', 'ro.kernel.qemu') == '1'
    assert adb('shell', 'id', '-u') == '0', 'Run adb -s emulator-5554 root first'
    adb('reverse', 'tcp:8765', 'tcp:8765')
    report = {'seed': 's063-v1-no-random', 'device': serial, 'loads': []}
    manifest = json.loads((OUT / 'manifest.json').read_text())
    for size in sizes:
        for package in packages:
            assert adb('shell', 'pm', 'path', package).startswith('package:')
            adb('shell', 'am', 'force-stop', package)
            root = f'/data/user/0/{package}'
            uid = adb('shell', f'stat -c %u {root}')
            assert uid.isdigit() and int(uid) >= 10000
            adb('shell', f'mkdir -p {root}/databases {root}/shared_prefs')
            stage = '/data/local/tmp/s063.db'
            adb('push', str(db_override or (OUT / f'catalog-{size}.db')), stage)
            target = f'{root}/databases/Movies'
            adb('shell', f'rm -f {target}-wal {target}-shm; cp {stage} {target}; chown {uid}:{uid} {root}/databases {target}; chmod 600 {target}; restorecon -RF {root}/databases')
            prefs = OUT / f'{package}_preferences.xml'
            prefs.write_text(f'<?xml version="1.0" encoding="utf-8"?><map><string name="anwap_base_url">{html.escape(BASE)}</string><boolean name="pref_key_home_highlights_enabled" value="true"/><boolean name="pref_compose_home" value="true"/></map>', encoding='utf-8')
            adb('push', str(prefs), '/data/local/tmp/s063-prefs.xml')
            adb('shell', f'cp /data/local/tmp/s063-prefs.xml {root}/shared_prefs/{prefs.name}; chown -R {uid}:{uid} {root}/shared_prefs; restorecon -RF {root}/shared_prefs')
            sha = adb('shell', f'sha256sum {target}').split()[0]
            if not db_override:
                assert sha == manifest['databases'][str(size)]['sha256']
            query = "select (select count(*) from movies), (select count(*) from history), (select count(*) from favorites), (select playable_id from playback_progress where movie_dbid=2);"
            actual = adb('shell', f'sqlite3 {target} {shlex.quote(query)}')
            assert actual == f'{size}|2|1|ep:2:2:201', actual
            start = adb('shell', 'am', 'start', '-W', '-n', package + '/com.arny.mobilecinema.presentation.MainActivity')
            assert 'Status: ok' in start
            time.sleep(2)
            assert adb('shell', 'pidof', package), 'App failed to stay alive with fixture'
            adb('shell', 'am', 'force-stop', package)
            assert adb('shell', f'sqlite3 {target} "select count(*) from movies;"') == str(size)
            report['loads'].append({'size': size, 'package': package, 'sha256_at_load': sha, 'actual': actual, 'room_open_smoke': 'PASS'})
    (OUT / 'load-report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, indent=2))


def net(serial, state):
    """S-071: deterministic offline/online switch for the emulator (never a phone)."""
    if not re.fullmatch(r'emulator-\d+', serial):
        raise ValueError('Only emulator serials accepted')
    if state not in ('off', 'on'):
        raise ValueError("state must be 'off' or 'on'")

    def adb(*cmd):
        return subprocess.run(['adb', '-s', serial, *cmd], capture_output=True,
                              env=dict(os.environ, MSYS_NO_PATHCONV='1'), timeout=120)

    flag = 'disable' if state == 'off' else 'enable'
    adb('shell', f'svc wifi {flag}')
    adb('shell', f'svc data {flag}')
    adb('shell', f'settings put global airplane_mode_on {1 if state == "off" else 0}')
    adb('shell', 'am broadcast -a android.intent.action.AIRPLANE_MODE --ez state '
                 + ('true' if state == 'off' else 'false'))
    # Ping is unreliable inside the emulator (ICMP may be filtered), so report the flags the
    # failure matrix actually depends on instead of guessing reachability.
    wifi = adb('shell', 'settings get global wifi_on').stdout.decode().strip()
    data = adb('shell', 'settings get global mobile_data').stdout.decode().strip()
    airplane = adb('shell', 'settings get global airplane_mode_on').stdout.decode().strip()
    print(json.dumps({'serial': serial, 'network': state,
                      'wifi_on': wifi, 'mobile_data_on': data, 'airplane_mode_on': airplane}))


def verify(serial):
    """Verify fixture HTTP responses from the emulator, preserving raw bytes via pull."""
    if not re.fullmatch(r'emulator-\d+', serial):
        raise ValueError('Only emulator serials accepted')
    def adb(*args):
        return subprocess.run(['adb', '-s', serial, *args], capture_output=True,
                              timeout=20, check=True, env=dict(os.environ, MSYS_NO_PATHCONV='1'))
    assert adb('shell', 'getprop', 'ro.kernel.qemu').stdout.strip() == b'1'
    checks = []
    cases = [('/poster.png', 200), ('/missing.png', 404), ('/test.mp4', 200),
             ('/films/search/?slv=matrix', 200), ('/serials/search/?word=matrix', 200),
             ('/update.txt', 200), ('/data.zip', 404)]
    for index, (path, status) in enumerate(cases):
        request = f'GET {path} HTTP/1.0\\r\\nHost: 127.0.0.1:8765\\r\\n\\r\\n'
        # Keeping stdin open briefly avoids toybox nc's early EOF; pull avoids PTY CRLF conversion.
        cmd = f'( printf {shlex.quote(request)}; sleep 1 ) | toybox nc -q 2 -w 5 127.0.0.1 8765 > /data/local/tmp/s063-http.bin'
        adb('shell', cmd)
        raw = OUT / f'http-{index}.bin'
        adb('pull', '/data/local/tmp/s063-http.bin', str(raw))
        head, sep, body = raw.read_bytes().partition(b'\r\n\r\n')
        assert sep and int(head.split()[1]) == status, path
        if path in ('/poster.png', '/test.mp4'):
            assert hashlib.sha256(body).hexdigest() == digest(OUT / path.lstrip('/'))
        if path.startswith('/films'):
            assert 'Матрица'.encode() in body and b'namefilm' in body
        if path.startswith('/serials'):
            assert 'ничего не найдено'.encode() in body
        if path == '/update.txt':
            assert body == b'2030.01.01 00:00'
        checks.append({'path': path, 'status': status, 'bytes': len(body), 'emulator_fetch': 'PASS'})
    media = json.loads(subprocess.check_output(['ffprobe', '-v', 'error', '-show_entries',
           'format=duration:stream=codec_name,width,height', '-of', 'json', str(OUT / 'test.mp4')], text=True))
    assert float(media['format']['duration']) == 30 and media['streams'][0]['codec_name'] == 'h264'
    report = {'checks': checks, 'media': media, 'scope': 'Fixture HTTP fetch, not full UI/playback test'}
    (OUT / 'network-report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['generate', 'serve', 'load', 'verify', 'net'])
    parser.add_argument('--serial', default='emulator-5554')
    parser.add_argument('--size', type=int, choices=[100, 1000, 10000])
    parser.add_argument('--db', type=Path)
    parser.add_argument('--package', action='append')
    parser.add_argument('--state', choices=['off', 'on'], default='off',
                        help="net action: 'off' = offline, 'on' = back online")
    args = parser.parse_args()
    if args.action == 'generate': generate()
    elif args.action == 'load':
        packages = tuple(args.package) if args.package else PACKAGES
        load(args.serial, (args.size,) if args.size else (100, 1000, 10000), args.db, packages)
    elif args.action == 'verify': verify(args.serial)
    elif args.action == 'net': net(args.serial, args.state)
    else: ThreadingHTTPServer(('127.0.0.1', 8765), Handler).serve_forever()
