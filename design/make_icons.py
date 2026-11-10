"""Brano icon set: renames the Gemini SVGs and draws the missing ones in the same style
(24x24, 2px round strokes, soft filled shapes, a single currentColor).

Run from the project root:  python -I design/make_icons.py
"""
import os
import shutil

ROOT = os.path.dirname(os.path.abspath(__file__))
ICONS = os.path.join(ROOT, 'icons')
LAUNCHER = os.path.join(ROOT, 'launcher')

# Gemini files kept as they are, under their final names.
GEMINI = {
    'gemini-svg (1).svg': 'Play.svg',
    'gemini-svg.svg': 'Pause.svg',
    'gemini-svg (2).svg': 'Album.svg',
    'heart.svg': 'Favorite.svg',
    'gemini-svg (3).svg': 'SkipNext.svg',
    'gemini-svg (4).svg': 'SkipPrevious.svg',
    'gemini-svg (5).svg': 'Repeat.svg',
    'gemini-svg (6).svg': 'RepeatOn.svg',
    'gemini-svg (8).svg': 'ChevronDown.svg',
    'gemini-svg (9).svg': 'Close.svg',
    'gemini-svg (10).svg': 'MoreVert.svg',
    'gemini-svg (11).svg': 'DragHandle.svg',
    'gemini-svg (12).svg': 'Info.svg',
    'gemini-svg (13).svg': 'Back.svg',
    'gemini-svg (15).svg': 'PlaylistAdd.svg',
    'gemini-svg (16).svg': 'FavoriteBorder.svg',
    'gemini-svg (17).svg': 'Sort.svg',
}
# Gemini files redrawn: the "1" of RepeatOne is unreadable, Playlist looks like a menu.
REJECTED = {'gemini-svg (7).svg': 'RepeatOne-gemini.svg', 'gemini-svg (14).svg': 'Playlist-gemini.svg'}

S = 'fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"'
F = 'fill="currentColor"'


def thin(w):
    """Stroke attributes with a custom width (no duplicate stroke-width attribute)."""
    return S.replace('stroke-width="2"', f'stroke-width="{w}"')

# Gemini's Repeat outline, reused so RepeatOne matches it exactly.
REPEAT = ('M7 7h10a3 3 0 0 1 3 3v2a1 1 0 1 0 2 0v-2a5 5 0 0 0-5-5H7V3.5a1 1 0 0 0-1.7-.7l-3 3a1 1 0 0 0 0 1.4l3 3'
          'A1 1 0 0 0 7 9.5V7zm10 10H7a3 3 0 0 1-3-3v-2a1 1 0 1 0-2 0v2a5 5 0 0 0 5 5h10v1.5a1 1 0 0 0 1.7.7l3-3'
          'a1 1 0 0 0 0-1.4l-3-3a1 1 0 0 0-1.7.7V17z')


def shuffle(dy=0.0):
    def y(v):
        return f'{v + dy:g}'
    return (f'<path {S} d="M3 {y(7)}h2.6c1.7 0 3.2.8 4.1 2.2l3.6 5.6c.9 1.4 2.4 2.2 4.1 2.2H20"/>'
            f'<path {S} d="M3 {y(17)}h2.6c1.7 0 3.2-.8 4.1-2.2l.5-.8M13.8 {y(9.99)}l.5-.8c.9-1.4 2.4-2.2 4.1-2.2H20"/>'
            f'<path {S} d="M17.5 {y(4.5)}L20 {y(7)}l-2.5 2.5M17.5 {y(14.5)}L20 {y(17)}l-2.5 2.5"/>')


DRAWN = {
    'Shuffle.svg': shuffle(),
    # Same "active" dot as Gemini's RepeatOn, the arrows lifted to make room.
    'ShuffleOn.svg': shuffle(-1.5) + f'<circle {F} cx="12" cy="21" r="1.3"/>',
    'RepeatOne.svg': f'<path {F} d="{REPEAT}"/>'
                     f'<path {thin(1.8)} d="M10.9 10.4l1.6-1.1v5.4"/>',
    'Playlist.svg': f'<path {S} d="M4 6h12M4 11h12M4 16h7"/>'
                    f'<path {S} d="M18.5 17V8.6l2.5 1"/><circle {F} cx="16.3" cy="17.2" r="2.4"/>',
    'Queue.svg': f'<path {S} d="M4 6h16M4 11h16M11 17h9"/>'
                 f'<path {F} d="M4 15.3v4.4a.8.8 0 0 0 1.2.7l3.4-2.2a.8.8 0 0 0 0-1.4l-3.4-2.2a.8.8 0 0 0-1.2.7z"/>',
    'SleepTimer.svg': f'<path {S} d="M20 14.6A8 8 0 1 1 9.4 4a6.4 6.4 0 0 0 10.6 10.6z"/>'
                      f'<path {thin(1.6)} d="M16 4.5h3l-3 3.5h3"/>',
    'Search.svg': f'<circle {S} cx="10.5" cy="10.5" r="6.5"/><path {S} d="M15.4 15.4L20 20"/>',
    'Library.svg': f'<rect {S} x="3" y="7" width="14" height="14" rx="3"/>'
                   f'<path {S} d="M7 3.5h10.5A3.5 3.5 0 0 1 21 7v10.5"/>'
                   f'<circle {S} cx="10" cy="14" r="3.2"/><circle {F} cx="10" cy="14" r="1"/>',
    'Artist.svg': f'<circle {S} cx="10" cy="7.5" r="3.8"/><path {S} d="M3 20a7 7 0 0 1 10.6-6"/>'
                  f'<path {S} d="M19.5 18.5V11l2 .8"/><circle {F} cx="17.5" cy="18.7" r="2.2"/>',
    'Folder.svg': f'<path {S} d="M3 8a3 3 0 0 1 3-3h3.2a2 2 0 0 1 1.5.7L12 7.2h6a3 3 0 0 1 3 3V17a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3z"/>',
    # Soft sliders read better at this size than a gear and match the rounded strokes.
    'Settings.svg': f'<path {S} d="M4 7.5h5M15 7.5h5M4 16.5h9.9M20 16.5h0"/>'
                    f'<circle {S} cx="12" cy="7.5" r="2.6"/><circle {S} cx="17" cy="16.5" r="2.6"/>',
    'Add.svg': f'<path {S} d="M12 5v14M5 12h14"/>',
    'Import.svg': f'<path {S} d="M4 14.5V17a3 3 0 0 0 3 3h10a3 3 0 0 0 3-3v-2.5"/><path {S} d="M12 4v10.5M7.8 10.5l4.2 4.2 4.2-4.2"/>',
    'Export.svg': f'<path {S} d="M4 14.5V17a3 3 0 0 0 3 3h10a3 3 0 0 0 3-3v-2.5"/><path {S} d="M12 14.5V4M7.8 8.2L12 4l4.2 4.2"/>',
    'Delete.svg': f'<path {S} d="M4 7h16M9.5 7V5.5A1.5 1.5 0 0 1 11 4h2a1.5 1.5 0 0 1 1.5 1.5V7"/>'
                  f'<path {S} d="M6 7l1 11.2A2 2 0 0 0 9 20h6a2 2 0 0 0 2-1.8L18 7M10 11v5M14 11v5"/>',
    'Hide.svg': f'<path {S} d="M2.5 12C4.5 8.2 8 6 12 6s7.5 2.2 9.5 6c-2 3.8-5.5 6-9.5 6s-7.5-2.2-9.5-6z"/>'
                f'<circle {S} cx="12" cy="12" r="2.8"/><path {S} d="M4 4l16 16"/>',
    # A microphone, as most players use for lyrics.
    'Lyrics.svg': f'<rect {S} x="9" y="3" width="6" height="10.5" rx="3"/>'
                  f'<path {S} d="M5.8 11a6.2 6.2 0 0 0 12.4 0M12 17.2V20M9 20h6"/>',
    'MusicNote.svg': f'<path {S} d="M14 17.5V4.5l5 1.8"/><ellipse {F} cx="10.6" cy="17.6" rx="3.4" ry="3" transform="rotate(-20 10.6 17.6)"/>',
}


def svg(size, body):
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {size} {size}">\n  {body}\n</svg>\n'


# Launcher (from Gemini's design/icon-app-svg.svg): a lilac vinyl with two light groove arcs,
# a dark label and a white note. Dash arrays are not supported by Android vectors, so the
# grooves are explicit arcs. Everything sits inside the 66px safe circle (radius 33).
import math

LILAC, LILAC_SOFT, DARK, WHITE = '#7C5CFF', '#B9A6FF', '#14101F', '#FFFFFF'
C = 54


def _arc(r, a0, a1):
    p = lambda a: (C + r * math.cos(math.radians(a)), C + r * math.sin(math.radians(a)))
    (x0, y0), (x1, y1) = p(a0), p(a1)
    large = 1 if (a1 - a0) % 360 > 180 else 0
    return f'M{x0:.2f} {y0:.2f}A{r} {r} 0 {large} 1 {x1:.2f} {y1:.2f}'


def _note():
    """Gemini's note, scaled into the label and centred on (54, 54)."""
    return ('<circle fill="{f}" cx="51.01" cy="58.04" r="2.94"/>'
            '<path fill="{f}" d="M52.48 58.04V48.3a1.3 1.3 0 0 1 1.3-1.3H58.63a1.3 1.3 0 0 1 1.3 1.3v1.72'
            'a1.3 1.3 0 0 1-1.3 1.3H53.95V58.04Z"/>')

DISC_R, LABEL_R = 29, 11.5
GROOVES = (_arc(21, 140, 285), _arc(21, 320, 70))
LAUNCHER_FILES = {
    'launcher_foreground.svg': svg(108, f'<circle fill="{LILAC}" cx="{C}" cy="{C}" r="{DISC_R}"/>'
                                        + ''.join(f'<path fill="none" stroke="{LILAC_SOFT}" stroke-width="4" stroke-linecap="round" d="{d}"/>' for d in GROOVES)
                                        + f'<circle fill="{DARK}" cx="{C}" cy="{C}" r="{LABEL_R}"/>' + _note().replace('{f}', WHITE)),
    'launcher_background.svg': svg(108, '<defs><radialGradient id="g" cx="54" cy="54" r="64" gradientUnits="userSpaceOnUse">'
                                        f'<stop offset="0" stop-color="#2E2266"/><stop offset="1" stop-color="{DARK}"/></radialGradient></defs>'
                                        '<rect width="108" height="108" fill="url(#g)"/>'),
    'launcher_monochrome.svg': svg(108, f'<path fill="#000" fill-rule="evenodd" d="M{C - DISC_R} {C}a{DISC_R} {DISC_R} 0 1 0 {2 * DISC_R} 0a{DISC_R} {DISC_R} 0 1 0 {-2 * DISC_R} 0z'
                                        f'M{C - LABEL_R} {C}a{LABEL_R} {LABEL_R} 0 1 0 {2 * LABEL_R} 0a{LABEL_R} {LABEL_R} 0 1 0 {-2 * LABEL_R} 0z"/>'
                                        + _note().replace('{f}', '#000')),
    'notification.svg': svg(24, '<path fill="#FFFFFF" fill-rule="evenodd" d="M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0zM8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0z"/>'
                               '<circle fill="#FFFFFF" cx="12" cy="12" r="1.6"/>'),
}

def main():
    os.makedirs(os.path.join(ICONS, 'scartate'), exist_ok=True)
    os.makedirs(LAUNCHER, exist_ok=True)
    for old, new in {**GEMINI}.items():
        if os.path.exists(os.path.join(ICONS, old)):
            shutil.move(os.path.join(ICONS, old), os.path.join(ICONS, new))
    for old, new in REJECTED.items():
        if os.path.exists(os.path.join(ICONS, old)):
            shutil.move(os.path.join(ICONS, old), os.path.join(ICONS, 'scartate', new))
    for name, body in DRAWN.items():
        open(os.path.join(ICONS, name), 'w', encoding='utf-8').write(svg(24, body))
    for name, code in LAUNCHER_FILES.items():
        open(os.path.join(LAUNCHER, name), 'w', encoding='utf-8').write(code)
    names = sorted(f for f in os.listdir(ICONS) if f.endswith('.svg'))
    print(len(names), 'icons:', ', '.join(n[:-4] for n in names))


if __name__ == '__main__':
    main()
