"""CPU TV smoke fixtures; expose only the existing whitelist plus the long resume clip."""
from http.server import ThreadingHTTPServer
from urllib.parse import urlparse
import argparse
import re

from perf_fixtures import Handler, OUT


class TvHandler(Handler):
    def do_GET(self):
        if urlparse(self.path).path != '/tv-long.mp4':
            return super().do_GET()
        path = OUT / 'tv-long.mp4'
        if not path.is_file():
            self.send_error(404)
            return
        data = path.read_bytes()
        range_header = self.headers.get('Range')
        if range_header:
            match = re.fullmatch(r'bytes=(\d+)-(\d*)', range_header)
            if not match:
                self.send_error(416)
                return
            start = int(match[1])
            end = min(int(match[2]) if match[2] else len(data) - 1, len(data) - 1)
            if start > end:
                self.send_error(416)
                return
            total = len(data)
            data = data[start:end + 1]
            self.send_response(206)
            self.send_header('Content-Range', f'bytes {start}-{end}/{total}')
        else:
            self.send_response(200)
        self.send_header('Content-Type', 'video/mp4')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, default=8776)
    args = parser.parse_args()
    ThreadingHTTPServer(('127.0.0.1', args.port), TvHandler).serve_forever()
