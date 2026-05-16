package xiaozhi.modules.appv2.service.projection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Optimizes multi-segment paths to minimize pen-up travel distance.
 * Uses greedy nearest-neighbor TSP: picks the next segment whose start (or end,
 * if reversed) is closest to the current pen position.
 */
public final class PathOptimizer {

    private PathOptimizer() {}

    public static List<PathPoint> optimize(List<PathPoint> flatPath) {
        List<List<PathPoint>> segments = splitSegments(flatPath);
        if (segments.size() <= 1) {
            return flatPath;
        }

        List<List<PathPoint>> ordered = new ArrayList<>();
        boolean[] used = new boolean[segments.size()];
        double cx = 0, cy = 0;

        for (int round = 0; round < segments.size(); round++) {
            double bestDist = Double.MAX_VALUE;
            int bestIdx = -1;
            boolean bestReverse = false;

            for (int i = 0; i < segments.size(); i++) {
                if (used[i]) continue;
                List<PathPoint> seg = segments.get(i);
                PathPoint start = seg.get(0);
                PathPoint end = seg.get(seg.size() - 1);

                double dStart = dist(cx, cy, start.getX(), start.getY());
                double dEnd = dist(cx, cy, end.getX(), end.getY());

                if (dStart < bestDist) {
                    bestDist = dStart;
                    bestIdx = i;
                    bestReverse = false;
                }
                if (dEnd < bestDist) {
                    bestDist = dEnd;
                    bestIdx = i;
                    bestReverse = true;
                }
            }

            used[bestIdx] = true;
            List<PathPoint> seg = segments.get(bestIdx);
            if (bestReverse) {
                seg = reverseSegment(seg);
            }
            ordered.add(seg);
            PathPoint last = seg.get(seg.size() - 1);
            cx = last.getX();
            cy = last.getY();
        }

        return flatten(ordered);
    }

    private static List<List<PathPoint>> splitSegments(List<PathPoint> path) {
        List<List<PathPoint>> segments = new ArrayList<>();
        List<PathPoint> current = null;
        for (PathPoint pt : path) {
            if ("M".equals(pt.getCmd())) {
                if (current != null && !current.isEmpty()) {
                    segments.add(current);
                }
                current = new ArrayList<>();
            }
            if (current != null) {
                current.add(pt);
            }
        }
        if (current != null && !current.isEmpty()) {
            segments.add(current);
        }
        return segments;
    }

    private static List<PathPoint> reverseSegment(List<PathPoint> seg) {
        if (seg.size() <= 1) return seg;
        List<PathPoint> reversed = new ArrayList<>(seg.size());
        PathPoint last = seg.get(seg.size() - 1);
        reversed.add(PathPoint.move(last.getX(), last.getY(), 0));
        for (int i = seg.size() - 2; i >= 0; i--) {
            PathPoint pt = seg.get(i);
            reversed.add(PathPoint.line(pt.getX(), pt.getY(), 0));
        }
        return reversed;
    }

    private static List<PathPoint> flatten(List<List<PathPoint>> segments) {
        List<PathPoint> result = new ArrayList<>();
        for (List<PathPoint> seg : segments) {
            result.addAll(seg);
        }
        return result;
    }

    private static double dist(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        return dx * dx + dy * dy;
    }
}