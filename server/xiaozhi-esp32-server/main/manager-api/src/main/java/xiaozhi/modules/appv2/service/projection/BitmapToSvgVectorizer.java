package xiaozhi.modules.appv2.service.projection;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

/**
 * Vectorizes a bitmap line drawing into an SVG path using centerline extraction.
 * Algorithm: threshold → Zhang-Suen skeletonize → trace paths → RDP simplify.
 */
public class BitmapToSvgVectorizer {

    private static final int WORK_SIZE = 512;
    private static final double RDP_EPSILON = 0.5;
    private static final int MAX_POINTS = 800;
    private static final int MERGE_DIST = 8;
    private static final int VIEWBOX = 100;

    public String vectorize(byte[] imageBytes) throws Exception {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (src == null) throw new IllegalArgumentException("cannot decode image");

        int[] gray = toGrayscale(src);
        int w = src.getWidth(), h = src.getHeight();

        // Auto-crop white borders
        int[] crop = autoCrop(gray, w, h);
        int cx = crop[0], cy = crop[1], cw = crop[2], ch = crop[3];
        if (cw <= 0 || ch <= 0) throw new IllegalArgumentException("empty image");

        // Resize to working size
        double factor = (double) WORK_SIZE / Math.max(cw, ch);
        int nw = Math.max(1, (int)(cw * factor));
        int nh = Math.max(1, (int)(ch * factor));
        boolean[] binary = resizeAndThreshold(gray, w, cx, cy, cw, ch, nw, nh);

        // Skeletonize (Zhang-Suen thinning)
        boolean[] skeleton = zhangSuenThin(binary, nw, nh);

        // Trace paths
        List<List<int[]>> paths = tracePaths(skeleton, nw, nh);

        // Merge nearby endpoints
        mergePaths(paths, MERGE_DIST);

        // Simplify and build SVG
        return buildSvg(paths, nw, nh);
    }

    private static int[] toGrayscale(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int[] gray = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xff, g = (rgb >> 8) & 0xff, b = rgb & 0xff;
                gray[y * w + x] = (r * 299 + g * 587 + b * 114) / 1000;
            }
        }
        return gray;
    }

    private static int[] autoCrop(int[] gray, int w, int h) {
        int xmin = w, xmax = 0, ymin = h, ymax = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if (gray[y * w + x] < 200) {
                    xmin = Math.min(xmin, x); xmax = Math.max(xmax, x);
                    ymin = Math.min(ymin, y); ymax = Math.max(ymax, y);
                }
        int pad = Math.max(5, (ymax - ymin) / 20);
        xmin = Math.max(0, xmin - pad); ymin = Math.max(0, ymin - pad);
        xmax = Math.min(w - 1, xmax + pad); ymax = Math.min(h - 1, ymax + pad);
        return new int[]{xmin, ymin, xmax - xmin + 1, ymax - ymin + 1};
    }

    private static boolean[] resizeAndThreshold(int[] gray, int srcW,
            int cx, int cy, int cw, int ch, int nw, int nh) {
        boolean[] r = new boolean[nw * nh];
        for (int y = 0; y < nh; y++)
            for (int x = 0; x < nw; x++) {
                int sx = Math.min(cx + (int)((double)x / nw * cw), cx + cw - 1);
                int sy = Math.min(cy + (int)((double)y / nh * ch), cy + ch - 1);
                r[y * nw + x] = gray[sy * srcW + sx] < 128;
            }
        return r;
    }

    // PLACEHOLDER_ZHANG_SUEN

    private static boolean[] zhangSuenThin(boolean[] img, int w, int h) {
        boolean[] result = img.clone();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int step = 0; step < 2; step++) {
                boolean[] toRemove = new boolean[w * h];
                for (int y = 1; y < h - 1; y++) {
                    for (int x = 1; x < w - 1; x++) {
                        if (!result[y * w + x]) continue;
                        int[] p = neighbors(result, w, x, y);
                        int b = countNonZero(p);
                        int a = transitions(p);
                        if (b < 2 || b > 6 || a != 1) continue;
                        if (step == 0) {
                            if (p[0] * p[2] * p[4] != 0) continue;
                            if (p[2] * p[4] * p[6] != 0) continue;
                        } else {
                            if (p[0] * p[2] * p[6] != 0) continue;
                            if (p[0] * p[4] * p[6] != 0) continue;
                        }
                        toRemove[y * w + x] = true;
                        changed = true;
                    }
                }
                for (int i = 0; i < w * h; i++)
                    if (toRemove[i]) result[i] = false;
            }
        }
        return result;
    }

    private static int[] neighbors(boolean[] img, int w, int x, int y) {
        return new int[]{
            img[(y-1)*w+x] ? 1 : 0,     // P2 (N)
            img[(y-1)*w+x+1] ? 1 : 0,   // P3 (NE)
            img[y*w+x+1] ? 1 : 0,       // P4 (E)
            img[(y+1)*w+x+1] ? 1 : 0,   // P5 (SE)
            img[(y+1)*w+x] ? 1 : 0,     // P6 (S)
            img[(y+1)*w+x-1] ? 1 : 0,   // P7 (SW)
            img[y*w+x-1] ? 1 : 0,       // P8 (W)
            img[(y-1)*w+x-1] ? 1 : 0    // P9 (NW)
        };
    }

    private static int countNonZero(int[] p) {
        int c = 0; for (int v : p) c += v; return c;
    }

    private static int transitions(int[] p) {
        int t = 0;
        for (int i = 0; i < 8; i++)
            if (p[i] == 0 && p[(i + 1) % 8] == 1) t++;
        return t;
    }

    // PLACEHOLDER_TRACE

    private static List<List<int[]>> tracePaths(boolean[] skel, int w, int h) {
        Set<Long> skelSet = new HashSet<>();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if (skel[y * w + x]) skelSet.add(key(x, y));

        Set<Long> junctions = new HashSet<>();
        Set<Long> endpts = new HashSet<>();
        for (long k : skelSet) {
            int nb = countNb8(skelSet, kx(k), ky(k));
            if (nb >= 3) junctions.add(k);
            else if (nb == 1) endpts.add(k);
        }

        Set<Long> visited = new HashSet<>();
        List<List<int[]>> paths = new ArrayList<>();

        // Trace from endpoints first
        for (long ep : endpts) traceOne(skelSet, junctions, visited, paths, kx(ep), ky(ep));
        // Then from junctions
        for (long jp : junctions) traceOne(skelSet, junctions, visited, paths, kx(jp), ky(jp));
        // Remaining
        for (long k : skelSet) {
            if (!visited.contains(k))
                traceOne(skelSet, junctions, visited, paths, kx(k), ky(k));
        }
        paths.sort(Comparator.comparingInt((List<int[]> p) -> p.size()).reversed());
        return paths;
    }

    private static void traceOne(Set<Long> skelSet, Set<Long> junctions,
            Set<Long> visited, List<List<int[]>> paths, int sx, int sy) {
        if (visited.contains(key(sx, sy))) return;
        List<int[]> path = new ArrayList<>();
        path.add(new int[]{sx, sy});
        visited.add(key(sx, sy));
        int cx = sx, cy = sy;
        while (true) {
            int[] nxt = directionAwareNb(skelSet, visited, junctions, path, cx, cy);
            if (nxt == null) break;
            long nk = key(nxt[0], nxt[1]);
            path.add(nxt); visited.add(nk);
            cx = nxt[0]; cy = nxt[1];
        }
        if (path.size() >= 2) paths.add(path);
    }

    private static int[] directionAwareNb(Set<Long> skel, Set<Long> visited,
            Set<Long> junctions, List<int[]> path, int x, int y) {
        List<int[]> candidates = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                long k = key(x+dx, y+dy);
                if (skel.contains(k) && !visited.contains(k))
                    candidates.add(new int[]{x+dx, y+dy});
            }
        if (candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.get(0);
        // At junction: follow direction of travel
        if (path.size() >= 2) {
            int[] prev = path.get(path.size() - 2);
            int dirX = x - prev[0], dirY = y - prev[1];
            int[] best = null; double bestDot = -999;
            for (int[] c : candidates) {
                double dot = (c[0]-x)*dirX + (c[1]-y)*dirY;
                if (dot > bestDot) { bestDot = dot; best = c; }
            }
            return best;
        }
        return candidates.get(0);
    }

    private static int[] bestNb8(Set<Long> skel, Set<Long> visited, int x, int y) {
        int[] best = null; double bd = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                long k = key(x+dx, y+dy);
                if (skel.contains(k) && !visited.contains(k)) {
                    double d = dx*dx + dy*dy;
                    if (d < bd) { bd = d; best = new int[]{x+dx, y+dy}; }
                }
            }
        return best;
    }

    private static int countNb8(Set<Long> s, int x, int y) {
        int c = 0;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                if ((dx|dy) != 0 && s.contains(key(x+dx, y+dy))) c++;
        return c;
    }

    private static long key(int x, int y) { return ((long)x << 32) | (y & 0xFFFFFFFFL); }
    private static int kx(long k) { return (int)(k >> 32); }
    private static int ky(long k) { return (int)k; }

    // PLACEHOLDER_MERGE

    private static void mergePaths(List<List<int[]>> paths, int dist) {
        boolean merged = true;
        int mergeCount = 0;
        while (merged && mergeCount < 200) {
            merged = false;
            for (int i = 0; i < paths.size() && !merged; i++) {
                for (int j = i + 1; j < paths.size() && !merged; j++) {
                    List<int[]> pi = paths.get(i), pj = paths.get(j);
                    int[] ie = pi.get(pi.size()-1), js = pj.get(0);
                    int[] is = pi.get(0), je = pj.get(pj.size()-1);
                    double d1 = pdist(ie, js), d2 = pdist(ie, je);
                    double d3 = pdist(is, js), d4 = pdist(is, je);
                    double min = Math.min(Math.min(d1,d2), Math.min(d3,d4));
                    if (min <= dist) {
                        List<int[]> combined = new ArrayList<>();
                        if (min == d1) { combined.addAll(pi); combined.addAll(pj); }
                        else if (min == d2) { combined.addAll(pi); List<int[]> rev = new ArrayList<>(pj); java.util.Collections.reverse(rev); combined.addAll(rev); }
                        else if (min == d3) { List<int[]> rev = new ArrayList<>(pi); java.util.Collections.reverse(rev); combined.addAll(rev); combined.addAll(pj); }
                        else { combined.addAll(pj); combined.addAll(pi); }
                        paths.set(i, combined); paths.remove(j);
                        merged = true;
                        mergeCount++;
                    }
                }
            }
        }
    }

    private static double pdist(int[] a, int[] b) {
        return Math.sqrt((a[0]-b[0])*(a[0]-b[0]) + (a[1]-b[1])*(a[1]-b[1]));
    }

    private String buildSvg(List<List<int[]>> paths, int imgW, int imgH) {
        double sx = (double) VIEWBOX / imgW, sy = (double) VIEWBOX / imgH;
        StringBuilder sb = new StringBuilder();
        int totalPts = 0;
        for (List<int[]> path : paths) {
            if (totalPts >= MAX_POINTS) break;
            List<double[]> pts = new ArrayList<>();
            for (int[] p : path) pts.add(new double[]{p[0] * sx, p[1] * sy});
            List<double[]> simplified = rdpSimplify(pts, RDP_EPSILON);
            if (simplified.size() < 2) continue;
            int budget = MAX_POINTS - totalPts;
            if (simplified.size() > budget) simplified = simplified.subList(0, budget);
            for (int i = 0; i < simplified.size(); i++) {
                double[] pt = simplified.get(i);
                sb.append(i == 0 ? 'M' : 'L');
                sb.append(round1(pt[0])).append(' ').append(round1(pt[1])).append(' ');
            }
            totalPts += simplified.size();
        }
        return "<svg viewBox=\"0 0 " + VIEWBOX + " " + VIEWBOX
                + "\"><path d=\"" + sb.toString().trim()
                + "\" fill=\"none\" stroke=\"black\" stroke-width=\"0.8\"/></svg>";
    }

    private static List<double[]> rdpSimplify(List<double[]> pts, double eps) {
        if (pts.size() <= 2) return pts;
        double[] s = pts.get(0), e = pts.get(pts.size()-1);
        double lx = e[0]-s[0], ly = e[1]-s[1];
        double ll = Math.sqrt(lx*lx + ly*ly);
        double maxD = 0; int maxI = 0;
        for (int i = 1; i < pts.size()-1; i++) {
            double d;
            if (ll < 1e-10) { d = Math.sqrt(Math.pow(pts.get(i)[0]-s[0],2)+Math.pow(pts.get(i)[1]-s[1],2)); }
            else {
                double vx = pts.get(i)[0]-s[0], vy = pts.get(i)[1]-s[1];
                double proj = Math.max(0, Math.min(ll, (vx*lx+vy*ly)/ll));
                double cx = s[0]+lx/ll*proj, cy = s[1]+ly/ll*proj;
                d = Math.sqrt(Math.pow(pts.get(i)[0]-cx,2)+Math.pow(pts.get(i)[1]-cy,2));
            }
            if (d > maxD) { maxD = d; maxI = i; }
        }
        if (maxD > eps) {
            List<double[]> left = rdpSimplify(pts.subList(0, maxI+1), eps);
            List<double[]> right = rdpSimplify(pts.subList(maxI, pts.size()), eps);
            List<double[]> result = new ArrayList<>(left.subList(0, left.size()-1));
            result.addAll(right);
            return result;
        }
        return List.of(pts.get(0), pts.get(pts.size()-1));
    }

    private static String round1(double v) {
        long r = Math.round(v * 10);
        return r % 10 == 0 ? String.valueOf(r / 10) : (r / 10) + "." + Math.abs(r % 10);
    }
}