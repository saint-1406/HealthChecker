"""Builds the VitaCore launcher icon from the OCTO logo: the octopus without the wordmark, traced into a vector.

The logo is a black silhouette on cream. Dark pixels are kept, the "OCTO" lettering under the octopus is dropped (the
widest blank band of rows separates the two), and potrace turns the silhouette into curves. The result is written as
Android vector drawables:

- res/drawable/ic_launcher_foreground.xml — the octopus, centred in the adaptive icon's 66 dp safe circle;
- res/drawable/ic_launcher_background.xml — the cream of the logo;
- res/drawable/ic_launcher_monochrome.xml — the same octopus for Android 13+ themed icons;
- res/drawable/ic_launcher.xml — background and octopus in one drawable, used in the app itself.

Run: python tools/icon/octo_icon.py path/to/octo-logo.png   (needs pillow and potracer)
"""
import sys
from pathlib import Path

import numpy as np
import potrace
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / 'app/src/main/res'
BACKGROUND = '#FBF7F1'
INK = '#111111'
VIEW = 108.0     # adaptive icon canvas, dp
WIDTH = 58.0     # octopus width on the canvas: the round silhouette stays inside the 66 dp safe circle


def octopus_mask(path):
    img = Image.open(path).convert('L')
    a = np.array(img) < 128                     # black ink
    rows = np.where(a.any(axis=1))[0]
    # The octopus and the wordmark are separated by the longest run of empty rows inside the drawing.
    gaps = [(rows[i + 1] - rows[i], rows[i]) for i in range(len(rows) - 1)]
    size, end = max(gaps)
    if size < 10:
        raise ValueError('could not find the gap between the octopus and the lettering')
    a = a[:end + 1]
    ys, xs = np.where(a)
    return a[ys.min():ys.max() + 1, xs.min():xs.max() + 1]


PAD = 10


def trace(mask):
    # potracer fills the False pixels, so it gets the inverse; a blank margin keeps the image frame out of the outline.
    bitmap = potrace.Bitmap(np.pad(~mask, PAD, constant_values=True))
    return bitmap.trace(turdsize=8, alphamax=1.0, opticurve=True, opttolerance=0.2)


def path_data(curves, h, w):
    scale = WIDTH / w
    ox = (VIEW - w * scale) / 2
    oy = (VIEW - h * scale) / 2

    def p(pt):
        return f'{ox + (pt.x - PAD) * scale:.2f},{oy + (pt.y - PAD) * scale:.2f}'

    out = []
    for curve in curves:
        out.append('M' + p(curve.start_point))
        for seg in curve.segments:
            if seg.is_corner:
                out.append('L' + p(seg.c) + 'L' + p(seg.end_point))
            else:
                out.append('C' + p(seg.c1) + ' ' + p(seg.c2) + ' ' + p(seg.end_point))
        out.append('Z')
    return ''.join(out)


def vector(*paths):
    body = ''.join(f'\n    <path android:fillColor="{color}" android:fillType="evenOdd" android:pathData="{data}"/>' for color, data in paths)
    return (f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" '
            f'android:viewportWidth="108" android:viewportHeight="108">{body}\n</vector>\n')


def main(src):
    mask = octopus_mask(src)
    h, w = mask.shape
    data = path_data(trace(mask), h, w)
    square = f'M0,0h{VIEW:g}v{VIEW:g}h-{VIEW:g}z'
    (RES / 'drawable/ic_launcher_foreground.xml').write_text(vector((INK, data)), encoding='utf-8')
    (RES / 'drawable/ic_launcher_monochrome.xml').write_text(vector(('#FFFFFFFF', data)), encoding='utf-8')
    (RES / 'drawable/ic_launcher_background.xml').write_text(vector((BACKGROUND, square)), encoding='utf-8')
    (RES / 'drawable/ic_launcher.xml').write_text(vector((BACKGROUND, square), (INK, data)), encoding='utf-8')
    print(f'octopus {w}x{h} px -> {len(data)} chars of path data')


if __name__ == '__main__':
    main(sys.argv[1])
