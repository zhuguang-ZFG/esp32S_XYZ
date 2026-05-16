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
        """Trace a path from start, stopping at junctions."""
        path = [start]
        visited.add(start)
        current = start
        while True:
            nbs = [n for n in nb8(*current) if n not in visited]
            if not nbs:
                break
            # At a junction, stop this segment (pen up)
            nxt = min(nbs, key=lambda p: (p[0]-current[0])**2 + (p[1]-current[1])**2)
            if nxt in junctions and len(path) > 1:
                # Include the junction point but stop here
                path.append(nxt)
                visited.add(nxt)
                break
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

    # Merge paths whose endpoints are close (fix broken lines at junctions)
    merge_dist = 3  # pixels - keep small to avoid merging separate features
    merged = True
    while merged:
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
                else:
                    j += 1
            i += 1

    paths.sort(key=len, reverse=True)

    # Build SVG: simplify each path with RDP, scale to viewbox
    scale_x = viewbox / new_w
    scale_y = viewbox / new_h
    svg_parts = []
    total_pts = 0

    for path in paths:
        if total_pts >= max_points:
            break
        # Convert (row, col) to (x, y) = (col, row)
        xy_path = [(c, r) for r, c in path]
        simplified = rdp(xy_path, 0.5)
        if len(simplified) < 2:
            continue
        budget = max_points - total_pts
        if len(simplified) > budget:
            simplified = simplified[:budget]

        for i, (x, y) in enumerate(simplified):
            sx = round(x * scale_x, 1)
            sy = round(y * scale_y, 1)
            cmd = 'M' if i == 0 else 'L'
            svg_parts.append(f'{cmd}{sx} {sy}')
        total_pts += len(simplified)

    path_d = ' '.join(svg_parts)
    svg = (f'<svg viewBox="0 0 {viewbox} {viewbox}">'
           f'<path d="{path_d}" fill="none" stroke="black" stroke-width="0.8"/></svg>')
    vec_time = time.time() - t0
    return svg, total_pts, vec_time


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
