package xiaozhi.modules.appv2.service.projection;

import java.util.List;

/**
 * Evaluates vectorized SVG path quality for pen plotter suitability.
 * Returns a score 0-100 and identifies specific issues.
 */
public final class VectorizationQualityScorer {

    public record Score(int total, int continuity, int coverage, int complexity,
                        boolean acceptable, String issue) {}

    private static final int MIN_ACCEPTABLE = 60;

    public static Score evaluate(List<PathPoint> path, int viewbox) {
        if (path == null || path.isEmpty()) {
            return new Score(0, 0, 0, 0, false, "empty_path");
        }

        // Single-pass: collect all metrics at once
        int strokes = 0, totalPts = path.size();
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (PathPoint pt : path) {
            if ("M".equals(pt.getCmd())) strokes++;
            minX = Math.min(minX, pt.getX());
            maxX = Math.max(maxX, pt.getX());
            minY = Math.min(minY, pt.getY());
            maxY = Math.max(maxY, pt.getY());
        }

        int continuityScore = scoreContinuity(strokes, totalPts);
        int coverageScore = scoreCoverage(minX, maxX, minY, maxY, viewbox);
        int complexityScore = scoreComplexity(totalPts);

        int total = (continuityScore * 40 + coverageScore * 35 + complexityScore * 25) / 100;
        String issue = identifyIssue(continuityScore, coverageScore, complexityScore);
        return new Score(total, continuityScore, coverageScore, complexityScore,
                total >= MIN_ACCEPTABLE, issue);
    }

    private static int scoreContinuity(int strokes, int totalPts) {
        if (strokes == 0) return 0;
        double ptsPerStroke = (double) totalPts / strokes;
        if (ptsPerStroke >= 15) return 100;
        if (ptsPerStroke >= 8) return 80;
        if (ptsPerStroke >= 4) return 60;
        if (ptsPerStroke >= 2) return 40;
        return 20;
    }

    private static int scoreCoverage(double minX, double maxX,
            double minY, double maxY, int viewbox) {
        double width = maxX - minX, height = maxY - minY;
        double coverageRatio = (width * height) / ((double) viewbox * viewbox);
        if (coverageRatio >= 0.3 && coverageRatio <= 0.9) return 100;
        if (coverageRatio >= 0.2 && coverageRatio <= 0.95) return 70;
        if (coverageRatio >= 0.1) return 40;
        return 10;
    }

    private static int scoreComplexity(int pts) {
        if (pts >= 50 && pts <= 500) return 100;
        if (pts >= 20 && pts <= 800) return 75;
        if (pts >= 10 && pts <= 1000) return 50;
        if (pts < 10) return 20;
        return 30;
    }

    private static String identifyIssue(int cont, int cov, int comp) {
        if (cov < 40) return "too_sparse";
        if (cont < 40) return "too_fragmented";
        if (comp < 50) return "bad_complexity";
        return null;
    }
}