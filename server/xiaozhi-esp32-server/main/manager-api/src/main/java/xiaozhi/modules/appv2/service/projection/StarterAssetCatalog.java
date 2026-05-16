package xiaozhi.modules.appv2.service.projection;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

@Service
public class StarterAssetCatalog {
    public static final String PROJECTION_NAME = "draw_generated_starter_asset_v1";

    private static final Map<String, String> ASSETS = Map.of(
            "starter_star",
            "<svg viewBox=\"0 0 20 20\"><path d=\"M10 2 L12 8 L18 8 L13 12 L15 18 L10 14 L5 18 L7 12 L2 8 L8 8 L10 2\" fill=\"none\" stroke=\"black\"/></svg>",
            "starter_house",
            "<svg viewBox=\"0 0 20 20\"><path d=\"M3 10 L10 3 L17 10 L17 18 L5 18 L5 10 L17 10\" fill=\"none\" stroke=\"black\"/></svg>",
            "starter_tree",
            "<svg viewBox=\"0 0 20 20\"><path d=\"M10 3 L4 11 L8 11 L5 16 L9 16 L9 19 L11 19 L11 16 L15 16 L12 11 L16 11 L10 3\" fill=\"none\" stroke=\"black\"/></svg>",
            "starter_fish",
            "<svg viewBox=\"0 0 20 20\"><path d=\"M3 10 L7 6 L14 6 L18 10 L14 14 L7 14 L3 10 L6 10\" fill=\"none\" stroke=\"black\"/></svg>",
            "starter_flower",
            "<svg viewBox=\"0 0 20 20\"><path d=\"M10 10 L10 18 L10 10 L6 6 L10 10 L14 6 L10 10 L6 14 L10 10 L14 14 L10 10 L10 4\" fill=\"none\" stroke=\"black\"/></svg>");

    public Optional<String> findSvg(String assetId) {
        return Optional.ofNullable(ASSETS.get(assetId));
    }
}
