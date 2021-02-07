package org.popcraft.bluemapessentials;

import com.earth2me.essentials.IEssentials;
import com.earth2me.essentials.User;
import com.earth2me.essentials.UserMap;
import com.earth2me.essentials.api.IWarps;
import com.earth2me.essentials.commands.WarpNotFoundException;
import com.flowpowered.math.vector.Vector2i;
import com.flowpowered.math.vector.Vector3d;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapAPIListener;
import de.bluecolored.bluemap.api.marker.Marker;
import de.bluecolored.bluemap.api.marker.MarkerAPI;
import de.bluecolored.bluemap.api.marker.MarkerSet;
import de.bluecolored.bluemap.api.marker.POIMarker;
import net.ess3.api.InvalidWorldException;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

public final class BlueMapEssentials extends JavaPlugin implements BlueMapAPIListener {
    private IEssentials essentials;
    private BlueMapAPI blueMap;
    private Set<Marker> warpMarkers;
    private String warpImageURL;
    private long updateInterval;
    private boolean warpsEnabled;
    private String warpLabelFormat;
    private final String MARKERSET_LABEL_WARPS = "warps";

    @Override
    public void onEnable() {
        this.getConfig().options().copyDefaults(true);
        this.getConfig().options().copyHeader(true);
        this.saveConfig();
        this.essentials = (IEssentials) getServer().getPluginManager().getPlugin("Essentials");
        this.warpMarkers = new HashSet<>();
        this.updateInterval = Math.max(1, getConfig().getLong("update-interval", 300));
        this.warpsEnabled = getConfig().getBoolean("warps.enabled", true);
        this.warpLabelFormat = getConfig().getString("warps.label", "%warp%");
        BlueMapAPI.registerListener(this);
        new Metrics(this, 9011);
    }

    @Override
    public void onDisable() {
        BlueMapAPI.unregisterListener(this);
    }

    @Override
    public void onEnable(BlueMapAPI blueMap) {
        this.blueMap = blueMap;
        loadImages();
        addMarkers();
        getServer().getScheduler().runTaskTimerAsynchronously(this, this::refreshMarkers, 0, 20 * updateInterval);
    }

    @Override
    public void onDisable(BlueMapAPI blueMap) {
        getServer().getScheduler().cancelTasks(this);
        removeMarkers();
        this.blueMap = null;
    }

    private void loadImages() {
        try (InputStream warpImage = getResource("warp.png")) {
            if (warpImage != null) {
                this.warpImageURL = blueMap.createImage(ImageIO.read(warpImage), "essentials/warp");
            }
        } catch (IOException ignored) {
        }
    }

    private void addMarkers() {
        if (essentials == null) {
            return;
        }
        try {
            final MarkerAPI markerAPI = blueMap.getMarkerAPI();
            if (warpsEnabled) {
                addWarpMarkers(markerAPI);
            }
            markerAPI.save();
        } catch (IOException ignored) {
        }
    }

    private void addWarpMarkers(MarkerAPI markerAPI) {
        IWarps warps = essentials.getWarps();
        for (final String warp : warps.getList()) {
            final MarkerSet markerSetWarps = markerAPI.createMarkerSet(MARKERSET_LABEL_WARPS);
            final Location warpLocation;
            try {
                warpLocation = warps.getWarp(warp);
            } catch (WarpNotFoundException | InvalidWorldException e) {
                continue;
            }
            World warpWorld = warpLocation.getWorld();
            if (warpWorld == null) {
                continue;
            }
            blueMap.getWorld(warpWorld.getUID()).ifPresent(blueWorld -> blueWorld.getMaps().forEach(map -> {
                String warpMarkerId = String.format("warp:%s:%s", map.getName(), warp);
                Vector3d warpMarkerPos = Vector3d.from(warpLocation.getX(), warpLocation.getY(), warpLocation.getZ());
                POIMarker warpMarker = markerSetWarps.createPOIMarker(warpMarkerId, map, warpMarkerPos);
                warpMarker.setLabel(warpLabelFormat.replace("%warp%", warp));
                Vector2i iconAnchor = warpMarker.getIconAnchor();
                if (warpImageURL != null) {
                    warpMarker.setIcon(warpImageURL, iconAnchor);
                }
                warpMarkers.add(warpMarker);
            }));
        }
    }

    private void removeMarkers() {
        if (essentials == null) {
            return;
        }
        try {
            final MarkerAPI markerAPI = blueMap.getMarkerAPI();
            if (warpsEnabled) {
                final MarkerSet markerSetWarps = markerAPI.createMarkerSet(MARKERSET_LABEL_WARPS);
                warpMarkers.forEach(markerSetWarps::removeMarker);
                warpMarkers.clear();
            }
            markerAPI.save();
        } catch (IOException ignored) {
        }
    }

    private void refreshMarkers() {
        removeMarkers();
        addMarkers();
    }
}
