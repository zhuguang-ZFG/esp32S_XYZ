import httpx, json, time, base64
from PIL import Image
from io import BytesIO
import numpy as np
from skimage.morphology import skeletonize

API_KEY = 'sk-sp-djI.pO4-by3NZh1AD6lxgsUZqcFjfGrg2wwbZOwfD9vDR0mMnSAlHi_b_pq9aM-CWV69RZCWwk0SFbmUAl6As84Guvmb6kwep7tqP7rXSt-w0QGaooimi0T6QWSkVtP_xTtR5kaUA8BliYNLkZuvPxO3GgAmHrEvRcFp7SItvt1wZ0sCTFWjhWg9SCMCe86MU_9e.MEYCIQDB2_A46Boq4Pl32PdQR2jOBEbRFL5suPHllq1HrToiDQIhAOq_SBm7Bn32LNkeMOMgFELobO8nYJE6vMt1z3jasZJT'
URL = 'https://token-plan.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions'


def gen(prompt):
    client = httpx.Client(timeout=60.0)
    body = {'model': 'qwen-image-2.0', 'messages': [{'role': 'user', 'content': [
        {'type': 'text', 'text': f'{prompt}，简笔画，黑色线条，纯白背景，无阴影无填充无文字'}
    ]}]}
    t0 = time.time()
    resp = client.post(URL, json=body, headers={
        'Authorization': f'Bearer {API_KEY}', 'Content-Type': 'application/json'
    })
    gen_time = time.time() - t0
    data = resp.json()
    img_url = data['output']['choices'][0]['message']['content'][0]['image']
    t0 = time.time()
    img_data = client.get(img_url).content
    dl_time = time.time() - t0
    return img_data, gen_time, dl_time


def rdp(pts, eps):
    """Ramer-Douglas-Peucker simplification."""
    if len(pts) <= 2:
        return pts
    start = np.array(pts[0], dtype=float)
    end = np.array(pts[-1], dtype=float)
    line = end - start
    ll = np.linalg.norm(line)
    if ll < 1e-10:
        dists = [np.linalg.norm(np.array(p, dtype=float) - start) for p in pts]
        idx = int(np.argmax(dists))
        if dists[idx] > eps:
            return rdp(pts[:idx+1], eps)[:-1] + rdp(pts[idx:], eps)
        return [pts[0], pts[-1]]
    lu = line / ll
    max_d, max_i = 0, 0
    for i in range(1, len(pts) - 1):
        v = np.array(pts[i], dtype=float) - start
        proj = np.clip(np.dot(v, lu), 0, ll)
        d = np.linalg.norm(np.array(pts[i], dtype=float) - (start + lu * proj))
        if d > max_d:
            max_d, max_i = d, i
    if max_d > eps:
        return rdp(pts[:max_i+1], eps)[:-1] + rdp(pts[max_i:], eps)
    return [pts[0], pts[-1]]


def trace_skeleton(img_data, viewbox=100, max_points=800):
    """Skeletonize -> trace centerline paths -> RDP simplify."""
    t0 = time.time()
    img = Image.open(BytesIO(img_data)).convert('L')
    arr = np.array(img)

    # Auto-crop white borders
    dark_mask = arr < 200
    rows = np.any(dark_mask, axis=1)
    cols = np.any(dark_mask, axis=0)
    if not rows.any():
        return '', 0, 0
    rmin, rmax = np.where(rows)[0][[0, -1]]
    cmin, cmax = np.where(cols)[0][[0, -1]]
    pad = max(5, (rmax - rmin) // 20)
    rmin = max(0, rmin - pad)
    rmax = min(arr.shape[0] - 1, rmax + pad)
    cmin = max(0, cmin - pad)
    cmax = min(arr.shape[1] - 1, cmax + pad)
    cropped = arr[rmin:rmax, cmin:cmax]

    # Resize to 512px longest side (higher res = better detail preservation)
    h, w = cropped.shape
    factor = 512 / max(h, w)
    new_h, new_w = int(h * factor), int(w * factor)
    img_r = Image.fromarray(cropped).resize((new_w, new_h), Image.LANCZOS)
    arr_r = np.array(img_r)

    # Skeletonize: reduce strokes to 1px center lines
    binary = arr_r < 128
    skeleton = skeletonize(binary)

    # Extract skeleton pixel coordinates
    skel_set = set(zip(*[c.tolist() for c in np.where(skeleton)]))
    # skel_set contains (row, col) tuples
    if not skel_set:
        return '', 0, 0

    def nb8(r, c):
        """8-connected neighbors in skeleton."""
        result = []
        for dr in [-1, 0, 1]:
            for dc in [-1, 0, 1]:
                if dr == 0 and dc == 0:
                    continue
                if (r + dr, c + dc) in skel_set:
                    result.append((r + dr, c + dc))
        return result

    # Classify pixels by connectivity
    junctions = set((r, c) for r, c in skel_set if len(nb8(r, c)) >= 3)
    endpoints = [(r, c) for r, c in skel_set if len(nb8(r, c)) == 1]
    visited = set()
    paths = []

    def trace_from(start):
        """Trace a path from start, passing through junctions by following direction."""
        path = [start]
        visited.add(start)
        current = start
        while True:
            nbs = [n for n in nb8(*current) if n not in visited]
            if not nbs:
                break
            nxt = None
            if current in junctions and len(path) >= 2:
                # At junction: continue in same direction (minimize angle change)
                prev = path[-2]
                dx, dy = current[0] - prev[0], current[1] - prev[1]
                best_dot = -999
                for nb in nbs:
                    ndx, ndy = nb[0] - current[0], nb[1] - current[1]
                    dot = dx * ndx + dy * ndy  # direction alignment
                    if dot > best_dot:
                        best_dot = dot
                        nxt = nb
            else:
                nxt = min(nbs, key=lambda p: (p[0]-current[0])**2 + (p[1]-current[1])**2)

            path.append(nxt)
            visited.add(nxt)
            current = nxt
        return path

    # Trace from endpoints first (clean starts)
    starts = endpoints if endpoints else sorted(skel_set)[:20]
    for start in starts:
        if start in visited:
            continue
        path = trace_from(start)
        if len(path) >= 2:
            paths.append(path)

    # Trace from junctions (pick up remaining segments)
    for jpt in sorted(junctions):
        if jpt in visited:
            continue
        path = trace_from(jpt)
        if len(path) >= 2:
            paths.append(path)

    # Trace any remaining unvisited pixels
    remaining = skel_set - visited
    while remaining:
        start = min(remaining)
        path = [start]
        visited.add(start)
        remaining.discard(start)
        current = start
        while True:
            nbs = [n for n in nb8(*current) if n not in visited]
            if not nbs:
                break
            nxt = min(nbs, key=lambda p: (p[0]-current[0])**2 + (p[1]-current[1])**2)
            path.append(nxt)
            visited.add(nxt)
            remaining.discard(nxt)
            current = nxt
        if len(path) >= 2:
            paths.append(path)

    paths.sort(key=len, reverse=True)

    # Merge paths whose endpoints are close (fix broken lines)
    merge_dist = 8
    merged = True
    max_merges = 200
    merge_count = 0
    while merged and merge_count < max_merges:
        merged = False
        i = 0
        while i < len(paths):
            j = i + 1
            while j < len(paths):
                pi_start, pi_end = paths[i][0], paths[i][-1]
                pj_start, pj_end = paths[j][0], paths[j][-1]

                def dist(a, b):
                    return ((a[0]-b[0])**2 + (a[1]-b[1])**2) ** 0.5

                # Try all 4 endpoint combinations
                d1 = dist(pi_end, pj_start)    # i_end -> j_start
                d2 = dist(pi_end, pj_end)      # i_end -> j_end (reverse j)
                d3 = dist(pi_start, pj_start)  # i_start -> j_start (reverse i)
                d4 = dist(pi_start, pj_end)    # j_end -> i_start

                min_d = min(d1, d2, d3, d4)
                if min_d <= merge_dist:
                    if min_d == d1:
                        paths[i] = paths[i] + paths[j]
                    elif min_d == d2:
                        paths[i] = paths[i] + paths[j][::-1]
                    elif min_d == d3:
                        paths[i] = paths[i][::-1] + paths[j]
                    else:
                        paths[i] = paths[j] + paths[i]
                    paths.pop(j)
                    merged = True
                    merge_count += 1
                else:
                    j += 1
            i += 1

    paths.sort(key=len, reverse=True)

    # Build SVG: fit Bezier curves for smoothness, then linearize for device
    scale_x = viewbox / new_w
    scale_y = viewbox / new_h
    svg_parts = []
    total_pts = 0

    for path in paths:
        if total_pts >= max_points:
            break
        # Convert (row, col) to (x, y) = (col, row), scaled
        xy_path = [(c * scale_x, r * scale_y) for r, c in path]

        # Fit cubic Bezier curves through points, then subdivide
        smoothed = fit_and_subdivide(xy_path, max_error=0.3)
        if len(smoothed) < 2:
            continue
        budget = max_points - total_pts
        if len(smoothed) > budget:
            smoothed = smoothed[:budget]

        for i, (x, y) in enumerate(smoothed):
            sx = round(x, 1)
            sy = round(y, 1)
            cmd = 'M' if i == 0 else 'L'
            svg_parts.append(f'{cmd}{sx} {sy}')
        total_pts += len(smoothed)

    path_d = ' '.join(svg_parts)
    svg = (f'<svg viewBox="0 0 {viewbox} {viewbox}">'
           f'<path d="{path_d}" fill="none" stroke="black" stroke-width="0.8"/></svg>')
    vec_time = time.time() - t0
    return svg, total_pts, vec_time


def fit_and_subdivide(points, max_error=0.3):
    """Fit cubic Bezier curves to point sequence, then adaptively subdivide."""
    if len(points) <= 2:
        return points

    # First: RDP to get key points (control polygon)
    key_pts = rdp(points, 1.0)
    if len(key_pts) <= 2:
        return key_pts

    # Fit Bezier segments between consecutive key points
    result = [key_pts[0]]
    for i in range(len(key_pts) - 1):
        p0 = np.array(key_pts[i])
        p3 = np.array(key_pts[i + 1])

        # Find original points between these two key points
        seg_pts = get_segment_points(points, key_pts[i], key_pts[i + 1])
        if len(seg_pts) < 3:
            result.append(key_pts[i + 1])
            continue

        # Estimate control points for cubic Bezier
        p1, p2 = estimate_bezier_controls(seg_pts)

        # Adaptive subdivision of the Bezier curve
        subdivided = subdivide_bezier(p0, p1, p2, p3, max_error)
        result.extend(subdivided[1:])  # skip first (already in result)

    return result


def get_segment_points(all_pts, start, end):
    """Get points from all_pts between start and end."""
    start_idx = None
    end_idx = None
    for i, p in enumerate(all_pts):
        if start_idx is None and abs(p[0]-start[0]) < 0.01 and abs(p[1]-start[1]) < 0.01:
            start_idx = i
        if abs(p[0]-end[0]) < 0.01 and abs(p[1]-end[1]) < 0.01:
            end_idx = i
    if start_idx is None:
        start_idx = 0
    if end_idx is None:
        end_idx = len(all_pts) - 1
    if start_idx > end_idx:
        start_idx, end_idx = end_idx, start_idx
    return all_pts[start_idx:end_idx + 1]


def estimate_bezier_controls(pts):
    """Estimate cubic Bezier control points P1, P2 from a point sequence."""
    pts = [np.array(p) for p in pts]
    n = len(pts)
    p0, p3 = pts[0], pts[-1]
    # Use 1/3 and 2/3 points as initial estimates, adjusted by tangent
    t1_idx = max(1, n // 3)
    t2_idx = min(n - 2, 2 * n // 3)
    # Tangent at start
    tang_start = pts[min(2, n-1)] - pts[0]
    tang_end = pts[-1] - pts[max(0, n-3)]
    chord = np.linalg.norm(p3 - p0)
    if chord < 1e-6:
        return p0 + (pts[t1_idx] - p0) * 0.5, p3 + (pts[t2_idx] - p3) * 0.5
    # P1 = P0 + tangent_start * chord/3
    t_len = chord / 3.0
    t_norm = np.linalg.norm(tang_start)
    if t_norm > 1e-6:
        p1 = p0 + tang_start / t_norm * t_len
    else:
        p1 = p0 + (pts[t1_idx] - p0) * 0.66
    t_norm = np.linalg.norm(tang_end)
    if t_norm > 1e-6:
        p2 = p3 - tang_end / t_norm * t_len
    else:
        p2 = p3 + (pts[t2_idx] - p3) * 0.66
    return p1, p2


def subdivide_bezier(p0, p1, p2, p3, max_error, depth=0):
    """Adaptively subdivide cubic Bezier into line segments."""
    if depth > 8:
        return [tuple(p0), tuple(p3)]
    # Check if curve is flat enough (distance of control points from chord)
    chord = p3 - p0
    chord_len = np.linalg.norm(chord)
    if chord_len < 1e-6:
        return [tuple(p0), tuple(p3)]
    # Max distance of P1, P2 from line P0-P3
    d1 = np.abs(np.cross(chord, p1 - p0)) / chord_len
    d2 = np.abs(np.cross(chord, p2 - p0)) / chord_len
    if max(d1, d2) <= max_error:
        return [tuple(p0), tuple(p3)]
    # De Casteljau split at t=0.5
    m01 = (p0 + p1) / 2
    m12 = (p1 + p2) / 2
    m23 = (p2 + p3) / 2
    m012 = (m01 + m12) / 2
    m123 = (m12 + m23) / 2
    mid = (m012 + m123) / 2
    left = subdivide_bezier(p0, m01, m012, mid, max_error, depth + 1)
    right = subdivide_bezier(mid, m123, m23, p3, max_error, depth + 1)
    return left + right[1:]


if __name__ == '__main__':
    prompts = ['猫', '星星', '房子', '花', '鱼']
    html_cards = []

    for p in prompts:
        print(f'{p}...', end=' ', flush=True)
        img_data, gen_time, dl_time = gen(p)
        svg, pts, vec_time = trace_skeleton(img_data)
        total = gen_time + dl_time + vec_time
        print(f'{total:.1f}s | {pts} pts | vec={vec_time:.2f}s')

        img = Image.open(BytesIO(img_data))
        img.thumbnail((250, 250))
        buf = BytesIO()
        img.save(buf, format='PNG')
        b64 = base64.b64encode(buf.getvalue()).decode()
        html_cards.append((p, b64, svg, f'{total:.1f}s', pts))

    html = ('<!DOCTYPE html><html><head><meta charset="utf-8">'
            '<title>Skeleton Vectorization</title>\n'
            '<style>body{font-family:sans-serif;padding:20px;background:#f5f5f5}'
            '.row{display:flex;gap:24px;margin:24px 0}'
            '.card{background:white;padding:16px;border-radius:8px;text-align:center;'
            'box-shadow:0 2px 4px rgba(0,0,0,.1)}'
            'img,svg{width:240px;height:240px;border:1px solid #ddd;background:white}'
            'h3{margin:8px 0}p{margin:4px 0;font-size:12px;color:#666}'
            '</style></head><body>\n'
            '<h1>qwen-image-2.0 + Skeletonize Centerline</h1>\n'
            '<p>Left: AI original | Right: Skeleton centerline (pen plotter path)</p>\n')

    for p, b64, svg, timing, pts in html_cards:
        html += (f'<div class="row"><div class="card"><h3>{p} (AI)</h3>'
                 f'<img src="data:image/png;base64,{b64}"/></div>\n'
                 f'<div class="card"><h3>{p} (skeleton {pts}pts)</h3>'
                 f'{svg}<p>{timing}</p></div></div>\n')

    html += '</body></html>'
    with open('docs/ai-svg-quality-test.html', 'w', encoding='utf-8') as f:
        f.write(html)
    print('\nDone! Open docs/ai-svg-quality-test.html')
