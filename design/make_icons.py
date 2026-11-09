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
    'MusicNote.svg': f'<path {S} d="M14 17.5V4.5l5 1.8"/><ellipse {F} cx="10.6" cy="17.6" rx="3.4" ry="3" transform="rotate(-20 10.6 17.6)"/>',
}


def svg(size, body):
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {size} {size}">\n  {body}\n</svg>\n'


# Launcher: a vinyl disc that is the bowl of a lowercase "b" (stem on the left), inside the 66px safe circle.
CREAM, INK = '#FFF7ED', '#231F1A'
B_STEM = 'M36 33.5a3.5 3.5 0 0 1 7 0V61h-7z'
LAUNCHER_FILES = {
    'launcher_foreground.svg': svg(108, f'<path fill="{CREAM}" d="{B_STEM}"/>'
                                        f'<circle fill="{CREAM}" cx="57" cy="61" r="19"/>'
                                        f'<circle fill="none" stroke="{INK}" stroke-opacity=".25" stroke-width="1.2" cx="57" cy="61" r="14"/>'
                                        f'<circle fill="#7C5CFF" cx="57" cy="61" r="6.3"/><circle fill="{CREAM}" cx="57" cy="61" r="1.8"/>'),
    'launcher_background.svg': svg(108, '<defs><radialGradient id="g" cx="54" cy="30" r="90" gradientUnits="userSpaceOnUse">'
                                        '<stop offset="0" stop-color="#7C5CFF"/><stop offset="1" stop-color="#7C2D12"/></radialGradient></defs>'
                                        '<rect width="108" height="108" fill="url(#g)"/>'),
    'launcher_monochrome.svg': svg(108, f'<path fill="#000" d="{B_STEM}"/>'
                                        '<path fill="#000" fill-rule="evenodd" d="M76 61a19 19 0 1 1-38 0 19 19 0 0 1 38 0zM63.3 61a6.3 6.3 0 1 0-12.6 0 6.3 6.3 0 0 0 12.6 0z"/>'
                                        '<circle fill="#000" cx="57" cy="61" r="1.8"/>'),
    'notification.svg': svg(24, '<path fill="#FFFFFF" d="M5 5.2a1.7 1.7 0 0 1 3.4 0V14H5z"/>'
                               '<path fill="#FFFFFF" fill-rule="evenodd" d="M21 14.2a6.8 6.8 0 1 1-13.6 0 6.8 6.8 0 0 1 13.6 0zM16.4 14.2a2.2 2.2 0 1 0-4.4 0 2.2 2.2 0 0 0 4.4 0z"/>'),
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
