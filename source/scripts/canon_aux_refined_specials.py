"""Original faceted crystal and folded-paper crane geometry for ACA, 2026-10-29.

These are hand-authored low-poly surfaces, not imported meshes or texture cards.
Coordinates use the vanilla item's 0..16 design space.  The caller owns export,
material textures and display transforms.  Public API: crystal(mesh), crane(mesh),
where mesh provides poly(points, material, uv=None, both=False).

The crystal is a closed eight-sided cut stone with a narrow optical girdle, a slim
inner cut core, and two small detached refraction splinters.  The crane is built from a closed body, folded
wing sheets with real dihedral angles and thin edges, a triangular folded neck,
beak and raised tapered tail.  All artwork and coordinates are repository-original.
"""
import math


def _sub(a, b):
    return tuple(x - y for x, y in zip(a, b))


def _cross(a, b):
    return (a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0])


def _normal(points):
    return _cross(_sub(points[1], points[0]), _sub(points[2], points[0]))


def _outward(mesh, points, material, center):
    """Orient each convex crystal/body facet away from its interior reference."""
    points = list(points)
    normal = _normal(points)
    mid = tuple(sum(v[i] for v in points) / len(points) for i in range(3))
    if sum(normal[i] * (mid[i] - center[i]) for i in range(3)) < 0:
        points.reverse()
    mesh.poly(points, material)


def _ring(y, radius, depth, phase=math.pi / 8):
    return [(8 + radius * math.cos(phase + i * math.pi / 4), y,
             8 + depth * math.sin(phase + i * math.pi / 4)) for i in range(8)]


def _sliver(mesh, center, radius, half_height, color):
    """Eight real triangular facets; a tiny optical splinter, not a particle."""
    x, y, z = center
    ring = [(x + radius, y, z), (x, y, z + radius * .7),
            (x - radius, y, z), (x, y, z - radius * .7)]
    top, bottom = (x + .09, y + half_height, z), (x - .06, y - half_height, z)
    for i in range(4):
        following = (i + 1) % 4
        _outward(mesh, [top, ring[i], ring[following]],
                 color if i % 2 else 'pearl', center)
        _outward(mesh, [bottom, ring[following], ring[i]],
                 'white' if i % 2 else color, center)


def crystal(mesh):
    """80 faces: cut crown/girdle/pavilion, inner core and two optical splinters.

    The enclosed core is physically present for a translucent pearl material; an
    opaque pearl material naturally hides it rather than exposing intersecting bars.
    """
    apex, tip = (8.06, 15.1, 8.02), (7.94, 1.25, 7.98)
    crown = _ring(12.35, 2.15, 1.77)
    upper = _ring(8.95, 3.04, 2.44)
    lower = _ring(8.77, 3.04, 2.44)
    pavilion = _ring(4.65, 1.35, 1.08)
    inside = (8, 8.6, 8)
    shades = ('pearl', 'white', 'pearl', 'lilac',
              'pearl', 'lilac', 'white', 'pearl')
    for i in range(8):
        j = (i + 1) % 8
        _outward(mesh, [apex, crown[i], crown[j]], shades[i], inside)
        # Split the crown and pavilion along actual diagonal cut planes.  Adjacent
        # rings taper differently, so these are planar triangles with crisp normals.
        _outward(mesh, [crown[i], upper[i], crown[j]], shades[i], inside)
        _outward(mesh, [crown[j], upper[i], upper[j]],
                 'white' if i in (0, 6) else shades[(i + 1) % 8], inside)
        _outward(mesh, [upper[i], lower[i], lower[j], upper[j]],
                 'cyan' if i in (1, 5) else 'white', inside)
        _outward(mesh, [lower[i], pavilion[i], lower[j]], shades[(i + 2) % 8], inside)
        _outward(mesh, [lower[j], pavilion[i], pavilion[j]], shades[i], inside)
        _outward(mesh, [pavilion[i], tip, pavilion[j]], shades[(i + 4) % 8], inside)
    _sliver(mesh, (4.37, 10.45, 7.9), .22, .92, 'cyan')
    _sliver(mesh, (11.62, 6.55, 8.25), .18, .72, 'lilac')
    _sliver(mesh, (8, 8.3, 8), .35, 5.3, 'white')


def _orient_up(points):
    points = list(points)
    if _normal(points)[1] < 0:
        points.reverse()
    return points


def _sheet(mesh, boundary, triangles, colors, thickness=.055):
    """Closed folded sheet: individual triangles, underside and thin exposed edge.

    Triangle vertices need not share a plane: the ridge is real geometry.  A small
    common downward offset is used only for paper thickness, not a fake outline.
    """
    for tri, color in zip(triangles, colors):
        top = _orient_up(tri)
        mesh.poly(top, color)
        mesh.poly([(x, y - thickness, z) for x, y, z in reversed(top)], 'paper_shadow')
    edge_center = tuple(sum(p[i] for p in boundary) / len(boundary) for i in range(3))
    for a, b in zip(boundary, boundary[1:] + boundary[:1]):
        quad = [a, (a[0], a[1] - thickness, a[2]),
                (b[0], b[1] - thickness, b[2]), b]
        # The edge normal lies in XZ; use the sheet centre to keep it outward.
        _outward(mesh, quad, 'paper_shadow', edge_center)


def _crease(mesh, a, b, width=.018):
    """A subtle narrow crease laid just above its existing triangular surface."""
    dx, dz = b[0] - a[0], b[2] - a[2]
    length = math.hypot(dx, dz)
    if length < 1e-6:
        return
    ox, oz = -dz / length * width, dx / length * width
    mesh.poly(_orient_up([(a[0] + ox, a[1] + .008, a[2] + oz),
                          (b[0] + ox, b[1] + .008, b[2] + oz),
                          (b[0] - ox, b[1] + .008, b[2] - oz),
                          (a[0] - ox, a[1] + .008, a[2] - oz)]), 'paper_shadow')


def _neck(mesh):
    """A bent, closed triangular paper fold, with no box joints or round tube."""
    sections = [((8, 6.2, 6.6), .35, .24),
                ((8, 8.6, 4.75), .29, .22),
                ((8, 11.35, 3.95), .25, .18),
                ((8, 11.15, 2.68), .24, .15)]
    rings = []
    for (x, y, z), width, fold in sections:
        rings.append([(x - width, y, z), (x + width, y, z),
                      (x, y + .055, z + fold)])
    for k in range(len(rings) - 1):
        a, b = rings[k], rings[k + 1]
        inside = tuple(sum(v[i] for v in a + b) / 6 for i in range(3))
        for edge in range(3):
            nxt = (edge + 1) % 3
            color = ('white', 'paper', 'paper_shadow')[edge]
            _outward(mesh, [a[edge], b[edge], b[nxt]], color, inside)
            _outward(mesh, [a[edge], b[nxt], a[nxt]], color, inside)
    start_inside = tuple(sum(v[i] for v in rings[0] + rings[1]) / 6 for i in range(3))
    _outward(mesh, list(reversed(rings[0])), 'paper_shadow', start_inside)
    # The last ring is closed by the sharply folded beak, rather than another box.
    beak_tip = (8, 10.72, 1.15)
    inside = tuple((sum(v[i] for v in rings[-1]) + beak_tip[i]) / 4 for i in range(3))
    for edge in range(3):
        nxt = (edge + 1) % 3
        points = [rings[-1][edge], beak_tip, rings[-1][nxt]]
        _outward(mesh, points, 'paper' if edge < 2 else 'paper_shadow', inside)
    # One tiny red printed point on the paper tip is a material mark, not a beak mesh.
    mesh.poly([(8, 10.728, 1.15), (8.04, 10.813, 1.45),
               (7.96, 10.813, 1.45)], 'red', both=True)


def crane(mesh):
    """A lightweight, closed paper crane with wide angular wings and raised tail.

    About seventy faces, including thin sheet edges and creases; the two wings
    have slightly different lifts to expose their fold geometry in item views.
    """
    forward = (8, 6.35, 5.95)
    top = (8, 7.35, 8.1)
    back = (8, 6.5, 10.55)
    left = (6.68, 6.08, 8.25)
    right = (9.32, 6.08, 8.25)
    keel = (8, 4.8, 8.35)
    inside = (8, 6.1, 8.15)
    for points, color in [([forward, top, left], 'white'),
                          ([forward, right, top], 'paper'),
                          ([top, back, left], 'paper'),
                          ([top, right, back], 'white'),
                          ([forward, left, keel], 'paper_shadow'),
                          ([forward, keel, right], 'paper'),
                          ([left, back, keel], 'paper'),
                          ([right, keel, back], 'paper_shadow')]:
        _outward(mesh, points, color, inside)

    for side in (-1, 1):
        # The broad panels meet at an elevated diagonal ridge.  Root points remain
        # on the body, so neither wing is a disconnected flat billboard.
        def p(x, y, z):
            return (8 + side * (x - 8), y + (.32 if side > 0 and x < 6 else 0), z)
        # Barycentric points on the existing front/back body facets form the root;
        # their shared base is buried slightly in the body, not floating above it.
        a = p(7.472, 6.442, 7.30)
        b = p(.8, 11.3, 8.85)
        c = p(2.65, 8.75, 11.5)
        d = p(7.34, 6.4175, 9.0325)
        fold = p(4.65, 8.62, 8.55)
        _sheet(mesh, [a, b, c, d], [[a, b, fold], [b, c, fold],
                                  [c, d, fold], [d, a, fold]],
               ['white', 'paper', 'paper_shadow', 'paper'])
        _crease(mesh, a, fold)
        _crease(mesh, fold, b)
        # A restrained printed tip marks the underlying single sheet without
        # painting the entire wing red or introducing a separate fantasy ornament.
        aa = tuple(b[i] + (fold[i] - b[i]) * .12 for i in range(3))
        cc = tuple(b[i] + (c[i] - b[i]) * .12 for i in range(3))
        mesh.poly(_orient_up([(b[0], b[1] + .009, b[2]),
                              (aa[0], aa[1] + .009, aa[2]),
                              (cc[0], cc[1] + .009, cc[2])]), 'red')

    ta, tip, tb, ridge = ((7.75, 6.42, 9.85), (8, 12.12, 14.72),
                          (8.25, 6.42, 9.85), (8, 8.48, 10.82))
    _sheet(mesh, [ta, tip, tb], [[ta, tip, ridge], [tip, tb, ridge], [tb, ta, ridge]],
           ['white', 'paper', 'paper_shadow'], thickness=.045)
    _crease(mesh, ridge, tip, .014)
    _neck(mesh)
